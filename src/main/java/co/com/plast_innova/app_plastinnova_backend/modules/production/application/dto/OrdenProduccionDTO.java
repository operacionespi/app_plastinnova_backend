package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una orden con sus productos, sus entregas entre procesos, su historial y lo que movió en el inventario. */
public record OrdenProduccionDTO(
        Long id,
        String numero,
        EstadoOrdenProduccion estado,
        LocalDate fechaRequerida,
        String observaciones,
        String usuarioCreacion,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCierre,
        /** Etapa en que lo producido entra al inventario: destino del último proceso de toda ruta. */
        EstadoResponseDTO etapaRecepcion,
        List<ProductoOrdenDTO> productos,
        List<EntregaProcesoDTO> entregas,
        List<HistorialOrdenDTO> historial,
        List<MovimientoDTO> movimientos
) {}
