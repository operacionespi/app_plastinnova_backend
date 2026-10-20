package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CorreccionCantidadRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FechaEntregaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.MovimientoNoSaleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoDetalleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;

import java.util.List;

public interface PedidoService {
    PedidoResponseDTO obtenerPedidoPorId(Long pedidoId);
    PedidoResponseDTO transicionarEstadoPedido(Long pedidoId, CambioEstadoPedidoRequestDTO request, String emailOperador);
    PedidoResponseDTO asignarFechaEstimadaEntrega(Long pedidoId, FechaEntregaRequestDTO request, String emailOperador);
    PedidoDetalleResponseDTO transicionarEstadoDetalle(Long detalleId, CambioEstadoDetalleRequestDTO request, String emailOperador);
    PedidoDetalleResponseDTO corregirCantidadDetalle(Long detalleId, CorreccionCantidadRequestDTO request, String emailUsuario);
    MovimientoNoSaleResponseDTO marcarNoSale(Long detalleId, JustificacionRequestDTO request, String emailUsuario);
    MovimientoNoSaleResponseDTO devolverLinea(Long detalleId, JustificacionRequestDTO request, String emailUsuario);
    List<HistorialEstadoResponseDTO> obtenerHistorialPedido(Long pedidoId);
    List<HistorialEstadoResponseDTO> obtenerHistorialDetalle(Long detalleId);
}
