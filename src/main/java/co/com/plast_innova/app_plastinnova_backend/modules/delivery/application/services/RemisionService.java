package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.EntregasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.LineaEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.LineaRemisionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionLineaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.EstadoRemision;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.Remision;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.RemisionLinea;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.repositories.RemisionRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.MovimientoInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.AvanceLineas;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.OrdenProduccionLineaRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Remisiones: entregas de producto terminado de un pedido. Cada línea sale de la bodega de producto terminado, primero
 * de lo comprometido para ella y luego de lo libre; cuando sus remisiones completan lo solicitado, la línea pasa sola
 * a Entregado (ya no se marca a mano). Anular una remisión devuelve el producto a los slots de donde salió, lo vuelve a
 * comprometer para la línea y la regresa a la etapa en que estaba.
 */
@Service
public class RemisionService {

    private final RemisionRepository remisionRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository detalleRepository;
    private final HistorialEstadoDetalleRepository historialDetalleRepository;
    private final EstadoDetalleRepository estadoRepository;
    private final ItemRepository itemRepository;
    private final ReservaInventarioRepository reservaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final OrdenProduccionLineaRepository ordenLineaRepository;
    private final ReservaInventarioService reservas;
    private final InventarioService inventario;
    private final AlmacenProductoTerminado almacen;
    private final AvanceLineas avanceLineas;
    private final PedidoMapper pedidoMapper;
    private final String codigoEntregado;

    public RemisionService(RemisionRepository remisionRepository, PedidoRepository pedidoRepository,
                           PedidoDetalleRepository detalleRepository,
                           HistorialEstadoDetalleRepository historialDetalleRepository,
                           EstadoDetalleRepository estadoRepository, ItemRepository itemRepository,
                           ReservaInventarioRepository reservaRepository,
                           MovimientoInventarioRepository movimientoRepository,
                           OrdenProduccionLineaRepository ordenLineaRepository, ReservaInventarioService reservas,
                           InventarioService inventario, AlmacenProductoTerminado almacen, AvanceLineas avanceLineas,
                           PedidoMapper pedidoMapper,
                           @Value("${app.entregas.estado-linea-entregada}") String codigoEntregado) {
        this.remisionRepository = remisionRepository;
        this.pedidoRepository = pedidoRepository;
        this.detalleRepository = detalleRepository;
        this.historialDetalleRepository = historialDetalleRepository;
        this.estadoRepository = estadoRepository;
        this.itemRepository = itemRepository;
        this.reservaRepository = reservaRepository;
        this.movimientoRepository = movimientoRepository;
        this.ordenLineaRepository = ordenLineaRepository;
        this.reservas = reservas;
        this.inventario = inventario;
        this.almacen = almacen;
        this.avanceLineas = avanceLineas;
        this.pedidoMapper = pedidoMapper;
        this.codigoEntregado = codigoEntregado.strip();
    }

    @Transactional(readOnly = true)
    public EntregasPedidoDTO entregasPedido(Long pedidoId) {
        Pedido pedido = pedido(pedidoId);
        Bodega bodega = almacen.obtener();
        String cerrado = motivoCerrado(pedido);
        List<PedidoDetalle> detalles = detalleRepository.findByPedidoIdAndEliminadoEnFmFalse(pedidoId).stream()
                .sorted(Comparator.comparing(PedidoDetalle::getPosicion, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PedidoDetalle::getId))
                .toList();
        Map<Long, BigDecimal> reservadas = new HashMap<>();
        reservaRepository.findByPedidoDetallePedidoId(pedidoId)
                .forEach(r -> reservadas.put(r.getPedidoDetalle().getId(), r.getCantidad()));
        Map<Long, String> ordenes = ordenLineaRepository.activasDe(detalles.stream().map(PedidoDetalle::getId).toList())
                .stream().collect(Collectors.toMap(OrdenProduccionLineaRepository.OrdenDeLinea::getDetalleId,
                        OrdenProduccionLineaRepository.OrdenDeLinea::getNumero));
        Map<Integer, BigDecimal> libres = new HashMap<>();

        List<LineaEntregaDTO> lineas = detalles.stream().map(d -> {
            Optional<Item> item = item(d);
            BigDecimal reservada = reservadas.getOrDefault(d.getId(), BigDecimal.ZERO);
            BigDecimal libre = item.map(i -> libres.computeIfAbsent(i.getId(),
                    id -> inventario.libre(id, bodega.getId()).max(BigDecimal.ZERO))).orElse(BigDecimal.ZERO);
            String motivo = cerrado != null ? cerrado : motivoLinea(d);
            int maximo = 0;
            if (motivo == null) {
                maximo = item.isEmpty() ? d.cantidadPorEntregar()
                        : reservada.add(libre).min(BigDecimal.valueOf(d.cantidadPorEntregar())).intValue();
                if (maximo == 0) {
                    motivo = "No hay producto en " + bodega.getCodigo() + " para la línea.";
                }
            }
            return new LineaEntregaDTO(d.getId(), d.getPosicion(), sku(d),
                    item.map(Item::getDescripcion).orElse(d.getProducto().getNombre()),
                    item.map(Item::getUnidad).orElse(null), pedidoMapper.toEstadoResponseDTO(d.getEstadoActual()),
                    d.getCantidadSolicitada(), d.getCantidadEntregada(), d.cantidadPorEntregar(),
                    Decimales.paraMostrar(reservada), Decimales.paraMostrar(libre), maximo, item.isEmpty(),
                    ordenes.get(d.getId()), motivo);
        }).toList();
        return new EntregasPedidoDTO(pedido.getId(), pedido.getNumeroPedido(), pedido.getCliente().getRazonSocial(),
                bodega.getCodigo(), cerrado, lineas,
                remisionRepository.findByPedidoIdOrderByFechaDescIdDesc(pedidoId).stream().map(this::dto).toList());
    }

