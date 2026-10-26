package co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.Remision;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Bitácora inmutable del inventario (ver V14__inventario.sql). */
@Entity
@Table(name = "Movimiento_Inventario")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimientoInventario tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_origen_id")
    private Slot slotOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_destino_id")
    private Slot slotDestino;

    /** Siempre positiva; en un ajuste es la diferencia entre lo contado y el saldo. */
    @Column(name = "cantidad", nullable = false, precision = 18, scale = 6)
    private BigDecimal cantidad;

    /** Solo en AJUSTE y CARGA_INICIAL. */
    @Column(name = "cantidad_contada", precision = 18, scale = 6)
    private BigDecimal cantidadContada;

    @Column(name = "motivo", length = 100)
    private String motivo;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Línea de pedido despachada, o para la que se produjo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_detalle_id")
    private PedidoDetalle pedidoDetalle;

    /** Orden de producción: entrada de lo producido o consumo de sus componentes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_produccion_id")
    private OrdenProduccion ordenProduccion;

    /** Remisión que entregó (salida) o cuya anulación devolvió (entrada) el producto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remision_id")
    private Remision remision;
}
