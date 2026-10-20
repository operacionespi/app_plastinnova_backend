package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.EstadoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.ModoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncLog;

import java.time.LocalDateTime;

/** Bitácora de una ejecución de la sincronización. Fechas en UTC. */
public record SyncLogResponseDTO(
        Long id,
        String entidad,
        EstadoSync estado,
        ModoSync modo,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        int registrosLeidos,
        int registrosInsertados,
        int registrosActualizados,
        int registrosEliminados,
        int registrosOmitidos,
        int registrosConError,
        String disparadoPor,
        String mensajeError,
        String avisos
) {
    public static SyncLogResponseDTO de(SyncLog log) {
        return new SyncLogResponseDTO(log.getId(), log.getEntidad(), log.getEstado(), log.getModo(), log.getFechaInicio(),
                log.getFechaFin(), log.getRegistrosLeidos(), log.getRegistrosInsertados(),
                log.getRegistrosActualizados(), log.getRegistrosEliminados(), log.getRegistrosOmitidos(),
                log.getRegistrosConError(),
                log.getDisparadoPor(), log.getMensajeError(), log.getAvisos());
    }
}
