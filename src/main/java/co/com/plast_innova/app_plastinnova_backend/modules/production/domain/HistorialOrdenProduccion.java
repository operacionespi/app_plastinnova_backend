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

/** Bitácora inmutable de una orden de producción. */
@Entity
@Table(name = "Historial_Orden_Produccion")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HistorialOrdenProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_produccion_id", nullable = false)
    private OrdenProduccion orden;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoHistorialOrden tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_anterior_id")
    private EstadoDetalle etapaAnterior;

    /** Solo en el historial anterior a las entregas entre procesos (V18). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_id")
    private EstadoDetalle etapa;

    /** Piezas de la orden (creación y edición) o reportadas (cambios de etapa anteriores a V18). */
    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "observaciones", length = 500)
    private String observaciones;
}
