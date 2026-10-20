package co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/**
 * Estado general del pedido.
 */
@Entity
@Table(name = "Estado_Pedido")
@NoArgsConstructor
public class EstadoPedido extends EstadoCatalogo {

    /** Final y del sistema (V11): el pedido llega solo cuando todas sus líneas quedan en No sale. */
    public static final String CODIGO_ANULADO = "ANULADO";
}
