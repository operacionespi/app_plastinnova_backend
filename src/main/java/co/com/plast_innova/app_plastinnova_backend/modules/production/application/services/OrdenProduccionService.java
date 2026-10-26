package co.com.plast_innova.app_plastinnova_backend.modules.production.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosDetalleService;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReferenciaMovimiento;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.ReservaInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.MovimientoInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.ComponenteDirectoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.AvanceLineas;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AvanceOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ComponenteOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EdicionOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.HistorialOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.HistorialOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionLinea;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoHistorialOrden;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.HistorialOrdenProduccionRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.OrdenProduccionLineaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.OrdenProduccionRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Órdenes de producción. La orden recorre las etapas del catálogo de líneas y arrastra las líneas de pedido que atiende
 * (cada una con su parte de las piezas). Al llegar a la etapa de almacén:
 * <ol>
 *   <li>consume los componentes directos de la lista de materiales desde la bodega que ella indica, sin tocar lo
 *       comprometido por pedidos (si alguno no alcanza, el reporte se rechaza completo);</li>
 *   <li>lo producido entra al slot de producción de la bodega destino;</li>
 *   <li>cada línea queda con sus piezas comprometidas y lo demás queda libre;</li>
 *   <li>la orden termina y las líneas siguen solas: se entregan con remisión.</li>
 * </ol>
 * Los ítems se bloquean en orden de id antes de mover el inventario, igual que las reservas.
 */
@Service
@RequiredArgsConstructor
public class OrdenProduccionService {

    private final OrdenProduccionRepository ordenRepository;
    private final OrdenProduccionLineaRepository lineaRepository;
    private final HistorialOrdenProduccionRepository historialRepository;
    private final ItemRepository itemRepository;
    private final BodegaRepository bodegaRepository;
    private final PedidoDetalleRepository detalleRepository;
    private final EstadoDetalleRepository estadoRepository;
    private final GestionEstadosDetalleService estados;
    private final ListaMaterialesService listaMateriales;
    private final InventarioService inventario;
    private final ReservaInventarioService reservas;
    private final ReservaInventarioRepository reservaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AlmacenProductoTerminado almacen;
    private final AvanceLineas avanceLineas;
    private final PedidoMapper pedidoMapper;

    @Transactional(readOnly = true)
    public Page<OrdenProduccionResumenDTO> listar(String texto, EstadoOrdenProduccion estado, Integer etapaId,
                                                  Pageable pageable) {
        return ordenRepository.buscar(texto == null ? "" : texto.strip(), estado, etapaId, pageable).map(o ->
                new OrdenProduccionResumenDTO(o.getId(), o.getNumero(), o.getItem().getCodigo(),
                        o.getItem().getDescripcion(), o.getItem().getUnidad(), o.getCantidad(), o.getCantidadProcesada(),
                        o.getEstado(), pedidoMapper.toEstadoResponseDTO(o.getEtapa()), o.getBodegaDestino().getCodigo(),
                        o.getFechaRequerida(), o.getFechaCreacion(), o.getFechaCierre(),
                        o.getLineas().stream()
                                .filter(l -> Boolean.TRUE.equals(l.getActiva()) || !o.estaAbierta())
                                .map(l -> l.getPedidoDetalle().getPedido().getNumeroPedido())
                                .distinct().sorted().toList()));
    }

