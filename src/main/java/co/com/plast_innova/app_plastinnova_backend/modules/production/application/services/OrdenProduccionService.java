package co.com.plast_innova.app_plastinnova_backend.modules.production.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.MovimientoInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.CantidadProductoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ComponenteOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EdicionOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EntregaProcesoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EtapaProductoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.HistorialOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.HistorialOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionEtapa;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionLinea;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionProducto;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoHistorialOrden;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.EntregaProcesoRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Órdenes de producción de uno o más productos. Se crean eligiendo líneas pendientes de uno o varios pedidos (se
 * agrupan por ítem) y/o productos sin pedido. Cada producto copia la ruta de su ítem; sus piezas empiezan en el primer
 * proceso y avanzan con entregas entre procesos ({@link EntregaProcesoService}). Las líneas que atiende siguen la
 * etapa donde están sus piezas ({@link SeguimientoLineas}).
 */
@Service
@RequiredArgsConstructor
public class OrdenProduccionService {

    private final OrdenProduccionRepository ordenRepository;
    private final OrdenProduccionLineaRepository lineaRepository;
    private final HistorialOrdenProduccionRepository historialRepository;
    private final EntregaProcesoRepository entregaRepository;
    private final ItemRepository itemRepository;
    private final BodegaRepository bodegaRepository;
    private final PedidoDetalleRepository detalleRepository;
    private final ListaMaterialesService listaMateriales;
    private final InventarioService inventario;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AlmacenProductoTerminado almacen;
    private final RutaProduccionService rutas;
    private final SeguimientoLineas seguimiento;
    private final PedidoMapper pedidoMapper;

    @Transactional(readOnly = true)
    public Page<OrdenProduccionResumenDTO> listar(String texto, EstadoOrdenProduccion estado, Integer etapaId,
                                                  Pageable pageable) {
        return ordenRepository.buscar(texto == null ? "" : texto.strip(), estado, etapaId, pageable).map(o -> {
            Map<Integer, EtapaProductoDTO> enProceso = new LinkedHashMap<>();
            o.getProductos().stream()
                    .flatMap(p -> p.getEtapas().stream())
                    .filter(e -> e.getCantidad() > 0)
                    .sorted(Comparator.comparing((OrdenProduccionEtapa e) -> e.getEtapa().getOrdenLogico()))
                    .forEach(e -> enProceso.merge(e.getEtapa().getId(),
                            new EtapaProductoDTO(pedidoMapper.toEstadoResponseDTO(e.getEtapa()), e.getCantidad()),
                            (a, b) -> new EtapaProductoDTO(a.etapa(), a.cantidad() + b.cantidad())));
            return new OrdenProduccionResumenDTO(o.getId(), o.getNumero(), o.getEstado(),
                    o.getProductos().stream().map(p -> new ProductoResumenDTO(p.getItem().getCodigo(),
                            p.getItem().getDescripcion(), p.getCantidad(), p.getCantidadRecibida(), p.getCantidadBaja()))
                            .toList(),
                    o.getProductos().stream().mapToInt(OrdenProduccionProducto::getCantidad).sum(),
                    o.getProductos().stream().mapToInt(OrdenProduccionProducto::getCantidadRecibida).sum(),
                    List.copyOf(enProceso.values()), o.getFechaRequerida(), o.getFechaCreacion(), o.getFechaCierre(),
                    o.getLineas().stream()
                            .filter(l -> Boolean.TRUE.equals(l.getActiva()) || !o.estaAbierta())
                            .map(l -> l.getPedidoDetalle().getPedido().getNumeroPedido())
                            .distinct().sorted().toList());
        });
    }

