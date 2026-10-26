package co.com.plast_innova.app_plastinnova_backend.modules.production.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AsignacionItemsRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ItemRutaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProcesosDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.RutaProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.RutaProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.ItemRuta;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.RutaProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.RutaProduccionEtapa;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.ItemRutaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.production.repositories.RutaProduccionRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rutas de producción: qué procesos recorre cada ítem y en qué orden. Los procesos son etapas del catálogo de líneas
 * activas que no son la inicial, una final ni la de almacén; la etapa de almacén cierra toda ruta. Un ítem sin ruta
 * (o con una ruta inactiva) recorre todos los procesos en el orden del catálogo.
 */
@Service
@RequiredArgsConstructor
public class RutaProduccionService {

    private final RutaProduccionRepository rutaRepository;
    private final ItemRutaRepository itemRutaRepository;
    private final ItemRepository itemRepository;
    private final EstadoDetalleRepository estadoRepository;
    private final AlmacenProductoTerminado almacen;
    private final PedidoMapper pedidoMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** Los procesos de un ítem y la ruta de donde salen (null: todos los del catálogo). */
    public record RutaResuelta(RutaProduccion ruta, List<EstadoDetalle> etapas) {}

    @Transactional(readOnly = true)
    public ProcesosDTO procesosDisponibles() {
        return new ProcesosDTO(procesos().stream().map(pedidoMapper::toEstadoResponseDTO).toList(),
                pedidoMapper.toEstadoResponseDTO(recepcion()));
    }

