package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "Sync_Log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "entidad", nullable = false, length = 100)
    private String entidad;

    @Column(name = "fecha_inicio", nullable = false, updatable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "registros_leidos", nullable = false)
    @Builder.Default
    private Integer registrosLeidos = 0;

    @Column(name = "registros_insertados", nullable = false)
    @Builder.Default
    private Integer registrosInsertados = 0;

    @Column(name = "registros_actualizados", nullable = false)
    @Builder.Default
    private Integer registrosActualizados = 0;

    @Column(name = "registros_con_error", nullable = false)
    @Builder.Default
    private Integer registrosConError = 0;

    /** Líneas que desaparecieron de FileMaker y se marcaron como eliminadas. */
    @Column(name = "registros_eliminados", nullable = false)
    @Builder.Default
    private Integer registrosEliminados = 0;

    /** Líneas leídas que no se importaron porque su pedido ya estaba en un estado final en FileMaker. */
    @Column(name = "registros_omitidos", nullable = false)
    @Builder.Default
    private Integer registrosOmitidos = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSync estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", length = 20)
    private ModoSync modo;

    /** NULL = ejecución del scheduler; email del usuario = ejecución manual. */
    @Column(name = "disparado_por", length = 150)
    private String disparadoPor;

    @Column(name = "mensaje_error")
    private String mensajeError;

    /** Situaciones que no impidieron sincronizar, ej. un estado de FileMaker que no existe en el catálogo. */
    @Column(name = "avisos")
    private String avisos;

    @PrePersist
    protected void onCreate() {
        if (this.fechaInicio == null) {
            this.fechaInicio = FechaUtc.ahora();
        }
    }
}
