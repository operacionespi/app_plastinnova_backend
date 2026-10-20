package co.com.plast_innova.app_plastinnova_backend.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Credenciales inválidas, token ausente, expirado o malformado.
     * Retorna HTTP 401 (Unauthorized) para que el frontend redirija al login.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        // Un usuario inactivo se trata como inexistente (BadCredentials): no se revela qué cuentas existen
        String detalle = ex instanceof BadCredentialsException
                ? "Email o contraseña incorrectos."
                : "Debe autenticarse para acceder a este recurso.";
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detalle);
        problemDetail.setTitle("No autenticado");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/unauthorized"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * El usuario está autenticado pero no tiene el rol o permiso requerido (@PreAuthorize).
     * Retorna HTTP 403 (Forbidden).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación.");
        problemDetail.setTitle("Acceso denegado");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/forbidden"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Maneja las excepciones cuando no se encuentra un registro en la base de datos.
     * Retorna HTTP 404 (Not Found).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFoundException(ResourceNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recurso no encontrado");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Maneja violaciones a las reglas de negocio.
     * Retorna HTTP 409 (Conflict) o 400 (Bad Request). Usaremos 409 para conflictos de estado.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRuleException(BusinessRuleException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Conflicto de Regla de Negocio");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/business-rule-violation"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Dos usuarios modificaron el mismo pedido o línea al mismo tiempo (@Version). El segundo debe recargar y reintentar.
     * Retorna HTTP 409 (Conflict).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Otro usuario modificó este registro al mismo tiempo. Recargue la información e intente de nuevo.");
        problemDetail.setTitle("Modificación concurrente");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/concurrent-modification"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Parámetros válidos en formato pero no admitidos (ej. ordenamiento por un campo no permitido).
     * Retorna HTTP 400 (Bad Request).
     */
    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail handleSolicitudInvalidaException(SolicitudInvalidaException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Solicitud inválida");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Parámetro de la URL ausente o con un valor que no corresponde al tipo (ej. fecha mal escrita o nivel inexistente).
     * Retorna HTTP 400 (Bad Request) indicando el parámetro.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleParametroInvalido(Exception ex) {
        String parametro = ex instanceof MissingServletRequestParameterException faltante
                ? faltante.getParameterName()
                : ((MethodArgumentTypeMismatchException) ex).getName();
        String detalle = ex instanceof MissingServletRequestParameterException
                ? "Falta el parámetro obligatorio '" + parametro + "'."
                : "El valor del parámetro '" + parametro + "' no es válido.";
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problemDetail.setTitle("Solicitud inválida");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Cuerpo de la petición vacío o con JSON mal formado (ej. una fecha con formato incorrecto).
     * Retorna HTTP 400 (Bad Request).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene valores con formato incorrecto.");
        problemDetail.setTitle("Solicitud inválida");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Intercepta las validaciones de entrada fallidas en los DTOs (@Valid).
     * Retorna HTTP 400 (Bad Request) con una lista detallada de qué campos fallaron.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La petición contiene datos inválidos o incompletos.");
        problemDetail.setTitle("Error de Validación");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }

        problemDetail.setProperty("invalid_params", errores);
        return problemDetail;
    }

    /**
     * Captura las restricciones de base de datos (Ej: Intentar insertar un email o documento duplicado).
     * Protege el sistema frente a UQ_Usuario_Email, UQ_Cliente_Documento, etc.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La operación no se pudo completar debido a un conflicto en la base de datos (posible registro duplicado o en uso).");
        problemDetail.setTitle("Conflicto de Integridad de Datos");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/data-integrity-violation"));
        problemDetail.setProperty("timestamp", Instant.now());

        // Se loguea internamente, pero NUNCA se envía el detalle de la BD al cliente.
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return problemDetail;
    }

    /**
     * Fallback general para excepciones no controladas.
     * Retorna HTTP 500 (Internal Server Error) evitando mostrar trazas de código al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno en el servidor. Contacte al administrador.");
        problemDetail.setTitle("Error Interno del Servidor");
        problemDetail.setType(URI.create("https://api.empresa.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        // Aquí idealmente se integra con una herramienta de APM como Sentry, Datadog o ELK.
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        return problemDetail;
    }
}