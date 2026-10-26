package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Línea de pedido que atiende una orden de producción, con las piezas asignadas a ella. */
@Entity
@Table(name = "Orden_Produccion_Linea")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrdenProduccionLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_produccion_id", nullable = false)
    private OrdenProduccion orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_producto_id", nullable = false)
    private OrdenProduccionProducto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_detalle_id", nullable = false)
    private PedidoDetalle pedidoDetalle;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** Piezas que ya llegaron al almacén y quedaron comprometidas para la línea. */
    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida = 0;

    /** La línea sigue las etapas del producto; false al desvincularla, al recibir todo o cuando el producto termina. */
    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    public int pendiente() {
        return Math.max(cantidad - cantidadRecibida, 0);
    }
}
