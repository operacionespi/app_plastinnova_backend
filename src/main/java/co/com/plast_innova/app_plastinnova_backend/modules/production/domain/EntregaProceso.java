package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
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

import java.time.LocalDateTime;

/** Piezas de un producto que un proceso entrega a otro o da de baja. Inmutable. */
@Entity
@Table(name = "Entrega_Proceso")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EntregaProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_producto_id", nullable = false)
    private OrdenProduccionProducto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoEntregaProceso tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_origen_id", nullable = false)
    private EstadoDetalle etapaOrigen;

    /** null en las bajas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_destino_id")
    private EstadoDetalle etapaDestino;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "observaciones", length = 500)
    private String observaciones;
}
