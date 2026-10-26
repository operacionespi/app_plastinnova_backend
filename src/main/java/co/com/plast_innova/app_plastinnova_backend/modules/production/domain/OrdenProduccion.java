package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Orden de producción de uno o más productos (ver V17 y V18__op_productos_rutas_entregas.sql). */
@Entity
@Table(name = "Orden_Produccion")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrdenProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** OP-000001. */
    @Column(name = "numero", nullable = false, unique = true, length = 20)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOrdenProduccion estado = EstadoOrdenProduccion.ABIERTA;

    @Column(name = "fecha_requerida")
    private LocalDate fechaRequerida;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "usuario_creacion", nullable = false, length = 150)
    private String usuarioCreacion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /** Al terminar o anularse. */
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrdenProduccionProducto> productos = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrdenProduccionLinea> lineas = new ArrayList<>();

    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean estaAbierta() {
        return estado == EstadoOrdenProduccion.ABIERTA;
    }

    /** Todos sus productos llegaron al almacén o se dieron de baja. */
    public boolean completa() {
        return productos.stream().allMatch(OrdenProduccionProducto::terminado);
    }

    /** Líneas que siguen a la orden (las desvinculadas quedan como antecedente). */
    public List<OrdenProduccionLinea> lineasActivas() {
        return lineas.stream().filter(l -> Boolean.TRUE.equals(l.getActiva())).toList();
    }
}
