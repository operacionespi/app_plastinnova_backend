package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FilaPedidoFm;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerRegistrosResponseDTO.Registro;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.mapper.FilaPedidoFmParser;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.mapper.FilaPedidoFmParser.FilaInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilaPedidoFmParserTest {

    private static Map<String, Object> camposValidos() {
        Map<String, Object> campos = new HashMap<>();
        campos.put("LinPed_FechaCreacion", "10/08/2026 08:30:00");
        campos.put("LinPed_AlistamArea", "Internacional");
        campos.put("LinPed_Estado", "En ensamble");
        campos.put("LinPed_PosPed", 2);
        campos.put("LinPed_CodPT", " PT-100 ");
        campos.put("LinPed_CantPedido", 150);
        campos.put("LinPed_NumPedido", "5001");
        campos.put("LinPed_Cliente", "Plásticos  del Valle S.A.S.");
        campos.put("Pedidos::Ped_Estado", "Creado");
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Ciudad", "Cali");
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Pais", "Colombia");
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Distribuidor", "Juan Pérez");
        campos.put("LinPed_Anexo", "SI");
        return campos;
    }

    @Test
    void parseaRegistroCompletoYConvierteFechaAUtc() {
        FilaPedidoFm fila = FilaPedidoFmParser.parsear(new Registro(camposValidos(), "77", "3"));

        assertEquals("77", fila.recordId());
        assertEquals(3, fila.modId());
        assertEquals("5001", fila.numeroPedido());
        assertEquals(2, fila.posicion());
        assertEquals("PT-100", fila.codigoProducto());
        assertEquals(150, fila.cantidad());
        // 08:30 en Bogotá (UTC-5) = 13:30 UTC
        assertEquals(LocalDateTime.of(2026, 10, 8, 13, 30), fila.fechaCreacionUtc());
        assertEquals(TipoAlistamiento.INTERNACIONAL, fila.tipoAlistamiento());
        assertTrue(fila.esAnexo());
        assertEquals("Plásticos del Valle S.A.S.", fila.cliente());
        assertEquals("Juan Pérez", fila.vendedor());
        assertEquals(64, fila.huella().length());
    }

    @Test
    void aceptaFechaSinHoraYNumerosComoTexto() {
        Map<String, Object> campos = camposValidos();
        campos.put("LinPed_FechaCreacion", "01/15/2026");
        campos.put("LinPed_CantPedido", "20");
        campos.put("LinPed_PosPed", "");
        campos.put("LinPed_AlistamArea", "NACIONAL");

        FilaPedidoFm fila = FilaPedidoFmParser.parsear(new Registro(campos, "1", "0"));

        assertEquals(LocalDateTime.of(2026, 1, 15, 5, 0), fila.fechaCreacionUtc());
        assertEquals(20, fila.cantidad());
        assertNull(fila.posicion());
        assertEquals(TipoAlistamiento.NACIONAL, fila.tipoAlistamiento());
    }

    @Test
    void soloInternacionalesEsInternacionalYCualquierOtraAreaEsNacional() {
        Map<String, TipoAlistamiento> esperado = new HashMap<>();
        esperado.put("Internacionales", TipoAlistamiento.INTERNACIONAL);
        esperado.put("INTERNACIONAL", TipoAlistamiento.INTERNACIONAL);
        esperado.put("Nacionales", TipoAlistamiento.NACIONAL);
        esperado.put("PuntoDeVenta", TipoAlistamiento.NACIONAL);
        esperado.put("TapaTuerca", TipoAlistamiento.NACIONAL);
        esperado.put("Locales", TipoAlistamiento.NACIONAL);
        esperado.put("", TipoAlistamiento.NACIONAL);
        esperado.forEach((area, tipo) -> {
            Map<String, Object> campos = camposValidos();
            campos.put("LinPed_AlistamArea", area);
            assertEquals(tipo, FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).tipoAlistamiento(), area);
        });
    }

    @Test
    void soloSiMarcaLaLineaComoAnexo() {
        Map<String, Boolean> esperado = Map.of("SI", true, "Sí", true, "No", false, "NO", false, "", false);
        esperado.forEach((valor, anexo) -> {
            Map<String, Object> campos = camposValidos();
            campos.put("LinPed_Anexo", valor);
            assertEquals(anexo, FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).esAnexo(), valor);
        });
        Map<String, Object> sinCampo = camposValidos();
        sinCampo.remove("LinPed_Anexo");
        assertFalse(FilaPedidoFmParser.parsear(new Registro(sinCampo, "1", "0")).esAnexo());
    }

    @Test
    void lecturaParcialToleraRegistrosInvalidos() {
        Map<String, Object> campos = camposValidos();
        campos.put("LinPed_CodPT", "");               // inválido para parsear(), no para la lectura parcial
        campos.put("zMarcaFechaYHoraModificación", "10/09/2026 17:45:10");
        Registro registro = new Registro(campos, "1", "0");

        assertEquals("5001", FilaPedidoFmParser.numeroPedido(registro));
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 45, 10), FilaPedidoFmParser.fechaModificacionUtc(registro));
        assertEquals(LocalDateTime.of(2026, 10, 8, 13, 30), FilaPedidoFmParser.fechaCreacionUtc(registro));

        campos.put("zMarcaFechaYHoraModificación", "ayer");
        campos.remove("LinPed_FechaCreacion");
        assertNull(FilaPedidoFmParser.fechaModificacionUtc(registro));
        assertNull(FilaPedidoFmParser.fechaCreacionUtc(registro));
    }

    @Test
    void rechazaCamposObligatoriosVaciosYValoresInvalidos() {
        Map<String, Object> sinProducto = camposValidos();
        sinProducto.put("LinPed_CodPT", "");
        assertTrue(assertThrows(FilaInvalidaException.class,
                () -> FilaPedidoFmParser.parsear(new Registro(sinProducto, "1", "0")))
                .getMessage().contains("LinPed_CodPT"));

        Map<String, Object> cantidadDecimal = camposValidos();
        cantidadDecimal.put("LinPed_CantPedido", 2.5);
        assertThrows(FilaInvalidaException.class,
                () -> FilaPedidoFmParser.parsear(new Registro(cantidadDecimal, "1", "0")));

        Map<String, Object> fechaInvalida = camposValidos();
        fechaInvalida.put("LinPed_FechaCreacion", "2026-10-08");
        assertThrows(FilaInvalidaException.class,
                () -> FilaPedidoFmParser.parsear(new Registro(fechaInvalida, "1", "0")));
    }

    @Test
    void laFechaDeModificacionEsOpcionalYSeConvierteAUtc() {
        Map<String, Object> campos = camposValidos();
        assertNull(FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).fechaModificacionUtc());

        campos.put("zMarcaFechaYHoraModificación", "");
        assertNull(FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).fechaModificacionUtc());

        campos.put("zMarcaFechaYHoraModificación", "10/09/2026 17:45:10");
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 45, 10),
                FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).fechaModificacionUtc());

        // Mismo nombre con la tilde como carácter aparte (NFD)
        Map<String, Object> nfd = camposValidos();
        nfd.put(Normalizer.normalize("zMarcaFechaYHoraModificación", Normalizer.Form.NFD), "10/09/2026 17:45:10");
        assertEquals(LocalDateTime.of(2026, 10, 9, 22, 45, 10),
                FilaPedidoFmParser.parsear(new Registro(nfd, "1", "0")).fechaModificacionUtc());

        Map<String, Object> invalida = camposValidos();
        invalida.put("zMarcaFechaYHoraModificación", "ayer");
        assertTrue(assertThrows(FilaInvalidaException.class,
                () -> FilaPedidoFmParser.parsear(new Registro(invalida, "1", "0")))
                .getMessage().contains("zMarcaFechaYHoraModificación"));
    }

    @Test
    void laHuellaCambiaSiCambiaUnCampoRelacionado() {
        Map<String, Object> campos = camposValidos();
        String antes = FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).huella();
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Distribuidor", "Ana Gómez");
        // El modId no cambia (el vendedor es un campo del cliente), pero la huella sí
        String despues = FilaPedidoFmParser.parsear(new Registro(campos, "1", "0")).huella();
        assertNotEquals(antes, despues);
    }
}
