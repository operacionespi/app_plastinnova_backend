package co.com.plast_innova.app_plastinnova_backend.modules.customer.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Ciudad;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Cliente")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Cliente extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Razón social tal como llega en LinPed_Cliente (FileMaker no envía un código de cliente). */
    @Column(name = "filemaker_id", nullable = false, unique = true, length = 100)
    private String filemakerId;

    // No es único: FileMaker puede traer documentos duplicados o vacíos
    @Column(name = "documento_identidad", length = 50)
    private String documentoIdentidad;

    @Column(name = "razon_social", nullable = false, length = 255)
    private String razonSocial;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefono", length = 50)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_id")
    private Ciudad ciudad;

    /** Vendedor asignado al cliente (Cliente_Distribuidor en FileMaker). */
    @Column(name = "vendedor", length = 150)
    private String vendedor;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fm_mod_id")
    private Integer fmModId;

    @Column(name = "fecha_modificacion_fm")
    private LocalDateTime fechaModificacionFm;
}
