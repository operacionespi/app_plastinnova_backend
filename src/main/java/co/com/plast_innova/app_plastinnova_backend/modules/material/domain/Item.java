package co.com.plast_innova.app_plastinnova_backend.modules.material.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ítem del maestro del ERP: producto terminado, semiterminado o materia prima. El código coincide con el SKU de los
 * productos que llegan de FileMaker. Se escribe solo desde la carga de CSV (ver CargaListaMaterialesService).
 */
@Entity
@Table(name = "Item")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Item extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 50)
    private String codigo;

    /** f120_id del ERP. */
    @Column(name = "erp_id")
    private Integer erpId;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoItem tipo;

    @Column(name = "unidad", length = 10)
    private String unidad;

    /** false: el código solo aparece en la lista de materiales (sin descripción, tipo ni unidad). */
    @Column(name = "en_maestro", nullable = false)
    private Boolean enMaestro = true;
}
