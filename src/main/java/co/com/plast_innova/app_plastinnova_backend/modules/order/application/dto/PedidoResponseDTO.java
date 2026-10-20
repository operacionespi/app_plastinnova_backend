package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
        Long id,
        String filemakerId,
        String numeroPedido,
        String clienteRazonSocial,
        String clienteDocumento,
        String vendedor,
        /** Dirección de envío como llega de FileMaker. */
        String direccionEnvio,
        String ciudad,
        String pais,
        EstadoResponseDTO estadoActual,
        LocalDateTime fechaCreacionFm,
        /** Última modificación en FileMaker de cualquiera de sus líneas. */
        LocalDateTime fechaModificacionFm,
        LocalDateTime fechaIngreso,
        /** Día comprometido de entrega; lo asigna la app. */
        LocalDate fechaEstimadaEntrega,
        /** Fecha en que el pedido llegó a un estado final; null si sigue abierto. */
        LocalDateTime fechaCierre,
        BigDecimal totalPedido,
        /** El pedido ya no tiene líneas activas en FileMaker. */
        Boolean eliminadoEnFm,
        /** Abierto y con la fecha estimada de entrega vencida (Colombia). */
        boolean atrasado,
        List<PedidoDetalleResponseDTO> detalles
) {}
