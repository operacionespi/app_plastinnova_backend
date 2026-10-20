package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

/**
 * Pedidos creados en el rango agrupados por ubicación de entrega.
 *
 * @param ubicacionId null para los pedidos sin ubicación (o sin departamento / zona asignados)
 */
public record KpiUbicacionDTO(
        Integer ubicacionId,
        String ubicacion,
        long pedidos,
        long pedidosCerrados,
        long lineas,
        long piezasSolicitadas
) {}
