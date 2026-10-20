package co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * Estado de una línea de producción (producto dentro del pedido).
 */
@Entity
@Table(name = "Estado_Detalle")
@NoArgsConstructor
public class EstadoDetalle extends EstadoCatalogo {

    /** Final y del sistema (V11): la línea no se despacha. Solo el supervisor la marca o la devuelve. */
    public static final String CODIGO_NO_SALE = "NO_SALE";
}