    @Transactional(readOnly = true)
    public OrdenProduccionDTO obtener(Long id) {
        OrdenProduccion orden = orden(id);
        List<ProductoOrdenDTO> productos = orden.getProductos().stream().map(p -> producto(orden, p)).toList();
        List<EntregaProcesoDTO> entregas = entregaRepository.findByProductoOrdenIdOrderByFechaDescIdDesc(id).stream()
                .map(e -> new EntregaProcesoDTO(e.getId(), e.getProducto().getId(), e.getProducto().getItem().getCodigo(),
                        e.getTipo(), pedidoMapper.toEstadoResponseDTO(e.getEtapaOrigen()),
                        e.getEtapaDestino() == null ? null : pedidoMapper.toEstadoResponseDTO(e.getEtapaDestino()),
                        e.getCantidad(), e.getUsuario(), e.getFecha(), e.getObservaciones()))
                .toList();
        List<HistorialOrdenDTO> historial = historialRepository.findByOrdenIdOrderByFechaDescIdDesc(id).stream()
                .map(h -> new HistorialOrdenDTO(h.getId(), h.getTipo(),
                        h.getEtapaAnterior() == null ? null : pedidoMapper.toEstadoResponseDTO(h.getEtapaAnterior()),
                        h.getEtapa() == null ? null : pedidoMapper.toEstadoResponseDTO(h.getEtapa()), h.getCantidad(),
                        h.getUsuario(), h.getFecha(), h.getObservaciones()))
                .toList();
        return new OrdenProduccionDTO(orden.getId(), orden.getNumero(), orden.getEstado(), orden.getFechaRequerida(),
                orden.getObservaciones(), orden.getUsuarioCreacion(), orden.getFechaCreacion(), orden.getFechaCierre(),
                pedidoMapper.toEstadoResponseDTO(rutas.recepcion()), productos, entregas, historial,
                movimientoRepository.findByOrdenProduccionIdOrderById(id).stream().map(MovimientoDTO::de).toList());
    }

    /**
     * Líneas de pedidos abiertos con piezas por producir y sin orden: de donde se crea una orden. Las que no se pueden
     * incluir llevan el motivo (el producto no está en el maestro de ítems o la línea ya avanzó por su cuenta).
     */
    @Transactional(readOnly = true)
    public Page<LineaOrdenDTO> lineasPendientes(String texto, Pageable pageable) {
        EstadoDetalle recepcion = rutas.recepcion();
        Map<String, Optional<Item>> items = new HashMap<>();
        Map<Integer, EstadoDetalle> primeros = new HashMap<>();
        return detalleRepository.pendientesSinOrden(texto == null ? "" : texto.strip(), pageable).map(d -> {
            String sku = SeguimientoLineas.sku(d);
            Optional<Item> item = sku == null ? Optional.empty() : items.computeIfAbsent(sku, itemRepository::findByCodigo);
            String motivo = null;
            if (item.isEmpty()) {
                motivo = sku + " no está en el maestro de ítems: cargue la lista de materiales.";
            } else {
                EstadoDetalle primero = primeros.computeIfAbsent(item.get().getId(),
                        i -> rutas.deItem(item.get()).etapas().getFirst());
                EstadoDetalle actual = d.getEstadoActual();
                if (!actual.getId().equals(recepcion.getId()) && actual.getOrdenLogico() > primero.getOrdenLogico()) {
                    motivo = "Ya avanzó por su cuenta hasta " + actual.getNombre() + ".";
                }
            }
            return linea(d, item.map(Item::getCodigo).orElse(sku), item.map(Item::getDescripcion).orElse(null),
                    seguimiento.faltante(d), 0, false, motivo);
        });
    }

    /** Líneas que pueden atender los productos de la orden que aún están en proceso. */
    @Transactional(readOnly = true)
    public List<LineaOrdenDTO> candidatas(Long id) {
        OrdenProduccion orden = orden(id);
        EstadoDetalle recepcion = rutas.recepcion();
        List<LineaOrdenDTO> candidatas = new ArrayList<>();
        for (OrdenProduccionProducto producto : orden.getProductos()) {
            if (producto.terminado()) {
                continue;
            }
            List<PedidoDetalle> pendientes = detalleRepository.pendientesDeProducto(producto.getItem().getCodigo().strip());
            Set<Long> conOrden = lineaRepository.activasDe(pendientes.stream().map(PedidoDetalle::getId).toList())
                    .stream()
                    .map(OrdenProduccionLineaRepository.OrdenDeLinea::getDetalleId)
                    .collect(Collectors.toSet());
            for (PedidoDetalle d : pendientes) {
                if (conOrden.contains(d.getId()) || seguimiento.motivoNoVinculable(producto, d, recepcion) != null) {
                    continue;
                }
                int faltante = seguimiento.faltante(d);
                if (faltante > 0) {
                    candidatas.add(linea(d, producto.getItem().getCodigo(), producto.getItem().getDescripcion(),
                            faltante, 0, false, null));
                }
            }
        }
        return candidatas;
    }

