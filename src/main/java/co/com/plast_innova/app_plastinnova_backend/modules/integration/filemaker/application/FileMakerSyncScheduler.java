package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.FileMakerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincronización automática del layout PedidosLineas-Api.
 * Solo se registra con app.filemaker.sync.enabled=true (FM_SYNC_ENABLED en .env).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.filemaker.sync.enabled", havingValue = "true")
public class FileMakerSyncScheduler {

    private final FileMakerSyncService syncService;

    // Corre en el hilo del scheduler; si una ejecución tarda más que el intervalo, la siguiente se omite
    @Scheduled(cron = "${app.filemaker.sync.cron}", zone = "America/Bogota")
    public void sincronizarPedidos() {
        log.info("Iniciando tarea programada: sincronización incremental de pedidos desde FileMaker");
        syncService.ejecutarProgramada();
    }

    // Detecta las líneas borradas en FileMaker (no dejan marca de modificación) releyendo los pedidos abiertos
    @Scheduled(cron = "${app.filemaker.sync.cron-reconciliacion}", zone = "America/Bogota")
    public void reconciliarPedidos() {
        log.info("Iniciando tarea programada: reconciliación de pedidos abiertos con FileMaker");
        syncService.ejecutarReconciliacion();
    }
}
