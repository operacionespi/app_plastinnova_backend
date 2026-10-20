package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto;

/**
 * Resultado de eliminar un estado: se borra si nunca se usó; si está en uso solo se desactiva.
 */
public record ResultadoEliminacionDTO(Integer id, Accion accion, String mensaje) {

    public enum Accion {
        ELIMINADO,
        DESACTIVADO
    }
}
