package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import jakarta.persistence.CascadeType;
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

/** Orden de producción de un ítem (ver V17__ordenes_produccion_entregas.sql). */
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Donde entra lo producido (slot de producción). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_destino_id", nullable = false)
    private Bodega bodegaDestino;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOrdenProduccion estado = EstadoOrdenProduccion.ABIERTA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_id", nullable = false)
    private EstadoDetalle etapa;

    /** Piezas reportadas por la última etapa. */
    @Column(name = "cantidad_procesada", nullable = false)
    private Integer cantidadProcesada = 0;

    @Column(name = "avance_reportado", nullable = false)
    private Boolean avanceReportado = false;

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

    /** Lo que puede reportar la etapa actual: lo que reportó la anterior, o la cantidad de la orden. */
    public int cantidadMaximaReportable() {
        return Boolean.TRUE.equals(avanceReportado) ? Math.min(cantidadProcesada, cantidad) : cantidad;
    }

    /** Líneas que siguen a la orden (las desvinculadas quedan como antecedente). */
    public List<OrdenProduccionLinea> lineasActivas() {
        return lineas.stream().filter(l -> Boolean.TRUE.equals(l.getActiva())).toList();
    }

    public int cantidadAsignada() {
        return lineasActivas().stream().mapToInt(OrdenProduccionLinea::getCantidad).sum();
    }
}
