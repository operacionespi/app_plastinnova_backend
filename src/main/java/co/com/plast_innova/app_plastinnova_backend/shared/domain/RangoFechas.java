package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Rango [desdeUtc, hastaUtc) para consultar fechas guardadas en UTC a partir de días calendario de Colombia.
 * Ej: del 01/10 al 01/10 = 01/10 05:00 UTC hasta 02/10 05:00 UTC.
 */
public record RangoFechas(LocalDateTime desdeUtc, LocalDateTime hastaUtc) {

    /** Evita consultas que recorran años de historial desde el tablero. */
    public static final long MAXIMO_DIAS = 366;

    /**
     * @param desde primer día incluido (hora Colombia)
     * @param hasta último día incluido (hora Colombia)
     */
    public static RangoFechas deDiasColombia(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new SolicitudInvalidaException("Debe indicar la fecha inicial y la final del rango.");
        }
        if (hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS) {
            throw new SolicitudInvalidaException("El rango no puede superar " + MAXIMO_DIAS + " días.");
        }
        return new RangoFechas(inicioDelDiaUtc(desde), inicioDelDiaUtc(hasta.plusDays(1)));
    }

    /** 00:00 hora Colombia del día indicado, expresado en UTC. */
    public static LocalDateTime inicioDelDiaUtc(LocalDate dia) {
        return dia.atStartOfDay(FechaUtc.ZONA_COLOMBIA).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
