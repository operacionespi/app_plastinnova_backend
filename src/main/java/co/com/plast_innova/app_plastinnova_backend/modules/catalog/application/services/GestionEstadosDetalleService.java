package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.TransicionEstadoDetalleRepository;
import org.springframework.stereotype.Service;

@Service
public class GestionEstadosDetalleService extends GestionEstadosService<EstadoDetalle, TransicionEstadoDetalle> {

    public GestionEstadosDetalleService(EstadoDetalleRepository estadoRepository,
                                        TransicionEstadoDetalleRepository transicionRepository) {
        super(estadoRepository, transicionRepository, "línea", EstadoDetalle.CODIGO_NO_SALE);
    }

    @Override
    protected EstadoDetalle nuevoEstado() {
        return new EstadoDetalle();
    }

    @Override
    protected TransicionEstadoDetalle nuevaTransicion(EstadoDetalle origen, EstadoDetalle destino) {
        TransicionEstadoDetalle transicion = new TransicionEstadoDetalle();
        transicion.setEstadoOrigen(origen);
        transicion.setEstadoDestino(destino);
        return transicion;
    }
}
