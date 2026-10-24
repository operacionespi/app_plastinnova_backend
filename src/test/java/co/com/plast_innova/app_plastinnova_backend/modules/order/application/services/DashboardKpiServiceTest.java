package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.DashboardKpiService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.kpi.KpiRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardKpiServiceTest {

    private static final FiltroPedidoRequestDTO SIN_FILTROS = new FiltroPedidoRequestDTO(null, null, null, null, null, null, null, null, null);

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoDetalleRepository pedidoDetalleRepository;
    @Mock private EstadoDetalleRepository estadoDetalleRepository;
    @Mock private EstadoPedidoRepository estadoPedidoRepository;
    @Mock private KpiRepository kpiRepository;
    @Mock private PedidoMapper pedidoMapper;

    @InjectMocks
    private DashboardKpiService service;

    @Test
    @SuppressWarnings("unchecked")
    void buscarPedidos_SinOrdenUsaFechaDeCreacionDescendenteConDesempatePorId() {
        when(pedidoRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.<Pedido>empty());

        service.buscarPedidosPaginados(SIN_FILTROS, PageRequest.of(2, 20));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(pedidoRepository).findAll(any(Specification.class), pagina.capture());
        assertEquals(2, pagina.getValue().getPageNumber());
        assertEquals(Sort.by(Sort.Direction.DESC, "fechaCreacionFm").and(Sort.by("id")), pagina.getValue().getSort());
        verifyNoInteractions(pedidoDetalleRepository); // página vacía: no se consultan totales
    }

    @Test
    void buscarPedidos_RechazaOrdenarPorCamposNoPermitidos() {
        SolicitudInvalidaException ex = assertThrows(SolicitudInvalidaException.class, () ->
                service.buscarPedidosPaginados(SIN_FILTROS, PageRequest.of(0, 20, Sort.by("cliente.razonSocial"))));
        assertEquals("No se puede ordenar por 'cliente.razonSocial'. Campos permitidos: fechaCierre, fechaCreacionFm, "
                + "fechaEstimadaEntrega, fechaIngreso, numeroPedido.", ex.getMessage());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void tiempoCiclo_RechazaLaMismaFaseComoOrigenYDestino() {
        LocalDate hoy = LocalDate.of(2026, 10, 1);
        assertThrows(SolicitudInvalidaException.class, () -> service.calcularTiempoCiclo(3, 3, hoy, hoy));
        verifyNoInteractions(kpiRepository);
    }
}
