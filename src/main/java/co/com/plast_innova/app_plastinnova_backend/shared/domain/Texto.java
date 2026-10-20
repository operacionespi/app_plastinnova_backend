package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalización de textos que llegan de FileMaker (espacios sobrantes, tildes, mayúsculas).
 */
public final class Texto {

    private Texto() {
    }

    /** Quita espacios al inicio/fin y colapsa los internos. Retorna null si queda vacío. */
    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Clave de comparación: sin tildes, en mayúsculas y con espacios/guiones convertidos a "_".
     * Ej: "En ensamble" → "EN_ENSAMBLE", "Almacén 1" → "ALMACEN_1".
     */
    public static String clave(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null) {
            return null;
        }
        String sinTildes = Normalizer.normalize(limpio, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[\\s_-]+", "_");
    }
}