    @Transactional
    public OrdenProduccionDTO crear(OrdenProduccionRequestDTO request, String usuario) {
        Set<String> codigos = new HashSet<>();
        for (ProductoOrdenRequestDTO p : request.productos()) {
            if (!codigos.add(p.itemCodigo().strip().toUpperCase())) {
                throw new BusinessRuleException("El ítem " + p.itemCodigo().strip() + " está dos veces en la orden: "
                        + "junte sus cantidades en un solo producto.");
            }
        }
        OrdenProduccion orden = new OrdenProduccion();
        orden.setNumero("OP-%06d".formatted(ordenRepository.siguienteNumero()));
        orden.setFechaRequerida(request.fechaRequerida());
        orden.setObservaciones(Texto.limpiar(request.observaciones()));
        orden.setUsuarioCreacion(usuario);
        orden.setFechaCreacion(FechaUtc.ahora());
        ordenRepository.save(orden);
        EstadoDetalle recepcion = rutas.recepcion();
        Set<Long> detalles = new HashSet<>();
        for (ProductoOrdenRequestDTO p : request.productos()) {
            nuevoProducto(orden, p, recepcion, detalles);
        }
        ordenRepository.saveAndFlush(orden);
        orden.getProductos().forEach(p -> seguimiento.sincronizar(p, recepcion, usuario, "Orden creada"));
        registrar(orden, TipoHistorialOrden.CREACION, totalPiezas(orden), usuario,
                Optional.ofNullable(orden.getObservaciones()).orElse(descripcion(orden)));
        return obtener(orden.getId());
    }