    @Transactional(readOnly = true)
    public OrdenProduccionDTO obtener(Long id) {
        OrdenProduccion orden = orden(id);
        boolean abierta = orden.estaAbierta();
        int piezas = abierta ? orden.cantidadMaximaReportable() : orden.getCantidadProcesada();
        Map<String, Optional<Bodega>> bodegas = new HashMap<>();
        List<ComponenteOrdenDTO> componentes = listaMateriales.componentesDirectos(orden.getItem().getCodigo()).stream()
                .map(c -> {
                    BigDecimal requerido = c.cantidadPorUnidad().multiply(BigDecimal.valueOf(piezas));
                    BigDecimal libre = Optional.ofNullable(c.bodega())
                            .flatMap(b -> bodegas.computeIfAbsent(b, bodegaRepository::findByCodigo))
                            .map(b -> inventario.libre(c.itemId(), b.getId()).max(BigDecimal.ZERO))
                            .orElse(null);
                    boolean alcanza = !abierta || c.bodega() == null
                            || (libre != null && libre.compareTo(requerido) >= 0);
                    return new ComponenteOrdenDTO(c.codigo(), c.descripcion(), c.tipo(), c.unidad(), c.bodega(),
                            Decimales.paraMostrar(c.cantidadPorUnidad()), Decimales.paraMostrar(requerido),
                            Decimales.paraMostrar(libre), alcanza);
                })
                .toList();
        List<LineaOrdenDTO> lineas = orden.getLineas().stream()
                .sorted(Comparator.comparing((OrdenProduccionLinea l) -> !Boolean.TRUE.equals(l.getActiva()))
                        .thenComparing(OrdenProduccionLinea::getId))
                .map(l -> linea(l.getPedidoDetalle(), l.getCantidad(), Boolean.TRUE.equals(l.getActiva())))
                .toList();
        List<HistorialOrdenDTO> historial = historialRepository.findByOrdenIdOrderByFechaDescIdDesc(id).stream()
                .map(h -> new HistorialOrdenDTO(h.getId(), h.getTipo(),
                        h.getEtapaAnterior() == null ? null : pedidoMapper.toEstadoResponseDTO(h.getEtapaAnterior()),
                        pedidoMapper.toEstadoResponseDTO(h.getEtapa()), h.getCantidad(), h.getUsuario(), h.getFecha(),
                        h.getObservaciones()))
                .toList();
        return new OrdenProduccionDTO(orden.getId(), orden.getNumero(), orden.getItem().getCodigo(),
                orden.getItem().getDescripcion(), orden.getItem().getUnidad(), orden.getCantidad(),
                orden.getCantidadProcesada(), orden.cantidadMaximaReportable(), orden.cantidadAsignada(),
                orden.getEstado(), pedidoMapper.toEstadoResponseDTO(orden.getEtapa()),
                abierta ? estados.destinosPermitidos(orden.getEtapa().getId()).stream()
                        .filter(e -> !Boolean.TRUE.equals(e.esFinal()))
                        .map(e -> new EstadoResponseDTO(e.id(), e.codigo(), e.nombre(), e.color()))
                        .toList() : List.of(),
                almacen.codigoEstadoRecepcion(), orden.getBodegaDestino().getCodigo(), orden.getFechaRequerida(),
                orden.getObservaciones(), orden.getUsuarioCreacion(), orden.getFechaCreacion(), orden.getFechaCierre(),
                lineas, componentes, historial,
                movimientoRepository.findByOrdenProduccionIdOrderById(id).stream().map(MovimientoDTO::de).toList());
    }

    /** Líneas de pedidos abiertos que piden el ítem de la orden y aún no tienen quién las produzca. */
    @Transactional(readOnly = true)
    public List<LineaOrdenDTO> candidatas(Long id) {
        OrdenProduccion orden = orden(id);
        List<PedidoDetalle> pendientes = detalleRepository.pendientesDeProducto(orden.getItem().getCodigo().strip());
        Set<Long> conOrden = lineaRepository.activasDe(pendientes.stream().map(PedidoDetalle::getId).toList()).stream()
                .map(OrdenProduccionLineaRepository.OrdenDeLinea::getDetalleId)
                .collect(Collectors.toSet());
        List<LineaOrdenDTO> candidatas = new ArrayList<>();
        for (PedidoDetalle d : pendientes) {
            if (conOrden.contains(d.getId()) || motivoNoVinculable(orden, d) != null) {
                continue;
            }
            int faltante = faltante(d);
            if (faltante > 0) {
                candidatas.add(linea(d, faltante, false));
            }
        }
        return candidatas;
    }

