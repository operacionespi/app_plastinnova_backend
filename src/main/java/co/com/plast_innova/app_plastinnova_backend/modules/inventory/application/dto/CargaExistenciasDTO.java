package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import java.util.List;

public record CargaExistenciasDTO(
        Integer filas,
        /** Filas cuyo saldo cambió (las demás ya tenían esa cantidad). */
        Integer movimientos,
        Integer slotsCreados,
        List<String> avisos
) {}
