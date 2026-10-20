package co.com.plast_innova.app_plastinnova_backend.modules.order.domain;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.customer.domain.Cliente;
import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Ciudad;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Pedido")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "filemaker_id", nullable = false, unique = true, length = 100)
    private String filemakerId;

    /** Consecutivo visible para el usuario (número de pedido en FileMaker). */
    @Column(name = "numero_pedido", length = 50)
    private String numeroPedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Dirección en texto libre, tal como viene de FileMaker. */
    @Column(name = "direccion_envio", length = 500)
    private String direccionEnvio;

    /** Ciudad de entrega normalizada: base de los indicadores por ciudad, zona, departamento y país. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_envio_id")
    private Ciudad ciudadEnvio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_asignado_id")
    private Usuario usuarioAsignado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_actual_id", nullable = false)
    private EstadoPedido estadoActual;

    @Column(name = "fecha_creacion_fm", nullable = false)
    private LocalDateTime fechaCreacionFm;

    /** Fecha (UTC) en que el pedido ingresó a este sistema. */
    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private LocalDateTime fechaIngreso;

    /** Día comprometido de entrega (sin hora). Lo asigna la app; FileMaker no lo envía. */
    @Column(name = "fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    /** Fecha en que el pedido llegó a un estado final. Base del indicador de cumplimiento (OTIF). */
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "total_pedido", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPedido = BigDecimal.ZERO;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    @Column(name = "eliminado_en_fm", nullable = false)
    private Boolean eliminadoEnFm = false;

    @Column(name = "fm_mod_id")
    private Integer fmModId;

    /** Última modificación en FileMaker de cualquiera de sus líneas (UTC). */
    @Column(name = "fecha_modificacion_fm")
    private LocalDateTime fechaModificacionFm;

    /** Bloqueo optimista: evita que dos usuarios sobrescriban el mismo pedido al mismo tiempo. */
    @Version
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Long version;

    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC, id ASC")
    private List<PedidoDetalle> detalles = new ArrayList<>();

    /** Abierto (sin fecha de cierre) y el día comprometido de entrega ya pasó en Colombia. */
    public boolean estaAtrasado(LocalDate hoy) {
        return fechaCierre == null && fechaEstimadaEntrega != null && fechaEstimadaEntrega.isBefore(hoy);
    }

    @PrePersist
    protected void alCrear() {
        if (fechaIngreso == null) {
            fechaIngreso = FechaUtc.ahora();
        }
    }

    @PreUpdate
    protected void alActualizar() {
        fechaActualizacion = FechaUtc.ahora();
    }

    // Helper method para asegurar sincronía bidireccional
    public void addDetalle(PedidoDetalle detalle) {
        detalles.add(detalle);
        detalle.setPedido(this);
    }

    public void removeDetalle(PedidoDetalle detalle) {
        detalles.remove(detalle);
        detalle.setPedido(null);
    }
}
