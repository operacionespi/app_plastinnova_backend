package co.com.plast_innova.app_plastinnova_backend.modules.material.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Bitácora de las recargas de bodegas, ítems y lista de materiales desde CSV. */
@Entity
@Table(name = "Carga_Lista_Materiales")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CargaListaMateriales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Email de quien hizo la carga. */
    @Column(name = "usuario", nullable = false, length = 150)
    private String usuario;

    @Column(name = "bodegas", nullable = false)
    private Integer bodegas;

    @Column(name = "items", nullable = false)
    private Integer items;

    @Column(name = "relaciones", nullable = false)
    private Integer relaciones;

    /** Códigos de la lista de materiales que no están en el maestro de ítems. */
    @Column(name = "items_sin_maestro", nullable = false)
    private Integer itemsSinMaestro;

    /** Un aviso por línea. */
    @Column(name = "avisos")
    private String avisos;
}
