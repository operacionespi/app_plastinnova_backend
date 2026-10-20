package co.com.plast_innova.app_plastinnova_backend.modules.order.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Historial_Estado_Detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HistorialEstadoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_detalle_id", nullable = false)
    private PedidoDetalle pedidoDetalle;

    /** NULL cuando es el primer registro (creación de la línea). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_anterior_id")
    private EstadoDetalle estadoAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoDetalle estado;

    /** NULL cuando el cambio lo realizó la sincronización. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 20)
    @Builder.Default
    private OrigenCambio origen = OrigenCambio.USUARIO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    @Builder.Default
    private TipoMovimientoDetalle tipo = TipoMovimientoDetalle.CAMBIO_ESTADO;

    /** Cantidad reportada en este movimiento: permite medir piezas reales por fase y por operador. */
    @Column(name = "cantidad_procesada")
    private Integer cantidadProcesada;

    /** Solo en correcciones: las piezas que había antes de corregir. */
    @Column(name = "cantidad_anterior")
    private Integer cantidadAnterior;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private LocalDateTime fechaCambio;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @PrePersist
    protected void alCrear() {
        if (fechaCambio == null) {
            fechaCambio = FechaUtc.ahora();
        }
    }
}
