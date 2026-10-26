package co.com.plast_innova.app_plastinnova_backend.modules.production.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReferenciaMovimiento;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.ComponenteDirectoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.BajaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EntregaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EntregaProceso;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionEtapa;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionProducto;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoEntregaProceso;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.EntregaProcesoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.OrdenProduccionProductoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entregas entre procesos de un producto de la orden:
 * <ul>
 *   <li><b>Avance</b>: al proceso siguiente de su ruta. Desde el último va al almacén: se consumen los componentes
 *       directos de la lista de materiales (si alguno no alcanza se rechaza), las piezas entran al slot de producción
 *       de la bodega destino y quedan comprometidas para las líneas que las esperan.</li>
 *   <li><b>Devolución</b>: a un proceso anterior, con el motivo (reproceso).</li>
 *   <li><b>Baja</b>: desecho. Las piezas salen de la orden y sus componentes se consumen igual que al recibirlas.</li>
 * </ul>
 * Las líneas que sigue el producto quedan en la etapa donde están sus piezas. El producto se bloquea antes que los
 * ítems del inventario, y los ítems en orden de id.
 */
@Service
@RequiredArgsConstructor
public class EntregaProcesoService {

    private final OrdenProduccionProductoRepository productoRepository;
    private final EntregaProcesoRepository entregaRepository;
    private final ItemRepository itemRepository;
    private final BodegaRepository bodegaRepository;
    private final ListaMaterialesService listaMateriales;
    private final InventarioService inventario;
    private final AlmacenProductoTerminado almacen;
    private final RutaProduccionService rutas;
    private final SeguimientoLineas seguimiento;
    private final OrdenProduccionService ordenes;

    @Transactional
    public OrdenProduccionDTO entregar(Long ordenId, EntregaProcesoRequestDTO request, String usuario) {
        OrdenProduccionProducto producto = producto(ordenId, request.productoId());
        OrdenProduccionEtapa origen = producto.etapa(request.etapaOrigenId())
                .orElseThrow(() -> new BusinessRuleException("El proceso que entrega no está en la ruta de "
                        + producto.getItem().getCodigo() + "."));
        validarPiezas(producto, origen, request.cantidad());
        String observaciones = Texto.limpiar(request.observaciones());
        EstadoDetalle recepcion = rutas.recepcion();
        int indice = producto.getEtapas().indexOf(origen);
        boolean ultimo = indice == producto.getEtapas().size() - 1;
        TipoEntregaProceso tipo;
        EstadoDetalle destino;

        if (request.etapaDestinoId().equals(recepcion.getId())) {
            if (!ultimo) {
                throw new BusinessRuleException(origen.getEtapa().getNombre() + " entrega a "
                        + producto.getEtapas().get(indice + 1).getEtapa().getNombre()
                        + ": solo el último proceso de la ruta entrega al almacén.");
            }
            tipo = TipoEntregaProceso.AVANCE;
            destino = recepcion;
            origen.setCantidad(origen.getCantidad() - request.cantidad());
            consumir(producto, request.cantidad(), "Recepción", usuario, true);
            producto.setCantidadRecibida(producto.getCantidadRecibida() + request.cantidad());
            seguimiento.comprometer(producto, request.cantidad(), usuario);
        } else {
            OrdenProduccionEtapa llegada = producto.etapa(request.etapaDestinoId())
                    .orElseThrow(() -> new BusinessRuleException("El proceso que recibe no está en la ruta de "
                            + producto.getItem().getCodigo() + "."));
            int destinoIndice = producto.getEtapas().indexOf(llegada);
            if (destinoIndice == indice + 1) {
                tipo = TipoEntregaProceso.AVANCE;
            } else if (destinoIndice < indice) {
                tipo = TipoEntregaProceso.DEVOLUCION;
                if (observaciones == null) {
                    throw new BusinessRuleException("Indique el motivo por el que se devuelven las piezas a "
                            + llegada.getEtapa().getNombre() + ".");
                }
            } else {
                throw new BusinessRuleException(origen.getEtapa().getNombre() + " entrega a "
                        + (ultimo ? recepcion.getNombre() : producto.getEtapas().get(indice + 1).getEtapa().getNombre())
                        + ": no puede saltarse procesos de la ruta.");
            }
            destino = llegada.getEtapa();
            origen.setCantidad(origen.getCantidad() - request.cantidad());
            llegada.setCantidad(llegada.getCantidad() + request.cantidad());
        }
        registrar(producto, tipo, origen.getEtapa(), destino, request.cantidad(), usuario, observaciones);
        cerrar(producto, recepcion, usuario, observaciones);
        return ordenes.obtener(ordenId);
    }

