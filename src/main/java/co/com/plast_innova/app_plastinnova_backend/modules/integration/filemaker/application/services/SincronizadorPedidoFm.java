package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.Producto;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.ProductoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.customer.domain.Cliente;
import co.com.plast_innova.app_plastinnova_backend.modules.customer.repositories.ClienteRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Ciudad;
import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Pais;
import co.com.plast_innova.app_plastinnova_backend.modules.geography.repositories.CiudadRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.geography.repositories.PaisRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FilaPedidoFm;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.CatalogoEstados.Resolucion;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aplica en SQL Server las líneas de un pedido leídas de FileMaker, en una transacción propia por pedido:
 * si un pedido falla, los demás se guardan igual.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Cliente, producto, país y ciudad se crean si no existen; cliente (vendedor, ciudad) se actualiza.</li>
 *   <li>El estado de FileMaker solo se usa al crear el pedido o la línea. Después el estado lo maneja la app
 *       y la sincronización nunca lo sobrescribe.</li>
 *   <li>Un estado de FileMaker que no existe en el catálogo se reemplaza por el estado inicial y se reporta como aviso.</li>
 *   <li>Un pedido nuevo que en FileMaker ya está despachado, entregado (app.filemaker.sync.estados-pedido-omitidos)
 *       o en un estado final del catálogo (ej. Facturado) no se importa: el control inicia con los pedidos en proceso.
 *       Si el pedido ya existe en la app, se sigue sincronizando normalmente.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SincronizadorPedidoFm {

    private static final String OBSERVACION_CREACION = "Creado desde FileMaker";

    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository detalleRepository;
    private final HistorialEstadoPedidoRepository historialPedidoRepository;
    private final HistorialEstadoDetalleRepository historialDetalleRepository;
    private final EstadoPedidoRepository estadoPedidoRepository;
    private final EstadoDetalleRepository estadoDetalleRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final PaisRepository paisRepository;
    private final CiudadRepository ciudadRepository;
    private final ReservaInventarioService reservas;

    @Transactional
    public Resultado sincronizar(String numeroPedido, List<FilaPedidoFm> filas, CatalogoEstados estados) {
        List<String> avisos = new ArrayList<>();
        // Los datos del pedido y del cliente vienen repetidos en cada línea: se toman de la primera
        FilaPedidoFm cabecera = filas.getFirst();

        Pedido pedido = pedidoRepository.findByFilemakerId(numeroPedido).orElse(null);
        if (pedido == null && estados.esPedidoOmitido(cabecera.estadoPedido())) {
            // Se verifica antes de crear cliente, ciudad o productos para no dejar datos huérfanos
            return Resultado.omitido(filas.size());
        }

        Ciudad ciudad = obtenerCiudad(cabecera.ciudad(), cabecera.pais());
        Cliente cliente = obtenerCliente(cabecera, ciudad);
        Map<String, Producto> productos = obtenerProductos(filas);
        LocalDateTime fechaCreacion = filas.stream()
                .map(FilaPedidoFm::fechaCreacionUtc)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        // La del pedido es la modificación más reciente de sus líneas
        LocalDateTime fechaModificacion = filas.stream()
                .map(FilaPedidoFm::fechaModificacionUtc)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (pedido == null) {
            pedido = crearPedido(numeroPedido, cabecera, estados, avisos);
        }
        pedido.setNumeroPedido(numeroPedido);
        pedido.setCliente(cliente);
        pedido.setCiudadEnvio(ciudad);
        pedido.setFechaCreacionFm(fechaCreacion);
        pedido.setFechaModificacionFm(fechaModificacion);
        pedido.setEliminadoEnFm(false);
        if (pedido.getId() == null) {
            pedidoRepository.save(pedido);
            registrarHistorialPedido(pedido, estados.estadoPedido(cabecera.estadoPedido()));
        }

        Map<String, PedidoDetalle> existentes = detalleRepository
                .findByFilemakerIdIn(filas.stream().map(FilaPedidoFm::recordId).toList())
                .stream()
                .collect(Collectors.toMap(PedidoDetalle::getFilemakerId, d -> d));

        int insertadas = 0;
        int actualizadas = 0;
        for (FilaPedidoFm fila : filas) {
            PedidoDetalle detalle = existentes.get(fila.recordId());
            if (detalle == null) {
                detalle = new PedidoDetalle();
                detalle.setFilemakerId(fila.recordId());
                Resolucion estado = estados.estadoDetalle(fila.estadoLinea());
                detalle.setEstadoActual(estadoDetalleRepository.getReferenceById(estado.estadoId()));
                if (!estado.reconocido()) {
                    avisos.add("Pedido " + numeroPedido + ", línea " + fila.recordId()
                            + ": estado de línea '" + estado.noReconocido() + "' no existe en el catálogo; se asignó el estado inicial");
                }
                aplicarCampos(detalle, pedido, fila, productos);
                detalleRepository.save(detalle);
                registrarHistorialDetalle(detalle, estado);
                insertadas++;
            } else if (!fila.huella().equals(detalle.getHuellaFm()) || detalle.getEliminadoEnFm()
                    || !Objects.equals(detalle.getPedido().getId(), pedido.getId())) {
                aplicarCampos(detalle, pedido, fila, productos);
                reservas.ajustarACantidad(detalle);
                actualizadas++;
            }
        }
        return new Resultado(insertadas, actualizadas, 0, avisos);
    }

    private Pedido crearPedido(String numeroPedido, FilaPedidoFm cabecera, CatalogoEstados estados, List<String> avisos) {
        Pedido pedido = new Pedido();
        pedido.setFilemakerId(numeroPedido);
        Resolucion estado = estados.estadoPedido(cabecera.estadoPedido());
        pedido.setEstadoActual(estadoPedidoRepository.getReferenceById(estado.estadoId()));
        if (!estado.reconocido()) {
            avisos.add("Pedido " + numeroPedido + ": estado '" + estado.noReconocido()
                    + "' no existe en el catálogo; se asignó el estado inicial");
        }
        return pedido;
    }

    /** Datos que FileMaker es dueño de mantener. El estado y la cantidad procesada los maneja la app. */
    private void aplicarCampos(PedidoDetalle detalle, Pedido pedido, FilaPedidoFm fila, Map<String, Producto> productos) {
        detalle.setPedido(pedido);
        detalle.setProducto(productos.get(fila.codigoProducto()));
        detalle.setPosicion(fila.posicion());
        detalle.setCantidadSolicitada(fila.cantidad());
        detalle.setTipoAlistamiento(fila.tipoAlistamiento());
        detalle.setEsAnexo(fila.esAnexo());
        detalle.setFechaCreacionFm(fila.fechaCreacionUtc());
        detalle.setFechaModificacionFm(fila.fechaModificacionUtc());
        detalle.setFmModId(fila.modId());
        detalle.setHuellaFm(fila.huella());
        detalle.setEliminadoEnFm(false);
    }

    private void registrarHistorialPedido(Pedido pedido, Resolucion estado) {
        historialPedidoRepository.save(HistorialEstadoPedido.builder()
                .pedido(pedido)
                .estado(pedido.getEstadoActual())
                .origen(OrigenCambio.SYNC)
                .observaciones(observacion(estado))
                .build());
    }

    private void registrarHistorialDetalle(PedidoDetalle detalle, Resolucion estado) {
        historialDetalleRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estado(detalle.getEstadoActual())
                .origen(OrigenCambio.SYNC)
                .observaciones(observacion(estado))
                .build());
    }

    private static String observacion(Resolucion estado) {
        String texto = estado.reconocido()
                ? OBSERVACION_CREACION
                : OBSERVACION_CREACION + ". Estado '" + estado.noReconocido() + "' no reconocido: se asignó el estado inicial";
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    private Cliente obtenerCliente(FilaPedidoFm cabecera, Ciudad ciudad) {
        Cliente cliente = clienteRepository.findByFilemakerId(cabecera.cliente()).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setFilemakerId(cabecera.cliente());
            return nuevo;
        });
        cliente.setRazonSocial(cabecera.cliente());
        cliente.setVendedor(cabecera.vendedor());
        cliente.setCiudad(ciudad);
        return cliente.getId() == null ? clienteRepository.save(cliente) : cliente;
    }

    /** Sin país no se puede ubicar la ciudad (puede haber homónimas en distintos países). */
    private Ciudad obtenerCiudad(String nombreCiudad, String nombrePais) {
        if (nombreCiudad == null || nombrePais == null) {
            return null;
        }
        Pais pais = paisRepository.findByNombre(nombrePais).orElseGet(() -> {
            Pais nuevo = new Pais();
            nuevo.setNombre(nombrePais);
            return paisRepository.save(nuevo);
        });
        return ciudadRepository.findByPaisIdAndNombre(pais.getId(), nombreCiudad).orElseGet(() -> {
            Ciudad nueva = new Ciudad();
            nueva.setPais(pais);
            nueva.setNombre(nombreCiudad);
            return ciudadRepository.save(nueva);
        });
    }

    /** FileMaker solo envía el código (LinPed_CodPT): el nombre inicia igual al código y se puede editar en la app. */
    private Map<String, Producto> obtenerProductos(List<FilaPedidoFm> filas) {
        Set<String> codigos = filas.stream().map(FilaPedidoFm::codigoProducto).collect(Collectors.toSet());
        Map<String, Producto> productos = new HashMap<>();
        // La comparación en BD ignora mayúsculas: se indexa por la llave guardada y por el código recibido
        for (Producto p : productoRepository.findByFilemakerIdIn(codigos)) {
            productos.put(p.getFilemakerId(), p);
            codigos.stream().filter(c -> c.equalsIgnoreCase(p.getFilemakerId())).forEach(c -> productos.put(c, p));
        }
        for (String codigo : codigos) {
            productos.computeIfAbsent(codigo, c -> {
                Producto nuevo = new Producto();
                nuevo.setFilemakerId(c);
                nuevo.setSku(c);
                nuevo.setNombre(c);
                return productoRepository.save(nuevo);
            });
        }
        return productos;
    }

    public record Resultado(int lineasInsertadas, int lineasActualizadas, int lineasOmitidas, List<String> avisos) {

        static Resultado omitido(int lineas) {
            return new Resultado(0, 0, lineas, List.of());
        }
    }
}
