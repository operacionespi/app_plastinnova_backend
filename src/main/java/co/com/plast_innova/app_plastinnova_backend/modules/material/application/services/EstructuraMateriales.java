package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Copia en memoria e inmutable de la lista de materiales completa (unas 87 mil relaciones). Explota la estructura de
 * un ítem sin consultar la base; se reemplaza entera cuando se recarga la lista (ver ListaMaterialesService).
 */
final class EstructuraMateriales {

    /** La carga rechaza los ciclos; esto solo evita un desbordamiento si llegaran a existir. */
    private static final int PROFUNDIDAD_MAXIMA = 50;

    private static final Comparator<TipoItem> ORDEN_TIPO = Comparator.nullsLast(Comparator.comparing(t -> switch (t) {
        case SEMITERMINADO -> 0;
        case MATERIA_PRIMA -> 1;
        case PRODUCTO_TERMINADO -> 2;
    }));

    record ItemBom(int id, String codigo, String descripcion, TipoItem tipo, String unidad, boolean enMaestro) {
    }

    record Componente(int itemPadreId, int itemHijoId, int secuencia, BigDecimal cantidad, String bodega) {
    }

    private final Map<String, ItemBom> porCodigo = new HashMap<>();
    private final Map<Integer, ItemBom> porId = new HashMap<>();
    private final Map<Integer, List<Componente>> componentes = new HashMap<>();

    EstructuraMateriales(Collection<ItemBom> items, Collection<Componente> relaciones) {
        for (ItemBom item : items) {
            porCodigo.put(clave(item.codigo()), item);
            porId.put(item.id(), item);
        }
        for (Componente c : relaciones) {
            componentes.computeIfAbsent(c.itemPadreId(), k -> new ArrayList<>()).add(c);
        }
        Comparator<Componente> orden = Comparator.comparingInt(Componente::secuencia)
                .thenComparing(c -> porId.get(c.itemHijoId()).codigo());
        componentes.values().forEach(lista -> lista.sort(orden));
    }

    /** Busca por código sin importar mayúsculas ni espacios al inicio/fin, igual que SQL Server. */
    Optional<ItemBom> item(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(porCodigo.get(clave(codigo)));
    }

    boolean tieneComponentes(ItemBom item) {
        return componentes.containsKey(item.id());
    }

    /** Árbol completo del ítem para la cantidad indicada de la raíz. */
    NodoMaterialDTO explotar(ItemBom raiz, BigDecimal cantidad) {
        return nodo(raiz, null, null, null, cantidad, 0);
    }

    /** Ítems que aparecen en la estructura de las raíces (incluidas). */
    Set<Integer> alcanzables(Collection<Integer> raices) {
        return niveles(raices).keySet();
    }

    /**
     * Requerimiento neto por componente y bodega para la demanda de las raíces (ítem → cantidad), descontando las
     * existencias de la bodega que indica la lista para cada componente. Se recorre por niveles (cada ítem después de
     * todos los que lo usan): solo lo que falta de un ítem se explota hacia sus componentes, así que si hay
     * semiterminados en bodega se necesitan menos de sus materias primas. Las raíces no se descuentan (es lo que se
     * pide) y no aparecen en el resultado, salvo que también sean componente de otra raíz.
     *
     * @param existencias cantidad disponible por ítem y código de bodega
     */
    List<TotalMaterialDTO> totalizar(Map<Integer, BigDecimal> demanda, Map<UbicacionItem, BigDecimal> existencias) {
        Map<Integer, Integer> niveles = niveles(demanda.keySet());
        Map<Integer, Map<String, BigDecimal>> requerido = new HashMap<>();
        List<TotalMaterialDTO> totales = new ArrayList<>();
        niveles.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> {
                    ItemBom item = porId.get(e.getKey());
                    BigDecimal faltaTotal = demanda.getOrDefault(item.id(), BigDecimal.ZERO);
                    for (Map.Entry<String, BigDecimal> r : requerido.getOrDefault(item.id(), Map.of()).entrySet()) {
                        BigDecimal disponible = existencias.getOrDefault(new UbicacionItem(item.id(), r.getKey()), BigDecimal.ZERO);
                        BigDecimal falta = r.getValue().subtract(disponible).max(BigDecimal.ZERO);
                        faltaTotal = faltaTotal.add(falta);
                        totales.add(new TotalMaterialDTO(item.codigo(), item.descripcion(), item.tipo(), item.unidad(),
                                item.enMaestro(), r.getKey(), redondear(r.getValue()), redondear(disponible),
                                redondear(falta), tieneComponentes(item)));
                    }
                    if (faltaTotal.signum() > 0) {
                        for (Componente c : componentes.getOrDefault(item.id(), List.of())) {
                            requerido.computeIfAbsent(c.itemHijoId(), k -> new LinkedHashMap<>())
                                    .merge(c.bodega(), faltaTotal.multiply(c.cantidad()), BigDecimal::add);
                        }
                    }
                });
        return totales.stream()
                .sorted(Comparator.comparing(TotalMaterialDTO::tipo, ORDEN_TIPO)
                        .thenComparing(TotalMaterialDTO::codigo)
                        .thenComparing(TotalMaterialDTO::bodega, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Ítem en una bodega (código; null si la lista no indica bodega). */
    record UbicacionItem(int itemId, String bodega) {
    }

    /** Nivel más profundo en que aparece cada ítem bajo las raíces (nivel 0): define el orden del neteo. */
    private Map<Integer, Integer> niveles(Collection<Integer> raices) {
        Map<Integer, Integer> niveles = new HashMap<>();
        raices.forEach(id -> asignarNivel(id, 0, niveles));
        return niveles;
    }

    private void asignarNivel(int itemId, int nivel, Map<Integer, Integer> niveles) {
        Integer actual = niveles.get(itemId);
        if ((actual != null && actual >= nivel) || nivel > PROFUNDIDAD_MAXIMA) {
            return;
        }
        niveles.put(itemId, nivel);
        componentes.getOrDefault(itemId, List.of()).forEach(c -> asignarNivel(c.itemHijoId(), nivel + 1, niveles));
    }

    private NodoMaterialDTO nodo(ItemBom item, Integer secuencia, String bodega, BigDecimal cantidadPorPadre,
                                 BigDecimal cantidad, int profundidad) {
        List<NodoMaterialDTO> hijos = new ArrayList<>();
        if (profundidad < PROFUNDIDAD_MAXIMA) {
            for (Componente c : componentes.getOrDefault(item.id(), List.of())) {
                hijos.add(nodo(porId.get(c.itemHijoId()), c.secuencia(), c.bodega(), c.cantidad(),
                        cantidad.multiply(c.cantidad()), profundidad + 1));
            }
        }
        return new NodoMaterialDTO(item.codigo(), item.descripcion(), item.tipo(), item.unidad(), item.enMaestro(),
                secuencia, bodega, cantidadPorPadre == null ? null : redondear(cantidadPorPadre), redondear(cantidad),
                hijos);
    }

    static BigDecimal redondear(BigDecimal valor) {
        return Decimales.paraMostrar(valor);
    }

    static String clave(String codigo) {
        return codigo.strip().toUpperCase(Locale.ROOT);
    }
}
