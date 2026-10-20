package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.CatalogoEstados;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.CatalogoEstados.Resolucion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoEstadosTest {

    private static EstadoDetalle detalle(int id, String codigo, String nombre, boolean inicial) {
        EstadoDetalle e = new EstadoDetalle();
        e.setId(id);
        e.setCodigo(codigo);
        e.setNombre(nombre);
        e.setEsInicial(inicial);
        return e;
    }

    private static EstadoPedido pedido(int id, String codigo, String nombre, boolean inicial) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        e.setCodigo(codigo);
        e.setNombre(nombre);
        e.setEsInicial(inicial);
        return e;
    }

    private final CatalogoEstados catalogo = CatalogoEstados.de(
            List.of(pedido(1, "CREADO", "Creado", true), pedido(2, "EN_ALISTAMIENTO", "En alistamiento", false)),
            List.of(detalle(10, "CREADO", "Creado", true),
                    detalle(13, "EN_ENSAMBLE", "En ensamble", false),
                    detalle(16, "ALMACEN1", "Almacén 1", false)),
            List.of("Despachado", "Entregado", "Anulado"),
            List.of("ParaAlistar:CREADO"),
            List.of("EnAlistamiento:CREADO", "Pausa:NO_EXISTE"));

    @Test
    void reconoceNombreYCodigoSinImportarTildesNiMayusculas() {
        assertEquals(13, catalogo.estadoDetalle("En ensamble").estadoId());
        assertEquals(13, catalogo.estadoDetalle("EN ENSAMBLE").estadoId());
        assertEquals(16, catalogo.estadoDetalle("Almacen1").estadoId());   // por código
        assertEquals(16, catalogo.estadoDetalle("almacen 1").estadoId());  // por nombre sin tilde
        assertEquals(2, catalogo.estadoPedido(" En  alistamiento ").estadoId());
        assertEquals(2, catalogo.estadoPedido("EnAlistamiento").estadoId());   // pegado, como lo envía FileMaker
        assertEquals(13, catalogo.estadoDetalle("EnEnsamble").estadoId());
    }

    @Test
    void aplicaLasEquivalenciasConfiguradas() {
        Resolucion paraAlistar = catalogo.estadoPedido("ParaAlistar");
        assertEquals(1, paraAlistar.estadoId());
        assertTrue(paraAlistar.reconocido());   // equivalencia explícita: sin aviso
        assertEquals(10, catalogo.estadoDetalle("EnAlistamiento").estadoId());
        assertTrue(catalogo.estadoDetalle("EnAlistamiento").reconocido());
        // Código destino inexistente: se trata como no reconocido
        assertFalse(catalogo.estadoDetalle("Pausa").reconocido());
        // La equivalencia de líneas no afecta a los pedidos: "En alistamiento" sigue existiendo para el pedido
        assertEquals(2, catalogo.estadoPedido("EnAlistamiento").estadoId());
        assertThrows(IllegalStateException.class, () -> CatalogoEstados.de(
                List.of(pedido(1, "CREADO", "Creado", true)), List.of(detalle(10, "CREADO", "Creado", true)),
                List.of(), List.of("SinCodigo"), List.of()));
    }

    @Test
    void estadoDesconocidoOVacioUsaElInicial() {
        Resolucion desconocido = catalogo.estadoDetalle("Pintura");
        assertEquals(10, desconocido.estadoId());
        assertFalse(desconocido.reconocido());
        assertEquals("Pintura", desconocido.noReconocido());

        assertEquals(1, catalogo.estadoPedido(null).estadoId());
        assertTrue(catalogo.estadoPedido("Creado").reconocido());
    }

    @Test
    void identificaPedidosEnEstadoFinal() {
        EstadoPedido facturado = pedido(5, "FACTURADO", "Facturado", false);
        facturado.setEsFinal(true);
        CatalogoEstados conFinal = CatalogoEstados.de(
                List.of(pedido(1, "CREADO", "Creado", true), facturado),
                List.of(detalle(10, "CREADO", "Creado", true)),
                List.of(), List.of(), List.of());

        assertTrue(conFinal.esPedidoOmitido("facturado"));
        assertFalse(conFinal.esPedidoOmitido("Creado"));
        assertFalse(conFinal.esPedidoOmitido("Anulado")); // desconocido: se importa con el estado inicial
    }

    @Test
    void omiteLosEstadosConfiguradosAunqueNoExistanEnElCatalogo() {
        assertTrue(catalogo.esPedidoOmitido("Despachado"));
        assertTrue(catalogo.esPedidoOmitido(" ENTREGADO "));
        assertTrue(catalogo.esPedidoOmitido("Anulado"));
        assertFalse(catalogo.esPedidoOmitido("ParaAlistar"));
        assertFalse(catalogo.esPedidoOmitido("EnAlistamiento"));
        assertFalse(catalogo.esPedidoOmitido(null));
    }

    @Test
    void fallaSiNoHayEstadoInicial() {
        assertThrows(IllegalStateException.class, () -> CatalogoEstados.de(
                List.of(pedido(2, "EN_ALISTAMIENTO", "En alistamiento", false)),
                List.of(detalle(10, "CREADO", "Creado", true)), List.of(), List.of(), List.of()));
    }
}
