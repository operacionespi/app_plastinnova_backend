package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

/**
 * Movimientos registrados por cada usuario en el rango, por estado de destino.
 *
 * @param piezas piezas reportadas al pasar las líneas a este estado
 */
public record KpiProduccionOperadorDTO(
        Integer usuarioId,
        String usuarioNombre,
        Integer estadoId,
        String estadoNombre,
        long movimientos,
        long lineas,
        long piezas
) {}