    @Transactional(readOnly = true)
    public Page<RemisionDTO> listar(String texto, Pageable pageable) {
        return remisionRepository.buscar(texto == null ? "" : texto.strip(), pageable).map(this::dto);
    }

    @Transactional(readOnly = true)
    public RemisionDTO obtener(Long id) {
        return dto(remision(id));
    }

    /**
     * Emite la remisión y saca de la bodega lo entregado. Si alguna línea no tiene suficiente producto no se entrega
     * nada.
     */
    @Transactional
    public RemisionDTO emitir(Long pedidoId, RemisionRequestDTO request, String usuario) {
        Pedido pedido = pedido(pedidoId);
        String cerrado = motivoCerrado(pedido);
        if (cerrado != null) {
            throw new BusinessRuleException(cerrado);
        }
        Set<Long> vistas = new HashSet<>();
        for (LineaRemisionRequestDTO l : request.lineas()) {
            if (!vistas.add(l.detalleId())) {
                throw new SolicitudInvalidaException("La línea " + l.detalleId() + " aparece dos veces en la remisión.");
            }
        }
        Map<Long, PedidoDetalle> detalles = new HashMap<>();
        for (LineaRemisionRequestDTO l : request.lineas()) {
            PedidoDetalle d = detalleRepository.findById(l.detalleId())
                    .filter(x -> x.getPedido().getId().equals(pedidoId))
                    .orElseThrow(() -> new ResourceNotFoundException("La línea " + l.detalleId() + " no existe en el pedido "
                            + pedido.getNumeroPedido() + "."));
            String motivo = motivoLinea(d);
            if (motivo != null) {
                throw new BusinessRuleException("Línea " + d.getPosicion() + ": " + motivo);
            }
            if (l.cantidad() > d.cantidadPorEntregar()) {
                throw new BusinessRuleException("Línea " + d.getPosicion() + ": le faltan " + d.cantidadPorEntregar()
                        + " por entregar y la remisión trae " + l.cantidad() + ".");
            }
            detalles.put(d.getId(), d);
        }

        Remision remision = new Remision();
        remision.setNumero("REM-%06d".formatted(remisionRepository.siguienteNumero()));
        remision.setPedido(pedido);
        remision.setTransportador(Texto.limpiar(request.transportador()));
        remision.setPlaca(Texto.limpiar(request.placa()) == null ? null : Texto.limpiar(request.placa()).toUpperCase());
        remision.setObservaciones(Texto.limpiar(request.observaciones()));
        remision.setUsuario(usuario);
        remision.setFecha(FechaUtc.ahora());
        remisionRepository.save(remision);

        EstadoDetalle entregado = entregado();
        // Ítems en orden de id (igual que las reservas): dos remisiones del mismo producto no se cruzan
        List<LineaRemisionRequestDTO> ordenadas = request.lineas().stream()
                .sorted(Comparator.comparing((LineaRemisionRequestDTO l) -> item(detalles.get(l.detalleId()))
                        .map(Item::getId).orElse(0)).thenComparing(LineaRemisionRequestDTO::detalleId))
                .toList();
        for (LineaRemisionRequestDTO l : ordenadas) {
            PedidoDetalle d = detalles.get(l.detalleId());
            RemisionLinea linea = new RemisionLinea();
            linea.setRemision(remision);
            linea.setPedidoDetalle(d);
            linea.setCantidad(l.cantidad());
            remision.getLineas().add(linea);

            reservas.entregar(d, l.cantidad(), remision, usuario);
            d.setCantidadEntregada(d.getCantidadEntregada() + l.cantidad());
            if (d.cantidadPorEntregar() == 0) {
                ordenLineaRepository.findByPedidoDetalleIdAndActivaTrue(d.getId()).ifPresent(o -> o.setActiva(false));
                reservas.liberarLinea(d.getId());
                avanceLineas.cambiarEtapa(d, entregado, d.getCantidadEntregada(), TipoMovimientoDetalle.CAMBIO_ESTADO,
                        usuario, "Remisión " + remision.getNumero() + ".");
            } else {
                detalleRepository.save(d);
                reservas.ajustarACantidad(d);
            }
        }
        remisionRepository.save(remision);
        return dto(remision);
    }

