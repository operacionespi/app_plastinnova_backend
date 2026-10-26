package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.HistorialEstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cambios de etapa de una línea que no hace el usuario sobre la línea misma: los hace su orden de producción o una
 * remisión. Quedan en el historial de la línea igual que los manuales (cuentan en los indicadores).
 */
@Component
@RequiredArgsConstructor
public class AvanceLineas {

    private final PedidoDetalleRepository detalleRepository;
    private final HistorialEstadoDetalleRepository historialRepository;
    private final UsuarioRepository usuarioRepository;

    public void cambiarEtapa(PedidoDetalle detalle, EstadoDetalle etapa, int cantidad, TipoMovimientoDetalle tipo,
                             String email, String observaciones) {
        EstadoDetalle anterior = detalle.getEstadoActual();
        detalle.setEstadoActual(etapa);
        detalle.setCantidadProcesada(cantidad);
        detalle.setAvanceReportado(true);
        detalleRepository.save(detalle);
        historialRepository.save(HistorialEstadoDetalle.builder()
                .pedidoDetalle(detalle)
                .estadoAnterior(anterior)
                .estado(etapa)
                .tipo(tipo)
                .cantidadProcesada(cantidad)
                .usuario(usuario(email))
                .origen(OrigenCambio.USUARIO)
                .observaciones(observaciones == null || observaciones.length() <= 500
                        ? observaciones : observaciones.substring(0, 499) + "…")
                .build());
    }

    public Usuario usuario(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario operador no existe o está inactivo en el sistema."));
    }
}
