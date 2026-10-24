package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.CargaListaMateriales;

import java.time.LocalDateTime;
import java.util.List;

public record CargaListaMaterialesDTO(
        Long id,
        LocalDateTime fecha,
        String usuario,
        Integer bodegas,
        Integer items,
        Integer relaciones,
        Integer itemsSinMaestro,
        List<String> avisos
) {
    public static CargaListaMaterialesDTO de(CargaListaMateriales c) {
        return new CargaListaMaterialesDTO(c.getId(), c.getFecha(), c.getUsuario(), c.getBodegas(), c.getItems(),
                c.getRelaciones(), c.getItemsSinMaestro(),
                c.getAvisos() == null ? List.of() : c.getAvisos().lines().toList());
    }
}
