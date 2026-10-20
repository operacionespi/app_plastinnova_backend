package co.com.plast_innova.app_plastinnova_backend.modules.order.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.Producto;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.EntidadAuditable;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Pedido_Detalle")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class PedidoDetalle extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // recordId de la línea en FileMaker. Único solo cuando tiene valor (índice filtrado UX_PedidoDetalle_Filemaker)
    @Column(name = "filemaker_id", length = 100)
    private String filemakerId;

    /** Posición de la línea dentro del pedido (LinPed_PosPed). */
    @Column(name = "posicion")
    private Integer posicion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_alistamiento", length = 20)
    private TipoAlistamiento tipoAlistamiento;

    /** La línea es un anexo agregado al pedido (LinPed_Anexo). */
    @Column(name = "es_anexo", nullable = false)
    private Boolean esAnexo = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_actual_id", nullable = false)
    private EstadoDetalle estadoActual;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    /** Piezas reportadas por la última etapa (o por la última corrección). */
    @Column(name = "cantidad_procesada", nullable = false)
    private Integer cantidadProcesada = 0;

    /** Un usuario ya reportó piezas: desde entonces cantidadProcesada limita lo que puede reportar la siguiente etapa. */
    @Column(name = "avance_reportado", nullable = false)
    private Boolean avanceReportado = false;

    // FileMaker no envía precio: queda en 0 hasta que se defina una fuente
    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(name = "eliminado_en_fm", nullable = false)
    private Boolean eliminadoEnFm = false;

    @Column(name = "fm_mod_id")
    private Integer fmModId;

    @Column(name = "fecha_modificacion_fm")
    private LocalDateTime fechaModificacionFm;

    /** Fecha de creación de la línea en FileMaker (UTC). */
    @Column(name = "fecha_creacion_fm")
    private LocalDateTime fechaCreacionFm;

    /** SHA-256 de los campos del layout: permite omitir las líneas que no cambiaron. */
    @Column(name = "huella_fm", length = 64)
    private String huellaFm;

    /**
     * Máximo de piezas que puede reportar la etapa actual: lo que reportó la anterior, o la cantidad solicitada si
     * nadie ha reportado. Nunca más de lo solicitado (FileMaker puede haber reducido la cantidad del pedido).
     */
    public int cantidadMaximaReportable() {
        return Boolean.TRUE.equals(avanceReportado)
                ? Math.min(cantidadProcesada, cantidadSolicitada)
                : cantidadSolicitada;
    }

    /** Bloqueo optimista: evita que dos operadores reporten avance sobre la misma línea a la vez. */
    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Long version;
}
