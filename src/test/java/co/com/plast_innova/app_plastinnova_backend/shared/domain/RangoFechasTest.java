package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RangoFechasTest {

    @Test
    void convierteDiasDeColombiaAUnRangoUtcConElUltimoDiaIncluido() {
        RangoFechas rango = RangoFechas.deDiasColombia(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(LocalDateTime.of(2026, 10, 1, 5, 0), rango.desdeUtc());
        assertEquals(LocalDateTime.of(2026, 11, 1, 5, 0), rango.hastaUtc());
    }

    @Test
    void rechazaRangosInvertidosODemasiadoLargos() {
        assertThrows(SolicitudInvalidaException.class, () ->
                RangoFechas.deDiasColombia(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
        assertThrows(SolicitudInvalidaException.class, () ->
                RangoFechas.deDiasColombia(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2))); // 367 días
        // 366 días exactos (año bisiesto completo) sí se permite
        RangoFechas.deDiasColombia(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
    }
}
