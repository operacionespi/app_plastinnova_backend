package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.TransicionEstadoPedidoRepository;
import org.springframework.stereotype.Service;

@Service
public class GestionEstadosPedidoService extends GestionEstadosService<EstadoPedido, TransicionEstadoPedido> {

    public GestionEstadosPedidoService(EstadoPedidoRepository estadoRepository,
                                       TransicionEstadoPedidoRepository transicionRepository) {
        super(estadoRepository, transicionRepository, "pedido", EstadoPedido.CODIGO_ANULADO);
    }

    @Override
    protected EstadoPedido nuevoEstado() {
        return new EstadoPedido();
    }

    @Override
    protected TransicionEstadoPedido nuevaTransicion(EstadoPedido origen, EstadoPedido destino) {
        TransicionEstadoPedido transicion = new TransicionEstadoPedido();
        transicion.setEstadoOrigen(origen);
        transicion.setEstadoDestino(destino);
        return transicion;
    }
}
