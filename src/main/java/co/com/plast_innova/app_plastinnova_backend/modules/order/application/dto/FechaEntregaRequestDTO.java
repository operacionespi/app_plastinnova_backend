package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import java.time.LocalDate;

/**
 * @param fechaEstimadaEntrega día comprometido de entrega (yyyy-MM-dd); null la quita
 */
public record FechaEntregaRequestDTO(
        LocalDate fechaEstimadaEntrega
) {
}
