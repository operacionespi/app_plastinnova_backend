package co.com.plast_innova.app_plastinnova_backend.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cantidades con decimales (lista de materiales, inventario): DECIMAL(18,6) en la base. */
public final class Decimales {

    public static final int ESCALA = 6;

    private Decimales() {
    }

    /** Redondeada a 6 decimales, sin ceros sobrantes y nunca en notación científica (1E+1), que el JSON mostraría tal cual. */
    public static BigDecimal paraMostrar(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal r = valor.setScale(ESCALA, RoundingMode.HALF_UP).stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0) : r;
    }
}
