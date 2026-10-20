package co.com.plast_innova.app_plastinnova_backend.shared.exception;

/**
 * Parámetros de la petición que no se pueden atender (ej. ordenar por un campo no permitido). HTTP 400.
 */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
