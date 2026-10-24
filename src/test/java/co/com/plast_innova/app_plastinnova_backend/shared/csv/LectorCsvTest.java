package co.com.plast_innova.app_plastinnova_backend.shared.csv;

import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LectorCsvTest {

    @Test
    void leeComillasComasInternasYQuitaElRelleno() {
        String csv = """
                f150_id,f150_descripcion,f150_descripcion_corta\r
                1A002,"Almacen De Aros, Emp, Sop Y Acc",Almacen2\r
                1A003,Almacen de piezas ,Almacen3            \r
                1I002,"Área de soldadura ""A""\",\r
                \r
                """;
        LectorCsv lector = LectorCsv.leer("bodegas.csv", csv.getBytes(StandardCharsets.UTF_8), "F150_ID", "f150_descripcion");

        assertEquals(3, lector.cantidadFilas());
        assertEquals("Almacen De Aros, Emp, Sop Y Acc", lector.valor(0, "f150_descripcion"));
        assertEquals("Almacen de piezas", lector.valor(1, "f150_descripcion"));
        assertEquals("Almacen3", lector.valor(1, "f150_descripcion_corta"));
        assertEquals("Área de soldadura \"A\"", lector.valor(2, "f150_descripcion"));
        assertNull(lector.valor(2, "f150_descripcion_corta"));
    }

    @Test
    void aceptaUtf8ConBomYWindows1252() {
        byte[] conBom = "﻿codigo,nombre\nX,Fábrica\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("Fábrica", LectorCsv.leer("a.csv", conBom, "codigo").valor(0, "nombre"));

        byte[] ansi = "codigo,nombre\nX,Fábrica\n".getBytes(Charset.forName("windows-1252"));
        assertEquals("Fábrica", LectorCsv.leer("a.csv", ansi, "codigo").valor(0, "nombre"));
    }

    @Test
    void rechazaArchivoVacioOSinLasColumnas() {
        assertThrows(SolicitudInvalidaException.class, () -> LectorCsv.leer("a.csv", new byte[0]));
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> LectorCsv.leer("items.csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), "f120_referencia"));
        assertTrue(e.getMessage().contains("items.csv"));
        assertTrue(e.getMessage().contains("f120_referencia"));
    }
}
