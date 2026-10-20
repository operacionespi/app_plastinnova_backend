package co.com.plast_innova.app_plastinnova_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
public class AsyncConfig {

    /** Ejecuciones manuales de la sincronización (solo una a la vez, ver FileMakerSyncService). */
    @Bean(name = "fileMakerSyncExecutor")
    public Executor fileMakerSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("FMSync-");
        executor.initialize();
        return executor;
    }
}
