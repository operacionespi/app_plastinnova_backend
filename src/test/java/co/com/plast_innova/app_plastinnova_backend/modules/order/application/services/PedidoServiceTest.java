package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosDetalleService;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosPedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.AlmacenProductoTerminado;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CorreccionCantidadRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.PedidoServiceImpl;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    private static final String EMAIL = "operador@empresa.com";

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoDetalleRepository pedidoDetalleRepository;
    @Mock private HistorialEstadoPedidoRepository historialPedidoRepository;
    @Mock private HistorialEstadoDetalleRepository historialDetalleRepository;
    @Mock private GestionEstadosPedidoService estadosPedido;
    @Mock private GestionEstadosDetalleService estadosDetalle;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PedidoMapper pedidoMapper;
    @Mock private ReservaInventarioService reservas;
    @Mock private AlmacenProductoTerminado almacen;

    @InjectMocks
    private PedidoServiceImpl pedidoService;

    @Captor private ArgumentCaptor<HistorialEstadoPedido> historialCaptor;
    @Captor private ArgumentCaptor<HistorialEstadoDetalle> historialDetalleCaptor;

    private Pedido pedido;
    private EstadoPedido creado;
    private EstadoPedido enAlistamiento;
    private EstadoPedido facturado;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        creado = estadoPedido(1, "CREADO", false);
        enAlistamiento = estadoPedido(2, "EN_ALISTAMIENTO", false);
        facturado = estadoPedido(5, "FACTURADO", true);

        pedido = new Pedido();
        pedido.setId(100L);
        pedido.setFilemakerId("FM-999");
        pedido.setEstadoActual(creado);

        usuario = new Usuario();
        usuario.setId(1);
        usuario.setEmail(EMAIL);
    }

    @Test
    void transicionarEstadoPedido_DebeActualizarEstadoYGuardarHistorial() {
        CambioEstadoPedidoRequestDTO request = new CambioEstadoPedidoRequestDTO(2, "Iniciando alistamiento");
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(pedido));
        when(estadosPedido.validarTransicion(creado, 2)).thenReturn(enAlistamiento);
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        when(pedidoMapper.toResponseDTO(any(Pedido.class)))
                .thenReturn(new PedidoResponseDTO(100L, "FM-999", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null));

        PedidoResponseDTO resultado = pedidoService.transicionarEstadoPedido(100L, request, EMAIL);

        assertNotNull(resultado);
        assertEquals(enAlistamiento, pedido.getEstadoActual());
        assertNull(pedido.getFechaCierre());
        verify(pedidoRepository).save(pedido);

        verify(historialPedidoRepository).save(historialCaptor.capture());
        HistorialEstadoPedido historial = historialCaptor.getValue();
        assertEquals(pedido, historial.getPedido());
        assertEquals(creado, historial.getEstadoAnterior());
        assertEquals(enAlistamiento, historial.getEstado());
        assertEquals(usuario, historial.getUsuario());
        assertEquals(OrigenCambio.USUARIO, historial.getOrigen());
        assertEquals("Iniciando alistamiento", historial.getObservaciones());
    }

    @Test
    void transicionarEstadoPedido_AlLlegarAEstadoFinalRegistraFechaCierre() {
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(pedido));
        when(estadosPedido.validarTransicion(creado, 5)).thenReturn(facturado);
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));

        pedidoService.transicionarEstadoPedido(100L, new CambioEstadoPedidoRequestDTO(5, null), EMAIL);

        assertNotNull(pedido.getFechaCierre());
        // Lo que el pedido tuviera comprometido sale de la bodega
        verify(reservas).despacharPedido(100L, EMAIL);
    }

    @Test
    void transicionarEstadoPedido_AlSalirDeEstadoFinalBorraFechaCierre() {
        pedido.setEstadoActual(facturado);
        pedido.setFechaCierre(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(pedido));
        when(estadosPedido.validarTransicion(facturado, 2)).thenReturn(enAlistamiento);
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));

        pedidoService.transicionarEstadoPedido(100L, new CambioEstadoPedidoRequestDTO(2, "Reapertura"), EMAIL);

        assertNull(pedido.getFechaCierre());
    }

    @Test
    void transicionarEstadoPedido_TransicionNoPermitidaNoGuardaNada() {
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(pedido));
        when(estadosPedido.validarTransicion(creado, 5))
                .thenThrow(new BusinessRuleException("No se permite pasar de Creado a Facturado"));

        assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoPedido(100L, new CambioEstadoPedidoRequestDTO(5, null), EMAIL));

        assertSame(creado, pedido.getEstadoActual());
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(historialPedidoRepository);
    }

    @Test
    void transicionarEstadoPedido_RechazaPedidoEliminadoEnFileMaker() {
        pedido.setEliminadoEnFm(true);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(pedido));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoPedido(100L, new CambioEstadoPedidoRequestDTO(2, null), EMAIL));

        assertTrue(ex.getMessage().contains("eliminado en FileMaker"));
        verifyNoInteractions(estadosPedido, historialPedidoRepository);
    }

    @Test
    void transicionarEstadoPedido_LanzaExcepcionSiPedidoNoExiste() {
        when(pedidoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                pedidoService.transicionarEstadoPedido(999L, new CambioEstadoPedidoRequestDTO(2, "Obs"), EMAIL));
    }

    @Test
    void transicionarEstadoDetalle_GuardaCantidadYHistorial() {
        EstadoDetalle solicitado = estadoDetalle(1, "SOLICITADO");
        EstadoDetalle enEnsamble = estadoDetalle(2, "EN_ENSAMBLE");
        PedidoDetalle detalle = detalle(solicitado, 10);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));
        when(estadosDetalle.validarTransicion(solicitado, 2)).thenReturn(enEnsamble);
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));

        pedidoService.transicionarEstadoDetalle(7L, new CambioEstadoDetalleRequestDTO(2, 10, null), EMAIL);

        assertEquals(enEnsamble, detalle.getEstadoActual());
        assertEquals(10, detalle.getCantidadProcesada());
        assertTrue(detalle.getAvanceReportado());
        verify(historialDetalleRepository).save(historialDetalleCaptor.capture());
        assertEquals(10, historialDetalleCaptor.getValue().getCantidadProcesada());
        assertEquals(solicitado, historialDetalleCaptor.getValue().getEstadoAnterior());
    }

    @Test
    void transicionarEstadoDetalle_RechazaCantidadMayorALaSolicitada() {
        PedidoDetalle detalle = detalle(estadoDetalle(1, "SOLICITADO"), 10);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoDetalle(7L, new CambioEstadoDetalleRequestDTO(2, 11, null), EMAIL));

        assertTrue(ex.getMessage().contains("supera la cantidad solicitada"));
        verifyNoInteractions(estadosDetalle, historialDetalleRepository);
    }

    @Test
    void transicionarEstadoDetalle_AvanzaConMenosPiezasPeroNoConMasQueLaEtapaAnterior() {
        EstadoDetalle enEnsamble = estadoDetalle(2, "EN_ENSAMBLE");
        EstadoDetalle resina = estadoDetalle(3, "RESINA");
        PedidoDetalle detalle = detalle(enEnsamble, 10);
        detalle.setCantidadProcesada(8);
        detalle.setAvanceReportado(true);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoDetalle(7L, new CambioEstadoDetalleRequestDTO(3, 9, null), EMAIL));
        assertTrue(ex.getMessage().contains("la etapa anterior reportó 8"), ex.getMessage());
        verifyNoInteractions(estadosDetalle, historialDetalleRepository);

        when(estadosDetalle.validarTransicion(enEnsamble, 3)).thenReturn(resina);
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));
        pedidoService.transicionarEstadoDetalle(7L, new CambioEstadoDetalleRequestDTO(3, 6, null), EMAIL);
        assertEquals(resina, detalle.getEstadoActual());
        assertEquals(6, detalle.cantidadMaximaReportable());
    }

    @Test
    void corregirCantidadDetalle_RegistraCorreccionConJustificacionSinCambiarDeEtapa() {
        EstadoDetalle resina = estadoDetalle(3, "RESINA");
        PedidoDetalle detalle = detalle(resina, 10);
        detalle.setCantidadProcesada(8);
        detalle.setAvanceReportado(true);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));
        when(usuarioRepository.findByEmailAndActivoTrue(EMAIL)).thenReturn(Optional.of(usuario));

        pedidoService.corregirCantidadDetalle(7L, new CorreccionCantidadRequestDTO(10, "  Se contaron mal en ensamble  "), EMAIL);

        assertEquals(resina, detalle.getEstadoActual());
        assertEquals(10, detalle.getCantidadProcesada());
        assertEquals(10, detalle.cantidadMaximaReportable());
        verify(historialDetalleRepository).save(historialDetalleCaptor.capture());
        HistorialEstadoDetalle h = historialDetalleCaptor.getValue();
        assertEquals(TipoMovimientoDetalle.CORRECCION_CANTIDAD, h.getTipo());
        assertEquals(8, h.getCantidadAnterior());
        assertEquals(10, h.getCantidadProcesada());
        assertEquals(resina, h.getEstadoAnterior());
        assertEquals(resina, h.getEstado());
        assertEquals("Se contaron mal en ensamble", h.getObservaciones());
        assertSame(usuario, h.getUsuario());
    }

    @Test
    void corregirCantidadDetalle_RechazaSinAvanceIgualOMayorALaSolicitada() {
        PedidoDetalle detalle = detalle(estadoDetalle(1, "CREADO"), 10);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));
        BusinessRuleException sinAvance = assertThrows(BusinessRuleException.class, () ->
                pedidoService.corregirCantidadDetalle(7L, new CorreccionCantidadRequestDTO(5, "Justificación suficiente"), EMAIL));
        assertTrue(sinAvance.getMessage().contains("no hay nada que corregir"));

        detalle.setAvanceReportado(true);
        detalle.setCantidadProcesada(8);
        BusinessRuleException igual = assertThrows(BusinessRuleException.class, () ->
                pedidoService.corregirCantidadDetalle(7L, new CorreccionCantidadRequestDTO(8, "Justificación suficiente"), EMAIL));
        assertTrue(igual.getMessage().contains("no hay cambio"));
        BusinessRuleException mayor = assertThrows(BusinessRuleException.class, () ->
                pedidoService.corregirCantidadDetalle(7L, new CorreccionCantidadRequestDTO(11, "Justificación suficiente"), EMAIL));
        assertTrue(mayor.getMessage().contains("supera la cantidad solicitada"));
        verifyNoInteractions(historialDetalleRepository);
    }

    @Test
    void transicionarEstadoDetalle_RechazaLineaDePedidoEliminado() {
        PedidoDetalle detalle = detalle(estadoDetalle(1, "SOLICITADO"), 10);
        detalle.getPedido().setEliminadoEnFm(true);
        when(pedidoDetalleRepository.findById(7L)).thenReturn(Optional.of(detalle));

        assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoDetalle(7L, new CambioEstadoDetalleRequestDTO(2, 1, null), EMAIL));
        verifyNoInteractions(historialDetalleRepository);
    }

    @Test
    void obtenerHistorialPedido_LanzaExcepcionSiPedidoNoExiste() {
        when(pedidoRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.obtenerHistorialPedido(999L));
    }

    private PedidoDetalle detalle(EstadoDetalle estado, int cantidadSolicitada) {
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setId(7L);
        detalle.setPedido(pedido);
        detalle.setEstadoActual(estado);
        detalle.setCantidadSolicitada(cantidadSolicitada);
        return detalle;
    }

    private static EstadoPedido estadoPedido(int id, String codigo, boolean esFinal) {
        EstadoPedido estado = new EstadoPedido();
        estado.setId(id);
        estado.setCodigo(codigo);
        estado.setNombre(codigo);
        estado.setEsFinal(esFinal);
        return estado;
    }

    private static EstadoDetalle estadoDetalle(int id, String codigo) {
        EstadoDetalle estado = new EstadoDetalle();
        estado.setId(id);
        estado.setCodigo(codigo);
        estado.setNombre(codigo);
        return estado;
    }
}
