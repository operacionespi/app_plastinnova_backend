package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Materiales que requiere un pedido: la estructura de cada línea vigente (sin las eliminadas en FileMaker ni las que
 * no salen) para su cantidad solicitada, y los totales por componente. No descuenta inventario.
 */
public record MaterialesPedidoDTO(
        Long pedidoId,
        String numeroPedido,
        List<LineaMaterialesDTO> lineas,
        /** Ordenados por tipo (semiterminados primero) y código. */
        List<TotalMaterialDTO> totales,
        /** Fecha (UTC) de la carga de lista de materiales usada; null si nunca se ha cargado. */
        LocalDateTime fechaCarga
) {}
