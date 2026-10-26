package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

import java.util.List;

/** Etapas que pueden ser procesos de una ruta (y la ruta de los ítems sin ruta), y la etapa de almacén. */
public record ProcesosDTO(
        List<EstadoResponseDTO> procesos,
        EstadoResponseDTO recepcion
) {}
