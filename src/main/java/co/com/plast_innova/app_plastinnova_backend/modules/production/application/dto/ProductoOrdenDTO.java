package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.util.List;

/** Un producto de la orden: dónde están sus piezas, las líneas que atiende y los materiales que consume. */
public record ProductoOrdenDTO(
        Long id,
        String itemCodigo,
        String itemDescripcion,
        String unidad,
        TipoItem tipo,
        int cantidad,
        /** Piezas en algún proceso. */
        int enProceso,
        int cantidadRecibida,
        int cantidadBaja,
        /** Lo que todavía les falta del almacén a las líneas que atiende. */
        int pendienteDeLineas,
        /** Piezas en proceso que nadie espera: se pueden asignar a más líneas. */
        int porAsignar,
        /** Nombre de la ruta; null si recorre todos los procesos del catálogo. */
        String ruta,
        String bodegaDestino,
        boolean terminado,
        /** Ya tiene entregas registradas (no se puede quitar de la orden). */
        boolean conEntregas,
        /** Procesos de su ruta en orden, con las piezas que hay en cada uno. */
        List<EtapaProductoDTO> etapas,
        List<LineaOrdenDTO> lineas,
        List<ComponenteOrdenDTO> componentes
) {}
