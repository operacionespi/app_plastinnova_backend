package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Un ítem que produce la orden. Sus piezas están repartidas entre los procesos de su ruta (etapas); las que llegaron
 * al almacén y las dadas de baja ya salieron de ellos.
 */
@Entity
@Table(name = "Orden_Produccion_Producto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrdenProduccionProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_produccion_id", nullable = false)
    private OrdenProduccion orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Donde entra lo producido (slot de producción). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_destino_id", nullable = false)
    private Bodega bodegaDestino;

    /** Ruta con que se creó; null si siguió todas las etapas del catálogo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    private RutaProduccion ruta;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida = 0;

    @Column(name = "cantidad_baja", nullable = false)
    private Integer cantidadBaja = 0;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("secuencia")
    private List<OrdenProduccionEtapa> etapas = new ArrayList<>();

    @OneToMany(mappedBy = "producto")
    @OrderBy("id")
    private List<OrdenProduccionLinea> lineas = new ArrayList<>();

    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Long version;

    /** Piezas que están en algún proceso. */
    public int enProceso() {
        return etapas.stream().mapToInt(OrdenProduccionEtapa::getCantidad).sum();
    }

    public boolean terminado() {
        return cantidadRecibida + cantidadBaja >= cantidad;
    }

    public Optional<OrdenProduccionEtapa> etapa(Integer etapaId) {
        return etapas.stream().filter(e -> e.getEtapa().getId().equals(etapaId)).findFirst();
    }

    public OrdenProduccionEtapa primeraEtapa() {
        return etapas.getFirst();
    }

    public List<OrdenProduccionLinea> lineasActivas() {
        return lineas.stream().filter(l -> Boolean.TRUE.equals(l.getActiva())).toList();
    }

    /** Piezas que aún les faltan del almacén a las líneas que sigue. */
    public int pendienteDeLineas() {
        return lineasActivas().stream().mapToInt(OrdenProduccionLinea::pendiente).sum();
    }
}
