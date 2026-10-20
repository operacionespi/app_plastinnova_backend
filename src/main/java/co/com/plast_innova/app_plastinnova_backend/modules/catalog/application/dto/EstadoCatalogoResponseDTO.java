package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoCatalogo;

public record EstadoCatalogoResponseDTO(
        Integer id,
        String codigo,
        String nombre,
        String descripcion,
        Integer ordenLogico,
        String color,
        Boolean esInicial,
        Boolean esFinal,
        Boolean activo
) {
    public static EstadoCatalogoResponseDTO de(EstadoCatalogo estado) {
        return new EstadoCatalogoResponseDTO(estado.getId(), estado.getCodigo(), estado.getNombre(),
                estado.getDescripcion(), estado.getOrdenLogico(), estado.getColor(),
                estado.getEsInicial(), estado.getEsFinal(), estado.getActivo());
    }
}
