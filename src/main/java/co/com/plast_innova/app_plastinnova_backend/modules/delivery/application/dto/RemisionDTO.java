package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.EstadoRemision;

import java.time.LocalDateTime;
import java.util.List;

public record RemisionDTO(
        Long id,
        String numero,
        Long pedidoId,
        String numeroPedido,
        String cliente,
        EstadoRemision estado,
        String transportador,
        String placa,
        String observaciones,
        String usuario,
        LocalDateTime fecha,
        String usuarioAnulacion,
        LocalDateTime fechaAnulacion,
        String motivoAnulacion,
        Integer totalPiezas,
        List<RemisionLineaDTO> lineas
) {}
