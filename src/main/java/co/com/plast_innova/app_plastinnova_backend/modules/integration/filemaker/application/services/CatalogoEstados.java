package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traduce los estados que escribe FileMaker ("En ensamble", "Almacen1"...) a los ids del catálogo local.
 * Compara sin tildes ni mayúsculas contra el código y el nombre de cada estado activo; los valores pegados
 * de FileMaker ("EnAlistamiento") se separan por palabras antes de comparar. Las equivalencias configuradas
 * ("ParaAlistar:CREADO") tienen prioridad sobre la comparación por nombre.
 * Se construye una vez por ejecución, así los cambios al catálogo aplican desde la siguiente sincronización.
 */
public final class CatalogoEstados {

    private final Map<String, Integer> pedidoPorClave;
    private final Integer pedidoInicialId;
    private final Set<Integer> pedidoFinalesIds;
    private final Set<String> pedidoOmitidosClaves;
    private final Map<String, Integer> detallePorClave;
    private final Integer detalleInicialId;

    private CatalogoEstados(Map<String, Integer> pedidoPorClave, Integer pedidoInicialId, Set<Integer> pedidoFinalesIds,
                            Set<String> pedidoOmitidosClaves, Map<String, Integer> detallePorClave, Integer detalleInicialId) {
        this.pedidoPorClave = pedidoPorClave;
        this.pedidoInicialId = pedidoInicialId;
        this.pedidoFinalesIds = pedidoFinalesIds;
        this.pedidoOmitidosClaves = pedidoOmitidosClaves;
        this.detallePorClave = detallePorClave;
        this.detalleInicialId = detalleInicialId;
    }

    /**
     * @param estadosPedidoOmitidos estados de pedido de FileMaker (ej. Despachado, Entregado) cuyos pedidos nuevos
     *                              no se importan, aunque no existan en el catálogo local
     * @param equivalenciasPedido   "ValorFileMaker:CODIGO_LOCAL" para estados de pedido, ej. "ParaAlistar:CREADO"
     * @param equivalenciasDetalle  igual, para estados de línea
     */
    public static CatalogoEstados de(List<EstadoPedido> estadosPedido, List<EstadoDetalle> estadosDetalle,
                                     Collection<String> estadosPedidoOmitidos,
                                     Collection<String> equivalenciasPedido, Collection<String> equivalenciasDetalle) {
        return new CatalogoEstados(
                indexar(estadosPedido, EstadoPedido::getId, EstadoPedido::getCodigo, EstadoPedido::getNombre,
                        equivalenciasPedido),
                inicial(estadosPedido, EstadoPedido::getEsInicial, EstadoPedido::getId, "pedido"),
                estadosPedido.stream().filter(e -> Boolean.TRUE.equals(e.getEsFinal()))
                        .map(EstadoPedido::getId).collect(Collectors.toSet()),
                estadosPedidoOmitidos.stream().map(CatalogoEstados::clave).filter(Objects::nonNull)
                        .collect(Collectors.toSet()),
                indexar(estadosDetalle, EstadoDetalle::getId, EstadoDetalle::getCodigo, EstadoDetalle::getNombre,
                        equivalenciasDetalle),
                inicial(estadosDetalle, EstadoDetalle::getEsInicial, EstadoDetalle::getId, "línea"));
    }

    public Resolucion estadoPedido(String valorFm) {
        return resolver(valorFm, pedidoPorClave, pedidoInicialId);
    }

    /**
     * True si un pedido nuevo con este estado de FileMaker no se debe importar: está en la lista de omitidos
     * (ej. Despachado) o corresponde a un estado final del catálogo local (ej. Facturado).
     */
    public boolean esPedidoOmitido(String valorFm) {
        if (pedidoOmitidosClaves.contains(clave(valorFm))) {
            return true;
        }
        Resolucion resolucion = estadoPedido(valorFm);
        return resolucion.reconocido() && pedidoFinalesIds.contains(resolucion.estadoId());
    }

    public Resolucion estadoDetalle(String valorFm) {
        return resolver(valorFm, detallePorClave, detalleInicialId);
    }

    private static Resolucion resolver(String valorFm, Map<String, Integer> porClave, Integer inicialId) {
        String clave = clave(valorFm);
        Integer id = clave == null ? null : porClave.get(clave);
        return id != null ? new Resolucion(id, null) : new Resolucion(inicialId, valorFm);
    }

    private static <E> Map<String, Integer> indexar(List<E> estados, Function<E, Integer> id,
                                                    Function<E, String> codigo, Function<E, String> nombre,
                                                    Collection<String> equivalencias) {
        Map<String, Integer> porClave = new HashMap<>();
        for (E estado : estados) {
            porClave.put(clave(nombre.apply(estado)), id.apply(estado));
            // El código tiene prioridad si coincide con el nombre de otro estado
            porClave.put(clave(codigo.apply(estado)), id.apply(estado));
        }
        Map<String, Integer> porCodigo = new HashMap<>();
        estados.forEach(e -> porCodigo.put(clave(codigo.apply(e)), id.apply(e)));
        for (String equivalencia : equivalencias) {
            if (equivalencia.isBlank()) {
                continue;
            }
            int separador = equivalencia.lastIndexOf(':');
            if (separador <= 0) {
                throw new IllegalStateException("Equivalencia de estado inválida (use Valor:CODIGO): '" + equivalencia + "'");
            }
            // Si el código destino no existe o está inactivo, el valor se trata como no reconocido (estado inicial)
            Integer destino = porCodigo.get(clave(equivalencia.substring(separador + 1)));
            String valorFm = clave(equivalencia.substring(0, separador));
            if (destino != null) {
                porClave.put(valorFm, destino);
            } else {
                porClave.remove(valorFm);
            }
        }
        return porClave;
    }

    /** Como {@link Texto#clave} pero separando palabras pegadas: "EnAlistamiento" → "EN_ALISTAMIENTO". */
    private static String clave(String valor) {
        return valor == null ? null : Texto.clave(valor.replaceAll("(?<=\\p{Ll})(?=\\p{Lu})", " "));
    }

    private static <E> Integer inicial(List<E> estados, Function<E, Boolean> esInicial,
                                       Function<E, Integer> id, String tipo) {
        return estados.stream()
                .filter(e -> Boolean.TRUE.equals(esInicial.apply(e)))
                .map(id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un estado inicial activo de " + tipo + ": configúrelo antes de sincronizar."));
    }

    /**
     * @param estadoId    estado a asignar
     * @param noReconocido valor de FileMaker que no existe en el catálogo (se asignó el estado inicial); null si coincidió
     */
    public record Resolucion(Integer estadoId, String noReconocido) {

        public boolean reconocido() {
            return noReconocido == null;
        }
    }
}
