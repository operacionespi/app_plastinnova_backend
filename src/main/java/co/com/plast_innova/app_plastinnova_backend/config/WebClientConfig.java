package co.com.plast_innova.app_plastinnova_backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${app.filemaker.api.base-url}")
    private String filemakerBaseUrl;

    @Value("${app.filemaker.api.verificar-tls}")
    private boolean verificarTls;

    @Bean
    public WebClient fileMakerWebClient() throws SSLException {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5 segundos para conectar
                // Páginas de cientos de registros: FileMaker puede tardar varios segundos en armarlas
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
        if (!verificarTls) {
            log.warn("Verificación TLS DESACTIVADA para FileMaker ({}): se acepta cualquier certificado. "
                    + "Instale un certificado válido y quite FM_VERIFICAR_TLS=false.", filemakerBaseUrl);
            SslContext sinVerificacion = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            // El certificado tampoco corresponde a la IP: se omite también la verificación del nombre del host
            httpClient = httpClient.secure(ssl -> ssl.sslContext(sinVerificacion)
                    .handlerConfigurator(handler -> {
                        SSLEngine engine = handler.engine();
                        SSLParameters parametros = engine.getSSLParameters();
                        parametros.setEndpointIdentificationAlgorithm(null);
                        engine.setSSLParameters(parametros);
                    }));
        }
        return WebClient.builder()
                .baseUrl(filemakerBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Una página de registros supera el límite por defecto de 256 KB
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