    @Transactional
    public OrdenProduccionDTO crear(OrdenProduccionRequestDTO request, String usuario) {
        Item item = itemRepository.findByCodigo(request.itemCodigo().strip())
                .orElseThrow(() -> new ResourceNotFoundException("El ítem " + request.itemCodigo().strip()
                        + " no existe en el maestro de ítems."));
        EstadoDetalle inicial = estadoRepository.findByEsInicialTrueAndActivoTrue()
                .orElseThrow(() -> new IllegalStateException("No hay estado inicial de línea activo."));
        OrdenProduccion orden = new OrdenProduccion();
        orden.setNumero("OP-%06d".formatted(ordenRepository.siguienteNumero()));
        orden.setItem(item);
        orden.setBodegaDestino(bodegaDestino(item, Texto.limpiar(request.bodegaDestino())));
        orden.setCantidad(request.cantidad());
        orden.setEtapa(inicial);
        orden.setFechaRequerida(request.fechaRequerida());
        orden.setObservaciones(Texto.limpiar(request.observaciones()));
        orden.setUsuarioCreacion(usuario);
        orden.setFechaCreacion(FechaUtc.ahora());
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.CREACION, null, orden.getCantidad(), usuario, orden.getObservaciones());
        return obtener(orden.getId());
    }

    @Transactional
    public OrdenProduccionDTO editar(Long id, EdicionOrdenRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        int asignada = orden.cantidadAsignada();
        if (request.cantidad() < asignada) {
            throw new BusinessRuleException("Las líneas de pedido tienen " + asignada + " piezas asignadas: la orden no "
                    + "puede quedar con " + request.cantidad() + ". Desvincule o reduzca líneas primero.");
        }
        List<String> cambios = new ArrayList<>();
        if (!request.cantidad().equals(orden.getCantidad())) {
            cambios.add("Cantidad " + orden.getCantidad() + " → " + request.cantidad());
            orden.setCantidad(request.cantidad());
        }
        if (!Objects.equals(request.fechaRequerida(), orden.getFechaRequerida())) {
            cambios.add("Fecha requerida " + texto(orden.getFechaRequerida()) + " → " + texto(request.fechaRequerida()));
            orden.setFechaRequerida(request.fechaRequerida());
        }
        String observaciones = Texto.limpiar(request.observaciones());
        if (!Objects.equals(observaciones, orden.getObservaciones())) {
            cambios.add("Observaciones actualizadas");
            orden.setObservaciones(observaciones);
        }
        if (!cambios.isEmpty()) {
            registrar(orden, TipoHistorialOrden.EDICION, orden.getEtapa(), orden.getCantidad(), usuario,
                    String.join(". ", cambios) + ".");
        }
        return obtener(id);
    }

    /**
     * La orden atenderá la línea: la línea pasa a la etapa de la orden y desde ahora avanza con ella. Lo asignado no
     * puede pasar de lo que le falta a la línea (sin lo comprometido ni lo entregado) ni de lo que queda sin asignar
     * en la orden.
     */
    @Transactional
    public OrdenProduccionDTO vincular(Long id, VinculoLineaRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        if (!orden.getBodegaDestino().getId().equals(almacen.obtener().getId())) {
            throw new BusinessRuleException("La orden entrega en " + orden.getBodegaDestino().getCodigo()
                    + ": solo las que entregan en " + almacen.codigo() + " atienden pedidos.");
        }
        PedidoDetalle detalle = detalleRepository.findById(request.detalleId())
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido " + request.detalleId() + " no existe."));
        String motivo = motivoNoVinculable(orden, detalle);
        if (motivo != null) {
            throw new BusinessRuleException(motivo);
        }
        lineaRepository.findByPedidoDetalleIdAndActivaTrue(detalle.getId()).ifPresent(l -> {
            throw new BusinessRuleException("La línea ya la atiende la orden " + l.getOrden().getNumero() + ".");
        });
        int faltante = faltante(detalle);
        if (request.cantidad() > faltante) {
            throw new BusinessRuleException("A la línea " + detalle.getPosicion() + " del pedido "
                    + detalle.getPedido().getNumeroPedido() + " le faltan " + faltante
                    + " piezas (sin lo comprometido ni lo entregado).");
        }
        int libres = orden.getCantidad() - orden.cantidadAsignada();
        if (request.cantidad() > libres) {
            throw new BusinessRuleException("La orden tiene " + orden.getCantidad() + " piezas y quedan " + libres
                    + " sin asignar. Aumente la cantidad de la orden primero.");
        }
        OrdenProduccionLinea linea = orden.getLineas().stream()
                .filter(l -> l.getPedidoDetalle().getId().equals(detalle.getId()))
                .findFirst()
                .orElseGet(() -> {
                    OrdenProduccionLinea nueva = new OrdenProduccionLinea();
                    nueva.setOrden(orden);
                    nueva.setPedidoDetalle(detalle);
                    orden.getLineas().add(nueva);
                    return nueva;
                });
        linea.setCantidad(request.cantidad());
        linea.setActiva(true);
        lineaRepository.save(linea);
        if (!detalle.getEstadoActual().getId().equals(orden.getEtapa().getId())) {
            int piezas = Boolean.TRUE.equals(orden.getAvanceReportado())
                    ? Math.min(request.cantidad(), orden.getCantidadProcesada()) : 0;
            avanceLineas.cambiarEtapa(detalle, orden.getEtapa(), piezas, TipoMovimientoDetalle.CAMBIO_ESTADO, usuario,
                    "Vinculada a la orden de producción " + orden.getNumero() + ".");
        }
        registrar(orden, TipoHistorialOrden.EDICION, orden.getEtapa(), orden.getCantidad(), usuario,
                "Atiende la línea " + detalle.getPosicion() + " del pedido " + detalle.getPedido().getNumeroPedido()
                        + " (" + request.cantidad() + " piezas).");
        return obtener(id);
    }

    /** La línea deja de seguir a la orden y queda en la etapa en que esté: desde ahí se reporta por separado. */
    @Transactional
    public OrdenProduccionDTO desvincular(Long id, Long detalleId, String usuario) {
        OrdenProduccion orden = abierta(id);
        OrdenProduccionLinea linea = orden.lineasActivas().stream()
                .filter(l -> l.getPedidoDetalle().getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La orden " + orden.getNumero()
                        + " no atiende la línea " + detalleId + "."));
        linea.setActiva(false);
        PedidoDetalle detalle = linea.getPedidoDetalle();
        registrar(orden, TipoHistorialOrden.EDICION, orden.getEtapa(), orden.getCantidad(), usuario,
                "Ya no atiende la línea " + detalle.getPosicion() + " del pedido "
                        + detalle.getPedido().getNumeroPedido() + ".");
        return obtener(id);
    }

    /**
     * Reporta las piezas que pasan a la siguiente etapa. Las líneas que atiende pasan con ella, cada una con su parte
     * (en el orden en que se vincularon). En la etapa de almacén se mueve el inventario y la orden termina.
     */
    @Transactional
    public OrdenProduccionDTO avanzar(Long id, AvanceOrdenRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        EstadoDetalle anterior = orden.getEtapa();
        EstadoDetalle nueva = estados.validarTransicion(anterior, request.etapaId());
        if (Boolean.TRUE.equals(nueva.getEsFinal())) {
            throw new BusinessRuleException("Una orden no pasa a " + nueva.getNombre() + ": termina al llegar a la "
                    + "bodega y sus líneas se entregan con remisión.");
        }
        int piezas = request.cantidad();
        if (piezas > orden.cantidadMaximaReportable()) {
            throw new BusinessRuleException("No se pueden reportar " + piezas + " piezas: "
                    + (Boolean.TRUE.equals(orden.getAvanceReportado())
                    ? "la etapa anterior reportó " + orden.getCantidadProcesada() + "."
                    : "la orden es de " + orden.getCantidad() + "."));
        }
        boolean recepcion = almacen.esRecepcion(nueva.getCodigo());
        String observaciones = Texto.limpiar(request.observaciones());
        if (recepcion) {
            recibir(orden, piezas, usuario);
        }

        orden.setEtapa(nueva);
        orden.setCantidadProcesada(piezas);
        orden.setAvanceReportado(true);
        int restante = piezas;
        for (OrdenProduccionLinea linea : orden.lineasActivas()) {
            PedidoDetalle detalle = linea.getPedidoDetalle();
            if (motivoFueraDeOrden(detalle) != null) {
                linea.setActiva(false);
                continue;
            }
            int parte = Math.min(Math.min(linea.getCantidad(), restante), detalle.getCantidadSolicitada());
            restante -= parte;
            avanceLineas.cambiarEtapa(detalle, nueva, parte, TipoMovimientoDetalle.CAMBIO_ESTADO, usuario,
                    orden.getNumero() + (observaciones == null ? "" : ": " + observaciones));
            if (recepcion) {
                reservas.comprometerProduccion(detalle, orden.getItem(), parte, usuario);
            }
        }
        if (recepcion) {
            orden.setEstado(EstadoOrdenProduccion.TERMINADA);
            orden.setFechaCierre(FechaUtc.ahora());
            orden.lineasActivas().forEach(l -> l.setActiva(false));
        }
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.CAMBIO_ETAPA, anterior, piezas, usuario, observaciones);
        return obtener(id);
    }

    /** Las líneas que atendía quedan en la etapa en que estén y se reportan por separado desde ahí. */
    @Transactional
    public OrdenProduccionDTO anular(Long id, String justificacion, String usuario) {
        OrdenProduccion orden = abierta(id);
        orden.lineasActivas().forEach(l -> l.setActiva(false));
        orden.setEstado(EstadoOrdenProduccion.ANULADA);
        orden.setFechaCierre(FechaUtc.ahora());
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.ANULACION, orden.getEtapa(), orden.getCantidadProcesada(), usuario,
                justificacion.strip());
        return obtener(id);
    }

    /**
     * Consume los componentes directos (si alguno no alcanza no se mueve nada) y lo producido entra al slot de
     * producción de la bodega destino.
     */
    private void recibir(OrdenProduccion orden, int piezas, String usuario) {
        BigDecimal cantidad = BigDecimal.valueOf(piezas);
        List<ComponenteDirectoDTO> componentes = listaMateriales.componentesDirectos(orden.getItem().getCodigo()).stream()
                .filter(c -> c.bodega() != null)
                .sorted(Comparator.comparing(ComponenteDirectoDTO::itemId))
                .toList();
        // Bloqueo de todos los ítems en orden de id: dos órdenes que consumen lo mismo se hacen en fila
        List<Integer> ids = new ArrayList<>(componentes.stream().map(ComponenteDirectoDTO::itemId).toList());
        ids.add(orden.getItem().getId());
        Map<Integer, Item> bloqueados = new HashMap<>();
        ids.stream().distinct().sorted().forEach(i -> bloqueados.put(i, itemRepository.bloquear(i).orElseThrow()));

        Map<String, Bodega> bodegas = new HashMap<>();
        List<String> faltantes = new ArrayList<>();
        for (ComponenteDirectoDTO c : componentes) {
            Bodega bodega = bodegas.computeIfAbsent(c.bodega(), codigo -> bodegaRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new BusinessRuleException("La bodega " + codigo + " de la lista de materiales no existe.")));
            BigDecimal requerido = c.cantidadPorUnidad().multiply(cantidad);
            BigDecimal libre = inventario.libre(c.itemId(), bodega.getId()).max(BigDecimal.ZERO);
            if (libre.compareTo(requerido) < 0) {
                faltantes.add(c.codigo() + " en " + bodega.getCodigo() + ": requiere "
                        + Decimales.paraMostrar(requerido) + " y hay " + Decimales.paraMostrar(libre));
            }
        }
        if (!faltantes.isEmpty()) {
            throw new BusinessRuleException("No alcanzan los materiales para reportar " + piezas + " piezas. "
                    + String.join("; ", faltantes) + ". Registre primero la entrada de lo que falta.");
        }
        ReferenciaMovimiento referencia = ReferenciaMovimiento.deOrden(orden);
        for (ComponenteDirectoDTO c : componentes) {
            BigDecimal faltante = inventario.sacarDeBodega(bloqueados.get(c.itemId()), bodegas.get(c.bodega()),
                    c.cantidadPorUnidad().multiply(cantidad), null, referencia, "Consumo de producción", usuario);
            if (faltante.signum() > 0) {
                throw new IllegalStateException("Faltaron " + faltante + " de " + c.codigo() + " en " + c.bodega());
            }
        }
        inventario.entrada(orden.getItem(), almacen.slotRecepcion(orden.getBodegaDestino()), cantidad, referencia,
                "Producción", usuario);
    }

    /**
     * Destino por defecto: los productos terminados (y los ítems que no son componente de nadie) van a la bodega de
     * producto terminado; los demás, a la bodega donde la lista de materiales los consume.
     */
    private Bodega bodegaDestino(Item item, String codigo) {
        if (codigo != null) {
            return bodegaRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new ResourceNotFoundException("La bodega " + codigo + " no existe."));
        }
        if (item.getTipo() != TipoItem.PRODUCTO_TERMINADO) {
            Optional<Bodega> consumo = listaMateriales.bodegaDeConsumo(item.getCodigo()).flatMap(bodegaRepository::findByCodigo);
            if (consumo.isPresent()) {
                return consumo.get();
            }
        }
        return almacen.obtener();
    }

    /** Lo que le falta a la línea sin lo que ya tiene comprometido ni lo entregado. */
    private int faltante(PedidoDetalle detalle) {
        BigDecimal reservada = reservaRepository.findByPedidoDetalleId(detalle.getId())
                .map(ReservaInventario::getCantidad).orElse(BigDecimal.ZERO);
        return Math.max(detalle.cantidadPorEntregar() - reservada.setScale(0, RoundingMode.CEILING).intValue(), 0);
    }

    private String motivoNoVinculable(OrdenProduccion orden, PedidoDetalle detalle) {
        String fuera = motivoFueraDeOrden(detalle);
        if (fuera != null) {
            return fuera;
        }
        String sku = detalle.getProducto().getSku() != null ? detalle.getProducto().getSku() : detalle.getProducto().getFilemakerId();
        if (sku == null || !sku.strip().equalsIgnoreCase(orden.getItem().getCodigo().strip())) {
            return "La línea es de " + sku + " y la orden produce " + orden.getItem().getCodigo() + ".";
        }
        // Una línea que ya avanzó por su cuenta solo se une a una orden que va en la misma etapa
        if (Boolean.TRUE.equals(detalle.getAvanceReportado())
                && !detalle.getEstadoActual().getId().equals(orden.getEtapa().getId())) {
            return "La línea ya avanzó por su cuenta hasta " + detalle.getEstadoActual().getNombre()
                    + " y la orden está en " + orden.getEtapa().getNombre() + ".";
        }
        if (almacen.esRecepcion(detalle.getEstadoActual().getCodigo())) {
            return "La línea ya está en " + detalle.getEstadoActual().getNombre() + ".";
        }
        return null;
    }

    /** La línea ya no puede seguir a una orden: se eliminó, no sale, terminó o su pedido se cerró. */
    private static String motivoFueraDeOrden(PedidoDetalle detalle) {
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

    private LineaOrdenDTO linea(PedidoDetalle d, int cantidad, boolean activa) {
        Pedido p = d.getPedido();
        return new LineaOrdenDTO(d.getId(), p.getId(), p.getNumeroPedido(), p.getCliente().getRazonSocial(),
                d.getPosicion(), pedidoMapper.toEstadoResponseDTO(d.getEstadoActual()), d.getCantidadSolicitada(),
                cantidad, activa, p.getFechaEstimadaEntrega());
    }

    private void registrar(OrdenProduccion orden, TipoHistorialOrden tipo, EstadoDetalle anterior, Integer cantidad,
                           String usuario, String observaciones) {
        historialRepository.save(HistorialOrdenProduccion.builder()
                .orden(orden)
                .tipo(tipo)
                .etapaAnterior(anterior)
                .etapa(orden.getEtapa())
                .cantidad(cantidad)
                .usuario(usuario)
                .fecha(FechaUtc.ahora())
                .observaciones(observaciones == null || observaciones.length() <= 500
                        ? observaciones : observaciones.substring(0, 499) + "…")
                .build());
    }

    private OrdenProduccion orden(Long id) {
        return ordenRepository.findWithItemById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La orden de producción " + id + " no existe."));
    }

    private OrdenProduccion abierta(Long id) {
        OrdenProduccion orden = orden(id);
        if (!orden.estaAbierta()) {
            throw new BusinessRuleException("La orden " + orden.getNumero() + " está "
                    + orden.getEstado().name().toLowerCase() + ": ya no admite cambios.");
        }
        return orden;
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? "sin fecha" : fecha.toString();
    }
}
