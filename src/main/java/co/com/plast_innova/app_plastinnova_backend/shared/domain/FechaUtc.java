package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Convención del sistema: todas las fechas se persisten en UTC (igual que SYSUTCDATETIME() en SQL Server).
 * Usar siempre este helper en lugar de LocalDateTime.now(), que depende de la zona del servidor.
 */
public final class FechaUtc {

    /** Zona horaria de la planta y de FileMaker: los usuarios ven y filtran fechas en esta zona. */
    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private FechaUtc() {
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
