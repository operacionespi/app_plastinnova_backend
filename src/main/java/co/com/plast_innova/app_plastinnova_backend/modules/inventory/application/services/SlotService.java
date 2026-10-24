package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.BodegaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.ExistenciaRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.SlotRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Slots de las bodegas. El código se guarda en mayúsculas y es único dentro de su bodega. Un slot con existencias no
 * se puede desactivar ni cambiar de bodega (primero se trasladan sus existencias).
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;
    private final BodegaRepository bodegaRepository;
    private final ExistenciaRepository existenciaRepository;

    @Transactional(readOnly = true)
    public List<BodegaDTO> bodegas() {
        return bodegaRepository.findAllByOrderByCodigoAsc().stream().map(BodegaDTO::de).toList();
    }

    /** bodegaId null = todos los slots. */
    @Transactional(readOnly = true)
    public List<SlotDTO> listar(Integer bodegaId) {
        List<Slot> slots = bodegaId == null
                ? slotRepository.findAllByOrderByBodegaCodigoAscCodigoAsc()
                : slotRepository.findByBodegaIdOrderByCodigoAsc(bodegaId);
        return slots.stream().map(SlotDTO::de).toList();
    }

    @Transactional
    public SlotDTO crear(SlotRequestDTO request) {
        Bodega bodega = bodega(request.bodegaId());
        String codigo = codigo(request.codigo());
        if (slotRepository.existsByBodegaIdAndCodigo(bodega.getId(), codigo)) {
            throw new BusinessRuleException("La bodega " + bodega.getCodigo() + " ya tiene el slot " + codigo + ".");
        }
        Slot slot = new Slot();
        slot.setBodega(bodega);
        aplicar(slot, codigo, request);
        return SlotDTO.de(slotRepository.save(slot));
    }

    @Transactional
    public SlotDTO actualizar(Integer id, SlotRequestDTO request) {
        Slot slot = slotRepository.findWithBodegaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El slot " + id + " no existe."));
        Bodega bodega = bodega(request.bodegaId());
        String codigo = codigo(request.codigo());
        if (slotRepository.existsByBodegaIdAndCodigoAndIdNot(bodega.getId(), codigo, id)) {
            throw new BusinessRuleException("La bodega " + bodega.getCodigo() + " ya tiene el slot " + codigo + ".");
        }
        boolean cambiaBodega = !bodega.getId().equals(slot.getBodega().getId());
        boolean seDesactiva = Boolean.TRUE.equals(slot.getActivo()) && Boolean.FALSE.equals(request.activo());
        if ((cambiaBodega || seDesactiva) && existenciaRepository.existsBySlotIdAndCantidadGreaterThan(id, BigDecimal.ZERO)) {
            throw new BusinessRuleException("El slot " + slot.getCodigo() + " tiene existencias: trasládelas antes de "
                    + (cambiaBodega ? "cambiarlo de bodega." : "desactivarlo."));
        }
        slot.setBodega(bodega);
        aplicar(slot, codigo, request);
        return SlotDTO.de(slot);
    }

    private void aplicar(Slot slot, String codigo, SlotRequestDTO request) {
        slot.setCodigo(codigo);
        slot.setDescripcion(Texto.limpiar(request.descripcion()));
        slot.setActivo(request.activo() == null || request.activo());
    }

    private Bodega bodega(Integer id) {
        return bodegaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La bodega " + id + " no existe."));
    }

    private static String codigo(String valor) {
        return Texto.limpiar(valor).toUpperCase(Locale.ROOT);
    }
}
