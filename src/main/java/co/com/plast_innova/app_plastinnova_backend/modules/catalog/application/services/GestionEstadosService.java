package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.ResultadoEliminacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.ResultadoEliminacionDTO.Accion;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoCatalogo;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstado;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoCatalogoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.TransicionEstadoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Administración de un catálogo de estados y de sus transiciones permitidas.
 * El catálogo de pedido y el de línea siguen las mismas reglas:
 * <ul>
 *   <li>Siempre debe existir un único estado inicial activo (lo usa la sincronización con FileMaker).</li>
 *   <li>Un estado que nunca se usó se elimina junto con sus transiciones; uno en uso solo se desactiva.</li>
 *   <li>Un estado inactivo no puede ser destino de una transición, pero los registros que ya están en él
 *       pueden salir por las transiciones configuradas.</li>
 *   <li>Cada catálogo tiene un estado del sistema (No sale / Anulado) al que se llega con acciones propias: se
 *       puede cambiar su nombre, color y orden, pero no su código ni su marca de final, no se elimina y no lleva
 *       transiciones.</li>
 * </ul>
 */
public abstract class GestionEstadosService<E extends EstadoCatalogo, T extends TransicionEstado<E>> {

    private final EstadoCatalogoRepository<E> estadoRepository;
    private final TransicionEstadoRepository<T> transicionRepository;
    /** "pedido" o "línea", para los mensajes. */
    private final String tipo;
    private final String codigoDelSistema;

    protected GestionEstadosService(EstadoCatalogoRepository<E> estadoRepository,
                                    TransicionEstadoRepository<T> transicionRepository, String tipo,
                                    String codigoDelSistema) {
        this.estadoRepository = estadoRepository;
        this.transicionRepository = transicionRepository;
        this.tipo = tipo;
        this.codigoDelSistema = codigoDelSistema;
    }

    protected abstract E nuevoEstado();

    protected abstract T nuevaTransicion(E origen, E destino);

    // ------------------------------------------------------------------ estados

