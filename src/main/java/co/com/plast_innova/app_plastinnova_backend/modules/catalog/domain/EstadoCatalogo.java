package co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Columnas comunes de los catálogos de estados (pedido y línea de producción).
 * Catálogo configurable: un estado en uso no se elimina, se desactiva.
 */
@MappedSuperclass
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public abstract class EstadoCatalogo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "orden_logico", nullable = false)
    private Integer ordenLogico;

    @Column(name = "color", length = 7)
    private String color;

    /** Estado asignado a los registros nuevos que llegan de FileMaker sin un estado reconocido. */
    @Column(name = "es_inicial", nullable = false)
    private Boolean esInicial = false;

    /** Al llegar a un estado final se registra la fecha de cierre (solo aplica al pedido). */
    @Column(name = "es_final", nullable = false)
    private Boolean esFinal = false;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