    @Transactional(readOnly = true)
    public List<RutaProduccionDTO> listar() {
        Map<Integer, Long> items = itemRutaRepository.contarPorRuta().stream()
                .collect(Collectors.toMap(ItemRutaRepository.ItemsPorRuta::getRutaId, ItemRutaRepository.ItemsPorRuta::getItems));
        return rutaRepository.findAllByOrderByActivaDescNombreAsc().stream()
                .map(r -> dto(r, items.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public RutaProduccionDTO crear(RutaProduccionRequestDTO request) {
        String nombre = request.nombre().strip();
        if (rutaRepository.existsByNombre(nombre)) {
            throw new BusinessRuleException("Ya existe una ruta llamada " + nombre + ".");
        }
        RutaProduccion ruta = new RutaProduccion();
        ruta.setFechaCreacion(FechaUtc.ahora());
        aplicar(ruta, request);
        rutaRepository.save(ruta);
        return dto(ruta, 0);
    }

    /** Cambiar los procesos no afecta las órdenes ya creadas: cada producto copió su ruta al crearse. */
    @Transactional
    public RutaProduccionDTO actualizar(Integer id, RutaProduccionRequestDTO request) {
        RutaProduccion ruta = ruta(id);
        String nombre = request.nombre().strip();
        if (rutaRepository.existsByNombreAndIdNot(nombre, id)) {
            throw new BusinessRuleException("Ya existe una ruta llamada " + nombre + ".");
        }
        ruta.getEtapas().clear();
        // Las filas viejas se borran antes de insertar las nuevas (secuencia y etapa son únicas por ruta)
        entityManager.flush();
        aplicar(ruta, request);
        rutaRepository.save(ruta);
        return dto(ruta, itemRutaRepository.findByRutaIdOrderByItemCodigo(id).size());
    }

    /** Solo se elimina si ninguna orden la usó; los ítems que la seguían pasan a recorrer todos los procesos. */
    @Transactional
    public void eliminar(Integer id) {
        RutaProduccion ruta = ruta(id);
        Long usos = entityManager.createQuery(
                        "SELECT COUNT(p) FROM OrdenProduccionProducto p WHERE p.ruta = :ruta", Long.class)
                .setParameter("ruta", ruta).getSingleResult();
        if (usos > 0) {
            throw new BusinessRuleException("La ruta " + ruta.getNombre() + " la usan " + usos
                    + " productos de órdenes de producción: desactívela en lugar de eliminarla.");
        }
        itemRutaRepository.deleteAll(itemRutaRepository.findByRutaIdOrderByItemCodigo(id));
        rutaRepository.delete(ruta);
    }

    @Transactional(readOnly = true)
    public List<ItemRutaDTO> items(Integer id) {
        ruta(id);
        return itemRutaRepository.findByRutaIdOrderByItemCodigo(id).stream()
                .map(r -> new ItemRutaDTO(r.getItem().getCodigo(), r.getItem().getDescripcion(), r.getItem().getTipo()))
                .toList();
    }

    @Transactional
    public List<ItemRutaDTO> asignar(Integer id, AsignacionItemsRequestDTO request) {
        RutaProduccion ruta = ruta(id);
        if (!Boolean.TRUE.equals(ruta.getActiva())) {
            throw new BusinessRuleException("La ruta " + ruta.getNombre() + " está inactiva.");
        }
        List<String> noExisten = new ArrayList<>();
        for (String codigo : request.codigos().stream().map(String::strip).distinct().toList()) {
            itemRepository.findByCodigo(codigo).ifPresentOrElse(item -> {
                ItemRuta asignacion = itemRutaRepository.findById(item.getId()).orElseGet(() -> {
                    ItemRuta nueva = new ItemRuta();
                    nueva.setItem(item);
                    return nueva;
                });
                asignacion.setRuta(ruta);
                itemRutaRepository.save(asignacion);
            }, () -> noExisten.add(codigo));
        }
        if (!noExisten.isEmpty()) {
            throw new ResourceNotFoundException("No están en el maestro de ítems: " + String.join(", ", noExisten) + ".");
        }
        return items(id);
    }

    @Transactional
    public List<ItemRutaDTO> quitar(Integer id, String codigo) {
        ruta(id);
        itemRepository.findByCodigo(codigo.strip())
                .flatMap(item -> itemRutaRepository.findById(item.getId()))
                .filter(r -> r.getRuta().getId().equals(id))
                .ifPresent(itemRutaRepository::delete);
        return items(id);
    }

    /** Los procesos que recorre el ítem al producirse. */
    @Transactional(readOnly = true)
    public RutaResuelta deItem(Item item) {
        List<EstadoDetalle> procesos = procesos();
        Map<Integer, EstadoDetalle> porId = procesos.stream().collect(Collectors.toMap(EstadoDetalle::getId, e -> e));
        return itemRutaRepository.findById(item.getId())
                .map(ItemRuta::getRuta)
                .filter(r -> Boolean.TRUE.equals(r.getActiva()))
                .map(r -> new RutaResuelta(r, r.getEtapas().stream()
                        .map(e -> porId.get(e.getEtapa().getId()))
                        .filter(Objects::nonNull)
                        .toList()))
                .filter(r -> !r.etapas().isEmpty())
                .orElseGet(() -> new RutaResuelta(null, procesos));
    }

    /** La etapa en que lo producido entra al inventario. */
    public EstadoDetalle recepcion() {
        return estadoRepository.findByCodigo(almacen.codigoEstadoRecepcion())
                .orElseThrow(() -> new IllegalStateException("La etapa de almacén " + almacen.codigoEstadoRecepcion()
                        + " no existe en el catálogo de líneas."));
    }

    private List<EstadoDetalle> procesos() {
        return estadoRepository.findByActivoTrueOrderByOrdenLogicoAscIdAsc().stream()
                .filter(e -> !Boolean.TRUE.equals(e.getEsInicial()) && !Boolean.TRUE.equals(e.getEsFinal())
                        && !almacen.esRecepcion(e.getCodigo()))
                .toList();
    }

    private void aplicar(RutaProduccion ruta, RutaProduccionRequestDTO request) {
        Map<Integer, EstadoDetalle> procesos = procesos().stream()
                .collect(Collectors.toMap(EstadoDetalle::getId, e -> e));
        Set<Integer> vistos = new HashSet<>();
        int secuencia = 0;
        for (Integer etapaId : request.etapaIds()) {
            EstadoDetalle etapa = procesos.get(etapaId);
            if (etapa == null) {
                throw new BusinessRuleException("La etapa " + etapaId + " no puede ser un proceso de la ruta: debe "
                        + "estar activa y no ser la inicial, una final ni la de almacén.");
            }
            if (!vistos.add(etapaId)) {
                throw new BusinessRuleException("La etapa " + etapa.getNombre() + " está dos veces en la ruta.");
            }
            RutaProduccionEtapa paso = new RutaProduccionEtapa();
            paso.setRuta(ruta);
            paso.setEtapa(etapa);
            paso.setSecuencia(++secuencia);
            ruta.getEtapas().add(paso);
        }
        ruta.setNombre(request.nombre().strip());
        ruta.setDescripcion(Texto.limpiar(request.descripcion()));
        ruta.setActiva(request.activa() == null || request.activa());
    }

    private RutaProduccionDTO dto(RutaProduccion r, long items) {
        return new RutaProduccionDTO(r.getId(), r.getNombre(), r.getDescripcion(), Boolean.TRUE.equals(r.getActiva()),
                r.getEtapas().stream().map(e -> pedidoMapper.toEstadoResponseDTO(e.getEtapa())).toList(), items);
    }

    private RutaProduccion ruta(Integer id) {
        return rutaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La ruta de producción " + id + " no existe."));
    }
}
