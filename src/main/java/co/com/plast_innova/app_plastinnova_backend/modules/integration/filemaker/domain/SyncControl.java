package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Marca de agua de la sincronización incremental por entidad:
 * la siguiente ejecución solo consulta en FileMaker los registros modificados después de esta fecha.
 */
@Entity
@Table(name = "Sync_Control")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SyncControl {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "entidad", length = 100)
    private String entidad;

    @Column(name = "ultima_fecha_modificacion_fm")
    private LocalDateTime ultimaFechaModificacionFm;

    @Column(name = "ultima_ejecucion_exitosa")
    private LocalDateTime ultimaEjecucionExitosa;
}
