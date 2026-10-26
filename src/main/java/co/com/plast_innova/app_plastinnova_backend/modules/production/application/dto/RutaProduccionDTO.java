package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

import java.util.List;

/** Una ruta del catálogo: sus procesos en orden (después del último, el almacén) y cuántos ítems la siguen. */
public record RutaProduccionDTO(
        Integer id,
        String nombre,
        String descripcion,
        boolean activa,
        List<EstadoResponseDTO> etapas,
        long items
) {}
