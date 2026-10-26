package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosDetalleService;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosPedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CorreccionCantidadRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FechaEntregaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.MovimientoNoSaleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoDetalleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.AuditoriaLog;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.repositories.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cambios de estado de pedidos y líneas. Reglas:
 * <ul>
 *   <li>Solo se permiten las transiciones configuradas en el catálogo, hacia estados activos.</li>
 *   <li>No se modifican pedidos ni líneas eliminados en FileMaker.</li>
 *   <li>Al entrar a un estado final se registra la fecha de cierre del pedido; si sale de él, se borra.</li>
 *   <li>La cantidad procesada de una línea no puede superar la cantidad solicitada.</li>
 *   <li>La fecha estimada de entrega no cambia en pedidos cerrados (protege el indicador de cumplimiento).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final HistorialEstadoPedidoRepository historialPedidoRepository;
    private final HistorialEstadoDetalleRepository historialDetalleRepository;
    private final GestionEstadosPedidoService estadosPedido;
    private final GestionEstadosDetalleService estadosDetalle;
    private final EstadoDetalleRepository estadoDetalleRepository;
    private final EstadoPedidoRepository estadoPedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaLogRepository auditoriaRepository;
    private final PedidoMapper pedidoMapper;
    private final ReservaInventarioService reservas;
    private final AlmacenProductoTerminado almacen;

    @Override
    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPedidoPorId(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido con ID " + pedidoId + " no existe."));
        return pedidoMapper.toResponseDTO(pedido);
    }

    @Override
    @Transactional
    public PedidoResponseDTO transicionarEstadoPedido(Long pedidoId, CambioEstadoPedidoRequestDTO request, String emailOperador) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido con ID " + pedidoId + " no existe."));
        if (Boolean.TRUE.equals(pedido.getEliminadoEnFm())) {
            throw new BusinessRuleException("El pedido fue eliminado en FileMaker y no admite cambios de estado.");
        }

        EstadoPedido estadoAnterior = pedido.getEstadoActual();
        EstadoPedido nuevoEstado = estadosPedido.validarTransicion(estadoAnterior, request.nuevoEstadoId());
        Usuario usuario = obtenerUsuarioActivo(emailOperador);

        pedido.setEstadoActual(nuevoEstado);
        if (Boolean.TRUE.equals(nuevoEstado.getEsFinal())) {
            pedido.setFechaCierre(FechaUtc.ahora());
            // Facturado: lo que quede comprometido sale de la bodega de producto terminado
            reservas.despacharPedido(pedido.getId(), emailOperador);
        } else if (Boolean.TRUE.equals(estadoAnterior.getEsFinal())) {
            // Reapertura (solo posible si se configuró una transición que sale del estado final)
            pedido.setFechaCierre(null);
        }
        pedidoRepository.save(pedido);

        historialPedidoRepository.save(HistorialEstadoPedido.builder()
                .pedido(pedido)
                .estadoAnterior(estadoAnterior)
                .estado(nuevoEstado)
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(request.observaciones())
                .build());

        return pedidoMapper.toResponseDTO(pedido);
    }

    @Override
    @Transactional
    public PedidoResponseDTO asignarFechaEstimadaEntrega(Long pedidoId, FechaEntregaRequestDTO request, String emailOperador) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido con ID " + pedidoId + " no existe."));
        if (Boolean.TRUE.equals(pedido.getEliminadoEnFm())) {
            throw new BusinessRuleException("El pedido fue eliminado en FileMaker y no admite cambios.");
        }
        if (pedido.getFechaCierre() != null) {
            throw new BusinessRuleException("El pedido ya está cerrado; su fecha estimada de entrega no se puede cambiar.");
        }

        LocalDate anterior = pedido.getFechaEstimadaEntrega();
        LocalDate nueva = request.fechaEstimadaEntrega();
        if (Objects.equals(anterior, nueva)) {
            return pedidoMapper.toResponseDTO(pedido);
        }
        Usuario usuario = obtenerUsuarioActivo(emailOperador);

        pedido.setFechaEstimadaEntrega(nueva);
        pedidoRepository.save(pedido);

        auditoriaRepository.save(AuditoriaLog.builder()
                .tablaAfectada("Pedido")
                .registroId(pedido.getId().toString())
                .campoModificado("fecha_estimada_entrega")
                .valorAnterior(anterior != null ? anterior.toString() : null)
                .valorNuevo(nueva != null ? nueva.toString() : null)
                .usuario(usuario)
                .build());

        return pedidoMapper.toResponseDTO(pedido);
    }

    @Override
    @Transactional
    public PedidoDetalleResponseDTO transicionarEstadoDetalle(Long detalleId, CambioEstadoDetalleRequestDTO request, String emailOperador) {
        PedidoDetalle detalle = pedidoDetalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido con ID " + detalleId + " no existe."));
        validarLineaVigente(detalle, "cambios de estado");
        if (request.cantidadProcesada() > detalle.getCantidadSolicitada()) {
            throw new BusinessRuleException("La cantidad procesada (" + request.cantidadProcesada()
                    + ") supera la cantidad solicitada de la línea (" + detalle.getCantidadSolicitada() + ").");
        }
        // Las piezas avanzan de etapa en etapa: no se pueden reportar más de las que llegaron de la anterior
        if (request.cantidadProcesada() > detalle.cantidadMaximaReportable()) {
            throw new BusinessRuleException("No se pueden reportar " + request.cantidadProcesada()
                    + " piezas: la etapa anterior reportó " + detalle.cantidadMaximaReportable()
                    + ". Si llegaron más, un supervisor debe corregir la cantidad de la etapa anterior.");
        }

        EstadoDetalle estadoAnterior = detalle.getEstadoActual();
        EstadoDetalle nuevoEstado = estadosDetalle.validarTransicion(estadoAnterior, request.nuevoEstadoId());
        Usuario usuario = obtenerUsuarioActivo(emailOperador);

        detalle.setEstadoActual(nuevoEstado);
        detalle.setCantidadProcesada(request.cantidadProcesada());
        detalle.setAvanceReportado(true);
        pedidoDetalleRepository.save(detalle);

        historialDetalleRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estadoAnterior(estadoAnterior)
                .estado(nuevoEstado)
                .cantidadProcesada(request.cantidadProcesada())
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(request.observaciones())
                .build());

        // Lo producido entra a la bodega de producto terminado comprometido para la línea; al entregarla, sale
        if (EstadoDetalle.CODIGO_NO_SALE.equals(nuevoEstado.getCodigo())) {
            reservas.liberarLinea(detalle.getId());
        } else if (Boolean.TRUE.equals(nuevoEstado.getEsFinal())) {
            reservas.despachar(detalle, request.cantidadProcesada(), emailOperador);
        } else if (almacen.esRecepcion(nuevoEstado.getCodigo()) && !almacen.esRecepcion(estadoAnterior.getCodigo())) {
            reservas.recibirProduccion(detalle, request.cantidadProcesada(), emailOperador);
        }
        return pedidoMapper.toDetalleResponseDTO(detalle);
    }

    @Override
    @Transactional
    public PedidoDetalleResponseDTO corregirCantidadDetalle(Long detalleId, CorreccionCantidadRequestDTO request, String emailUsuario) {
        PedidoDetalle detalle = pedidoDetalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido con ID " + detalleId + " no existe."));
        validarLineaVigente(detalle, "correcciones");
        if (!Boolean.TRUE.equals(detalle.getAvanceReportado())) {
            throw new BusinessRuleException("La línea aún no tiene piezas reportadas: no hay nada que corregir.");
        }
        int anterior = detalle.getCantidadProcesada();
        int nueva = request.cantidadProcesada();
        if (nueva > detalle.getCantidadSolicitada()) {
            throw new BusinessRuleException("La cantidad corregida (" + nueva
                    + ") supera la cantidad solicitada de la línea (" + detalle.getCantidadSolicitada() + ").");
        }
        if (nueva == anterior) {
            throw new BusinessRuleException("La línea ya tiene " + anterior + " piezas reportadas: no hay cambio que registrar.");
        }
        Usuario usuario = obtenerUsuarioActivo(emailUsuario);

        detalle.setCantidadProcesada(nueva);
        pedidoDetalleRepository.save(detalle);
        if (almacen.esRecepcion(detalle.getEstadoActual().getCodigo())) {
            reservas.corregirProduccion(detalle, anterior, nueva, emailUsuario);
        }

        // Movimiento propio en el historial: misma etapa, cantidad antes y después, y la justificación
        historialDetalleRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estadoAnterior(detalle.getEstadoActual())
                .estado(detalle.getEstadoActual())
                .tipo(TipoMovimientoDetalle.CORRECCION_CANTIDAD)
                .cantidadAnterior(anterior)
                .cantidadProcesada(nueva)
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(request.justificacion().trim())
                .build());

        return pedidoMapper.toDetalleResponseDTO(detalle);
    }

    /**
     * Solo el supervisor (detalle:no-sale). La línea queda en No sale sin cambiar sus piezas. Si con ella todas las
     * líneas vigentes del pedido quedan en No sale, el pedido se anula por no disponibilidad de producto.
     */
    @Override
    @Transactional
    public MovimientoNoSaleResponseDTO marcarNoSale(Long detalleId, JustificacionRequestDTO request, String emailUsuario) {
        PedidoDetalle detalle = pedidoDetalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido con ID " + detalleId + " no existe."));
        validarLineaVigente(detalle, "cambios de estado");
        EstadoDetalle actual = detalle.getEstadoActual();
        if (EstadoDetalle.CODIGO_NO_SALE.equals(actual.getCodigo())) {
            throw new BusinessRuleException("La línea ya está en " + actual.getNombre() + ".");
        }
        if (Boolean.TRUE.equals(actual.getEsFinal())) {
            throw new BusinessRuleException("La línea ya terminó en " + actual.getNombre() + ": no puede pasar a No sale.");
        }
        Pedido pedido = detalle.getPedido();
        if (pedido.getFechaCierre() != null) {
            throw new BusinessRuleException("El pedido " + pedido.getNumeroPedido() + " ya está cerrado en "
                    + pedido.getEstadoActual().getNombre() + ".");
        }
        EstadoDetalle noSale = estadoDelSistema(estadoDetalleRepository.findByCodigo(EstadoDetalle.CODIGO_NO_SALE));
        Usuario usuario = obtenerUsuarioActivo(emailUsuario);
        String justificacion = request.justificacion().trim();

        detalle.setEstadoActual(noSale);
        pedidoDetalleRepository.saveAndFlush(detalle); // el conteo de abajo debe ver esta línea ya en No sale
        reservas.liberarLinea(detalle.getId());
        historialDetalleRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estadoAnterior(actual)
                .estado(noSale)
                .tipo(TipoMovimientoDetalle.NO_SALE)
                .cantidadProcesada(detalle.getCantidadProcesada())
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(justificacion)
                .build());

        boolean anulado = pedidoDetalleRepository.countByPedidoIdAndEliminadoEnFmFalseAndEstadoActualIdNot(
                pedido.getId(), noSale.getId()) == 0;
        if (anulado) {
            EstadoPedido anulacion = estadoDelSistema(estadoPedidoRepository.findByCodigo(EstadoPedido.CODIGO_ANULADO));
            cambiarEstadoPedidoPorSistema(pedido, anulacion, usuario, recortar(
                    "Anulado: todas sus líneas están en No sale por no disponibilidad de producto. " + justificacion));
        }
        return new MovimientoNoSaleResponseDTO(pedidoMapper.toDetalleResponseDTO(detalle),
                pedidoMapper.toEstadoResponseDTO(pedido.getEstadoActual()), anulado);
    }

    /**
     * Solo el supervisor. La línea vuelve a la etapa en que estaba al marcarse No sale. Si el pedido estaba anulado,
     * se reabre en el estado que tenía antes de anularse.
     */
    @Override
    @Transactional
    public MovimientoNoSaleResponseDTO devolverLinea(Long detalleId, JustificacionRequestDTO request, String emailUsuario) {
        PedidoDetalle detalle = pedidoDetalleRepository.findById(detalleId)
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido con ID " + detalleId + " no existe."));
        validarLineaVigente(detalle, "cambios de estado");
        EstadoDetalle actual = detalle.getEstadoActual();
        if (!EstadoDetalle.CODIGO_NO_SALE.equals(actual.getCodigo())) {
            throw new BusinessRuleException("Solo se devuelven las líneas en No sale; esta está en " + actual.getNombre() + ".");
        }
        EstadoDetalle etapa = historialDetalleRepository
                .findFirstByPedidoDetalleIdAndTipoOrderByFechaCambioDescIdDesc(detalleId, TipoMovimientoDetalle.NO_SALE)
                .map(HistorialEstadoDetalle::getEstadoAnterior)
                .orElseThrow(() -> new BusinessRuleException(
                        "No se encontró la etapa en que estaba la línea antes de No sale: cámbiela desde FileMaker o el catálogo."));

        Pedido pedido = detalle.getPedido();
        EstadoPedido estadoPedido = pedido.getEstadoActual();
        boolean anulado = EstadoPedido.CODIGO_ANULADO.equals(estadoPedido.getCodigo());
        if (pedido.getFechaCierre() != null && !anulado) {
            throw new BusinessRuleException("El pedido " + pedido.getNumeroPedido() + " ya está cerrado en "
                    + estadoPedido.getNombre() + ": la línea no se puede devolver.");
        }
        Usuario usuario = obtenerUsuarioActivo(emailUsuario);
        String justificacion = request.justificacion().trim();

        detalle.setEstadoActual(etapa);
        pedidoDetalleRepository.save(detalle);
        historialDetalleRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estadoAnterior(actual)
                .estado(etapa)
                .tipo(TipoMovimientoDetalle.DEVOLUCION)
                .cantidadProcesada(detalle.getCantidadProcesada())
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(justificacion)
                .build());

        if (anulado) {
            EstadoPedido reapertura = historialPedidoRepository
                    .findFirstByPedidoIdAndEstadoIdOrderByFechaCambioDescIdDesc(pedido.getId(), estadoPedido.getId())
                    .map(HistorialEstadoPedido::getEstadoAnterior)
                    .orElseGet(() -> estadoPedidoRepository.findByEsInicialTrueAndActivoTrue().orElseThrow());
            cambiarEstadoPedidoPorSistema(pedido, reapertura, usuario, recortar(
                    "Reabierto: se devolvió la línea " + detalle.getPosicion() + " a " + etapa.getNombre() + ". " + justificacion));
        }
        return new MovimientoNoSaleResponseDTO(pedidoMapper.toDetalleResponseDTO(detalle),
                pedidoMapper.toEstadoResponseDTO(pedido.getEstadoActual()), anulado);
    }

    /** Anulación y reapertura: no siguen las transiciones del catálogo, las decide la regla de No sale. */
    private void cambiarEstadoPedidoPorSistema(Pedido pedido, EstadoPedido nuevo, Usuario usuario, String observaciones) {
        EstadoPedido anterior = pedido.getEstadoActual();
        pedido.setEstadoActual(nuevo);
        pedido.setFechaCierre(Boolean.TRUE.equals(nuevo.getEsFinal()) ? FechaUtc.ahora() : null);
        pedidoRepository.save(pedido);
        historialPedidoRepository.save(HistorialEstadoPedido.builder()
                .pedido(pedido)
                .estadoAnterior(anterior)
                .estado(nuevo)
                .usuario(usuario)
                .origen(OrigenCambio.USUARIO)
                .observaciones(observaciones)
                .build());
    }

    private static <E> E estadoDelSistema(Optional<E> estado) {
        return estado.orElseThrow(() -> new IllegalStateException("Falta un estado del sistema (migración V11)."));
    }

    private static String recortar(String texto) {
        return texto.length() <= 500 ? texto : texto.substring(0, 499) + "…";
    }

    private static void validarLineaVigente(PedidoDetalle detalle, String operacion) {
        if (Boolean.TRUE.equals(detalle.getEliminadoEnFm()) || Boolean.TRUE.equals(detalle.getPedido().getEliminadoEnFm())) {
            throw new BusinessRuleException("La línea fue eliminada en FileMaker y no admite " + operacion + ".");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialEstadoResponseDTO> obtenerHistorialPedido(Long pedidoId) {
        if (!pedidoRepository.existsById(pedidoId)) {
            throw new ResourceNotFoundException("El pedido con ID " + pedidoId + " no existe.");
        }
        return pedidoMapper.toHistorialPedidoDTOList(historialPedidoRepository.findByPedidoIdOrderByFechaCambioDescIdDesc(pedidoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialEstadoResponseDTO> obtenerHistorialDetalle(Long detalleId) {
        if (!pedidoDetalleRepository.existsById(detalleId)) {
            throw new ResourceNotFoundException("La línea de pedido con ID " + detalleId + " no existe.");
        }
        return pedidoMapper.toHistorialDetalleDTOList(historialDetalleRepository.findByPedidoDetalleIdOrderByFechaCambioDescIdDesc(detalleId));
    }

    private Usuario obtenerUsuarioActivo(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario operador no existe o está inactivo en el sistema."));
    }
}
