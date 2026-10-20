package co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Producto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Producto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "filemaker_id", nullable = false, unique = true, length = 100)
    private String filemakerId;

    // Único solo cuando tiene valor (índice filtrado UX_Producto_Sku)
    @Column(name = "sku", length = 50)
    private String sku;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "precio_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioBase = BigDecimal.ZERO;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fm_mod_id")
    private Integer fmModId;

    @Column(name = "fecha_modificacion_fm")
    private LocalDateTime fechaModificacionFm;
}
