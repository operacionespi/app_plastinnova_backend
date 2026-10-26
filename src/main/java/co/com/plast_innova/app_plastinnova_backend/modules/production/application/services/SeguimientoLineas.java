package co.com.plast_innova.app_plastinnova_backend.modules.production.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.ReservaInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.AvanceLineas;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionEtapa;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionLinea;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionProducto;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Las líneas de pedido que sigue un producto de una orden. Las piezas del producto se reparten entre sus líneas por
 * prioridad (entrega más próxima, luego la que se vinculó primero): cada línea toma las piezas más avanzadas que
 * quedan, y su etapa es la del proceso más atrasado donde tiene piezas. Al llegar al almacén, lo recibido se
 * compromete para las líneas en el mismo orden.
 */
@Component
@RequiredArgsConstructor
class SeguimientoLineas {

    private static final Comparator<OrdenProduccionLinea> PRIORIDAD = Comparator
            .comparing((OrdenProduccionLinea l) -> l.getPedidoDetalle().getPedido().getFechaEstimadaEntrega(),
                    Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(OrdenProduccionLinea::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    private final AvanceLineas avanceLineas;
    private final ReservaInventarioService reservas;
    private final ReservaInventarioRepository reservaRepository;

    /**
     * Lleva cada línea activa del producto a la etapa donde están sus piezas. Las que ya no pueden seguirlo (se
     * cerraron, se entregaron, recibieron todo) se desvinculan, y todas cuando el producto termina.
     */
    void sincronizar(OrdenProduccionProducto producto, EstadoDetalle recepcion, String usuario, String observaciones) {
        List<OrdenProduccionEtapa> etapas = producto.getEtapas();
        int[] restantes = etapas.stream().mapToInt(OrdenProduccionEtapa::getCantidad).toArray();
        for (OrdenProduccionLinea linea : producto.lineasActivas().stream().sorted(PRIORIDAD).toList()) {
            PedidoDetalle detalle = linea.getPedidoDetalle();
            if (motivoFueraDeOrden(detalle) != null || detalle.cantidadPorEntregar() <= 0) {
                linea.setActiva(false);
                continue;
            }
            int pendiente = linea.pendiente();
            int asignadas = 0;
            EstadoDetalle etapa = recepcion;
            // Del proceso más avanzado hacia atrás: la línea queda en el último donde tomó piezas
            for (int i = etapas.size() - 1; i >= 0 && asignadas < pendiente; i--) {
                int toma = Math.min(restantes[i], pendiente - asignadas);
                if (toma > 0) {
                    restantes[i] -= toma;
                    asignadas += toma;
                    etapa = etapas.get(i).getEtapa();
                }
            }
            if (pendiente == 0) {
                linea.setActiva(false);
            } else if (asignadas == 0 && linea.getCantidadRecibida() == 0) {
                // Sus piezas se dieron de baja: queda donde está hasta que el producto tenga más
                continue;
            }
            int piezas = Math.min(asignadas + linea.getCantidadRecibida(), detalle.getCantidadSolicitada());
            if (!detalle.getEstadoActual().getId().equals(etapa.getId()) || detalle.getCantidadProcesada() != piezas) {
                avanceLineas.cambiarEtapa(detalle, etapa, piezas, TipoMovimientoDetalle.CAMBIO_ESTADO, usuario,
                        producto.getOrden().getNumero() + (observaciones == null ? "" : ": " + observaciones));
            }
        }
        if (producto.terminado()) {
            producto.lineasActivas().forEach(l -> l.setActiva(false));
        }
    }

    /**
     * Las piezas que llegaron al almacén quedan comprometidas para las líneas por prioridad, hasta lo que cada una
     * espera; lo demás queda libre. Quien llama ya bloqueó el ítem.
     */
    void comprometer(OrdenProduccionProducto producto, int piezas, String usuario) {
        int restante = piezas;
        for (OrdenProduccionLinea linea : producto.lineasActivas().stream().sorted(PRIORIDAD).toList()) {
            if (restante == 0) {
                break;
            }
            PedidoDetalle detalle = linea.getPedidoDetalle();
            if (motivoFueraDeOrden(detalle) != null) {
                continue;
            }
            int parte = Math.min(linea.pendiente(), restante);
            if (parte > 0) {
                reservas.comprometerProduccion(detalle, producto.getItem(), parte, usuario);
                linea.setCantidadRecibida(linea.getCantidadRecibida() + parte);
                restante -= parte;
            }
        }
    }

    /** Si todos sus productos terminaron, la orden termina y deja de arrastrar líneas. */
    void cerrarSiCompleta(OrdenProduccion orden) {
        if (orden.estaAbierta() && orden.completa()) {
            orden.setEstado(EstadoOrdenProduccion.TERMINADA);
            orden.setFechaCierre(FechaUtc.ahora());
            orden.lineasActivas().forEach(l -> l.setActiva(false));
        }
    }

    /** Lo que le falta a la línea sin lo que ya tiene comprometido ni lo entregado. */
    int faltante(PedidoDetalle detalle) {
        BigDecimal reservada = reservaRepository.findByPedidoDetalleId(detalle.getId())
                .map(ReservaInventario::getCantidad).orElse(BigDecimal.ZERO);
        return Math.max(detalle.cantidadPorEntregar() - reservada.setScale(0, RoundingMode.CEILING).intValue(), 0);
    }

    /**
     * Por qué la línea no puede seguir al producto (null si puede): otro ítem, o ya avanzó por su cuenta más allá del
     * primer proceso de la ruta. Las que están en el almacén con piezas por producir sí pueden.
     */
    String motivoNoVinculable(OrdenProduccionProducto producto, PedidoDetalle detalle, EstadoDetalle recepcion) {
        String fuera = motivoFueraDeOrden(detalle);
        if (fuera != null) {
            return fuera;
        }
        String sku = sku(detalle);
        if (sku == null || !sku.equalsIgnoreCase(producto.getItem().getCodigo().strip())) {
            return "La línea es de " + sku + " y el producto es " + producto.getItem().getCodigo() + ".";
        }
        EstadoDetalle actual = detalle.getEstadoActual();
        EstadoDetalle primero = producto.primeraEtapa().getEtapa();
        if (!actual.getId().equals(recepcion.getId()) && actual.getOrdenLogico() > primero.getOrdenLogico()) {
            return "La línea ya avanzó por su cuenta hasta " + actual.getNombre() + ": solo se incluyen líneas que no "
                    + "han pasado de " + primero.getNombre() + ".";
        }
        return null;
    }

    /** La línea ya no puede seguir a una orden: se eliminó, no sale, terminó o su pedido se cerró. */
    static String motivoFueraDeOrden(PedidoDetalle detalle) {
        Pedido pedido = detalle.getPedido();
        if (Boolean.TRUE.equals(detalle.getEliminadoEnFm()) || Boolean.TRUE.equals(pedido.getEliminadoEnFm())) {
            return "La línea fue eliminada en FileMaker.";
        }
        if (pedido.getFechaCierre() != null) {
            return "El pedido " + pedido.getNumeroPedido() + " está cerrado.";
        }
        if (Boolean.TRUE.equals(detalle.getEstadoActual().getEsFinal())) {
            return "La línea ya está en " + detalle.getEstadoActual().getNombre() + ".";
        }
        return null;
    }

    /** Código del ítem que pide la línea: el SKU del producto, o su código de FileMaker si no tiene. */
    static String sku(PedidoDetalle detalle) {
        String sku = detalle.getProducto().getSku() != null ? detalle.getProducto().getSku()
                : detalle.getProducto().getFilemakerId();
        return sku == null ? null : sku.strip();
    }
}
