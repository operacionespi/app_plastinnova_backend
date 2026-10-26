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
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ReservaInventarioRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.SlotRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final ReservaInventarioRepository reservaRepository;

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
        // Ítem antes que saldo: un faltante revisa lo comprometido (ver respetarReservas) y debe bloquear en ese orden
        itemRepository.bloquear(item.getId());
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

    /**
     * Salida automática de un slot (despacho, remisión, consumo de una orden). Si la referencia es una línea de pedido,
     * quien llama ya bloqueó el ítem y redujo su reserva o verificó lo libre (ver ReservaInventarioService).
     */
    public MovimientoInventario salida(Item item, Slot origen, BigDecimal cantidad, ReferenciaMovimiento referencia,
                                       String motivo, String usuario) {
        return mover(TipoMovimientoInventario.SALIDA, item, origen, null, cantidad, null, motivo,
                referencia.observaciones(), usuario, referencia);
    }

    /** Entrada automática a un slot: lo producido, o lo que devuelve una remisión anulada. */
    public MovimientoInventario entrada(Item item, Slot destino, BigDecimal cantidad, ReferenciaMovimiento referencia,
                                        String motivo, String usuario) {
        return mover(TipoMovimientoInventario.ENTRADA, item, null, destino, cantidad, null, motivo,
                referencia.observaciones(), usuario, referencia);
    }

    /**
     * Saca la cantidad de los slots de la bodega: primero del preferido y luego de los de más saldo. Si no alcanza sale
     * lo que haya y retorna lo que faltó (quien llama decide si es un error).
     */
    public BigDecimal sacarDeBodega(Item item, Bodega bodega, BigDecimal cantidad, Slot preferido,
                                    ReferenciaMovimiento referencia, String motivo, String usuario) {
        BigDecimal pendiente = cantidad;
        List<Existencia> saldos = new ArrayList<>(existenciaRepository.bloquearEnBodega(item.getId(), bodega.getId()));
        if (preferido != null) {
            saldos.sort(Comparator.comparing(e -> !e.getSlot().getId().equals(preferido.getId())));
        }
        for (Existencia e : saldos) {
            if (pendiente.signum() <= 0) {
                break;
            }
            BigDecimal sale = pendiente.min(e.getCantidad());
            salida(item, e.getSlot(), sale, referencia, motivo, usuario);
            pendiente = pendiente.subtract(sale);
        }
        return pendiente.max(BigDecimal.ZERO);
    }

    /** Existencia del ítem en la bodega menos lo comprometido por los pedidos (puede ser negativa tras un conteo). */
    public BigDecimal libre(Integer itemId, Integer bodegaId) {
        return existenciaRepository.totalEnBodega(itemId, bodegaId)
                .subtract(reservaRepository.totalReservado(itemId, bodegaId, null));
    }

    private MovimientoInventario mover(TipoMovimientoInventario tipo, Item item, Slot origen, Slot destino,
                                       BigDecimal cantidad, BigDecimal contada, String motivo, String observaciones,
                                       String usuario) {
        return mover(tipo, item, origen, destino, cantidad, contada, motivo, observaciones, usuario, null);
    }

    private MovimientoInventario mover(TipoMovimientoInventario tipo, Item item, Slot origen, Slot destino,
                                       BigDecimal cantidad, BigDecimal contada, String motivo, String observaciones,
                                       String usuario, ReferenciaMovimiento referencia) {
        LocalDateTime ahora = FechaUtc.ahora();
        // Los movimientos de una línea sacan lo que tenía comprometido (su reserva ya se redujo)
        if ((referencia == null || !referencia.esDeLinea()) && sacaDeLaBodega(tipo, origen, destino)) {
            respetarReservas(tipo, item, origen.getBodega(), cantidad);
        }
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
                .pedidoDetalle(referencia == null ? null : referencia.detalle())
                .ordenProduccion(referencia == null ? null : referencia.orden())
                .remision(referencia == null ? null : referencia.remision())
                .build());
    }

    /**
     * Nada deja la bodega por debajo de lo que los pedidos tienen comprometido: ni las salidas, ni los traslados a otra
     * bodega, ni los conteos con faltante (hay que liberar reservas antes de ajustar).
     */
    private static boolean sacaDeLaBodega(TipoMovimientoInventario tipo, Slot origen, Slot destino) {
        return origen != null && (destino == null || !destino.getBodega().getId().equals(origen.getBodega().getId()));
    }

    private void respetarReservas(TipoMovimientoInventario tipo, Item item, Bodega bodega, BigDecimal cantidad) {
        // El bloqueo del ítem va antes que el de los saldos, en el mismo orden que al reservar y despachar
        itemRepository.bloquear(item.getId());
        BigDecimal reservado = reservaRepository.totalReservado(item.getId(), bodega.getId(), null);
        if (reservado.signum() == 0) {
            return;
        }
        BigDecimal existencia = existenciaRepository.totalEnBodega(item.getId(), bodega.getId());
        BigDecimal libre = existencia.subtract(reservado);
        if (libre.compareTo(cantidad) >= 0) {
            return;
        }
        String comprometidas = "Hay " + Decimales.paraMostrar(reservado) + " de " + item.getCodigo()
                + " comprometidas para pedidos en " + bodega.getCodigo();
        if (tipo == TipoMovimientoInventario.AJUSTE || tipo == TipoMovimientoInventario.CARGA_INICIAL) {
            throw new BusinessRuleException(comprometidas + " y el conteo dejaría "
                    + Decimales.paraMostrar(existencia.subtract(cantidad)) + " en la bodega: libere reservas antes de ajustar.");
        }
        throw new BusinessRuleException(comprometidas + ": solo se pueden sacar "
                + Decimales.paraMostrar(libre.max(BigDecimal.ZERO)) + ".");
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
