package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.ResultadoEliminacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.TransicionEstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del catálogo de estados. Se prueban sobre el catálogo de pedido; el de línea comparte la misma clase base.
 */
@ExtendWith(MockitoExtension.class)
class GestionEstadosServiceTest {

    @Mock private EstadoPedidoRepository estadoRepository;
    @Mock private TransicionEstadoPedidoRepository transicionRepository;

    private GestionEstadosPedidoService service;

    private EstadoPedido creado;
    private EstadoPedido enAlistamiento;
    private EstadoPedido facturado;

    @BeforeEach
    void setUp() {
        service = new GestionEstadosPedidoService(estadoRepository, transicionRepository);
        creado = estado(1, "CREADO", "Creado", true);
        enAlistamiento = estado(2, "EN_ALISTAMIENTO", "En alistamiento", false);
        facturado = estado(5, "FACTURADO", "Facturado", false);
    }

    // ------------------------------------------------------------------ validarTransicion

    @Test
    void validarTransicion_PermiteTransicionConfigurada() {
        when(estadoRepository.findById(2)).thenReturn(Optional.of(enAlistamiento));
        when(transicionRepository.existsByEstadoOrigenIdAndEstadoDestinoId(1, 2)).thenReturn(true);

        assertSame(enAlistamiento, service.validarTransicion(creado, 2));
    }

    @Test
    void validarTransicion_RechazaTransicionNoConfiguradaYListaLasPermitidas() {
        when(estadoRepository.findById(5)).thenReturn(Optional.of(facturado));
        when(transicionRepository.existsByEstadoOrigenIdAndEstadoDestinoId(1, 5)).thenReturn(false);
        when(transicionRepository.findByEstadoOrigenIdOrderByEstadoDestinoOrdenLogicoAsc(1))
                .thenReturn(List.of(transicion(creado, enAlistamiento)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.validarTransicion(creado, 5));

        assertEquals("No se permite pasar de Creado a Facturado; los estados permitidos son: En alistamiento.", ex.getMessage());
    }

    @Test
    void validarTransicion_RechazaMismoEstado() {
        when(estadoRepository.findById(1)).thenReturn(Optional.of(creado));

        assertThrows(BusinessRuleException.class, () -> service.validarTransicion(creado, 1));
    }

    @Test
    void validarTransicion_RechazaDestinoInactivo() {
        enAlistamiento.setActivo(false);
        when(estadoRepository.findById(2)).thenReturn(Optional.of(enAlistamiento));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.validarTransicion(creado, 2));
        assertTrue(ex.getMessage().contains("inactivo"));
    }

    // ------------------------------------------------------------------ estados

    @Test
    void crear_NormalizaElCodigoYLimpiaLosTextos() {
        when(estadoRepository.existsByCodigo("EN_REVISION")).thenReturn(false);
        when(estadoRepository.save(any(EstadoPedido.class))).thenAnswer(i -> i.getArgument(0));

        EstadoCatalogoResponseDTO creadoDto = service.crear(
                new EstadoCatalogoRequestDTO("En revisión", "  En   revisión ", null, 25, "#ab12cd", false, false));

        assertEquals("EN_REVISION", creadoDto.codigo());
        assertEquals("En revisión", creadoDto.nombre());
        assertEquals("#AB12CD", creadoDto.color());
        assertTrue(creadoDto.activo());
    }

    @Test
    void crear_RechazaCodigoDuplicado() {
        when(estadoRepository.existsByCodigo("CREADO")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
                service.crear(new EstadoCatalogoRequestDTO("creado", "Creado", null, 1, null, false, false)));
        verify(estadoRepository, never()).save(any());
    }

    @Test
    void crear_ComoInicialDesmarcaElInicialAnteriorAntesDeGuardar() {
        when(estadoRepository.existsByCodigo("NUEVO")).thenReturn(false);
        when(estadoRepository.findByEsInicialTrueAndActivoTrue()).thenReturn(Optional.of(creado));
        when(estadoRepository.save(any(EstadoPedido.class))).thenAnswer(i -> i.getArgument(0));

        service.crear(new EstadoCatalogoRequestDTO("NUEVO", "Nuevo", null, 5, null, true, false));

        assertFalse(creado.getEsInicial());
        InOrder orden = inOrder(estadoRepository);
        orden.verify(estadoRepository).saveAndFlush(creado);
        orden.verify(estadoRepository).save(any(EstadoPedido.class));
    }

