package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Columnas de auditoría comunes (fecha_creacion / fecha_actualizacion) en UTC.
 * Se asignan desde la aplicación para que la entidad en memoria tenga el valor sin recargarla de la BD.
 */
@Getter
@MappedSuperclass
public abstract class EntidadAuditable {

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = FechaUtc.ahora();
        }
    }

    @PreUpdate
    protected void alActualizar() {
        fechaActualizacion = FechaUtc.ahora();
    }
}
