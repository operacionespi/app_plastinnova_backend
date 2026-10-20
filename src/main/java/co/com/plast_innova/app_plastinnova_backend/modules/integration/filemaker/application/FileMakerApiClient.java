package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerAuthResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerRegistrosResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente de la Data API de FileMaker. Se usa desde hilos de sincronización (nunca desde peticiones HTTP),
 * por eso las llamadas son bloqueantes.
 */
@Component
public class FileMakerApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Código de FileMaker para "No records match the request". */
    private static final Pattern SIN_REGISTROS = Pattern.compile("\"code\"\\s*:\\s*\"401\"");
    private static final FileMakerRegistrosResponseDTO SIN_RESULTADOS = new FileMakerRegistrosResponseDTO(
            new FileMakerRegistrosResponseDTO.Response(new FileMakerRegistrosResponseDTO.DataInfo(0, 0, null), List.of()));

    private final WebClient fileMakerWebClient;
    private final String fmUser;
    private final String fmPassword;
    private final String database;

    public FileMakerApiClient(WebClient fileMakerWebClient,
                              @Value("${app.filemaker.api.username}") String fmUser,
                              @Value("${app.filemaker.api.password}") String fmPassword,
                              @Value("${app.filemaker.api.database}") String database) {
        this.fileMakerWebClient = fileMakerWebClient;
        this.fmUser = fmUser;
        this.fmPassword = fmPassword;
        this.database = database;
    }

    /**
     * Autentica contra la API de FileMaker y retorna el Bearer Token.
     */
    public String authenticate() {
        String credentials = Base64.getEncoder()
                .encodeToString((fmUser + ":" + fmPassword).getBytes(StandardCharsets.UTF_8));

        FileMakerAuthResponseDTO authResponse = fileMakerWebClient.post()
                .uri("/{db}/sessions", database)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, FileMakerApiClient::error)
                .bodyToMono(FileMakerAuthResponseDTO.class)
                .block();

        if (authResponse == null || authResponse.response() == null || authResponse.response().token() == null) {
            throw new IllegalStateException("No se pudo obtener el token de FileMaker.");
        }
        return authResponse.response().token();
    }

    /**
     * Busca registros de un layout (POST /_find). Si ningún registro cumple el criterio, FileMaker responde
     * con error 401 ("No records match the request"): se retorna una página vacía.
     *
     * @param criterios campo → criterio de búsqueda de FileMaker, ej. {"LinPed_FechaCreacion": ">=07/01/2026"}
     * @param offset    posición del primer registro (FileMaker empieza en 1)
     */
    public FileMakerRegistrosResponseDTO buscarRegistros(String token, String layout, Map<String, String> criterios,
                                                         int offset, int limite) {
        return buscarRegistros(token, layout, List.of(criterios), offset, limite);
    }

    /** Igual que {@link #buscarRegistros(String, String, Map, int, int)} con varias consultas: un registro basta que cumpla una (OR). */
    public FileMakerRegistrosResponseDTO buscarRegistros(String token, String layout, List<Map<String, String>> consultas,
                                                         int offset, int limite) {
        Map<String, Object> cuerpo = Map.of("query", consultas, "offset", offset, "limit", limite);
        FileMakerRegistrosResponseDTO respuesta = fileMakerWebClient.post()
                .uri("/{db}/layouts/{layout}/_find", database, layout)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("").map(json -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return JSON.readValue(json, FileMakerRegistrosResponseDTO.class);
                    }
                    if (SIN_REGISTROS.matcher(json).find()) {
                        return SIN_RESULTADOS;
                    }
                    throw new IllegalStateException(
                            "FileMaker respondió HTTP " + response.statusCode().value() + ": " + json);
                }))
                .block();

        if (respuesta == null || respuesta.response() == null) {
            throw new IllegalStateException("FileMaker devolvió una respuesta vacía para el layout " + layout);
        }
        return respuesta;
    }

    /**
     * Cierra la sesión activa en FileMaker para no consumir licencias concurrentes.
     */
    public void logout(String token) {
        fileMakerWebClient.delete()
                .uri("/{db}/sessions/{token}", database, token)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty()) // Ignoramos errores de logout para no afectar el flujo
                .block();
    }

    /** Incluye el cuerpo de la respuesta (código y mensaje de FileMaker) en el error. */
    private static Mono<Throwable> error(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(cuerpo -> new IllegalStateException(
                        "FileMaker respondió HTTP " + response.statusCode().value() + ": " + cuerpo));
    }
}
