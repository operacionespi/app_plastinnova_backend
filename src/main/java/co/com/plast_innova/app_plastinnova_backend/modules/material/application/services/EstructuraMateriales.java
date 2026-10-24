package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Copia en memoria e inmutable de la lista de materiales completa (unas 87 mil relaciones). Explota la estructura de
 * un ítem sin consultar la base; se reemplaza entera cuando se recarga la lista (ver ListaMaterialesService).
 */
final class EstructuraMateriales {

    /** Decimales de las cantidades que se entregan (las internas no se redondean). */
    private static final int DECIMALES = 6;
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

    /** Suma los componentes de los árboles por código (la raíz de cada árbol no cuenta: es lo que se pide). */
    List<TotalMaterialDTO> totalizar(Collection<NodoMaterialDTO> arboles) {
        Map<String, BigDecimal> cantidades = new LinkedHashMap<>();
        arboles.forEach(arbol -> arbol.componentes().forEach(c -> acumular(c, cantidades)));
        return cantidades.entrySet().stream()
                .map(e -> {
                    ItemBom item = porCodigo.get(clave(e.getKey()));
                    return new TotalMaterialDTO(item.codigo(), item.descripcion(), item.tipo(), item.unidad(),
                            item.enMaestro(), redondear(e.getValue()), tieneComponentes(item));
                })
                .sorted(Comparator.comparing(TotalMaterialDTO::tipo, ORDEN_TIPO).thenComparing(TotalMaterialDTO::codigo))
                .toList();
    }

    private void acumular(NodoMaterialDTO nodo, Map<String, BigDecimal> cantidades) {
        cantidades.merge(nodo.codigo(), nodo.cantidad(), BigDecimal::add);
        nodo.componentes().forEach(c -> acumular(c, cantidades));
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

    /** Sin ceros sobrantes y nunca en notación científica (1E+1), que el JSON mostraría tal cual. */
    static BigDecimal redondear(BigDecimal valor) {
        BigDecimal r = valor.setScale(DECIMALES, RoundingMode.HALF_UP).stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0) : r;
    }

    static String clave(String codigo) {
        return codigo.strip().toUpperCase(Locale.ROOT);
    }
}