    @Transactional
    public OrdenProduccionDTO darDeBaja(Long ordenId, BajaProcesoRequestDTO request, String usuario) {
        OrdenProduccionProducto producto = producto(ordenId, request.productoId());
        OrdenProduccionEtapa etapa = producto.etapa(request.etapaId())
                .orElseThrow(() -> new BusinessRuleException("El proceso no está en la ruta de "
                        + producto.getItem().getCodigo() + "."));
        validarPiezas(producto, etapa, request.cantidad());
        String motivo = request.motivo().strip();
        etapa.setCantidad(etapa.getCantidad() - request.cantidad());
        consumir(producto, request.cantidad(), "Baja de producción", usuario, false);
        producto.setCantidadBaja(producto.getCantidadBaja() + request.cantidad());
        registrar(producto, TipoEntregaProceso.BAJA, etapa.getEtapa(), null, request.cantidad(), usuario, motivo);
        cerrar(producto, rutas.recepcion(), usuario, "Baja de " + request.cantidad() + " en "
                + etapa.getEtapa().getNombre());
        return ordenes.obtener(ordenId);
    }

    private void validarPiezas(OrdenProduccionProducto producto, OrdenProduccionEtapa etapa, int cantidad) {
        if (cantidad > etapa.getCantidad()) {
            throw new BusinessRuleException("En " + etapa.getEtapa().getNombre() + " hay " + etapa.getCantidad()
                    + " piezas de " + producto.getItem().getCodigo() + ": no se pueden mover " + cantidad + ".");
        }
    }

    /** Las líneas siguen a sus piezas; si el producto terminó y con él todos los de la orden, la orden termina. */
    private void cerrar(OrdenProduccionProducto producto, EstadoDetalle recepcion, String usuario, String observaciones) {
        seguimiento.sincronizar(producto, recepcion, usuario, observaciones);
        seguimiento.cerrarSiCompleta(producto.getOrden());
        productoRepository.save(producto);
    }

    /**
     * Consume los componentes directos de las piezas (si alguno no alcanza no se mueve nada) y, si se reciben, entran
     * al slot de producción de la bodega destino.
     */
    private void consumir(OrdenProduccionProducto producto, int piezas, String motivo, String usuario, boolean recibir) {
        Item item = producto.getItem();
        BigDecimal cantidad = BigDecimal.valueOf(piezas);
        List<ComponenteDirectoDTO> componentes = listaMateriales.componentesDirectos(item.getCodigo()).stream()
                .filter(c -> c.bodega() != null)
                .sorted(Comparator.comparing(ComponenteDirectoDTO::itemId))
                .toList();
        // Bloqueo de todos los ítems en orden de id: dos órdenes que consumen lo mismo se hacen en fila
        List<Integer> ids = new ArrayList<>(componentes.stream().map(ComponenteDirectoDTO::itemId).toList());
        ids.add(item.getId());
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
            throw new BusinessRuleException("No alcanzan los materiales de " + piezas + " piezas de " + item.getCodigo()
                    + ". " + String.join("; ", faltantes) + ". Registre primero la entrada de lo que falta.");
        }
        ReferenciaMovimiento referencia = ReferenciaMovimiento.deOrden(producto.getOrden());
        String consumo = recibir ? "Consumo de producción" : "Consumo de producción dada de baja";
        for (ComponenteDirectoDTO c : componentes) {
            BigDecimal faltante = inventario.sacarDeBodega(bloqueados.get(c.itemId()), bodegas.get(c.bodega()),
                    c.cantidadPorUnidad().multiply(cantidad), null, referencia, consumo, usuario);
            if (faltante.signum() > 0) {
                throw new IllegalStateException("Faltaron " + faltante + " de " + c.codigo() + " en " + c.bodega());
            }
        }
        if (recibir) {
            inventario.entrada(bloqueados.get(item.getId()), almacen.slotRecepcion(producto.getBodegaDestino()), cantidad,
                    referencia, "Producción", usuario);
        }
    }

    private void registrar(OrdenProduccionProducto producto, TipoEntregaProceso tipo, EstadoDetalle origen,
                           EstadoDetalle destino, int cantidad, String usuario, String observaciones) {
        entregaRepository.save(EntregaProceso.builder()
                .producto(producto)
                .tipo(tipo)
                .etapaOrigen(origen)
                .etapaDestino(destino)
                .cantidad(cantidad)
                .usuario(usuario)
                .fecha(FechaUtc.ahora())
                .observaciones(observaciones)
                .build());
    }

    private OrdenProduccionProducto producto(Long ordenId, Long productoId) {
        OrdenProduccionProducto producto = productoRepository.bloquear(productoId)
                .filter(p -> p.getOrden().getId().equals(ordenId))
                .orElseThrow(() -> new ResourceNotFoundException("La orden " + ordenId + " no tiene el producto "
                        + productoId + "."));
        if (!producto.getOrden().estaAbierta()) {
            throw new BusinessRuleException("La orden " + producto.getOrden().getNumero() + " está "
                    + producto.getOrden().getEstado().name().toLowerCase() + ": ya no admite entregas.");
        }
        return producto;
    }
}
