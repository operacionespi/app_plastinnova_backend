package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.EstadoCompromiso;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaLineaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Existencia;
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
 * <p>Ciclo de una reserva: la crea un usuario (Comprometer) o la producción de la línea al llegar a la bodega
 * (recibirProduccion); se convierte en salida de inventario cuando la línea se entrega o el pedido se factura
 * (despachar), y se libera si la línea no sale o desaparece de FileMaker.
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
                    : BigDecimal.valueOf(d.getCantidadSolicitada()).min(reservada.add(disp.libre()));
            return new ReservaLineaDTO(d.getId(), d.getPosicion(), sku(d), item.map(Item::getCodigo).orElse(null),
                    item.map(Item::getDescripcion).orElse(d.getProducto().getNombre()),
                    item.map(Item::getUnidad).orElse(null), d.getEstadoActual().getNombre(), d.getCantidadSolicitada(),
                    Decimales.paraMostrar(reservada), Decimales.paraMostrar(disp.existencia()),
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
        if (cantidad.compareTo(BigDecimal.valueOf(detalle.getCantidadSolicitada())) > 0) {
            throw new BusinessRuleException("No se pueden comprometer " + Decimales.paraMostrar(cantidad)
                    + ": la línea solicita " + detalle.getCantidadSolicitada() + ".");
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
            BigDecimal objetivo = BigDecimal.valueOf(d.getCantidadSolicitada())
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

    /** FileMaker redujo la cantidad de la línea: la reserva no puede quedar por encima. */
    @Transactional
    public void ajustarACantidad(PedidoDetalle detalle) {
        BigDecimal solicitada = BigDecimal.valueOf(detalle.getCantidadSolicitada());
        reservaRepository.findByPedidoDetalleId(detalle.getId())
                .filter(r -> r.getCantidad().compareTo(solicitada) > 0)
                .ifPresent(r -> {
                    if (solicitada.signum() > 0) {
                        r.setCantidad(solicitada);
                    } else {
                        reservaRepository.delete(r);
                    }
                });
    }

    /**
     * La línea se entregó: sale del inventario lo comprometido (hasta las piezas entregadas), de los slots de la bodega
     * con más saldo primero, y la reserva desaparece. Lo producido para la línea ya está comprometido desde que llegó
     * a la bodega (ver recibirProduccion).
     */
    @Transactional
    public void despachar(PedidoDetalle detalle, int entregadas, String usuario) {
        reservaRepository.findByPedidoDetalleId(detalle.getId()).ifPresent(reserva -> {
            reservaRepository.delete(reserva);
            Item item = itemRepository.bloquear(reserva.getItem().getId()).orElseThrow();
            sacar(item, reserva.getBodega(), reserva.getCantidad().min(BigDecimal.valueOf(entregadas)), null, detalle,
                    "Despacho", usuario);
        });
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
        inventarioService.entradaDePedido(item, almacen.slotRecepcion(bodega), cantidad, detalle, "Producción", usuario);
        ReservaInventario actual = reservaRepository.findByPedidoDetalleId(detalle.getId()).orElse(null);
        BigDecimal reservada = actual == null ? BigDecimal.ZERO : actual.getCantidad();
        guardar(actual, detalle, item, bodega, reservada.add(cantidad)
                .min(BigDecimal.valueOf(detalle.getCantidadSolicitada())), usuario);
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

    /**
     * Saca la cantidad de los slots de la bodega: primero del preferido y luego de los de más saldo. Si no alcanza
     * (no debería: lo comprometido no se puede sacar por otra vía) sale lo que haya.
     */
    private void sacar(Item item, Bodega bodega, BigDecimal cantidad, Slot preferido, PedidoDetalle detalle,
                       String motivo, String usuario) {
        BigDecimal pendiente = cantidad;
        List<Existencia> saldos = new ArrayList<>(existenciaRepository.bloquearEnBodega(item.getId(), bodega.getId()));
        if (preferido != null) {
            saldos.sort(Comparator.comparing(e -> !e.getSlot().getId().equals(preferido.getId())));
        }
        for (Existencia e : saldos) {
            if (pendiente.signum() <= 0) {
                break;
            }
            BigDecimal sale = pendiente.min(e.getCantidad());
            inventarioService.salidaDePedido(item, e.getSlot(), sale, detalle, motivo, usuario);
            pendiente = pendiente.subtract(sale);
        }
        if (pendiente.signum() > 0) {
            log.warn("{} de la línea {} del pedido {}: faltaron {} de {} en {}", motivo, detalle.getId(),
                    detalle.getPedido().getNumeroPedido(), pendiente, item.getCodigo(), bodega.getCodigo());
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
        return null;
    }

    private static EstadoCompromiso estado(List<ReservaLineaDTO> pendientes) {
        if (pendientes.isEmpty()) {
            return EstadoCompromiso.NO_APLICA;
        }
        if (pendientes.stream().allMatch(l -> l.reservada().signum() == 0)) {
            return EstadoCompromiso.SIN_COMPROMETER;
        }
        return pendientes.stream().allMatch(l -> l.reservada().compareTo(BigDecimal.valueOf(l.cantidadSolicitada())) >= 0)
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
