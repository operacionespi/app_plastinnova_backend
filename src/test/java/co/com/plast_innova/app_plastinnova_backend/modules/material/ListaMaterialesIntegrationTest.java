package co.com.plast_innova.app_plastinnova_backend.modules.material;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.CargaListaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.LineaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.MaterialesPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carga de los CSV contra SQL Server real y materiales de un pedido.
 * IT-PT1 = 2 × LAM/IT (sec. 2) + 0.5 kg RESINA (sec. 1); LAM/IT = 3 × TORN-IT (no está en el maestro).
 */
@SpringBootTest
class ListaMaterialesIntegrationTest extends PruebaIntegracion {

    private static final String BODEGAS = """
            f150_id,f150_descripcion,f150_descripcion_corta
            1A001,"Almacen De Producto Terminado, PT",AlmacenPT\s\s\s
            1A013,Almacen de resina,Almacen13
            """;
    private static final String ITEMS = """
            f120_id,f120_referencia,f120_descripcion,f120_id_tipo_inv_serv,f120_id_unidad_inventario
            1,IT-PT1      ,Lámpara IT,ProductoTerminado,Und\s
            2,LAM/IT      ,Lámina IT                ,Semiterminado,Und
            3,RESINA-IT,Resina IT,MateriaPrima,Kg
            4,IT-PT2,Lámpara sin lista,ProductoTerminado,Und
            """;
    private static final String LISTA = """
            Secuencia,Item_Padre,Item_Hijo,Cantidad_Hijo,Id_Operacion,Bodega
            2,IT-PT1      ,LAM/IT      ,2,63512,1A001
            1,IT-PT1,RESINA-IT,0.5,,1A013
            1,LAM/IT,TORN-IT,3,63549,1A001
            """;

    @Autowired private CargaListaMaterialesService cargaService;
    @Autowired private ListaMaterialesService listaMaterialesService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Reserva_Inventario", "Movimiento_Inventario", "Pedido_Detalle", "Pedido", "Cliente",
                "Producto", "Existencia", "Slot", "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void cargaLosTresArchivosYCreaLosCodigosSinMaestro() {
        CargaListaMaterialesDTO carga = cargar(BODEGAS, ITEMS, LISTA);

        assertEquals(2, carga.bodegas());
        assertEquals(4, carga.items());
        assertEquals(3, carga.relaciones());
        assertEquals(1, carga.itemsSinMaestro());
        assertTrue(carga.avisos().getFirst().contains("TORN-IT"));
        assertEquals("Almacen De Producto Terminado, PT",
                jdbc.queryForObject("SELECT descripcion FROM Bodega WHERE codigo = '1A001'", String.class));
        assertEquals(Map.of("tipo", "SEMITERMINADO", "unidad", "Und", "descripcion", "Lámina IT"),
                jdbc.queryForMap("SELECT tipo, unidad, descripcion FROM Item WHERE codigo = 'LAM/IT'"));
        assertFalse(jdbc.queryForObject("SELECT en_maestro FROM Item WHERE codigo = 'TORN-IT'", Boolean.class));
        assertNull(jdbc.queryForObject("SELECT operacion_id FROM Lista_Material lm JOIN Item h ON h.id = lm.item_hijo_id "
                + "WHERE h.codigo = 'RESINA-IT'", Integer.class));

        NodoMaterialDTO arbol = listaMaterialesService.estructura("it-pt1", new BigDecimal("4"));
        assertEquals(List.of("RESINA-IT", "LAM/IT"), arbol.componentes().stream().map(NodoMaterialDTO::codigo).toList());
        assertEquals(new BigDecimal("2"), arbol.componentes().get(0).cantidad());
        assertEquals(new BigDecimal("24"), arbol.componentes().get(1).componentes().getFirst().cantidad());
        assertEquals("1A013", arbol.componentes().get(0).bodega());

