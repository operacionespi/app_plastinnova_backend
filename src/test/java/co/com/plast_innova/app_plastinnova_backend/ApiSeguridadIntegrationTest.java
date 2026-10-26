package co.com.plast_innova.app_plastinnova_backend;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API por HTTP, con la cadena de seguridad completa: login y JWT, permisos por rol (@PreAuthorize),
 * validación de entradas y formato de los errores (ProblemDetail).
 *
 * <p>Usuarios de prueba (it-*@test.local) con los roles de V2: ADMIN (todos los permisos), OPERADOR
 * (pedido:ver y detalle:cambiar-estado) y SUPERVISOR (además corrige cantidades, V10). Un pedido IT-1 en Creado con
 * una línea de 10 unidades.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSeguridadIntegrationTest extends PruebaIntegracion {

    private static final String CLAVE = "Clave-IT-123";
    private static final String ADMIN = "it-admin@test.local";
    private static final String OPERADOR = "it-oper@test.local";
    private static final String INACTIVO = "it-inactivo@test.local";
    private static final String GERENTE = "it-gerente@test.local";
    private static final String SUPERVISOR = "it-super@test.local";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private long pedidoId;
    private long detalleId;

    @BeforeEach
    void preparar() {
        limpiar();
        usuario(ADMIN, "ADMIN", true);
        usuario(OPERADOR, "OPERADOR", true);
        usuario(INACTIVO, "ADMIN", false);
        usuario(GERENTE, "GERENCIA", true);
        usuario(SUPERVISOR, "SUPERVISOR", true);

        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, nombre) VALUES ('IT-PT', 'IT-PT')");
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, estado_actual_id, fecha_creacion_fm)
                SELECT 'IT-1', 'IT-1', c.id, e.id, SYSUTCDATETIME()
                FROM Cliente c CROSS JOIN Estado_Pedido e WHERE c.filemaker_id = 'IT Cliente' AND e.codigo = 'CREADO'""");
        pedidoId = numero("SELECT id FROM Pedido WHERE filemaker_id = 'IT-1'");
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, pedido_id, producto_id, estado_actual_id, cantidad_solicitada, precio_unitario)
                SELECT 'IT-L1', ?, p.id, e.id, 10, 0
                FROM Producto p CROSS JOIN Estado_Detalle e WHERE p.filemaker_id = 'IT-PT' AND e.codigo = 'CREADO'""", pedidoId);
        detalleId = numero("SELECT id FROM Pedido_Detalle WHERE filemaker_id = 'IT-L1'");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Auditoria_Log", "Historial_Estado_Detalle", "Historial_Estado_Pedido", "Pedido_Detalle",
                "Pedido", "Cliente", "Producto")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        // Usuario_Rol se borra en cascada
        jdbc.update("DELETE FROM Usuario WHERE email LIKE 'it-%@test.local'");
        jdbc.update("DELETE FROM Estado_Pedido WHERE codigo LIKE 'IT[_]%'");
    }

    // ------------------------------------------------------------------ autenticación

    @Test
    void elLoginEntregaUnTokenQueAbreLaApi() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(conToken(get("/api/v1/pedidos/{id}", pedidoId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroPedido").value("IT-1"))
                .andExpect(jsonPath("$.estadoActual.codigo").value("CREADO"))
                .andExpect(jsonPath("$.detalles", hasSize(1)));
    }

    @Test
    void elLoginRechazaCredencialesInvalidasYUsuariosInactivos() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(ADMIN, "otra-clave")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));

        // Un email que no existe responde igual que una contraseña errada (no revela qué cuentas existen)
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("it-nadie@test.local", CLAVE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));

        // Un usuario inactivo se trata como inexistente (la consulta de login filtra activo = 1)
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(INACTIVO, CLAVE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("no-es-un-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.email").value("Formato de email inválido"))
                .andExpect(jsonPath("$.invalid_params.password").value("La contraseña es obligatoria"));
    }

    @Test
    void meDevuelveRolesYPermisosDelUsuarioDelToken() throws Exception {
        mvc.perform(conToken(get("/api/v1/auth/me"), OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OPERADOR))
                .andExpect(jsonPath("$.nombreCompleto").isNotEmpty())
                .andExpect(jsonPath("$.roles", contains("OPERADOR")))
                .andExpect(jsonPath("$.permisos", contains("detalle:cambiar-estado", "pedido:ver", "produccion:ver")));

        // /auth/me y /auth/contrasena no son públicos como /auth/login
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/auth/contrasena").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"x\", \"contrasenaNueva\": \"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elUsuarioCambiaSuPropiaContrasena() throws Exception {
        String token = token(OPERADOR);
        mvc.perform(put("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"otra\", \"contrasenaNueva\": \"Nueva-Clave-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta."));
        mvc.perform(put("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"%s\", \"contrasenaNueva\": \"corta\"}".formatted(CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.contrasenaNueva").exists());
        mvc.perform(put("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"%s\", \"contrasenaNueva\": \"%s\"}".formatted(CLAVE, CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña nueva debe ser distinta de la actual."));

        mvc.perform(put("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"%s\", \"contrasenaNueva\": \"Nueva-Clave-1\"}".formatted(CLAVE)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(OPERADOR, CLAVE)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(OPERADOR, "Nueva-Clave-1")))
                .andExpect(status().isOk());
    }

    @Test
    void gerenciaConsultaPeroNoModifica() throws Exception {
        mvc.perform(conToken(get("/api/v1/dashboard/kpis/pedidos-por-estado"), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/dashboard/vendedores"), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/dashboard/pedidos/conteo-por-estado"), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/pedidos/{id}", pedidoId), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/auth/me"), GERENTE))
                .andExpect(jsonPath("$.permisos", contains("entrega:ver", "inventario:ver", "kpi:ver", "material:ver",
                        "pedido:ver", "produccion:ver")));
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/materiales", pedidoId), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/materiales", pedidoId), OPERADOR))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/inventario/existencias"), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/inventario/existencias"), OPERADOR))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/inventario/movimientos"), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"ENTRADA\", \"itemCodigo\": \"X\", \"slotDestinoId\": 1, \"cantidad\": 1}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/inventario/slots"), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bodegaId\": 1, \"codigo\": \"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/reservas", pedidoId), OPERADOR))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/pedidos/{id}/reservas/comprometer", pedidoId), GERENTE))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/reservas/{detalleId}", pedidoId, detalleId), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 1}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/ordenes-produccion"), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/rutas-produccion"), GERENTE))
                .andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/rutas-produccion"), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"X\", \"etapaIds\": [1]}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/ordenes-produccion"), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productos\": [{\"itemCodigo\": \"X\", \"cantidad\": 1}]}"))
                .andExpect(status().isForbidden());
        // Puede consultar las entregas: responde 409 solo porque esta prueba no carga la bodega de producto terminado
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/entregas", pedidoId), GERENTE))
                .andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/pedidos/{id}/remisiones", pedidoId), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineas\": [{\"detalleId\": %d, \"cantidad\": 1}]}".formatted(detalleId)))
                .andExpect(status().isForbidden());

        mvc.perform(conToken(put("/api/v1/pedidos/{id}/estado", pedidoId), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d}".formatted(estadoPedido("EN_ALISTAMIENTO"))))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 1}".formatted(estadoDetalle("SOLICITADO"))))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/fecha-estimada-entrega", pedidoId), GERENTE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"2030-01-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/sincronizacion/filemaker/ejecuciones"), GERENTE))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/materiales/cargas")
                        .file("bodegas", new byte[0]).file("items", new byte[0]).file("listaMateriales", new byte[0])
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(GERENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elOperadorVeLaColaDeTrabajoDeSuEtapa() throws Exception {
        mvc.perform(conToken(post("/api/v1/pedidos/detalles/busqueda"), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadosIds\": [%d]}".formatted(estadoDetalle("CREADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(detalleId))
                .andExpect(jsonPath("$.content[0].numeroPedido").value("IT-1"))
                .andExpect(jsonPath("$.content[0].estadoActual.color").isNotEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(conToken(post("/api/v1/pedidos/detalles/busqueda"), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadosIds\": [%d]}".formatted(estadoDetalle("CALIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(conToken(post("/api/v1/pedidos/detalles/busqueda"), OPERADOR)
                        .param("sort", "producto.passwordHash")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenOConTokenInvalidoResponde401() throws Exception {
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));

        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());

        // Token bien formado pero alterado: la firma ya no coincide
        String token = token(ADMIN);
        String alterado = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDeUnUsuarioDesactivadoDejaDeFuncionar() throws Exception {
        String token = token(OPERADOR);
        jdbc.update("UPDATE Usuario SET activo = 0 WHERE email = ?", OPERADOR);

        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsSoloPermiteElFrontendConfigurado() throws Exception {
        mvc.perform(options("/api/v1/pedidos/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        mvc.perform(options("/api/v1/pedidos/1")
                        .header(HttpHeaders.ORIGIN, "http://sitio-ajeno.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ permisos por rol

    @Test
    void elOperadorConsultaYReportaAvancePeroNoGestiona() throws Exception {
        mvc.perform(conToken(get("/api/v1/pedidos/{id}", pedidoId), OPERADOR))
                .andExpect(status().isOk());
        mvc.perform(conToken(get("/api/v1/catalogos/estados-detalle"), OPERADOR))
                .andExpect(status().isOk());

        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 10}".formatted(estadoDetalle("SOLICITADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual.codigo").value("SOLICITADO"))
                .andExpect(jsonPath("$.cantidadProcesada").value(10));

        // Sin pedido:cambiar-estado, kpi:ver, estado:gestionar ni sync:ejecutar
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/estado", pedidoId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d}".formatted(estadoPedido("EN_ALISTAMIENTO"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        mvc.perform(conToken(get("/api/v1/dashboard/kpis/pedidos-por-estado"), OPERADOR))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/catalogos/estados-pedido"), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\": \"IT_X\", \"nombre\": \"IT X\", \"ordenLogico\": 9}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/sincronizacion/filemaker/ejecuciones"), OPERADOR))
                .andExpect(status().isForbidden());
        // Ve las órdenes de producción (reporta su avance) pero no las crea ni hace remisiones
        mvc.perform(conToken(get("/api/v1/ordenes-produccion"), OPERADOR))
                .andExpect(status().isOk());
        mvc.perform(conToken(post("/api/v1/ordenes-produccion"), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productos\": [{\"itemCodigo\": \"X\", \"cantidad\": 1}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(post("/api/v1/ordenes-produccion/{id}/bajas", 1), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\": 1, \"etapaId\": 1, \"cantidad\": 1, \"motivo\": \"Rota\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/remisiones"), OPERADOR))
                .andExpect(status().isForbidden());

        // El cambio rechazado no tocó el pedido
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/historial", pedidoId), OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void lasPiezasAvanzanDeEtapaEnEtapaYSoloElSupervisorCorrigeConJustificacion() throws Exception {
        // Creado → Solicitado con 8 de 10: la línea avanza con 8
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 8}".formatted(estadoDetalle("SOLICITADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadProcesada").value(8))
                .andExpect(jsonPath("$.cantidadMaximaReporte").value(8))
                .andExpect(jsonPath("$.avanceReportado").value(true));

        // La siguiente etapa no puede reportar más de 8
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 9}".formatted(estadoDetalle("EN_ENSAMBLE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se pueden reportar 9 piezas: la etapa anterior reportó 8. "
                        + "Si llegaron más, un supervisor debe corregir la cantidad de la etapa anterior."));

        // El operador no corrige; el supervisor sí, pero con justificación
        String correccion = "{\"cantidadProcesada\": 10, \"justificacion\": \"%s\"}";
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/cantidad", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(correccion.formatted("Se contaron mal")))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/cantidad", detalleId), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(correccion.formatted("  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.justificacion").exists());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/cantidad", detalleId), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(correccion.formatted("Dos piezas quedaron sin contar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual.codigo").value("SOLICITADO"))
                .andExpect(jsonPath("$.cantidadProcesada").value(10))
                .andExpect(jsonPath("$.cantidadMaximaReporte").value(10));

        // Con la corrección ya se pueden reportar las 10
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 10}".formatted(estadoDetalle("EN_ENSAMBLE"))))
                .andExpect(status().isOk());

        mvc.perform(conToken(get("/api/v1/pedidos/detalles/{id}/historial", detalleId), OPERADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].tipo").value("CAMBIO_ESTADO"))
                .andExpect(jsonPath("$[1].tipo").value("CORRECCION_CANTIDAD"))
                .andExpect(jsonPath("$[1].cantidadAnterior").value(8))
                .andExpect(jsonPath("$[1].cantidadProcesada").value(10))
                .andExpect(jsonPath("$[1].usuarioEmail").value(SUPERVISOR))
                .andExpect(jsonPath("$[1].observaciones").value("Dos piezas quedaron sin contar"));

        // En la cola de trabajo la línea informa el tope del próximo reporte
        mvc.perform(conToken(post("/api/v1/pedidos/detalles/busqueda"), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cantidadMaximaReporte").value(10))
                .andExpect(jsonPath("$.content[0].avanceReportado").value(true));
    }

    @Test
    void soloElSupervisorMarcaNoSaleYSiTodasLasLineasQuedanAsiElPedidoSeAnulaHastaQueDevuelvaUna() throws Exception {
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, pedido_id, producto_id, estado_actual_id, cantidad_solicitada, precio_unitario)
                SELECT 'IT-L2', ?, p.id, e.id, 5, 0
                FROM Producto p CROSS JOIN Estado_Detalle e WHERE p.filemaker_id = 'IT-PT' AND e.codigo = 'EN_ENSAMBLE'""", pedidoId);
        long linea2 = numero("SELECT id FROM Pedido_Detalle WHERE filemaker_id = 'IT-L2'");
        String justificacion = "{\"justificacion\": \"%s\"}";
        String motivo = justificacion.formatted("Proveedor sin materia prima");

        // Ni el operador ni el administrador: solo el supervisor, y siempre justificando
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", detalleId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", detalleId), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(justificacion.formatted("No hay")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.justificacion").exists());
        // Tampoco como un paso más del proceso
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 0}".formatted(estadoDetalle("NO_SALE"))))
                .andExpect(status().isConflict());

        // Primera línea: el pedido sigue abierto
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", detalleId), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linea.estadoActual.codigo").value("NO_SALE"))
                .andExpect(jsonPath("$.estadoPedido.codigo").value("CREADO"))
                .andExpect(jsonPath("$.cambioEstadoPedido").value(false));
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", detalleId), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isConflict());

        // La última: el pedido se anula y se cierra
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/no-sale", linea2), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPedido.codigo").value("ANULADO"))
                .andExpect(jsonPath("$.cambioEstadoPedido").value(true));
        mvc.perform(conToken(get("/api/v1/pedidos/{id}", pedidoId), SUPERVISOR))
                .andExpect(jsonPath("$.estadoActual.codigo").value("ANULADO"))
                .andExpect(jsonPath("$.fechaCierre").exists());

        // Devolver una línea: vuelve a su etapa y el pedido se reabre en el estado que tenía
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/devolucion", linea2), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isForbidden());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/devolucion", linea2), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(justificacion.formatted("Llegó el material del proveedor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linea.estadoActual.codigo").value("EN_ENSAMBLE"))
                .andExpect(jsonPath("$.estadoPedido.codigo").value("CREADO"))
                .andExpect(jsonPath("$.cambioEstadoPedido").value(true));
        mvc.perform(conToken(get("/api/v1/pedidos/{id}", pedidoId), SUPERVISOR))
                .andExpect(jsonPath("$.estadoActual.codigo").value("CREADO"))
                .andExpect(jsonPath("$.fechaCierre").doesNotExist());
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/devolucion", linea2), SUPERVISOR)
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isConflict());

        mvc.perform(conToken(get("/api/v1/pedidos/detalles/{id}/historial", linea2), SUPERVISOR))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("DEVOLUCION"))
                .andExpect(jsonPath("$[0].observaciones").value("Llegó el material del proveedor"))
                .andExpect(jsonPath("$[1].tipo").value("NO_SALE"))
                .andExpect(jsonPath("$[1].estadoAnterior.codigo").value("EN_ENSAMBLE"))
                .andExpect(jsonPath("$[1].usuarioEmail").value(SUPERVISOR));
        mvc.perform(conToken(get("/api/v1/pedidos/{id}/historial", pedidoId), SUPERVISOR))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].estado.codigo").value("CREADO"))
                .andExpect(jsonPath("$[1].estado.codigo").value("ANULADO"))
                .andExpect(jsonPath("$[1].observaciones").value(
                        "Anulado: todas sus líneas están en No sale por no disponibilidad de producto. Proveedor sin materia prima"));
    }

    @Test
    void losEstadosNoSaleYAnuladoNoSeBorranNiLlevanTransiciones() throws Exception {
        mvc.perform(conToken(delete("/api/v1/catalogos/estados-detalle/{id}", estadoDetalle("NO_SALE")), ADMIN))
                .andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/catalogos/estados-detalle/transiciones"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoOrigenId\": %d, \"estadoDestinoId\": %d}".formatted(estadoDetalle("CREADO"), estadoDetalle("NO_SALE"))))
                .andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/catalogos/estados-pedido/transiciones"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estadoOrigenId\": %d, \"estadoDestinoId\": %d}".formatted(estadoPedido("CREADO"), estadoPedido("ANULADO"))))
                .andExpect(status().isConflict());
        // El nombre sí se puede cambiar; el código no
        String edicion = "{\"codigo\": \"%s\", \"nombre\": \"No se despacha\", \"ordenLogico\": 80, \"esInicial\": false, \"esFinal\": true}";
        mvc.perform(conToken(put("/api/v1/catalogos/estados-detalle/{id}", estadoDetalle("NO_SALE")), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(edicion.formatted("SIN_STOCK")))
                .andExpect(status().isConflict());
        mvc.perform(conToken(put("/api/v1/catalogos/estados-detalle/{id}", estadoDetalle("NO_SALE")), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(edicion.formatted("NO_SALE")))
                .andExpect(status().isOk());
        jdbc.update("UPDATE Estado_Detalle SET nombre = 'No sale' WHERE codigo = 'NO_SALE'");
    }

    // ------------------------------------------------------------------ gestión de usuarios (punto F)

    @Test
    void soloElAdministradorGestionaUsuarios() throws Exception {
        mvc.perform(conToken(get("/api/v1/usuarios"), SUPERVISOR)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/usuarios"), GERENTE)).andExpect(status().isForbidden());
        mvc.perform(conToken(get("/api/v1/usuarios").param("texto", "it-OPER"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(OPERADOR))
                .andExpect(jsonPath("$[0].roles", contains("OPERADOR")));
        // Los inactivos solo si se piden
        mvc.perform(conToken(get("/api/v1/usuarios").param("texto", "inactivo"), ADMIN))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(conToken(get("/api/v1/usuarios").param("texto", "inactivo").param("incluirInactivos", "true"), ADMIN))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(conToken(get("/api/v1/usuarios/roles"), ADMIN))
                .andExpect(jsonPath("$[*].codigo", contains("ADMIN", "COORDINADOR", "SUPERVISOR", "OPERADOR", "GERENCIA")));
    }

    @Test
    void elUsuarioNuevoEntraConContrasenaTemporalYSinPermisosHastaCambiarla() throws Exception {
        String creado = mvc.perform(conToken(post("/api/v1/usuarios"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"IT-Nuevo@Test.local\", \"nombreCompleto\": \" Nora  Nueva \", \"roles\": [\"SUPERVISOR\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.email").value("it-nuevo@test.local"))
                .andExpect(jsonPath("$.usuario.nombreCompleto").value("Nora Nueva"))
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporal = JsonPath.read(creado, "$.contrasenaTemporal");
        assertTrue(temporal.matches("([A-HJ-NP-Za-km-z2-9]{5}-){3}[A-HJ-NP-Za-km-z2-9]{5}"), temporal);

        // Correo repetido (sin importar mayúsculas) y roles inexistentes
        mvc.perform(conToken(post("/api/v1/usuarios"), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"it-NUEVO@test.local\", \"nombreCompleto\": \"Otra\", \"roles\": [\"OPERADOR\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(conToken(post("/api/v1/usuarios"), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"it-otro@test.local\", \"nombreCompleto\": \"Otro\", \"roles\": [\"JEFE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Roles inexistentes: JEFE."));

        // Con la temporal: ve su cuenta pero no tiene permisos
        String token = tokenCon("it-nuevo@test.local", temporal);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                .andExpect(jsonPath("$.roles", contains("SUPERVISOR")))
                .andExpect(jsonPath("$.permisos", hasSize(0)));
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        // Al cambiarla recupera sus permisos con el mismo token
        mvc.perform(put("/api/v1/auth/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\": \"%s\", \"contrasenaNueva\": \"Propia-Clave-2026\"}".formatted(temporal)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(false))
                .andExpect(jsonPath("$.permisos", hasItem("detalle:no-sale")));
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(1, numero("""
                SELECT COUNT(*) FROM Auditoria_Log a JOIN Usuario u ON CAST(u.id AS NVARCHAR(100)) = a.registro_id
                WHERE a.tabla_afectada = 'Usuario' AND a.campo_modificado = 'creacion' AND u.email = 'it-nuevo@test.local'"""));
    }

    @Test
    void restablecerYDesactivarCierranLasSesionesAbiertas() throws Exception {
        long operadorId = numero("SELECT id FROM Usuario WHERE email = '" + OPERADOR + "'");
        String sesionAbierta = token(OPERADOR);

        String respuesta = mvc.perform(conToken(post("/api/v1/usuarios/{id}/contrasena-temporal", operadorId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporal = JsonPath.read(respuesta, "$.contrasenaTemporal");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionAbierta))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(OPERADOR, CLAVE)))
                .andExpect(status().isUnauthorized());
        String nueva = tokenCon(OPERADOR, temporal);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + nueva))
                .andExpect(status().isOk());

        // Desactivar: fuera de inmediato; al reactivarlo, sus tokens anteriores siguen sin servir
        mvc.perform(conToken(patch("/api/v1/usuarios/{id}/desactivar", operadorId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + nueva))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales(OPERADOR, temporal)))
                .andExpect(status().isUnauthorized());
        mvc.perform(conToken(post("/api/v1/usuarios/{id}/contrasena-temporal", operadorId), ADMIN))
                .andExpect(status().isConflict());
        mvc.perform(conToken(patch("/api/v1/usuarios/{id}/activar", operadorId), ADMIN))
                .andExpect(jsonPath("$.activo").value(true));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + nueva))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCon(OPERADOR, temporal)))
                .andExpect(status().isOk());
    }

    @Test
    void elAdministradorNoSeDesactivaNiSeQuitaSuRolYCambiaRolesDeOtros() throws Exception {
        long adminId = numero("SELECT id FROM Usuario WHERE email = '" + ADMIN + "'");
        long operadorId = numero("SELECT id FROM Usuario WHERE email = '" + OPERADOR + "'");
        mvc.perform(conToken(patch("/api/v1/usuarios/{id}/desactivar", adminId), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No puede desactivar su propio usuario."));
        mvc.perform(conToken(put("/api/v1/usuarios/{id}", adminId), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCompleto\": \"Admin\", \"roles\": [\"COORDINADOR\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No puede quitarse su propio rol de Administrador."));
        mvc.perform(conToken(post("/api/v1/usuarios/{id}/contrasena-temporal", adminId), ADMIN))
                .andExpect(status().isConflict());

        // Roles de otro usuario: aplican desde su siguiente petición
        String sesionOperador = token(OPERADOR);
        mvc.perform(conToken(put("/api/v1/usuarios/{id}", operadorId), ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCompleto\": \"Olga Operadora\", \"roles\": [\"OPERADOR\", \"SUPERVISOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("OPERADOR", "SUPERVISOR")));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionOperador))
                .andExpect(jsonPath("$.nombreCompleto").value("Olga Operadora"))
                .andExpect(jsonPath("$.permisos", hasItem("detalle:no-sale")));
        assertEquals(1, numero("""
                SELECT COUNT(*) FROM Auditoria_Log WHERE tabla_afectada = 'Usuario' AND campo_modificado = 'roles'
                  AND valor_anterior = 'OPERADOR' AND valor_nuevo = 'OPERADOR, SUPERVISOR'"""));
    }

    // ------------------------------------------------------------------ reglas de negocio y validación

    @Test
    void cambiarElEstadoDelPedidoRegistraElUsuarioDelToken() throws Exception {
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/estado", pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"observaciones\": \"Inicio\"}".formatted(estadoPedido("EN_ALISTAMIENTO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual.codigo").value("EN_ALISTAMIENTO"));

        mvc.perform(conToken(get("/api/v1/pedidos/{id}/historial", pedidoId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuarioEmail").value(ADMIN))
                .andExpect(jsonPath("$[0].estadoAnterior.codigo").value("CREADO"))
                .andExpect(jsonPath("$[0].observaciones").value("Inicio"));
    }

    @Test
    void losErroresSeRespondenComoProblemDetail() throws Exception {
        // 409: transición no configurada
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/estado", pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d}".formatted(estadoPedido("FACTURADO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "No se permite pasar de Creado a Facturado; los estados permitidos son: En alistamiento."));

        // 409: cantidad mayor a la solicitada
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": %d, \"cantidadProcesada\": 11}".formatted(estadoDetalle("SOLICITADO"))))
                .andExpect(status().isConflict());

        // 400: validación del cuerpo, JSON mal formado y parámetros
        mvc.perform(conToken(put("/api/v1/pedidos/detalles/{id}/estado", detalleId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidadProcesada\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid_params.nuevoEstadoId").value("El ID del nuevo estado es obligatorio"))
                .andExpect(jsonPath("$.invalid_params.cantidadProcesada").value("La cantidad procesada no puede ser negativa"));
        mvc.perform(conToken(put("/api/v1/pedidos/{id}/estado", pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstadoId\": "))
                .andExpect(status().isBadRequest());
        mvc.perform(conToken(get("/api/v1/dashboard/kpis/tiempo-entrega"), ADMIN)
                        .param("desde", "01/10/2026").param("hasta", "2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El valor del parámetro 'desde' no es válido."));
        mvc.perform(conToken(get("/api/v1/dashboard/kpis/tiempo-entrega"), ADMIN).param("desde", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el parámetro obligatorio 'hasta'."));
        mvc.perform(conToken(post("/api/v1/dashboard/pedidos/busqueda"), ADMIN)
                        .param("sort", "passwordHash,asc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        // 404
        mvc.perform(conToken(get("/api/v1/pedidos/{id}", 999_999_999L), ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));

        // El listado sí responde con un orden permitido
        mvc.perform(conToken(post("/api/v1/dashboard/pedidos/busqueda"), ADMIN)
                        .param("sort", "numeroPedido,asc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroPedido").value("IT-1"))
                // Formato estable de página (PagedModel), no el de PageImpl
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.totalElements").doesNotExist());

        // OpenAPI documenta la misma forma (de ahí se genera el cliente del frontend)
        mvc.perform(conToken(get("/v3/api-docs"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PagedModelPedidoResumenDTO.properties.page").exists())
                .andExpect(jsonPath("$.components.schemas.PagedModelSyncLogResponseDTO.properties.content").exists())
                // operationId estables (controlador + método): los usa el cliente generado del frontend
                .andExpect(jsonPath("$.paths['/api/v1/catalogos/estados-pedido'].get.operationId").value("estadosPedidoListar"))
                .andExpect(jsonPath("$.paths['/api/v1/catalogos/estados-detalle'].get.operationId").value("estadosDetalleListar"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.operationId").value("authUsuarioActual"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.responses['200'].content['application/json']").exists());
    }

    @Test
    void crearUnEstadoSinIndicarInicialNiFinalLosDejaEnFalse() throws Exception {
        mvc.perform(conToken(post("/api/v1/catalogos/estados-pedido"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\": \"IT_X\", \"nombre\": \"IT X\", \"ordenLogico\": 9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("IT_X"))
                .andExpect(jsonPath("$.esInicial").value(false))
                .andExpect(jsonPath("$.esFinal").value(false));
    }

    // ------------------------------------------------------------------ fecha estimada de entrega

    @Test
    void laFechaDeEntregaSeAsignaConPermisoYQuedaAuditada() throws Exception {
        String url = "/api/v1/pedidos/{id}/fecha-estimada-entrega";
        mvc.perform(conToken(put(url, pedidoId), OPERADOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"2026-10-20\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(conToken(put(url, pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"2026-10-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaEstimadaEntrega").value("2026-10-20"));
        // Repetir la misma fecha no genera otra auditoría
        mvc.perform(conToken(put(url, pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"2026-10-20\"}"))
                .andExpect(status().isOk());
        mvc.perform(conToken(put(url, pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaEstimadaEntrega").doesNotExist());

        assertEquals(List.of("null→2026-10-20|" + ADMIN, "2026-10-20→null|" + ADMIN), jdbc.queryForList("""
                SELECT ISNULL(a.valor_anterior, 'null') + N'→' + ISNULL(a.valor_nuevo, 'null') + '|' + u.email
                FROM Auditoria_Log a JOIN Usuario u ON u.id = a.usuario_id
                WHERE a.tabla_afectada = 'Pedido' AND a.registro_id = ? AND a.campo_modificado = 'fecha_estimada_entrega'
                ORDER BY a.id""", String.class, String.valueOf(pedidoId)));

        mvc.perform(conToken(put(url, pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"20/10/2026\"}"))
                .andExpect(status().isBadRequest());

        // Pedido cerrado: la fecha ya no cambia
        jdbc.update("UPDATE Pedido SET fecha_cierre = SYSUTCDATETIME() WHERE id = ?", pedidoId);
        mvc.perform(conToken(put(url, pedidoId), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaEstimadaEntrega\": \"2026-10-21\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pedido ya está cerrado; su fecha estimada de entrega no se puede cambiar."));
    }

    @Test
    void unPedidoSeAtrasaDesdeElDiaSiguienteASuFechaDeEntrega() throws Exception {
        LocalDate hoy = LocalDate.now(FechaUtc.ZONA_COLOMBIA);

        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = ? WHERE id = ?", hoy, pedidoId);
        mvc.perform(conToken(post("/api/v1/dashboard/pedidos/busqueda"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"soloAtrasados\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = ? WHERE id = ?", hoy.minusDays(1), pedidoId);
        mvc.perform(conToken(post("/api/v1/dashboard/pedidos/busqueda"), ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"soloAtrasados\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fechaEstimadaEntrega").value(hoy.minusDays(1).toString()));
    }

    // ------------------------------------------------------------------ datos de prueba

    private void usuario(String email, String rol, boolean activo) {
        jdbc.update("INSERT INTO Usuario (email, password_hash, nombre_completo, activo) VALUES (?, ?, ?, ?)",
                email, passwordEncoder.encode(CLAVE), email, activo);
        jdbc.update("""
                INSERT INTO Usuario_Rol (usuario_id, rol_id)
                SELECT u.id, r.id FROM Usuario u CROSS JOIN Rol r WHERE u.email = ? AND r.codigo = ?""", email, rol);
    }

    private String token(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(email, CLAVE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.accessToken");
    }

    private String tokenCon(String email, String clave) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(email, clave)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.accessToken");
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String email) throws Exception {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(email));
    }

    private static String credenciales(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private int estadoPedido(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Pedido WHERE codigo = ?", Integer.class, codigo);
    }

    private int estadoDetalle(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Detalle WHERE codigo = ?", Integer.class, codigo);
    }

    private long numero(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
