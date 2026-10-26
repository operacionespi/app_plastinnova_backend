package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Un ítem a producir y las líneas de pedido que atiende. */
public record ProductoOrdenRequestDTO(
        @NotBlank(message = "Indique el ítem a producir")
        @Size(max = 50)
        String itemCodigo,

        /** Piezas a producir; si no se indica, la suma de lo asignado a las líneas. */
        @Positive(message = "La cantidad debe ser mayor que cero")
        @Max(value = 1_000_000, message = "La cantidad no puede pasar de 1.000.000")
        Integer cantidad,

        /**
         * Código de la bodega donde entra lo producido. Si no se indica: la de producto terminado para los productos
         * terminados y los que atienden pedidos; para los demás, la bodega donde la lista de materiales los consume.
         */
        @Size(max = 20)
        String bodegaDestino,

        List<@NotNull @Valid VinculoLineaRequestDTO> lineas
) {}