    @Test
    void actualizar_NoPermiteDesmarcarElEstadoInicial() {
        when(estadoRepository.findById(1)).thenReturn(Optional.of(creado));
        when(estadoRepository.existsByCodigoAndIdNot("CREADO", 1)).thenReturn(false);

        assertThrows(BusinessRuleException.class, () ->
                service.actualizar(1, new EstadoCatalogoRequestDTO("CREADO", "Creado", null, 10, null, false, false)));
        assertTrue(creado.getEsInicial());
    }

    @Test
    void eliminar_EstadoEnUsoSoloSeDesactiva() {
        when(estadoRepository.findById(2)).thenReturn(Optional.of(enAlistamiento));
        when(estadoRepository.estaEnUso(2)).thenReturn(true);

        ResultadoEliminacionDTO resultado = service.eliminar(2);

        assertEquals(ResultadoEliminacionDTO.Accion.DESACTIVADO, resultado.accion());
        assertFalse(enAlistamiento.getActivo());
        verify(estadoRepository, never()).delete(any());
    }

    @Test
    void eliminar_EstadoSinUsoSeBorraConSusTransiciones() {
        List<TransicionEstadoPedido> transiciones = List.of(transicion(creado, enAlistamiento));
        when(estadoRepository.findById(2)).thenReturn(Optional.of(enAlistamiento));
        when(estadoRepository.estaEnUso(2)).thenReturn(false);
        when(transicionRepository.findByEstadoOrigenIdOrEstadoDestinoId(2, 2)).thenReturn(transiciones);

        ResultadoEliminacionDTO resultado = service.eliminar(2);

        assertEquals(ResultadoEliminacionDTO.Accion.ELIMINADO, resultado.accion());
        verify(transicionRepository).deleteAll(transiciones);
        verify(estadoRepository).delete(enAlistamiento);
    }

    @Test
    void eliminar_NoPermiteEliminarElEstadoInicial() {
        when(estadoRepository.findById(1)).thenReturn(Optional.of(creado));

        assertThrows(BusinessRuleException.class, () -> service.eliminar(1));
        assertTrue(creado.getActivo());
    }

    // ------------------------------------------------------------------ transiciones

    @Test
    void crearTransicion_RechazaDuplicada() {
        when(estadoRepository.findById(1)).thenReturn(Optional.of(creado));
        when(estadoRepository.findById(2)).thenReturn(Optional.of(enAlistamiento));
        when(transicionRepository.existsByEstadoOrigenIdAndEstadoDestinoId(1, 2)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.crearTransicion(new TransicionRequestDTO(1, 2)));
        verify(transicionRepository, never()).save(any());
    }

    @Test
    void crearTransicion_RechazaEstadoInactivo() {
        facturado.setActivo(false);
        when(estadoRepository.findById(1)).thenReturn(Optional.of(creado));
        when(estadoRepository.findById(5)).thenReturn(Optional.of(facturado));

        assertThrows(BusinessRuleException.class, () -> service.crearTransicion(new TransicionRequestDTO(1, 5)));
    }

    @Test
    void crearTransicion_RechazaMismoEstado() {
        assertThrows(BusinessRuleException.class, () -> service.crearTransicion(new TransicionRequestDTO(1, 1)));
    }

    private static EstadoPedido estado(int id, String codigo, String nombre, boolean esInicial) {
        EstadoPedido estado = new EstadoPedido();
        estado.setId(id);
        estado.setCodigo(codigo);
        estado.setNombre(nombre);
        estado.setOrdenLogico(id * 10);
        estado.setEsInicial(esInicial);
        return estado;
    }

    private static TransicionEstadoPedido transicion(EstadoPedido origen, EstadoPedido destino) {
        TransicionEstadoPedido transicion = new TransicionEstadoPedido();
        transicion.setEstadoOrigen(origen);
        transicion.setEstadoDestino(destino);
        return transicion;
    }
}
