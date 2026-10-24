package co.com.plast_innova.app_plastinnova_backend.modules.material.domain;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;

import java.util.Arrays;
import java.util.Optional;

/** Tipo de inventario del ítem en el ERP (f120_id_tipo_inv_serv). */
public enum TipoItem {
    PRODUCTO_TERMINADO("ProductoTerminado"),
    SEMITERMINADO("Semiterminado"),
    MATERIA_PRIMA("MateriaPrima");

    private final String valorErp;

    TipoItem(String valorErp) {
        this.valorErp = valorErp;
    }

    /** Acepta el valor del ERP ("ProductoTerminado") o el nombre del enum, sin importar mayúsculas ni espacios. */
    public static Optional<TipoItem> desdeErp(String valor) {
        String clave = Texto.clave(valor);
        if (clave == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equals(clave) || Texto.clave(t.valorErp).equals(clave))
                .findFirst();
    }
}