    @Transactional
    public RemisionDTO anular(Long id, String justificacion, String usuario) {
        Remision remision = remision(id);
        if (remision.getEstado() == EstadoRemision.ANULADA) {
            throw new BusinessRuleException("La remisión " + remision.getNumero() + " ya está anulada.");
        }
        Pedido pedido = remision.getPedido();
        if (pedido.getFechaCierre() != null) {
            throw new BusinessRuleException("El pedido " + pedido.getNumeroPedido() + " ya está cerrado en "
                    + pedido.getEstadoActual().getNombre() + ": la remisión no se puede anular.");
        }
        String motivo = justificacion.strip();
        for (RemisionLinea linea : remision.getLineas()) {
            PedidoDetalle d = linea.getPedidoDetalle();
            d.setCantidadEntregada(Math.max(d.getCantidadEntregada() - linea.getCantidad(), 0));
            if (d.getEstadoActual().getCodigo().equals(codigoEntregado)) {
                EstadoDetalle etapa = historialDetalleRepository
                        .findFirstByPedidoDetalleIdAndEstadoIdOrderByFechaCambioDescIdDesc(d.getId(), d.getEstadoActual().getId())
                        .map(HistorialEstadoDetalle::getEstadoAnterior)
                        .orElseThrow(() -> new BusinessRuleException("No se encontró la etapa en que estaba la línea "
                                + d.getPosicion() + " antes de entregarse."));
                avanceLineas.cambiarEtapa(d, etapa, d.getCantidadProcesada(), TipoMovimientoDetalle.DEVOLUCION, usuario,
                        "Anulación de la remisión " + remision.getNumero() + ": " + motivo);
            } else {
                detalleRepository.save(d);
            }
            reservas.devolverEntrega(d, remision, movimientoRepository.findByRemisionIdAndPedidoDetalleIdAndTipo(
                    remision.getId(), d.getId(), TipoMovimientoInventario.SALIDA), usuario);
        }
        remision.setEstado(EstadoRemision.ANULADA);
        remision.setUsuarioAnulacion(usuario);
        remision.setFechaAnulacion(FechaUtc.ahora());
        remision.setMotivoAnulacion(motivo);
        remisionRepository.save(remision);
        return dto(remision);
    }

    private RemisionDTO dto(Remision r) {
        Pedido p = r.getPedido();
        List<RemisionLineaDTO> lineas = r.getLineas().stream()
                .sorted(Comparator.comparing((RemisionLinea l) -> l.getPedidoDetalle().getPosicion(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(l -> new RemisionLineaDTO(l.getPedidoDetalle().getId(), l.getPedidoDetalle().getPosicion(),
                        sku(l.getPedidoDetalle()), l.getPedidoDetalle().getProducto().getNombre(), l.getCantidad()))
                .toList();
        return new RemisionDTO(r.getId(), r.getNumero(), p.getId(), p.getNumeroPedido(), p.getCliente().getRazonSocial(),
                r.getEstado(), r.getTransportador(), r.getPlaca(), r.getObservaciones(), r.getUsuario(), r.getFecha(),
                r.getUsuarioAnulacion(), r.getFechaAnulacion(), r.getMotivoAnulacion(),
                lineas.stream().mapToInt(RemisionLineaDTO::cantidad).sum(), lineas);
    }

    private static String motivoCerrado(Pedido pedido) {
        if (Boolean.TRUE.equals(pedido.getEliminadoEnFm())) {
            return "El pedido fue eliminado en FileMaker.";
        }
        if (pedido.getFechaCierre() != null) {
            return "El pedido está cerrado en " + pedido.getEstadoActual().getNombre() + ".";
        }
        return null;
    }

    private static String motivoLinea(PedidoDetalle d) {
        if (Boolean.TRUE.equals(d.getEliminadoEnFm())) {
            return "La línea fue eliminada en FileMaker.";
        }
        if (EstadoDetalle.CODIGO_NO_SALE.equals(d.getEstadoActual().getCodigo())) {
            return "La línea está en No sale.";
        }
        if (Boolean.TRUE.equals(d.getEstadoActual().getEsFinal()) || d.cantidadPorEntregar() == 0) {
            return "La línea ya se entregó completa.";
        }
        return null;
    }

    private EstadoDetalle entregado() {
        return estadoRepository.findByCodigo(codigoEntregado).orElseThrow(() -> new IllegalStateException(
                "No existe el estado de línea " + codigoEntregado + " (app.entregas.estado-linea-entregada)."));
    }

    private Pedido pedido(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido " + id + " no existe."));
    }

    private Remision remision(Long id) {
        return remisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La remisión " + id + " no existe."));
    }

    private Optional<Item> item(PedidoDetalle d) {
        return itemRepository.findByCodigo(sku(d).strip());
    }

    private static String sku(PedidoDetalle d) {
        return d.getProducto().getSku() != null ? d.getProducto().getSku() : d.getProducto().getFilemakerId();
    }
}
