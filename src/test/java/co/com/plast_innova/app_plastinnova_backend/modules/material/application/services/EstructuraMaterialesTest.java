package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.Componente;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.ItemBom;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.UbicacionItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem.MATERIA_PRIMA;
import static co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem.PRODUCTO_TERMINADO;
import static co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem.SEMITERMINADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PT1 = 2 × SEMI (sec. 2) + 0.5 kg RESINA (sec. 1); SEMI = 3 × TORNILLO + 0.25 kg RESINA. PT2 = 1 × SEMI.
 */
class EstructuraMaterialesTest {

    private static final ItemBom PT1 = new ItemBom(1, "PT1", "Lámpara 1", PRODUCTO_TERMINADO, "Und", true);
    private static final ItemBom PT2 = new ItemBom(2, "PT2", "Lámpara 2", PRODUCTO_TERMINADO, "Und", true);
    private static final ItemBom SEMI = new ItemBom(3, "LAM/SEMI", "Semiterminado", SEMITERMINADO, "Und", true);
    private static final ItemBom RESINA = new ItemBom(4, "RESINA", "Resina", MATERIA_PRIMA, "Kg", true);
    private static final ItemBom TORNILLO = new ItemBom(5, "TORNILLO", null, null, null, false);

    private final EstructuraMateriales estructura = new EstructuraMateriales(
            List.of(PT1, PT2, SEMI, RESINA, TORNILLO),
            List.of(new Componente(1, 3, 2, new BigDecimal("2"), "1A001"),
                    new Componente(1, 4, 1, new BigDecimal("0.5"), "1A013"),
                    new Componente(3, 5, 1, new BigDecimal("3"), "1A004"),
                    new Componente(3, 4, 2, new BigDecimal("0.25"), "1A013"),
                    new Componente(2, 3, 1, new BigDecimal("1"), "1A001")));

    @Test
    void explotaTodosLosNivelesMultiplicandoLasCantidades() {
        NodoMaterialDTO arbol = estructura.explotar(PT1, new BigDecimal("10"));

        assertEquals("PT1", arbol.codigo());
        assertEquals(new BigDecimal("10"), arbol.cantidad());
        assertNull(arbol.secuencia());
        // Ordenados por secuencia
        assertEquals(List.of("RESINA", "LAM/SEMI"), arbol.componentes().stream().map(NodoMaterialDTO::codigo).toList());
        assertEquals(new BigDecimal("5"), arbol.componentes().get(0).cantidad());
        assertEquals("1A013", arbol.componentes().get(0).bodega());

        NodoMaterialDTO semi = arbol.componentes().get(1);
        assertEquals(new BigDecimal("2"), semi.cantidadPorPadre());
        assertEquals(new BigDecimal("20"), semi.cantidad());
        assertEquals(new BigDecimal("60"), semi.componentes().get(0).cantidad()); // 10 × 2 × 3 tornillos
        assertEquals(new BigDecimal("5"), semi.componentes().get(1).cantidad());  // 10 × 2 × 0.25 kg
        assertTrue(semi.componentes().get(0).componentes().isEmpty());
    }

    @Test
    void sinExistenciasTotalizaPorCodigoYBodegaSinContarLasRaices() {
        List<TotalMaterialDTO> totales = estructura.totalizar(Map.of(1, new BigDecimal("10"), 2, new BigDecimal("4")), Map.of());

        // Semiterminados primero, luego materia prima; los ítems sin tipo al final
        assertEquals(List.of("LAM/SEMI", "RESINA", "TORNILLO"), totales.stream().map(TotalMaterialDTO::codigo).toList());
        assertEquals(new BigDecimal("24"), totales.get(0).cantidad());   // 20 + 4
        assertEquals("1A001", totales.get(0).bodega());
        assertTrue(totales.get(0).tieneComponentes());
        assertEquals(new BigDecimal("11"), totales.get(1).cantidad());   // 5 + 5 + 4 × 0.25
        assertEquals(new BigDecimal("11"), totales.get(1).faltante());
        assertEquals(new BigDecimal("0"), totales.get(1).disponible());
        assertFalse(totales.get(1).tieneComponentes());
        assertEquals(new BigDecimal("72"), totales.get(2).cantidad());   // 60 + 12
        assertFalse(totales.get(2).enMaestro());
    }

    @Test
    void lasExistenciasDeUnSemiterminadoReducenLasDeSusComponentes() {
        // 15 semiterminados en 1A001 cubren 15 de los 24: solo los 9 que faltan se explotan
        List<TotalMaterialDTO> totales = estructura.totalizar(Map.of(1, new BigDecimal("10"), 2, new BigDecimal("4")), Map.of(
                new UbicacionItem(3, "1A001"), new BigDecimal("15"),
                new UbicacionItem(4, "1A013"), new BigDecimal("3"),
                new UbicacionItem(4, "1A001"), new BigDecimal("100"),   // otra bodega: no cuenta
                new UbicacionItem(5, "1A004"), new BigDecimal("50")));

        TotalMaterialDTO semi = totales.get(0);
        assertEquals(new BigDecimal("24"), semi.cantidad());
        assertEquals(new BigDecimal("15"), semi.disponible());
        assertEquals(new BigDecimal("9"), semi.faltante());
        TotalMaterialDTO resina = totales.get(1);
        assertEquals(new BigDecimal("7.25"), resina.cantidad());         // 5 (directa de PT1) + 9 × 0.25
        assertEquals(new BigDecimal("3"), resina.disponible());
        assertEquals(new BigDecimal("4.25"), resina.faltante());
        TotalMaterialDTO tornillo = totales.get(2);
        assertEquals(new BigDecimal("27"), tornillo.cantidad());         // 9 × 3
        assertEquals(new BigDecimal("0"), tornillo.faltante());
    }

    @Test
    void siElSemiterminadoAlcanzaNoSeRequierenSusComponentes() {
        List<TotalMaterialDTO> totales = estructura.totalizar(Map.of(2, new BigDecimal("4")),
                Map.of(new UbicacionItem(3, "1A001"), new BigDecimal("4")));

        assertEquals(List.of("LAM/SEMI"), totales.stream().map(TotalMaterialDTO::codigo).toList());
        assertEquals(new BigDecimal("0"), totales.getFirst().faltante());
        assertEquals(Set.of(2, 3, 4, 5), estructura.alcanzables(List.of(2)));
    }

    @Test
    void buscaSinImportarMayusculasNiRelleno() {
        assertEquals(SEMI, estructura.item("  lam/semi  ").orElseThrow());
        assertTrue(estructura.item("NO-EXISTE").isEmpty());
        assertTrue(estructura.item(null).isEmpty());
        assertFalse(estructura.tieneComponentes(RESINA));
    }

    @Test
    void redondeaSinNotacionCientifica() {
        assertEquals("10", EstructuraMateriales.redondear(new BigDecimal("10.000000")).toPlainString());
        assertEquals("10", EstructuraMateriales.redondear(new BigDecimal("10.000000")).toString());
        assertEquals("0.333333", EstructuraMateriales.redondear(new BigDecimal("0.3333333")).toString());
    }
}