    @Transactional(readOnly = true)
    public List<EstadoCatalogoResponseDTO> listar(boolean incluirInactivos) {
        List<E> estados = incluirInactivos
                ? estadoRepository.findAllByOrderByOrdenLogicoAscIdAsc()
                : estadoRepository.findByActivoTrueOrderByOrdenLogicoAscIdAsc();
        return estados.stream().map(EstadoCatalogoResponseDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public EstadoCatalogoResponseDTO obtener(Integer id) {
        return EstadoCatalogoResponseDTO.de(buscar(id));
    }

    @Transactional
    public EstadoCatalogoResponseDTO crear(EstadoCatalogoRequestDTO request) {
        String codigo = codigoNormalizado(request.codigo());
        if (estadoRepository.existsByCodigo(codigo)) {
            throw new BusinessRuleException("Ya existe un estado de " + tipo + " con el código " + codigo + ".");
        }
        if (request.esInicial()) {
            quitarMarcaInicial();
        }
        E estado = nuevoEstado();
        aplicar(estado, request, codigo);
        return EstadoCatalogoResponseDTO.de(estadoRepository.save(estado));
    }

    @Transactional
    public EstadoCatalogoResponseDTO actualizar(Integer id, EstadoCatalogoRequestDTO request) {
        E estado = buscar(id);
        String codigo = codigoNormalizado(request.codigo());
        if (estadoRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new BusinessRuleException("Ya existe un estado de " + tipo + " con el código " + codigo + ".");
        }
        if (esDelSistema(estado) && (!codigo.equals(estado.getCodigo()) || !request.esFinal() || request.esInicial())) {
            throw new BusinessRuleException("El estado " + estado.getNombre() + " lo usa la aplicación: puede cambiar "
                    + "su nombre, color y orden, pero su código no cambia y sigue siendo final.");
        }
        boolean eraInicial = Boolean.TRUE.equals(estado.getEsInicial());
        if (eraInicial && !request.esInicial()) {
            throw new BusinessRuleException("Debe existir un estado inicial de " + tipo
                    + ": marque otro estado como inicial en lugar de desmarcar este.");
        }
        if (!eraInicial && request.esInicial()) {
            if (!Boolean.TRUE.equals(estado.getActivo())) {
                throw new BusinessRuleException("Un estado inactivo no puede ser el estado inicial: actívelo primero.");
            }
            quitarMarcaInicial();
        }
        aplicar(estado, request, codigo);
        return EstadoCatalogoResponseDTO.de(estado);
    }

    @Transactional
    public EstadoCatalogoResponseDTO activar(Integer id) {
        E estado = buscar(id);
        estado.setActivo(true);
        return EstadoCatalogoResponseDTO.de(estado);
    }

    @Transactional
    public ResultadoEliminacionDTO eliminar(Integer id) {
        E estado = buscar(id);
        if (esDelSistema(estado)) {
            throw new BusinessRuleException("El estado " + estado.getNombre() + " lo usa la aplicación y no se puede eliminar.");
        }
        if (Boolean.TRUE.equals(estado.getEsInicial())) {
            throw new BusinessRuleException("No se puede eliminar el estado inicial de " + tipo
                    + ": marque otro estado como inicial primero.");
        }
        if (estadoRepository.estaEnUso(id)) {
            estado.setActivo(false);
            return new ResultadoEliminacionDTO(id, Accion.DESACTIVADO,
                    "El estado está en uso en pedidos o en el historial: se desactivó en lugar de eliminarse.");
        }
        transicionRepository.deleteAll(transicionRepository.findByEstadoOrigenIdOrEstadoDestinoId(id, id));
        estadoRepository.delete(estado);
        return new ResultadoEliminacionDTO(id, Accion.ELIMINADO, "El estado y sus transiciones se eliminaron.");
    }

    // ------------------------------------------------------------------ transiciones

    @Transactional(readOnly = true)
    public List<TransicionResponseDTO> listarTransiciones() {
        return transicionRepository.findAllByOrderByEstadoOrigenOrdenLogicoAscEstadoDestinoOrdenLogicoAsc().stream()
                .map(TransicionResponseDTO::de)
                .toList();
    }

    /** Estados activos a los que se puede pasar desde el estado indicado. */
    @Transactional(readOnly = true)
    public List<EstadoCatalogoResponseDTO> destinosPermitidos(Integer estadoOrigenId) {
        buscar(estadoOrigenId);
        return destinosActivos(estadoOrigenId).stream().map(EstadoCatalogoResponseDTO::de).toList();
    }

    @Transactional
    public TransicionResponseDTO crearTransicion(TransicionRequestDTO request) {
        if (request.estadoOrigenId().equals(request.estadoDestinoId())) {
            throw new BusinessRuleException("El estado de origen y el de destino deben ser distintos.");
        }
        E origen = buscar(request.estadoOrigenId());
        E destino = buscar(request.estadoDestinoId());
        if (esDelSistema(origen) || esDelSistema(destino)) {
            E sistema = esDelSistema(origen) ? origen : destino;
            throw new BusinessRuleException("Al estado " + sistema.getNombre() + " se entra y se sale con acciones propias "
                    + "de la aplicación: no lleva transiciones.");
        }
        if (!Boolean.TRUE.equals(origen.getActivo()) || !Boolean.TRUE.equals(destino.getActivo())) {
            throw new BusinessRuleException("Solo se pueden crear transiciones entre estados activos.");
        }
        if (transicionRepository.existsByEstadoOrigenIdAndEstadoDestinoId(origen.getId(), destino.getId())) {
            throw new BusinessRuleException("La transición " + origen.getNombre() + " → " + destino.getNombre()
                    + " ya existe.");
        }
        return TransicionResponseDTO.de(transicionRepository.save(nuevaTransicion(origen, destino)));
    }

    @Transactional
    public void eliminarTransicion(Integer id) {
        T transicion = transicionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La transición de " + tipo + " " + id + " no existe."));
        transicionRepository.delete(transicion);
    }

    // ------------------------------------------------------------------ reglas para el cambio de estado

    /**
     * Carga el estado destino y verifica que se pueda pasar a él desde el estado actual.
     *
     * @throws ResourceNotFoundException si el estado destino no existe
     * @throws BusinessRuleException     si es el mismo estado, está inactivo o la transición no está permitida
     */
    @Transactional(readOnly = true)
    public E validarTransicion(E actual, Integer nuevoEstadoId) {
        E nuevo = estadoRepository.findById(nuevoEstadoId)
                .orElseThrow(() -> new ResourceNotFoundException("El estado de " + tipo + " " + nuevoEstadoId + " no existe."));
        if (nuevo.getId().equals(actual.getId())) {
            throw new BusinessRuleException("Ya se encuentra en el estado " + nuevo.getNombre() + ".");
        }
        if (!Boolean.TRUE.equals(nuevo.getActivo())) {
            throw new BusinessRuleException("El estado " + nuevo.getNombre() + " está inactivo.");
        }
        if (!transicionRepository.existsByEstadoOrigenIdAndEstadoDestinoId(actual.getId(), nuevo.getId())) {
            List<E> permitidos = destinosActivos(actual.getId());
            String opciones = permitidos.isEmpty()
                    ? "no hay transiciones configuradas desde este estado"
                    : "los estados permitidos son: " + permitidos.stream().map(E::getNombre).collect(Collectors.joining(", "));
            throw new BusinessRuleException("No se permite pasar de " + actual.getNombre() + " a " + nuevo.getNombre()
                    + "; " + opciones + ".");
        }
        return nuevo;
    }

    // ------------------------------------------------------------------ apoyo

    private boolean esDelSistema(E estado) {
        return codigoDelSistema.equals(estado.getCodigo());
    }

    private E buscar(Integer id) {
        return estadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El estado de " + tipo + " " + id + " no existe."));
    }

    private List<E> destinosActivos(Integer estadoOrigenId) {
        return transicionRepository.findByEstadoOrigenIdOrderByEstadoDestinoOrdenLogicoAsc(estadoOrigenId).stream()
                .map(TransicionEstado::getEstadoDestino)
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .toList();
    }

    /**
     * El índice único filtrado UX_*_Inicial admite un solo estado inicial activo. Hibernate ejecuta los INSERT
     * antes que los UPDATE, por eso se desmarca el inicial anterior y se envía a la BD antes de guardar el nuevo.
     */
    private void quitarMarcaInicial() {
        estadoRepository.findByEsInicialTrueAndActivoTrue().ifPresent(anterior -> {
            anterior.setEsInicial(false);
            estadoRepository.saveAndFlush(anterior);
        });
    }

    private static void aplicar(EstadoCatalogo estado, EstadoCatalogoRequestDTO request, String codigo) {
        estado.setCodigo(codigo);
        estado.setNombre(Texto.limpiar(request.nombre()));
        estado.setDescripcion(Texto.limpiar(request.descripcion()));
        estado.setOrdenLogico(request.ordenLogico());
        estado.setColor(request.color() == null ? null : request.color().toUpperCase());
        estado.setEsInicial(request.esInicial());
        estado.setEsFinal(request.esFinal());
    }

    private static String codigoNormalizado(String codigo) {
        String clave = Texto.clave(codigo);
        if (clave == null) {
            throw new BusinessRuleException("El código del estado es obligatorio.");
        }
        return clave;
    }
}
