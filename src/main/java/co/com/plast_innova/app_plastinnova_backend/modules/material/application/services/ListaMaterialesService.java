package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ExistenciaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.CargaListaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.ItemResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.LineaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.MaterialesPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.Componente;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.ItemBom;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.EstructuraMateriales.UbicacionItem;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.CargaListaMateriales;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.CargaListaMaterialesRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consultas sobre la lista de materiales: la estructura de un ítem y lo que requiere un pedido, neto de existencias.
 * Solo calcula: no crea órdenes ni reserva o consume inventario (eso lo hace ReservaInventarioService). Trabaja sobre
 * una copia en memoria que se recarga después de cada carga de CSV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListaMaterialesService {

    private final JdbcTemplate jdbc;
    private final ItemRepository itemRepository;
    private final CargaListaMaterialesRepository cargaRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final ExistenciaRepository existenciaRepository;
    private final ReservaInventarioRepository reservaRepository;

    private static final int ITEMS_POR_CONSULTA = 2000;

    /** null = hay que leerla de la base. Se lee y se invalida bajo el mismo candado (ver invalidar). */
    private volatile EstructuraMateriales estructura;

    /** Evento de CargaListaMaterialesService: la copia en memoria se descarta cuando la carga ya está confirmada. */
    public record ListaMaterialesCargada() {
    }

    @TransactionalEventListener
    public synchronized void invalidar(ListaMaterialesCargada evento) {
        estructura = null;
    }

    @Transactional(readOnly = true)
    public NodoMaterialDTO estructura(String codigo, BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser mayor que cero.");
        }
        EstructuraMateriales e = estructura();
        ItemBom item = e.item(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("El ítem " + codigo + " no existe en la lista de materiales."));
        return e.explotar(item, cantidad);
    }

    @Transactional(readOnly = true)
    public MaterialesPedidoDTO materialesPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("El pedido " + pedidoId + " no existe."));
        EstructuraMateriales e = estructura();
        Map<Long, BigDecimal> reservas = new HashMap<>();
        reservaRepository.findByPedidoDetallePedidoId(pedidoId)
                .forEach(r -> reservas.put(r.getPedidoDetalle().getId(), r.getCantidad()));

        List<LineaMaterialesDTO> lineas = pedidoDetalleRepository.findByPedidoIdAndEliminadoEnFmFalse(pedidoId).stream()
                .filter(d -> !EstadoDetalle.CODIGO_NO_SALE.equals(d.getEstadoActual().getCodigo()))
                .sorted(Comparator.comparing(PedidoDetalle::getPosicion, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PedidoDetalle::getId))
                .map(d -> {
                    String sku = d.getProducto().getSku() != null ? d.getProducto().getSku() : d.getProducto().getFilemakerId();
                    NodoMaterialDTO arbol = e.item(sku)
                            .map(item -> e.explotar(item, BigDecimal.valueOf(d.getCantidadSolicitada())))
                            .orElse(null);
                    return new LineaMaterialesDTO(d.getId(), d.getPosicion(), sku, d.getProducto().getNombre(),
                            d.getCantidadSolicitada(),
                            EstructuraMateriales.redondear(reservas.getOrDefault(d.getId(), BigDecimal.ZERO)), arbol);
                })
                .toList();

        // Del producto terminado solo se fabrica lo que no está comprometido de la bodega. Los componentes se calculan
        // para cada pedido por separado: sus existencias no se reparten entre los pedidos abiertos
        Map<Integer, BigDecimal> demanda = new HashMap<>();
        lineas.stream().filter(l -> l.estructura() != null).forEach(l -> demanda.merge(
                e.item(l.estructura().codigo()).orElseThrow().id(),
                BigDecimal.valueOf(l.cantidad()).subtract(l.reservada()).max(BigDecimal.ZERO), BigDecimal::add));
        return new MaterialesPedidoDTO(pedido.getId(), pedido.getNumeroPedido(), lineas,
                e.totalizar(demanda, existencias(e.alcanzables(demanda.keySet()))),
                cargaRepository.findFirstByOrderByFechaDesc().map(CargaListaMateriales::getFecha).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<ItemResumenDTO> buscarItems(String texto, Pageable pageable) {
        EstructuraMateriales e = estructura();
        String filtro = texto == null ? "" : texto.strip();
        return itemRepository.buscar(filtro, pageable).map(i -> new ItemResumenDTO(i.getCodigo(), i.getDescripcion(),
                i.getTipo(), i.getUnidad(), i.getEnMaestro(), e.item(i.getCodigo()).map(e::tieneComponentes).orElse(false)));
    }

    @Transactional(readOnly = true)
    public List<CargaListaMaterialesDTO> ultimasCargas() {
        return cargaRepository.findTop10ByOrderByFechaDesc().stream().map(CargaListaMaterialesDTO::de).toList();
    }

    /**
     * Existencias por ítem y bodega, sin lo comprometido por los pedidos; en grupos para no pasar el límite de
     * parámetros de SQL Server.
     */
    private Map<UbicacionItem, BigDecimal> existencias(Set<Integer> itemIds) {
        Map<UbicacionItem, BigDecimal> existencias = new HashMap<>();
        List<Integer> ids = List.copyOf(itemIds);
        for (int desde = 0; desde < ids.size(); desde += ITEMS_POR_CONSULTA) {
            List<Integer> grupo = ids.subList(desde, Math.min(desde + ITEMS_POR_CONSULTA, ids.size()));
            existenciaRepository.totalizarPorBodega(grupo)
                    .forEach(x -> existencias.put(new UbicacionItem(x.getItemId(), x.getBodega()), x.getCantidad()));
            reservaRepository.totalizarPorBodega(grupo).forEach(x -> existencias.computeIfPresent(
                    new UbicacionItem(x.getItemId(), x.getBodega()),
                    (k, cantidad) -> cantidad.subtract(x.getCantidad()).max(BigDecimal.ZERO)));
        }
        return existencias;
    }

    private EstructuraMateriales estructura() {
        EstructuraMateriales actual = estructura;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (estructura == null) {
                estructura = leer();
            }
            return estructura;
        }
    }

    private EstructuraMateriales leer() {
        long inicio = System.currentTimeMillis();
        List<ItemBom> items = jdbc.query("SELECT id, codigo, descripcion, tipo, unidad, en_maestro FROM Item",
                (rs, n) -> new ItemBom(rs.getInt("id"), rs.getString("codigo"), rs.getString("descripcion"),
                        rs.getString("tipo") == null ? null : TipoItem.valueOf(rs.getString("tipo")),
                        rs.getString("unidad"), rs.getBoolean("en_maestro")));
        List<Componente> relaciones = jdbc.query("""
                        SELECT lm.item_padre_id, lm.item_hijo_id, lm.secuencia, lm.cantidad, b.codigo AS bodega
                        FROM Lista_Material lm LEFT JOIN Bodega b ON b.id = lm.bodega_id""",
                (rs, n) -> new Componente(rs.getInt("item_padre_id"), rs.getInt("item_hijo_id"), rs.getInt("secuencia"),
                        rs.getBigDecimal("cantidad"), rs.getString("bodega")));
        log.info("Lista de materiales en memoria: {} ítems y {} relaciones en {} ms", items.size(), relaciones.size(),
                System.currentTimeMillis() - inicio);
        return new EstructuraMateriales(items, relaciones);
    }
}
