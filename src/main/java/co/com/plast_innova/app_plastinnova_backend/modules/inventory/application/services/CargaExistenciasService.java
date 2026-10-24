package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.CargaExistenciasDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.SlotRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.ItemRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.csv.LectorCsv;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carga inicial de existencias desde CSV (columnas Bodega, Slot, Item, Cantidad): cada fila es un conteo, así que el
 * saldo del ítem en ese slot queda en esa cantidad y la diferencia se registra como movimiento CARGA_INICIAL. Los
 * saldos que no vienen en el archivo no cambian. Los slots que no existen se crean. Si alguna fila tiene errores
 * (bodega o ítem desconocido, cantidad inválida, fila repetida) no se carga nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CargaExistenciasService {

    static final String ARCHIVO = "existencias.csv";
    private static final int MAXIMO_ERRORES = 20;
    private static final String MOTIVO = "Carga inicial";

    private final BodegaRepository bodegaRepository;
    private final ItemRepository itemRepository;
    private final SlotRepository slotRepository;
    private final InventarioService inventarioService;

    private record Fila(Bodega bodega, String slot, Item item, BigDecimal cantidad) {
    }

    @Transactional
    public CargaExistenciasDTO cargar(byte[] contenido, String usuario) {
        long inicio = System.currentTimeMillis();
        LectorCsv csv = LectorCsv.leer(ARCHIVO, contenido, "Bodega", "Slot", "Item", "Cantidad");
        Map<String, Bodega> bodegas = bodegaRepository.findAll().stream()
                .collect(Collectors.toMap(b -> clave(b.getCodigo()), Function.identity()));
        Map<String, Item> items = new HashMap<>();
        itemRepository.findAll().forEach(i -> items.put(clave(i.getCodigo()), i));

        List<String> errores = new ArrayList<>();
        List<Fila> filas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (int f = 0; f < csv.cantidadFilas() && errores.size() < MAXIMO_ERRORES; f++) {
            String prefijo = ARCHIVO + ", línea " + LectorCsv.linea(f) + ": ";
            String codigoBodega = csv.valor(f, "Bodega");
            String slot = csv.valor(f, "Slot");
            String codigoItem = csv.valor(f, "Item");
            String valor = csv.valor(f, "Cantidad");
            Bodega bodega = codigoBodega == null ? null : bodegas.get(clave(codigoBodega));
            Item item = codigoItem == null ? null : items.get(clave(codigoItem));
            BigDecimal cantidad = LectorCsv.decimal(valor);
            if (bodega == null) {
                errores.add(prefijo + "la bodega '" + (codigoBodega == null ? "" : codigoBodega) + "' no existe.");
            } else if (slot == null || slot.length() > 30) {
                errores.add(prefijo + "el slot es obligatorio y admite hasta 30 caracteres.");
            } else if (item == null) {
                errores.add(prefijo + "el ítem '" + (codigoItem == null ? "" : codigoItem) + "' no existe.");
            } else if (cantidad == null || cantidad.signum() < 0 || cantidad.scale() > 6
                    || cantidad.precision() - cantidad.scale() > 12) {
                errores.add(prefijo + "la cantidad '" + (valor == null ? "" : valor)
                        + "' no es válida (cero o más, hasta 6 decimales).");
            } else if (!vistas.add(clave(codigoBodega) + "\u0000" + clave(slot) + "\u0000" + clave(codigoItem))) {
                errores.add(prefijo + codigoItem + " está repetido en " + codigoBodega + " / " + slot + ".");
            } else {
                filas.add(new Fila(bodega, slot.toUpperCase(Locale.ROOT), item, cantidad));
            }
        }
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException("No se cargaron las existencias. " + String.join(" | ", errores)
                    + (errores.size() == MAXIMO_ERRORES ? " | (hay más errores)" : ""));
        }

        Map<String, Slot> slots = new HashMap<>();
        slotRepository.findAllByOrderByBodegaCodigoAscCodigoAsc()
                .forEach(s -> slots.put(clave(s.getBodega().getCodigo()) + "\u0000" + clave(s.getCodigo()), s));
        List<String> creados = new ArrayList<>();
        int movimientos = 0;
        for (Fila fila : filas) {
            Slot slot = slots.computeIfAbsent(clave(fila.bodega().getCodigo()) + "\u0000" + clave(fila.slot()), k -> {
                Slot nuevo = new Slot();
                nuevo.setBodega(fila.bodega());
                nuevo.setCodigo(fila.slot());
                creados.add(fila.bodega().getCodigo() + " / " + fila.slot());
                return slotRepository.save(nuevo);
            });
            if (inventarioService.ajustar(TipoMovimientoInventario.CARGA_INICIAL, fila.item(), slot, fila.cantidad(),
                    MOTIVO, null, usuario).isPresent()) {
                movimientos++;
            }
        }

        List<String> avisos = new ArrayList<>();
        if (!creados.isEmpty()) {
            avisos.add("Se crearon " + creados.size() + " slots: "
                    + String.join(", ", creados.subList(0, Math.min(30, creados.size()))) + (creados.size() > 30 ? "…" : ""));
        }
        log.info("Existencias cargadas por {}: {} filas, {} movimientos, {} slots nuevos en {} ms", usuario, filas.size(),
                movimientos, creados.size(), System.currentTimeMillis() - inicio);
        return new CargaExistenciasDTO(filas.size(), movimientos, creados.size(), avisos);
    }

    private static String clave(String codigo) {
        return codigo.strip().toUpperCase(Locale.ROOT);
    }
}
