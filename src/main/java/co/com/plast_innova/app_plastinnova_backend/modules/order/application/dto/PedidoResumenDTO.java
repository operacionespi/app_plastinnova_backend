package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fila del listado de pedidos (sin las líneas). Las cantidades suman solo las líneas activas en FileMaker.
 */
public record PedidoResumenDTO(
        Long id,
        String numeroPedido,
        String clienteRazonSocial,
        String vendedor,
        String ciudad,
        String pais,
        EstadoResponseDTO estadoActual,
        LocalDateTime fechaCreacionFm,
        LocalDateTime fechaModificacionFm,
        LocalDate fechaEstimadaEntrega,
        LocalDateTime fechaCierre,
        long lineas,
        long piezasSolicitadas,
        long piezasProcesadas,
        /** Abierto y con la fecha estimada de entrega vencida (Colombia). */
        boolean atrasado
) {}