        assertEquals(1, listaMaterialesService.buscarItems("lámpara", PageRequest.of(0, 10)).stream()
                .filter(i -> i.tieneComponentes() && i.codigo().equals("IT-PT1")).count());
        assertEquals(1, listaMaterialesService.ultimasCargas().size());
    }

    @Test
    void laRecargaConservaLosIdYReemplazaLaLista() {
        cargar(BODEGAS, ITEMS, LISTA);
        int idLamina = jdbc.queryForObject("SELECT id FROM Item WHERE codigo = 'LAM/IT'", Integer.class);
        listaMaterialesService.estructura("IT-PT1", BigDecimal.ONE); // deja la estructura en memoria

        // TORN-IT entra al maestro, IT-PT2 sale, y la lámpara ahora lleva 1 kg de resina y no lleva lámina
        cargar(BODEGAS, ITEMS.replace("4,IT-PT2,Lámpara sin lista,ProductoTerminado,Und", "5,TORN-IT,Tornillo,MateriaPrima,Und"),
                """
                Secuencia,Item_Padre,Item_Hijo,Cantidad_Hijo,Id_Operacion,Bodega
                1,IT-PT1,RESINA-IT,1,,1A013
                1,LAM/IT,TORN-IT,3,63549,1A001
                """);

        assertEquals(idLamina, jdbc.queryForObject("SELECT id FROM Item WHERE codigo = 'LAM/IT'", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT en_maestro FROM Item WHERE codigo = 'TORN-IT'", Boolean.class));
        assertFalse(jdbc.queryForObject("SELECT en_maestro FROM Item WHERE codigo = 'IT-PT2'", Boolean.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM Lista_Material", Integer.class));

        NodoMaterialDTO arbol = listaMaterialesService.estructura("IT-PT1", BigDecimal.ONE);
        assertEquals(1, arbol.componentes().size());
        assertEquals(new BigDecimal("1"), arbol.componentes().getFirst().cantidad());
        assertEquals(2, listaMaterialesService.ultimasCargas().size());
    }

    @Test
    void unArchivoConErroresNoCargaNada() {
        cargar(BODEGAS, ITEMS, LISTA);

        SolicitudInvalidaException ciclo = assertThrows(SolicitudInvalidaException.class, () -> cargar(BODEGAS, ITEMS, LISTA
                + "1,TORN-IT,IT-PT1,1,,1A001\n"));
        assertTrue(ciclo.getMessage().contains("ciclo"), ciclo.getMessage());
        assertTrue(ciclo.getMessage().contains("TORN-IT → IT-PT1"), ciclo.getMessage()); // el punto de partida del ciclo puede variar

        SolicitudInvalidaException errores = assertThrows(SolicitudInvalidaException.class, () -> cargar(BODEGAS, ITEMS, LISTA
                + "1,IT-PT2,RESINA-IT,0,,1A001\n"
                + "1,IT-PT2,LAM/IT,1,,9Z999\n"
                + "1,IT-PT1,RESINA-IT,1,,1A013\n"));
        assertTrue(errores.getMessage().contains("lista_materiales.csv, línea 5: la cantidad '0'"), errores.getMessage());
        assertTrue(errores.getMessage().contains("línea 6: la bodega 9Z999"), errores.getMessage());
        assertTrue(errores.getMessage().contains("línea 7: RESINA-IT está repetido"), errores.getMessage());

        assertThrows(SolicitudInvalidaException.class, () -> cargar(BODEGAS, "codigo,nombre\nX,Y\n", LISTA));

        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM Lista_Material", Integer.class));
        assertEquals(1, listaMaterialesService.ultimasCargas().size());
    }

    @Test
    void materialesDelPedidoSinLineasEliminadasNiNoSale() {
        cargar(BODEGAS, ITEMS, LISTA);
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT1', 'IT-PT1', 'IT-PT1'), "
                + "('IT-PT2', 'IT-PT2', 'IT-PT2'), ('IT-OTRO', 'IT-OTRO', 'IT-OTRO')");
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, estado_actual_id, fecha_creacion_fm)
                SELECT 'IT-MAT', 'IT-MAT', c.id, e.id, SYSUTCDATETIME() FROM Cliente c, Estado_Pedido e
                WHERE c.filemaker_id = 'IT Cliente' AND e.codigo = 'CREADO'""");
        long pedidoId = jdbc.queryForObject("SELECT id FROM Pedido WHERE filemaker_id = 'IT-MAT'", Long.class);
        linea(pedidoId, 1, "IT-PT1", "CREADO", 10, false);
        linea(pedidoId, 2, "IT-PT2", "CREADO", 5, false);    // está en el maestro pero no tiene lista
        linea(pedidoId, 3, "IT-OTRO", "CREADO", 1, false);   // no está en el maestro
        linea(pedidoId, 4, "IT-PT1", "NO_SALE", 100, false);
        linea(pedidoId, 5, "IT-PT1", "CREADO", 100, true);
        linea(pedidoId, 6, "IT-PT1", "CREADO", 2, false);

        MaterialesPedidoDTO materiales = listaMaterialesService.materialesPedido(pedidoId);

        assertEquals("IT-MAT", materiales.numeroPedido());
        assertEquals(List.of(1, 2, 3, 6), materiales.lineas().stream().map(LineaMaterialesDTO::posicion).toList());
        assertTrue(materiales.lineas().get(1).estructura().componentes().isEmpty());
        assertNull(materiales.lineas().get(2).estructura());
        assertEquals(List.of("LAM/IT", "RESINA-IT", "TORN-IT"),
                materiales.totales().stream().map(TotalMaterialDTO::codigo).toList());
        assertEquals(new BigDecimal("24"), materiales.totales().get(0).cantidad()); // (10 + 2) × 2
        assertEquals(new BigDecimal("6"), materiales.totales().get(1).cantidad());  // 12 × 0.5
        assertEquals(TipoItem.MATERIA_PRIMA, materiales.totales().get(1).tipo());
        assertEquals(new BigDecimal("72"), materiales.totales().get(2).cantidad()); // 24 × 3
        assertTrue(materiales.fechaCarga() != null);

        assertThrows(ResourceNotFoundException.class, () -> listaMaterialesService.materialesPedido(-1L));
    }

    private CargaListaMaterialesDTO cargar(String bodegas, String items, String lista) {
        return cargaService.cargar(bodegas.getBytes(StandardCharsets.UTF_8), items.getBytes(StandardCharsets.UTF_8),
                lista.getBytes(StandardCharsets.UTF_8), "it@plast-innova.com.co");
    }

    private void linea(long pedidoId, int posicion, String producto, String estado, int cantidad, boolean eliminada) {
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, posicion, pedido_id, producto_id, estado_actual_id,
                                            cantidad_solicitada, precio_unitario, eliminado_en_fm)
                SELECT ?, ?, ?, p.id, e.id, ?, 0, ? FROM Producto p, Estado_Detalle e
                WHERE p.filemaker_id = ? AND e.codigo = ?""",
                "IT-MAT-" + posicion, posicion, pedidoId, cantidad, eliminada, producto, estado);
    }
}
