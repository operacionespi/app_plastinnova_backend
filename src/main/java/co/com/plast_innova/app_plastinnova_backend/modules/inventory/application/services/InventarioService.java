package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Existencia;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.MovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ExistenciaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.MovimientoInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.SlotRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Movimientos de inventario: cada uno ajusta los saldos de sus slots y queda en la bitácora, en la misma
 * transacción. Los saldos se bloquean mientras se actualizan, así que dos movimientos simultáneos no se pisan y
 * ninguno deja un saldo negativo.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ExistenciaRepository existenciaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final SlotRepository slotRepository;
    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public Page<ExistenciaDTO> existencias(String texto, Integer bodegaId, Pageable pageable) {
        return existenciaRepository.buscar(texto == null ? "" : texto.strip(), bodegaId, pageable).map(ExistenciaDTO::de);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoDTO> movimientos(String itemCodigo, Integer bodegaId, TipoMovimientoInventario tipo,
                                           Pageable pageable) {
        return movimientoRepository.buscar(itemCodigo == null ? "" : itemCodigo.strip(), bodegaId, tipo, pageable)
                .map(MovimientoDTO::de);
    }

    @Transactional
    public MovimientoDTO registrar(MovimientoRequestDTO request, String usuario) {
        Item item = itemRepository.findByCodigo(request.itemCodigo().strip())
                .orElseThrow(() -> new ResourceNotFoundException("El ítem " + request.itemCodigo() + " no existe."));
        BigDecimal cantidad = request.cantidad();
        String motivo = Texto.limpiar(request.motivo());
        String observaciones = Texto.limpiar(request.observaciones());

        MovimientoInventario movimiento = switch (request.tipo()) {
            case ENTRADA -> {
                exigirPositiva(cantidad);
                sinSlot(request.slotOrigenId(), "Una entrada no tiene slot de origen.");
                yield mover(TipoMovimientoInventario.ENTRADA, item, null, slot(request.slotDestinoId(), "destino"),
                        cantidad, null, motivo, observaciones, usuario);
            }
            case SALIDA -> {
                exigirPositiva(cantidad);
                sinSlot(request.slotDestinoId(), "Una salida no tiene slot de destino.");
                yield mover(TipoMovimientoInventario.SALIDA, item, slot(request.slotOrigenId(), "origen"), null,
                        cantidad, null, motivo, observaciones, usuario);
            }
            case TRASLADO -> {
                exigirPositiva(cantidad);
                Slot origen = slot(request.slotOrigenId(), "origen");
                Slot destino = slot(request.slotDestinoId(), "destino");
                if (origen.getId().equals(destino.getId())) {
                    throw new SolicitudInvalidaException("El slot de origen y el de destino son el mismo.");
                }
                yield mover(TipoMovimientoInventario.TRASLADO, item, origen, destino, cantidad, null, motivo,
                        observaciones, usuario);
            }
            case AJUSTE -> {
                sinSlot(request.slotOrigenId(), "Un ajuste se indica con el slot contado (slotDestinoId).");
                Slot slot = slot(request.slotDestinoId(), "contado");
                yield ajustar(TipoMovimientoInventario.AJUSTE, item, slot, cantidad, motivo, observaciones, usuario)
                        .orElseThrow(() -> new BusinessRuleException("El saldo de " + item.getCodigo() + " en "
                                + slot.getCodigo() + " ya es " + Decimales.paraMostrar(cantidad) + ": no hay nada que ajustar."));
            }
            case CARGA_INICIAL -> throw new SolicitudInvalidaException("La carga inicial se hace con el CSV de existencias.");
        };
        return MovimientoDTO.de(movimiento);
    }

    /**
     * Deja el saldo del ítem en el slot en la cantidad contada y registra la diferencia. Vacío si el saldo ya era ese.
     * Lo usan el ajuste manual y la carga inicial (dentro de su transacción).
     */
    Optional<MovimientoInventario> ajustar(TipoMovimientoInventario tipo, Item item, Slot slot, BigDecimal contada,
                                           String motivo, String observaciones, String usuario) {
        BigDecimal saldo = existenciaRepository.bloquear(item.getId(), slot.getId())
                .map(Existencia::getCantidad).orElse(BigDecimal.ZERO);
        BigDecimal diferencia = contada.subtract(saldo);
        if (diferencia.signum() == 0) {
            return Optional.empty();
        }
        Slot origen = diferencia.signum() < 0 ? slot : null;
        Slot destino = diferencia.signum() > 0 ? slot : null;
        return Optional.of(mover(tipo, item, origen, destino, diferencia.abs(), contada, motivo, observaciones, usuario));
    }

    private MovimientoInventario mover(TipoMovimientoInventario tipo, Item item, Slot origen, Slot destino,
                                       BigDecimal cantidad, BigDecimal contada, String motivo, String observaciones,
                                       String usuario) {
        LocalDateTime ahora = FechaUtc.ahora();
        if (origen != null) {
            Existencia existencia = existenciaRepository.bloquear(item.getId(), origen.getId()).orElse(null);
            BigDecimal saldo = existencia == null ? BigDecimal.ZERO : existencia.getCantidad();
            if (saldo.compareTo(cantidad) < 0) {
                throw new BusinessRuleException("No hay suficiente " + item.getCodigo() + " en "
                        + origen.getBodega().getCodigo() + " / " + origen.getCodigo() + ": hay "
                        + Decimales.paraMostrar(saldo) + ".");
            }
            existencia.setCantidad(saldo.subtract(cantidad));
            existencia.setFechaActualizacion(ahora);
        }
        if (destino != null) {
            // A un slot inactivo no entra nada; de él sí puede salir lo que quede
            if (!Boolean.TRUE.equals(destino.getActivo())) {
                throw new BusinessRuleException("El slot " + destino.getBodega().getCodigo() + " / " + destino.getCodigo()
                        + " está inactivo.");
            }
            Existencia existencia = existenciaRepository.bloquear(item.getId(), destino.getId()).orElseGet(() -> {
                Existencia nueva = new Existencia();
                nueva.setItem(item);
                nueva.setSlot(destino);
                return nueva;
            });
            existencia.setCantidad(existencia.getCantidad().add(cantidad));
            existencia.setFechaActualizacion(ahora);
            existenciaRepository.save(existencia);
        }
        return movimientoRepository.save(MovimientoInventario.builder()
                .tipo(tipo)
                .item(item)
                .slotOrigen(origen)
                .slotDestino(destino)
                .cantidad(cantidad)
                .cantidadContada(contada)
                .motivo(motivo)
                .observaciones(observaciones)
                .usuario(usuario)
                .fecha(ahora)
                .build());
    }

    private Slot slot(Integer id, String rol) {
        if (id == null) {
            throw new SolicitudInvalidaException("Falta el slot de " + rol + ".");
        }
        return slotRepository.findWithBodegaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El slot " + id + " no existe."));
    }

    private static void sinSlot(Integer id, String mensaje) {
        if (id != null) {
            throw new SolicitudInvalidaException(mensaje);
        }
    }

    private static void exigirPositiva(BigDecimal cantidad) {
        if (cantidad.signum() <= 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser mayor que cero.");
        }
    }
}