    @Transactional
    public OrdenProduccionDTO editar(Long id, EdicionOrdenRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        List<String> cambios = new ArrayList<>();
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
            registrar(orden, TipoHistorialOrden.EDICION, null, usuario, String.join(". ", cambios) + ".");
        }
        return obtener(id);
    }

    @Transactional
    public OrdenProduccionDTO agregarProducto(Long id, ProductoOrdenRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        String codigo = request.itemCodigo().strip();
        if (orden.getProductos().stream().anyMatch(p -> p.getItem().getCodigo().strip().equalsIgnoreCase(codigo))) {
            throw new BusinessRuleException("La orden ya produce " + codigo + ": cambie la cantidad de ese producto.");
        }
        EstadoDetalle recepcion = rutas.recepcion();
        OrdenProduccionProducto producto = nuevoProducto(orden, request, recepcion, new HashSet<>());
        ordenRepository.saveAndFlush(orden);
        seguimiento.sincronizar(producto, recepcion, usuario, "Producto agregado a la orden");
        registrar(orden, TipoHistorialOrden.EDICION, producto.getCantidad(), usuario,
                "Agrega " + producto.getCantidad() + " de " + producto.getItem().getCodigo() + ".");
        return obtener(id);
    }

    /** La diferencia entra al primer proceso o sale de él (no puede dejar sin piezas a las líneas que espera). */
    @Transactional
    public OrdenProduccionDTO cambiarCantidad(Long id, Long productoId, CantidadProductoRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        OrdenProduccionProducto producto = producto(orden, productoId);
        int diferencia = request.cantidad() - producto.getCantidad();
        if (diferencia == 0) {
            return obtener(id);
        }
        OrdenProduccionEtapa primera = producto.primeraEtapa();
        if (-diferencia > primera.getCantidad()) {
            throw new BusinessRuleException("Solo se pueden quitar piezas que estén en " + primera.getEtapa().getNombre()
                    + " y hay " + primera.getCantidad() + ".");
        }
        if (producto.enProceso() + diferencia < producto.pendienteDeLineas()) {
            throw new BusinessRuleException("Las líneas de pedido esperan " + producto.pendienteDeLineas() + " piezas de "
                    + producto.getItem().getCodigo() + ": desvincule o reduzca líneas primero.");
        }
        String cambio = "Cantidad de " + producto.getItem().getCodigo() + " " + producto.getCantidad() + " → "
                + request.cantidad() + ".";
        primera.setCantidad(primera.getCantidad() + diferencia);
        producto.setCantidad(request.cantidad());
        seguimiento.sincronizar(producto, rutas.recepcion(), usuario, cambio);
        seguimiento.cerrarSiCompleta(orden);
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.EDICION, request.cantidad(), usuario, cambio);
        return obtener(id);
    }

    /** Solo un producto sin entregas ni líneas; la orden debe conservar al menos uno. */
    @Transactional
    public OrdenProduccionDTO quitarProducto(Long id, Long productoId, String usuario) {
        OrdenProduccion orden = abierta(id);
        OrdenProduccionProducto producto = producto(orden, productoId);
        if (orden.getProductos().size() == 1) {
            throw new BusinessRuleException("Es el único producto de la orden: anule la orden en su lugar.");
        }
        if (entregaRepository.existsByProductoId(productoId)) {
            throw new BusinessRuleException(producto.getItem().getCodigo() + " ya tiene entregas registradas: reduzca "
                    + "su cantidad o dé de baja las piezas.");
        }
        if (!producto.lineasActivas().isEmpty()) {
            throw new BusinessRuleException(producto.getItem().getCodigo() + " atiende líneas de pedido: desvincúlelas primero.");
        }
        List<OrdenProduccionLinea> antecedentes = List.copyOf(producto.getLineas());
        orden.getLineas().removeAll(antecedentes);
        lineaRepository.deleteAll(antecedentes);
        orden.getProductos().remove(producto);
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.EDICION, null, usuario, "Quita " + producto.getItem().getCodigo() + ".");
        return obtener(id);
    }

    /** La línea pasa a seguir al producto de su ítem; lo asignado sale de las piezas en proceso que nadie espera. */
    @Transactional
    public OrdenProduccionDTO vincular(Long id, VinculoLineaRequestDTO request, String usuario) {
        OrdenProduccion orden = abierta(id);
        PedidoDetalle detalle = detalleRepository.findById(request.detalleId())
                .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido " + request.detalleId() + " no existe."));
        String sku = SeguimientoLineas.sku(detalle);
        OrdenProduccionProducto producto = orden.getProductos().stream()
                .filter(p -> !p.terminado() && p.getItem().getCodigo().strip().equalsIgnoreCase(sku))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("La orden no tiene en proceso " + sku
                        + ": agréguelo como producto."));
        int porAsignar = producto.enProceso() - producto.pendienteDeLineas();
        if (request.cantidad() > porAsignar) {
            throw new BusinessRuleException("De " + producto.getItem().getCodigo() + " quedan " + porAsignar
                    + " piezas en proceso sin asignar. Aumente la cantidad del producto primero.");
        }
        EstadoDetalle recepcion = rutas.recepcion();
        asignar(orden, producto, detalle, request.cantidad(), recepcion);
        ordenRepository.saveAndFlush(orden);
        seguimiento.sincronizar(producto, recepcion, usuario, "Vinculada a la orden");
        registrar(orden, TipoHistorialOrden.EDICION, request.cantidad(), usuario,
                "Atiende la línea " + detalle.getPosicion() + " del pedido " + detalle.getPedido().getNumeroPedido()
                        + " (" + request.cantidad() + " de " + producto.getItem().getCodigo() + ").");
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
        // Las demás líneas del producto pueden tomar las piezas que ella tenía
        seguimiento.sincronizar(linea.getProducto(), rutas.recepcion(), usuario, null);
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.EDICION, null, usuario, "Ya no atiende la línea " + detalle.getPosicion()
                + " del pedido " + detalle.getPedido().getNumeroPedido() + ".");
        return obtener(id);
    }

    /** Las líneas que atendía quedan en la etapa en que estén; lo que ya llegó al almacén sigue en el inventario. */
    @Transactional
    public OrdenProduccionDTO anular(Long id, String justificacion, String usuario) {
        OrdenProduccion orden = abierta(id);
        orden.lineasActivas().forEach(l -> l.setActiva(false));
        orden.setEstado(EstadoOrdenProduccion.ANULADA);
        orden.setFechaCierre(FechaUtc.ahora());
        ordenRepository.save(orden);
        registrar(orden, TipoHistorialOrden.ANULACION,
                orden.getProductos().stream().mapToInt(OrdenProduccionProducto::enProceso).sum(), usuario,
                justificacion.strip());
        return obtener(id);
    }

    /**
     * Agrega el producto con la ruta de su ítem (todas sus piezas en el primer proceso) y las líneas que atiende. Sin
     * cantidad, produce lo asignado a las líneas.
     */
    private OrdenProduccionProducto nuevoProducto(OrdenProduccion orden, ProductoOrdenRequestDTO request,
                                                  EstadoDetalle recepcion, Set<Long> detallesDeLaOrden) {
        String codigo = request.itemCodigo().strip();
        Item item = itemRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("El ítem " + codigo + " no existe en el maestro de ítems."));
        List<VinculoLineaRequestDTO> lineas = Optional.ofNullable(request.lineas()).orElse(List.of());
        int asignado = lineas.stream().mapToInt(VinculoLineaRequestDTO::cantidad).sum();
        int cantidad = Optional.ofNullable(request.cantidad()).orElse(asignado);
        if (cantidad <= 0) {
            throw new BusinessRuleException("Indique la cantidad a producir de " + codigo + ".");
        }
        if (cantidad < asignado) {
            throw new BusinessRuleException("Las líneas tienen " + asignado + " piezas de " + codigo
                    + " asignadas: el producto no puede quedar con " + cantidad + ".");
        }
        Bodega bodega = bodegaDestino(item, Texto.limpiar(request.bodegaDestino()));
        if (!lineas.isEmpty() && !bodega.getId().equals(almacen.obtener().getId())) {
            throw new BusinessRuleException(codigo + " entrega en " + bodega.getCodigo() + ": solo lo que entra a "
                    + almacen.codigo() + " atiende pedidos.");
        }
        RutaProduccionService.RutaResuelta ruta = rutas.deItem(item);
        OrdenProduccionProducto producto = new OrdenProduccionProducto();
        producto.setOrden(orden);
        producto.setItem(item);
        producto.setBodegaDestino(bodega);
        producto.setRuta(ruta.ruta());
        producto.setCantidad(cantidad);
        int secuencia = 0;
        for (EstadoDetalle etapa : ruta.etapas()) {
            OrdenProduccionEtapa paso = new OrdenProduccionEtapa();
            paso.setProducto(producto);
            paso.setEtapa(etapa);
            paso.setSecuencia(++secuencia);
            paso.setCantidad(secuencia == 1 ? cantidad : 0);
            producto.getEtapas().add(paso);
        }
        orden.getProductos().add(producto);
        for (VinculoLineaRequestDTO v : lineas) {
            if (!detallesDeLaOrden.add(v.detalleId())) {
                throw new BusinessRuleException("La línea " + v.detalleId() + " está dos veces en la orden.");
            }
            PedidoDetalle detalle = detalleRepository.findById(v.detalleId())
                    .orElseThrow(() -> new ResourceNotFoundException("La línea de pedido " + v.detalleId() + " no existe."));
            asignar(orden, producto, detalle, v.cantidad(), recepcion);
        }
        return producto;
    }

    private void asignar(OrdenProduccion orden, OrdenProduccionProducto producto, PedidoDetalle detalle, int cantidad,
                         EstadoDetalle recepcion) {
        if (!producto.getBodegaDestino().getId().equals(almacen.obtener().getId())) {
            throw new BusinessRuleException(producto.getItem().getCodigo() + " entrega en "
                    + producto.getBodegaDestino().getCodigo() + ": solo lo que entra a " + almacen.codigo()
                    + " atiende pedidos.");
        }
        String motivo = seguimiento.motivoNoVinculable(producto, detalle, recepcion);
        if (motivo != null) {
            throw new BusinessRuleException("Línea " + detalle.getPosicion() + " del pedido "
                    + detalle.getPedido().getNumeroPedido() + ": " + motivo);
        }
        lineaRepository.findByPedidoDetalleIdAndActivaTrue(detalle.getId()).ifPresent(l -> {
            throw new BusinessRuleException("La línea " + detalle.getPosicion() + " del pedido "
                    + detalle.getPedido().getNumeroPedido() + " ya la atiende la orden " + l.getOrden().getNumero() + ".");
        });
        int faltante = seguimiento.faltante(detalle);
        if (cantidad > faltante) {
            throw new BusinessRuleException("A la línea " + detalle.getPosicion() + " del pedido "
                    + detalle.getPedido().getNumeroPedido() + " le faltan " + faltante
                    + " piezas (sin lo comprometido ni lo entregado).");
        }
        // Una línea que la orden ya atendió antes reutiliza su vínculo (único por orden y línea)
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
        if (linea.getProducto() != null && linea.getProducto() != producto) {
            linea.getProducto().getLineas().remove(linea);
        }
        if (!producto.getLineas().contains(linea)) {
            producto.getLineas().add(linea);
        }
        linea.setProducto(producto);
        linea.setCantidad(linea.getCantidadRecibida() + cantidad);
        linea.setActiva(true);
    }

    private ProductoOrdenDTO producto(OrdenProduccion orden, OrdenProduccionProducto p) {
        boolean enCurso = orden.estaAbierta() && !p.terminado();
        int piezas = enCurso ? p.enProceso() : p.getCantidadRecibida() + p.getCantidadBaja();
        Map<String, Optional<Bodega>> bodegas = new HashMap<>();
        List<ComponenteOrdenDTO> componentes = listaMateriales.componentesDirectos(p.getItem().getCodigo()).stream()
                .map(c -> {
                    BigDecimal requerido = c.cantidadPorUnidad().multiply(BigDecimal.valueOf(piezas));
                    BigDecimal libre = Optional.ofNullable(c.bodega())
                            .flatMap(b -> bodegas.computeIfAbsent(b, bodegaRepository::findByCodigo))
                            .map(b -> inventario.libre(c.itemId(), b.getId()).max(BigDecimal.ZERO))
                            .orElse(null);
                    boolean alcanza = !enCurso || c.bodega() == null
                            || (libre != null && libre.compareTo(requerido) >= 0);
                    return new ComponenteOrdenDTO(c.codigo(), c.descripcion(), c.tipo(), c.unidad(), c.bodega(),
                            Decimales.paraMostrar(c.cantidadPorUnidad()), Decimales.paraMostrar(requerido),
                            Decimales.paraMostrar(libre), alcanza);
                })
                .toList();
        List<LineaOrdenDTO> lineas = p.getLineas().stream()
                .sorted(Comparator.comparing((OrdenProduccionLinea l) -> !Boolean.TRUE.equals(l.getActiva()))
                        .thenComparing(OrdenProduccionLinea::getId))
                .map(l -> linea(l.getPedidoDetalle(), p.getItem().getCodigo(), p.getItem().getDescripcion(),
                        l.getCantidad(), l.getCantidadRecibida(), Boolean.TRUE.equals(l.getActiva()), null))
                .toList();
        List<EtapaProductoDTO> etapas = p.getEtapas().stream()
                .map(e -> new EtapaProductoDTO(pedidoMapper.toEstadoResponseDTO(e.getEtapa()), e.getCantidad()))
                .toList();
        return new ProductoOrdenDTO(p.getId(), p.getItem().getCodigo(), p.getItem().getDescripcion(),
                p.getItem().getUnidad(), p.getItem().getTipo(), p.getCantidad(), p.enProceso(), p.getCantidadRecibida(),
                p.getCantidadBaja(), p.pendienteDeLineas(), Math.max(p.enProceso() - p.pendienteDeLineas(), 0),
                p.getRuta() == null ? null : p.getRuta().getNombre(), p.getBodegaDestino().getCodigo(), p.terminado(),
                entregaRepository.existsByProductoId(p.getId()), etapas, lineas, componentes);
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

    private LineaOrdenDTO linea(PedidoDetalle d, String itemCodigo, String itemDescripcion, int cantidad,
                                int recibida, boolean activa, String motivo) {
        Pedido p = d.getPedido();
        return new LineaOrdenDTO(d.getId(), p.getId(), p.getNumeroPedido(), p.getCliente().getRazonSocial(),
                d.getPosicion(), itemCodigo, itemDescripcion, pedidoMapper.toEstadoResponseDTO(d.getEstadoActual()),
                d.getCantidadSolicitada(), cantidad, recibida, activa, p.getFechaEstimadaEntrega(), motivo);
    }

    private void registrar(OrdenProduccion orden, TipoHistorialOrden tipo, Integer cantidad, String usuario,
                           String observaciones) {
        historialRepository.save(HistorialOrdenProduccion.builder()
                .orden(orden)
                .tipo(tipo)
                .cantidad(cantidad)
                .usuario(usuario)
                .fecha(FechaUtc.ahora())
                .observaciones(observaciones == null || observaciones.length() <= 500
                        ? observaciones : observaciones.substring(0, 499) + "…")
                .build());
    }

    private static int totalPiezas(OrdenProduccion orden) {
        return orden.getProductos().stream().mapToInt(OrdenProduccionProducto::getCantidad).sum();
    }

    private static String descripcion(OrdenProduccion orden) {
        return orden.getProductos().stream()
                .map(p -> p.getCantidad() + " de " + p.getItem().getCodigo())
                .collect(Collectors.joining(", ")) + ".";
    }

    private OrdenProduccionProducto producto(OrdenProduccion orden, Long productoId) {
        return orden.getProductos().stream()
                .filter(p -> p.getId().equals(productoId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La orden " + orden.getNumero()
                        + " no tiene el producto " + productoId + "."));
    }

    private OrdenProduccion orden(Long id) {
        return ordenRepository.findDetalleById(id)
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
