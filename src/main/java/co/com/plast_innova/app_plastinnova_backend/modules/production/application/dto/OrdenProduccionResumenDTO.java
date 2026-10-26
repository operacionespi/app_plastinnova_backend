package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una orden en el listado. */
public record OrdenProduccionResumenDTO(
        Long id,
        String numero,
        EstadoOrdenProduccion estado,
        List<ProductoResumenDTO> productos,
        /** Suma de las piezas de sus productos. */
        int cantidad,
        int cantidadRecibida,
        /** Piezas en cada proceso, sumando los productos (solo los procesos con piezas). */
        List<EtapaProductoDTO> enProceso,
        LocalDate fechaRequerida,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCierre,
        /** Números de los pedidos cuyas líneas atiende (vigentes). */
        List<String> pedidos
) {}
