package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.specification;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PedidoDetalleSpecification {

    private PedidoDetalleSpecification() {
    }

    /** Cola de trabajo: solo líneas pendientes de pedidos abiertos. */
    public static Specification<PedidoDetalle> colaDeTrabajo(FiltroLineaRequestDTO filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            Join<PedidoDetalle, Pedido> pedido = root.join("pedido");

            predicados.add(cb.isFalse(root.get("eliminadoEnFm")));
            predicados.add(cb.isFalse(root.get("estadoActual").get("esFinal")));
            predicados.add(cb.isFalse(pedido.get("eliminadoEnFm")));
            predicados.add(cb.isNull(pedido.get("fechaCierre")));

            if (filtro.estadosIds() != null && !filtro.estadosIds().isEmpty()) {
                predicados.add(root.get("estadoActual").get("id").in(filtro.estadosIds()));
            }
            if (filtro.tipoAlistamiento() != null) {
                predicados.add(cb.equal(root.get("tipoAlistamiento"), filtro.tipoAlistamiento()));
            }
            if (filtro.esAnexo() != null) {
                predicados.add(cb.equal(root.get("esAnexo"), filtro.esAnexo()));
            }
            if (filtro.clienteId() != null) {
                predicados.add(cb.equal(pedido.get("cliente").get("id"), filtro.clienteId()));
            }
            String numeroPedido = Texto.limpiar(filtro.numeroPedido());
            if (numeroPedido != null) {
                // Prefijo, igual que el listado de pedidos. Se escapan los comodines de LIKE.
                String patron = numeroPedido.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicados.add(cb.like(pedido.get("numeroPedido"), patron, '\\'));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
