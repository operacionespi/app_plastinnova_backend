package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.specification;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.RangoFechas;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PedidoSpecification {

    /** Variante de la intercalación de la base que ignora tildes además de mayúsculas. */
    private static final String SIN_TILDES = "SQL_Latin1_General_CP1_CI_AI";

    private PedidoSpecification() {
    }

    public static Specification<Pedido> construirFiltro(FiltroPedidoRequestDTO filtro) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicados = new ArrayList<>();

            // Regla de negocio inmutable: Nunca mostrar pedidos eliminados lógicamente
            predicados.add(criteriaBuilder.isFalse(root.get("eliminadoEnFm")));

            String numeroPedido = Texto.limpiar(filtro.numeroPedido());
            if (numeroPedido != null) {
                // Prefijo: aprovecha IX_Pedido_NumeroPedido
                predicados.add(criteriaBuilder.like(root.get("numeroPedido"), escaparLike(numeroPedido) + "%", '\\'));
            }

            String ciudad = Texto.limpiar(filtro.ciudad());
            if (ciudad != null) {
                // Contiene, sin distinguir mayúsculas ni tildes: "bogota" encuentra "Bogotá"
                HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) criteriaBuilder;
                predicados.add(hcb.like(hcb.collate(root.get("ciudadEnvio").get("nombre"), SIN_TILDES),
                        "%" + escaparLike(ciudad) + "%", '\\'));
            }

            List<String> vendedores = filtro.vendedores() == null ? List.of()
                    : filtro.vendedores().stream().map(Texto::limpiar).filter(Objects::nonNull).distinct().toList();
            if (!vendedores.isEmpty()) {
                predicados.add(root.get("cliente").get("vendedor").in(vendedores));
            }

            if (filtro.clienteId() != null) {
                predicados.add(criteriaBuilder.equal(root.get("cliente").get("id"), filtro.clienteId()));
            }

            if (filtro.estadosIds() != null && !filtro.estadosIds().isEmpty()) {
                predicados.add(root.get("estadoActual").get("id").in(filtro.estadosIds()));
            }

            // Días calendario de Colombia; la fecha final se incluye completa
            if (filtro.fechaDesde() != null) {
                predicados.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fechaCreacionFm"),
                        RangoFechas.inicioDelDiaUtc(filtro.fechaDesde())));
            }
            if (filtro.fechaHasta() != null) {
                predicados.add(criteriaBuilder.lessThan(root.get("fechaCreacionFm"),
                        RangoFechas.inicioDelDiaUtc(filtro.fechaHasta().plusDays(1))));
            }

            if (Boolean.TRUE.equals(filtro.soloAbiertos()) || Boolean.TRUE.equals(filtro.soloAtrasados())) {
                predicados.add(criteriaBuilder.isNull(root.get("fechaCierre")));
            }

            if (Boolean.TRUE.equals(filtro.soloAtrasados())) {
                // La fecha de entrega es un día: el pedido se atrasa cuando ese día ya pasó en Colombia
                predicados.add(criteriaBuilder.lessThan(root.get("fechaEstimadaEntrega"),
                        LocalDate.now(FechaUtc.ZONA_COLOMBIA)));
            }

            return criteriaBuilder.and(predicados.toArray(new Predicate[0]));
        };
    }

    /** Escapa los comodines de LIKE con barra invertida. */
    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
