package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.Remision;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.EstadoCompromiso;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaLineaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.MovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.ReservaInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ExistenciaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Producto terminado comprometido por los pedidos en la bodega de producto terminado. Se reserva por bodega (no por
 * slot) y por línea, nunca más de lo solicitado ni más de lo libre: la existencia menos lo que tienen los demás
 * pedidos. Cada operación bloquea el ítem, así que dos pedidos no comprometen las mismas unidades.
 *
 * <p>Ciclo de una reserva: la crea un usuario (Comprometer), la producción de la línea al llegar a la bodega
 * (recibirProduccion) o la de su orden de producción (comprometerProduccion); se convierte en salida de inventario
 * con las remisiones (entregar) o cuando el pedido se factura (despacharPedido), y se libera si la línea no sale o
 * desaparece de FileMaker. Nunca supera lo que a la línea le falta por entregar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaInventarioService {

    private final ReservaInventarioRepository reservaRepository;
    private final ExistenciaRepository existenciaRepository;
    private final ItemRepository itemRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final InventarioService inventarioService;
    private final AlmacenProductoTerminado almacen;

    @Transactional(readOnly = true)
    public ReservasPedidoDTO reservasPedido(Long pedidoId) {
        Pedido pedido = pedido(pedidoId);
        Bodega bodega = almacen.obtener();
        Map<Long, ReservaInventario> reservas = new HashMap<>();
        reservaRepository.findByPedidoDetallePedidoId(pedidoId).forEach(r -> reservas.put(r.getPedidoDetalle().getId(), r));
        Map<Integer, Disponibilidad> disponibilidad = new HashMap<>();
        List<PedidoDetalle> detalles = lineas(pedidoId);

        List<ReservaLineaDTO> lineas = detalles.stream().map(d -> {
            Optional<Item> item = item(d);
            BigDecimal reservada = Optional.ofNullable(reservas.get(d.getId()))
                    .map(ReservaInventario::getCantidad).orElse(BigDecimal.ZERO);
            Disponibilidad disp = item.map(i -> disponibilidad.computeIfAbsent(i.getId(), id -> new Disponibilidad(
                    existenciaRepository.totalEnBodega(id, bodega.getId()),
                    reservaRepository.totalReservado(id, bodega.getId(), null)))).orElse(Disponibilidad.NINGUNA);
            String motivo = motivoNoReservable(pedido, d, item.isPresent());
            BigDecimal maximo = motivo != null ? reservada
                    : BigDecimal.valueOf(d.cantidadPorEntregar()).min(reservada.add(disp.libre()));
            return new ReservaLineaDTO(d.getId(), d.getPosicion(), sku(d), item.map(Item::getCodigo).orElse(null),
                    item.map(Item::getDescripcion).orElse(d.getProducto().getNombre()),
                    item.map(Item::getUnidad).orElse(null), d.getEstadoActual().getNombre(), d.getCantidadSolicitada(),
                    d.getCantidadEntregada(), Decimales.paraMostrar(reservada), Decimales.paraMostrar(disp.existencia()),
                    Decimales.paraMostrar(disp.libre()), Decimales.paraMostrar(maximo.max(reservada)), motivo == null,
                    motivo);
        }).toList();
        // Pendiente: lo que aún debería salir de la bodega. Las líneas sin ítem cuentan, sin reserva
        List<ReservaLineaDTO> pendientes = new ArrayList<>();
        for (int i = 0; i < detalles.size(); i++) {
            if (motivoCierre(pedido, detalles.get(i)) == null) {
                pendientes.add(lineas.get(i));
            }
        }
        return new ReservasPedidoDTO(pedido.getId(), pedido.getNumeroPedido(), bodega.getCodigo(), estado(pendientes),
                lineas);
    }

    @Transactional(readOnly = true)
    public Page<ReservaDTO> listar(String texto, Pageable pageable) {
        return reservaRepository.buscar(texto == null ? "" : texto.strip(), pageable).map(ReservaDTO::de);
    }

    /** Deja comprometida para la línea la cantidad indicada (0 la libera). */
    @Transactional
    public ReservasPedidoDTO reservar(Long pedidoId, Long detalleId, BigDecimal cantidad, String usuario) {
        Pedido pedido = pedido(pedidoId);
        PedidoDetalle detalle = pedidoDetalleRepository.findById(detalleId)
                .filter(d -> d.getPedido().getId().equals(pedidoId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La línea " + detalleId + " no existe en el pedido " + pedido.getNumeroPedido() + "."));
        Optional<ReservaInventario> actual = reservaRepository.findByPedidoDetalleId(detalleId);
        if (cantidad.signum() == 0) {
            actual.ifPresent(reservaRepository::delete);
            return reservasPedido(pedidoId);
        }
        Optional<Item> item = item(detalle);
        String motivo = Boolean.TRUE.equals(detalle.getEliminadoEnFm())
                ? "La línea fue eliminada en FileMaker."
                : EstadoDetalle.CODIGO_NO_SALE.equals(detalle.getEstadoActual().getCodigo())
                ? "La línea está en No sale."
                : motivoNoReservable(pedido, detalle, item.isPresent());
        if (motivo != null) {
            throw new BusinessRuleException(motivo);
        }
        if (cantidad.compareTo(BigDecimal.valueOf(detalle.cantidadPorEntregar())) > 0) {
            throw new BusinessRuleException("No se pueden comprometer " + Decimales.paraMostrar(cantidad)
                    + ": a la línea le faltan " + detalle.cantidadPorEntregar() + " por entregar.");
        }
        Bodega bodega = almacen.obtener();
        Item bloqueado = itemRepository.bloquear(item.get().getId()).orElseThrow();
        BigDecimal libre = libreSinLinea(bloqueado, bodega, detalleId);
        if (cantidad.compareTo(libre) > 0) {
            throw new BusinessRuleException("Solo hay " + Decimales.paraMostrar(libre.max(BigDecimal.ZERO)) + " de "
                    + bloqueado.getCodigo() + " libres en " + bodega.getCodigo() + ".");
        }
        guardar(actual.orElse(null), detalle, bloqueado, bodega, cantidad, usuario);
        return reservasPedido(pedidoId);
    }

    /**
     * Compromete para cada línea pendiente lo que alcance de lo libre, hasta lo solicitado, en el orden de las líneas.
     * Nunca reduce una reserva que ya existe.
     */
    @Transactional
    public ReservasPedidoDTO comprometer(Long pedidoId, String usuario) {
        Pedido pedido = pedido(pedidoId);
        Bodega bodega = almacen.obtener();
        Map<Long, ReservaInventario> reservas = new HashMap<>();
        reservaRepository.findByPedidoDetallePedidoId(pedidoId).forEach(r -> reservas.put(r.getPedidoDetalle().getId(), r));
        for (PedidoDetalle d : lineas(pedidoId)) {
            Optional<Item> item = item(d);
            if (motivoNoReservable(pedido, d, item.isPresent()) != null) {
                continue;
            }
            Item bloqueado = itemRepository.bloquear(item.get().getId()).orElseThrow();
            ReservaInventario actual = reservas.get(d.getId());
            BigDecimal reservada = actual == null ? BigDecimal.ZERO : actual.getCantidad();
            BigDecimal objetivo = BigDecimal.valueOf(d.cantidadPorEntregar())
                    .min(libreSinLinea(bloqueado, bodega, d.getId()));
            if (objetivo.compareTo(reservada) > 0) {
                guardar(actual, d, bloqueado, bodega, objetivo, usuario);
            }
        }
        return reservasPedido(pedidoId);
    }

    @Transactional
    public ReservasPedidoDTO liberar(Long pedidoId) {
        pedido(pedidoId);
        reservaRepository.deleteAll(reservaRepository.findByPedidoDetallePedidoId(pedidoId));
        return reservasPedido(pedidoId);
    }

    /** La línea pasó a No sale: lo comprometido vuelve a quedar libre. */
    @Transactional
    public void liberarLinea(Long detalleId) {
        reservaRepository.findByPedidoDetalleId(detalleId).ifPresent(reservaRepository::delete);
    }

    /** Sincronización: las líneas que desaparecieron de FileMaker dejan de reservar. */
    @Transactional
    public void liberarLineasEliminadas() {
        int liberadas = reservaRepository.liberarLineasEliminadas();
        if (liberadas > 0) {
            log.info("Se liberaron {} reservas de líneas eliminadas en FileMaker", liberadas);
        }
    }

    /** FileMaker redujo la cantidad de la línea, o una remisión entregó parte: la reserva no queda por encima. */
    @Transactional
    public void ajustarACantidad(PedidoDetalle detalle) {
        BigDecimal pendiente = BigDecimal.valueOf(detalle.cantidadPorEntregar());
        reservaRepository.findByPedidoDetalleId(detalle.getId())
                .filter(r -> r.getCantidad().compareTo(pendiente) > 0)
                .ifPresent(r -> {
                    if (pendiente.signum() > 0) {
                        r.setCantidad(pendiente.setScale(Decimales.ESCALA));
                    } else {
                        reservaRepository.delete(r);
                    }
                });
    }

    /**
     * Remisión: salen de la bodega las piezas entregadas, primero de lo comprometido para la línea y luego de lo libre
     * (sin tocar lo de otros pedidos). Quien llama suma lo entregado a la línea después. Si el producto no está en el
     * maestro de ítems no se mueve inventario.
     *
     * @throws BusinessRuleException si no hay suficientes piezas disponibles para la línea
     */
    @Transactional
    public void entregar(PedidoDetalle detalle, int cantidad, Remision remision, String usuario) {
        Optional<Item> encontrado = item(detalle);
        if (encontrado.isEmpty()) {
            log.info("{}: {} no está en el maestro de ítems; la línea {} se entrega sin mover inventario",
                    remision.getNumero(), sku(detalle), detalle.getPosicion());
            return;
        }
        Bodega bodega = almacen.obtener();
        Item item = itemRepository.bloquear(encontrado.get().getId()).orElseThrow();
        Optional<ReservaInventario> reserva = reservaRepository.findByPedidoDetalleId(detalle.getId());
        BigDecimal reservada = reserva.map(ReservaInventario::getCantidad).orElse(BigDecimal.ZERO);
        BigDecimal libre = inventarioService.libre(item.getId(), bodega.getId()).max(BigDecimal.ZERO);
        BigDecimal piezas = BigDecimal.valueOf(cantidad);
        if (piezas.compareTo(reservada.add(libre)) > 0) {
            throw new BusinessRuleException("Línea " + detalle.getPosicion() + " (" + item.getCodigo() + "): solo hay "
                    + Decimales.paraMostrar(reservada.add(libre)) + " disponibles en " + bodega.getCodigo() + " ("
                    + Decimales.paraMostrar(reservada) + " comprometidas para ella y " + Decimales.paraMostrar(libre)
                    + " libres).");
        }
        reserva.ifPresent(r -> {
            BigDecimal queda = r.getCantidad().subtract(piezas);
            if (queda.signum() > 0) {
                r.setCantidad(queda);
            } else {
                reservaRepository.delete(r);
            }
        });
        BigDecimal faltante = inventarioService.sacarDeBodega(item, bodega, piezas, null,
                ReferenciaMovimiento.deRemision(remision, detalle), "Entrega", usuario);
        if (faltante.signum() > 0) {
            throw new IllegalStateException("Faltaron " + faltante + " de " + item.getCodigo() + " al entregar");
        }
    }

    /**
     * Se anuló una remisión: lo que salió para la línea vuelve a los mismos slots (o al de recepción si alguno se
     * desactivó) y queda comprometido otra vez para ella. Quien llama ya descontó lo entregado de la línea.
     */
    @Transactional
    public void devolverEntrega(PedidoDetalle detalle, Remision remision, List<MovimientoInventario> salidas,
                                String usuario) {
        if (salidas.isEmpty()) {
            return;
        }
        Bodega bodega = almacen.obtener();
        Item item = itemRepository.bloquear(salidas.getFirst().getItem().getId()).orElseThrow();
        BigDecimal devuelto = BigDecimal.ZERO;
        ReferenciaMovimiento referencia = ReferenciaMovimiento.deRemision(remision, detalle);
        for (MovimientoInventario salida : salidas) {
            Slot slot = Boolean.TRUE.equals(salida.getSlotOrigen().getActivo())
                    ? salida.getSlotOrigen()
                    : almacen.slotRecepcion(salida.getSlotOrigen().getBodega());
            inventarioService.entrada(item, slot, salida.getCantidad(), referencia, "Anulación de remisión", usuario);
            devuelto = devuelto.add(salida.getCantidad());
        }
        if (bodega.getId().equals(salidas.getFirst().getSlotOrigen().getBodega().getId())) {
            aumentarReserva(detalle, item, bodega, devuelto, usuario);
        }
    }

    /**
     * Una orden de producción entregó a la bodega de producto terminado piezas para la línea: quedan comprometidas
     * para ella (hasta lo que le falta por entregar). La entrada al inventario la registra la orden.
     */
    @Transactional
    public void comprometerProduccion(PedidoDetalle detalle, Item item, int piezas, String usuario) {
        if (piezas > 0) {
            aumentarReserva(detalle, item, almacen.obtener(), BigDecimal.valueOf(piezas), usuario);
        }
    }

    /** El pedido se facturó: sale de la bodega todo lo que sus líneas tengan comprometido. */
    @Transactional
    public void despacharPedido(Long pedidoId, String usuario) {
        reservaRepository.findByPedidoDetallePedidoId(pedidoId).forEach(r -> {
            reservaRepository.delete(r);
            Item item = itemRepository.bloquear(r.getItem().getId()).orElseThrow();
            sacar(item, r.getBodega(), r.getCantidad(), null, r.getPedidoDetalle(), "Despacho", usuario);
        });
    }

    /**
     * La línea llegó a la etapa de almacén con las piezas producidas: entran al slot de recepción de la bodega de
     * producto terminado y quedan comprometidas para la línea (hasta lo solicitado). Si el producto no está en el
     * maestro de ítems no se mueve nada.
     */
    @Transactional
    public void recibirProduccion(PedidoDetalle detalle, int piezas, String usuario) {
        if (piezas <= 0) {
            return;
        }
        Optional<Item> encontrado = item(detalle);
        if (encontrado.isEmpty()) {
            log.warn("Línea {} del pedido {}: {} no está en el maestro de ítems; lo producido no entra al inventario",
                    detalle.getId(), detalle.getPedido().getNumeroPedido(), sku(detalle));
            return;
        }
        Bodega bodega = almacen.obtener();
        Item item = itemRepository.bloquear(encontrado.get().getId()).orElseThrow();
        BigDecimal cantidad = BigDecimal.valueOf(piezas);
        inventarioService.entrada(item, almacen.slotRecepcion(bodega), cantidad, ReferenciaMovimiento.deLinea(detalle),
                "Producción", usuario);
        aumentarReserva(detalle, item, bodega, cantidad, usuario);
    }

    /**
     * Se corrigieron las piezas de una línea que ya está en la etapa de almacén: entra la diferencia, o sale (primero
     * del slot de recepción) y se descuenta de lo comprometido.
     */
    @Transactional
    public void corregirProduccion(PedidoDetalle detalle, int anterior, int nueva, String usuario) {
        if (nueva > anterior) {
            recibirProduccion(detalle, nueva - anterior, usuario);
            return;
        }
        Optional<ReservaInventario> reserva = reservaRepository.findByPedidoDetalleId(detalle.getId());
        Optional<Item> encontrado = item(detalle);
        if (nueva == anterior || encontrado.isEmpty()) {
            return;
        }
        Bodega bodega = almacen.obtener();
        Item item = itemRepository.bloquear(encontrado.get().getId()).orElseThrow();
        BigDecimal sobra = BigDecimal.valueOf(anterior - nueva);
        reserva.ifPresent(r -> {
            BigDecimal queda = r.getCantidad().subtract(sobra);
            if (queda.signum() > 0) {
                r.setCantidad(queda);
            } else {
                reservaRepository.delete(r);
            }
        });
        sacar(item, bodega, sobra, almacen.slotRecepcion(bodega), detalle, "Corrección de producción", usuario);
    }

    /** Sale lo comprometido de la línea; si no alcanza (no debería: nadie más puede tomarlo) sale lo que haya. */
    private void sacar(Item item, Bodega bodega, BigDecimal cantidad, Slot preferido, PedidoDetalle detalle,
                       String motivo, String usuario) {
        BigDecimal faltante = inventarioService.sacarDeBodega(item, bodega, cantidad, preferido,
                ReferenciaMovimiento.deLinea(detalle), motivo, usuario);
        if (faltante.signum() > 0) {
            log.warn("{} de la línea {} del pedido {}: faltaron {} de {} en {}", motivo, detalle.getId(),
                    detalle.getPedido().getNumeroPedido(), faltante, item.getCodigo(), bodega.getCodigo());
        }
    }

    /** Suma a lo comprometido para la línea, sin pasar de lo que le falta por entregar. */
    private void aumentarReserva(PedidoDetalle detalle, Item item, Bodega bodega, BigDecimal cantidad, String usuario) {
        ReservaInventario actual = reservaRepository.findByPedidoDetalleId(detalle.getId()).orElse(null);
        BigDecimal reservada = actual == null ? BigDecimal.ZERO : actual.getCantidad();
        BigDecimal nueva = reservada.add(cantidad).min(BigDecimal.valueOf(detalle.cantidadPorEntregar()));
        if (nueva.signum() > 0) {
            guardar(actual, detalle, item, bodega, nueva, usuario);
        } else if (actual != null) {
            reservaRepository.delete(actual);
        }
    }

    private BigDecimal libreSinLinea(Item item, Bodega bodega, Long detalleId) {
        return existenciaRepository.totalEnBodega(item.getId(), bodega.getId())
                .subtract(reservaRepository.totalReservado(item.getId(), bodega.getId(), detalleId));
    }

    private void guardar(ReservaInventario actual, PedidoDetalle detalle, Item item, Bodega bodega, BigDecimal cantidad,
                         String usuario) {
        ReservaInventario reserva = actual != null ? actual : new ReservaInventario();
        reserva.setPedidoDetalle(detalle);
        reserva.setItem(item);
        reserva.setBodega(bodega);
        reserva.setCantidad(cantidad.setScale(6));
        reserva.setUsuario(usuario);
        reserva.setFecha(FechaUtc.ahora());
        reservaRepository.save(reserva);
    }

    private Pedido pedido(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido " + pedidoId + " no existe."));
    }

    /** Las mismas líneas que los materiales del pedido: sin las eliminadas en FileMaker ni las que no salen. */
    private List<PedidoDetalle> lineas(Long pedidoId) {
        return pedidoDetalleRepository.findByPedidoIdAndEliminadoEnFmFalse(pedidoId).stream()
                .filter(d -> !EstadoDetalle.CODIGO_NO_SALE.equals(d.getEstadoActual().getCodigo()))
                .sorted(Comparator.comparing(PedidoDetalle::getPosicion, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PedidoDetalle::getId))
                .toList();
    }

    private Optional<Item> item(PedidoDetalle d) {
        return itemRepository.findByCodigo(sku(d).strip());
    }

    private static String sku(PedidoDetalle d) {
        return d.getProducto().getSku() != null ? d.getProducto().getSku() : d.getProducto().getFilemakerId();
    }

    private static String motivoNoReservable(Pedido pedido, PedidoDetalle d, boolean tieneItem) {
        String cierre = motivoCierre(pedido, d);
        if (cierre != null) {
            return cierre;
        }
        return tieneItem ? null : "El producto no está en el maestro de ítems.";
    }

    /** La línea ya no saldrá de la bodega: se entregó o el pedido terminó. */
    private static String motivoCierre(Pedido pedido, PedidoDetalle d) {
        if (Boolean.TRUE.equals(pedido.getEliminadoEnFm())) {
            return "El pedido fue eliminado en FileMaker.";
        }
        if (pedido.getFechaCierre() != null) {
            return "El pedido está cerrado en " + pedido.getEstadoActual().getNombre() + ".";
        }
        if (Boolean.TRUE.equals(d.getEstadoActual().getEsFinal())) {
            return "La línea ya está en " + d.getEstadoActual().getNombre() + ".";
        }
        if (d.cantidadPorEntregar() == 0) {
            return "La línea ya se entregó completa.";
        }
        return null;
    }

    private static EstadoCompromiso estado(List<ReservaLineaDTO> pendientes) {
        if (pendientes.isEmpty()) {
            return EstadoCompromiso.NO_APLICA;
        }
        if (pendientes.stream().allMatch(l -> l.reservada().signum() == 0)) {
            return EstadoCompromiso.SIN_COMPROMETER;
        }
        return pendientes.stream().allMatch(l -> l.reservada()
                .compareTo(BigDecimal.valueOf(l.cantidadSolicitada() - l.cantidadEntregada())) >= 0)
                ? EstadoCompromiso.COMPROMETIDO
                : EstadoCompromiso.PARCIAL;
    }

    private record Disponibilidad(BigDecimal existencia, BigDecimal reservado) {
        static final Disponibilidad NINGUNA = new Disponibilidad(BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal libre() {
            return existencia.subtract(reservado).max(BigDecimal.ZERO);
        }
    }
}
