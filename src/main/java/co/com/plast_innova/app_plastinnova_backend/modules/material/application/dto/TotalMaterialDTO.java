package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;

/**
 * Requerimiento de un componente en una bodega, sumado en todas las líneas del pedido. La cantidad requerida ya
 * descuenta lo que cubren las existencias de los ítems de nivel superior (si hay lámparas semiterminadas en bodega se
 * requieren menos de sus piezas).
 */
public record TotalMaterialDTO(
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        Boolean enMaestro,
        /** Bodega de la que la lista de materiales toma el componente; null si no la indica. */
        String bodega,
        BigDecimal cantidad,
        /** Existencias del ítem en esa bodega (todos sus slots). */
        BigDecimal disponible,
        /** Lo que no cubren las existencias: cantidad − disponible, mínimo cero. */
        BigDecimal faltante,
        /** true: se fabrica a partir de otros componentes; false: no tiene lista de materiales (se compra o se toma de bodega). */
        Boolean tieneComponentes
) {}
