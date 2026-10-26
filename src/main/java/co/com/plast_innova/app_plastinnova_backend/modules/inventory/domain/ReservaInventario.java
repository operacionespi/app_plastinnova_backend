package co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Producto terminado de una bodega comprometido para una línea de pedido (ver V15__reserva_inventario.sql). */
@Entity
@Table(name = "Reserva_Inventario")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReservaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_detalle_id", nullable = false, unique = true)
    private PedidoDetalle pedidoDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;

    @Column(name = "cantidad", nullable = false, precision = 18, scale = 6)
    private BigDecimal cantidad;

    /** Quien hizo el último cambio. */
    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
