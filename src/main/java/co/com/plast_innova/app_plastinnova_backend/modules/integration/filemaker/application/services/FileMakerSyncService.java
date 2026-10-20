package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoPedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.FileMakerApiClient;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FilaPedidoFm;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerRegistrosResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerRegistrosResponseDTO.Registro;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.mapper.FilaPedidoFmParser;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.mapper.FilaPedidoFmParser.FilaInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.EstadoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.ModoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncControl;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncLog;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories.SyncControlRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories.SyncLogRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.ResumenLineaFm;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sincroniza el layout "PedidosLineas-Api" de FileMaker (una fila por línea de pedido) hacia SQL Server.
 *
 * <p>Modos de lectura ({@link ModoSync}):
 * <ul>
 *   <li>COMPLETA: todas las líneas creadas desde app.filemaker.sync.fecha-desde. Solo la primera vez (sin marca)
 *       o a pedido; es lenta porque LinPed_FechaCreacion es un cálculo.</li>
 *   <li>INCREMENTAL (programada): busca las líneas con zMarcaFechaYHoraModificación desde la última marca menos un
 *       margen, y luego descarga esos pedidos completos (el sincronizador siempre recibe todas las líneas del pedido).
 *       La marca solo avanza si ningún pedido falló, y se toma de la búsqueda por modificación.</li>
 *   <li>RECONCILIACION (nocturna): relee los pedidos abiertos en la app. Borrar una línea en FileMaker no deja
 *       marca de modificación, así que es la única forma de detectar esas eliminaciones sin leer todo.</li>
 * </ul>
 * Solo se escriben los pedidos cuyas líneas cambiaron (huella SHA-256 de cada registro). Las líneas que ya no
 * aparecen en FileMaker se marcan como eliminadas (no se borran). Los pedidos nuevos en un estado omitido o final
 * no se importan (se cuentan como omitidos).
 */
@Slf4j
@Service
public class FileMakerSyncService {

    public static final String ENTIDAD = "PedidosLineas";
    private static final int MAX_MENSAJES = 50;
    /** SQL Server admite ~2100 parámetros por consulta. */
    private static final int LOTE_ELIMINADAS = 1000;
    private static final DateTimeFormatter FORMATO_FECHA_FM = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter FORMATO_MARCA_FM = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
    /** Pedidos por búsqueda de FileMaker (una consulta OR por pedido). */
    private static final int LOTE_PEDIDOS_FM = 50;

    private final FileMakerApiClient apiClient;
    private final SincronizadorPedidoFm sincronizador;
    private final SyncLogRepository syncLogRepository;
    private final SyncControlRepository syncControlRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository detalleRepository;
    private final EstadoPedidoRepository estadoPedidoRepository;
    private final EstadoDetalleRepository estadoDetalleRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor executor;
    private final String layout;
    private final int tamanoPagina;
    private final List<String> estadosPedidoOmitidos;
    private final List<String> equivalenciasEstadoPedido;
    private final List<String> equivalenciasEstadoDetalle;
    /** La búsqueda incremental arranca este tiempo antes de la marca: cubre relojes desfasados y guardados lentos. */
    private final Duration margenIncremental;
    /** Solo se sincronizan las líneas creadas desde esta fecha (hora Colombia, como la guarda FileMaker). */
    private final LocalDateTime fechaDesdeColombia;
    private final LocalDateTime fechaDesdeUtc;

    /** Evita dos ejecuciones simultáneas (programada + manual). */
    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);

    public FileMakerSyncService(FileMakerApiClient apiClient,
                                SincronizadorPedidoFm sincronizador,
                                SyncLogRepository syncLogRepository,
                                SyncControlRepository syncControlRepository,
                                PedidoRepository pedidoRepository,
                                PedidoDetalleRepository detalleRepository,
                                EstadoPedidoRepository estadoPedidoRepository,
                                EstadoDetalleRepository estadoDetalleRepository,
                                TransactionTemplate transactionTemplate,
                                @Qualifier("fileMakerSyncExecutor") Executor executor,
                                @Value("${app.filemaker.sync.layout}") String layout,
                                @Value("${app.filemaker.sync.tamano-pagina}") int tamanoPagina,
                                @Value("${app.filemaker.sync.estados-pedido-omitidos}") List<String> estadosPedidoOmitidos,
                                @Value("${app.filemaker.sync.equivalencias-estado-pedido}") List<String> equivalenciasEstadoPedido,
                                @Value("${app.filemaker.sync.equivalencias-estado-detalle}") List<String> equivalenciasEstadoDetalle,
                                @Value("${app.filemaker.sync.fecha-desde}") LocalDateTime fechaDesdeColombia,
                                @Value("${app.filemaker.sync.margen-incremental-minutos}") long margenIncrementalMinutos) {
        this.apiClient = apiClient;
        this.sincronizador = sincronizador;
        this.syncLogRepository = syncLogRepository;
        this.syncControlRepository = syncControlRepository;
        this.pedidoRepository = pedidoRepository;
        this.detalleRepository = detalleRepository;
        this.estadoPedidoRepository = estadoPedidoRepository;
        this.estadoDetalleRepository = estadoDetalleRepository;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
        this.layout = layout;
        this.tamanoPagina = tamanoPagina;
        this.estadosPedidoOmitidos = estadosPedidoOmitidos;
        this.equivalenciasEstadoPedido = equivalenciasEstadoPedido;
        this.equivalenciasEstadoDetalle = equivalenciasEstadoDetalle;
        this.fechaDesdeColombia = fechaDesdeColombia;
        this.margenIncremental = Duration.ofMinutes(margenIncrementalMinutos);
        this.fechaDesdeUtc = fechaDesdeColombia.atZone(FilaPedidoFmParser.ZONA_FILEMAKER)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Ejecución manual: registra la bitácora y sincroniza en segundo plano.
     * El avance se consulta con el id de la bitácora retornada.
     *
     * @param modo INCREMENTAL pasa a COMPLETA si todavía no hay marca de modificación
     */
    public SyncLog iniciarManual(String emailUsuario, ModoSync modo) {
        if (!enEjecucion.compareAndSet(false, true)) {
            throw new BusinessRuleException("Ya hay una sincronización con FileMaker en curso.");
        }
        try {
            SyncLog bitacora = syncLogRepository.save(nuevaBitacora(emailUsuario, modoEfectivo(modo)));
            executor.execute(() -> ejecutarYLiberar(bitacora));
            return bitacora;
        } catch (RuntimeException e) {
            enEjecucion.set(false);
            throw e;
        }
    }

    /** Ejecución del scheduler (incremental): si ya hay una en curso, se omite. */
    public void ejecutarProgramada() {
        ejecutarProgramada(ModoSync.INCREMENTAL);
    }

    /** Reconciliación nocturna de los pedidos abiertos: si ya hay una ejecución en curso, se omite. */
    public void ejecutarReconciliacion() {
        ejecutarProgramada(ModoSync.RECONCILIACION);
    }

    private void ejecutarProgramada(ModoSync modo) {
        if (!enEjecucion.compareAndSet(false, true)) {
            log.info("Sincronización programada ({}) omitida: ya hay una en curso.", modo);
            return;
        }
        ejecutarYLiberar(syncLogRepository.save(nuevaBitacora(null, modoEfectivo(modo))));
    }

    /** Sin marca todavía no se sabe desde dónde leer: la primera ejecución es completa. */
    private ModoSync modoEfectivo(ModoSync solicitado) {
        return solicitado == ModoSync.INCREMENTAL && ultimaMarcaUtc() == null ? ModoSync.COMPLETA : solicitado;
    }

    private LocalDateTime ultimaMarcaUtc() {
        return syncControlRepository.findById(ENTIDAD).map(SyncControl::getUltimaFechaModificacionFm).orElse(null);
    }

    private void ejecutarYLiberar(SyncLog bitacora) {
        try {
            ejecutar(bitacora);
        } finally {
            enEjecucion.set(false);
        }
    }

    void ejecutar(SyncLog bitacora) {
        Resumen resumen = new Resumen();
        try {
            CatalogoEstados estados = CatalogoEstados.de(
                    estadoPedidoRepository.findByActivoTrue(), estadoDetalleRepository.findByActivoTrue(),
                    estadosPedidoOmitidos, equivalenciasEstadoPedido, equivalenciasEstadoDetalle);

            Lectura lectura = leer(bitacora.getModo(), resumen);
            Map<String, ResumenLineaFm> conocidas = detalleRepository.findResumenesSincronizacion(fechaDesdeUtc).stream()
                    .collect(Collectors.toMap(ResumenLineaFm::filemakerId, Function.identity()));

            lectura.porPedido.forEach((numeroPedido, filas) -> {
                if (sinCambios(filas, conocidas)) {
                    return;
                }
                try {
                    SincronizadorPedidoFm.Resultado r = sincronizador.sincronizar(numeroPedido, filas, estados);
                    resumen.insertados += r.lineasInsertadas();
                    resumen.actualizados += r.lineasActualizadas();
                    resumen.omitidos += r.lineasOmitidas();
                    r.avisos().forEach(resumen::aviso);
                } catch (Exception e) {
                    // Pedido completo revertido: se reintenta en la siguiente ejecución (su huella no se guardó)
                    log.warn("No se pudo sincronizar el pedido {}: {}", numeroPedido, e.getMessage(), e);
                    resumen.pedidosFallidos++;
                    resumen.conError += filas.size();
                    resumen.error("Pedido " + numeroPedido + ": " + causaRaiz(e));
                }
            });

            if (lectura.completa) {
                resumen.eliminados = marcarEliminadas(lectura, conocidas);
            } else {
                resumen.aviso("No se marcaron líneas eliminadas: FileMaker no devolvió todos los registros esperados");
            }

            bitacora.setEstado(resumen.conError > 0 ? EstadoSync.PARCIAL : EstadoSync.EXITO);
            // Un pedido que falló se reintenta solo si la marca no lo deja atrás
            registrarEjecucionExitosa(resumen.pedidosFallidos == 0 ? lectura.nuevaMarcaUtc : null);
            log.info("Sincronización FileMaker {} {}: leídos={}, insertados={}, actualizados={}, eliminados={}, omitidos={}, con error={}",
                    bitacora.getModo(), bitacora.getEstado(), resumen.leidos, resumen.insertados, resumen.actualizados,
                    resumen.eliminados, resumen.omitidos, resumen.conError);
        } catch (Exception e) {
            log.error("Error en la sincronización con FileMaker: {}", e.getMessage(), e);
            bitacora.setEstado(EstadoSync.ERROR);
            resumen.error(causaRaiz(e));
        } finally {
            bitacora.setRegistrosLeidos(resumen.leidos);
            bitacora.setRegistrosInsertados(resumen.insertados);
            bitacora.setRegistrosActualizados(resumen.actualizados);
            bitacora.setRegistrosEliminados(resumen.eliminados);
            bitacora.setRegistrosOmitidos(resumen.omitidos);
            bitacora.setRegistrosConError(resumen.conError);
            bitacora.setMensajeError(resumen.errores());
            bitacora.setAvisos(resumen.avisos());
            bitacora.setFechaFin(FechaUtc.ahora());
            syncLogRepository.save(bitacora);
        }
    }

    private Lectura leer(ModoSync modo, Resumen resumen) {
        String token = apiClient.authenticate();
        try {
            return switch (modo) {
                case COMPLETA -> leerCompleta(token, resumen);
                case INCREMENTAL -> leerIncremental(token, resumen);
                case RECONCILIACION -> leerPedidos(token, new LinkedHashSet<>(pedidoRepository.findFilemakerIdsAbiertos()),
                        new Lectura(), resumen);
            };
        } finally {
            apiClient.logout(token);
        }
    }

    /**
     * Todas las líneas creadas desde la fecha de corte. FileMaker busca por día (funciona igual si el campo es
     * fecha o marca de tiempo); la hora exacta del corte se aplica al agrupar.
     */
    private Lectura leerCompleta(String token, Resumen resumen) {
        Lectura lectura = new Lectura();
        lectura.pedidosConsultados = null; // todo el rango: cualquier línea conocida que falte se evalúa
        Map<String, String> criterio = Map.of(FilaPedidoFmParser.FECHA_CREACION,
                ">=" + fechaDesdeColombia.format(FORMATO_FECHA_FM));
        leerBusqueda(token, List.of(criterio), lectura, resumen);
        lectura.nuevaMarcaUtc = lectura.maxModificacionUtc;
        return lectura;
    }

    /**
     * Paso 1: líneas modificadas desde la marca (solo se usa el número de pedido y la marca; la búsqueda por
     * modificación es rápida, a diferencia de la de fecha de creación). Paso 2: esos pedidos completos.
     */
    private Lectura leerIncremental(String token, Resumen resumen) {
        LocalDateTime desdeColombia = ultimaMarcaUtc().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(FilaPedidoFmParser.ZONA_FILEMAKER).toLocalDateTime()
                .minus(margenIncremental);
        Map<String, String> criterio = Map.of(FilaPedidoFmParser.FECHA_MODIFICACION,
                ">=" + desdeColombia.format(FORMATO_MARCA_FM));

        Set<String> pedidos = new LinkedHashSet<>();
        LocalDateTime[] marca = {null};
        boolean completa = recorrerPaginas(token, List.of(criterio), registro -> {
            String numero = FilaPedidoFmParser.numeroPedido(registro);
            LocalDateTime modificacion = FilaPedidoFmParser.fechaModificacionUtc(registro);
            if (modificacion != null && (marca[0] == null || modificacion.isAfter(marca[0]))) {
                marca[0] = modificacion;
            }
            // Pedidos anteriores al corte no se controlan: no se descargan
            LocalDateTime creacion = FilaPedidoFmParser.fechaCreacionUtc(registro);
            if (numero != null && (creacion == null || !creacion.isBefore(fechaDesdeUtc))) {
                pedidos.add(numero);
            }
        });

        Lectura lectura = leerPedidos(token, pedidos, new Lectura(), resumen);
        lectura.completa &= completa;
        lectura.nuevaMarcaUtc = completa ? marca[0] : null;
        return lectura;
    }

    /** Descarga las líneas de los pedidos indicados, por lotes de búsquedas exactas por número de pedido. */
    private Lectura leerPedidos(String token, Set<String> numerosPedido, Lectura lectura, Resumen resumen) {
        lectura.pedidosConsultados = numerosPedido;
        List<String> numeros = List.copyOf(numerosPedido);
        for (int i = 0; i < numeros.size(); i += LOTE_PEDIDOS_FM) {
            List<Map<String, String>> consultas = numeros.subList(i, Math.min(i + LOTE_PEDIDOS_FM, numeros.size()))
                    .stream()
                    .map(numero -> Map.of(FilaPedidoFmParser.NUM_PEDIDO, "==" + numero))
                    .toList();
            leerBusqueda(token, consultas, lectura, resumen);
        }
        return lectura;
    }

    /** Lee todas las páginas de una búsqueda. Los registros inválidos se cuentan como error y se omiten. */
    private void leerBusqueda(String token, List<Map<String, String>> consultas, Lectura lectura, Resumen resumen) {
        boolean completa = recorrerPaginas(token, consultas, registro -> {
            resumen.leidos++;
            lectura.recordIdsLeidos.add(registro.recordId());
            try {
                FilaPedidoFm fila = FilaPedidoFmParser.parsear(registro);
                if (fila.fechaModificacionUtc() != null && (lectura.maxModificacionUtc == null
                        || fila.fechaModificacionUtc().isAfter(lectura.maxModificacionUtc))) {
                    lectura.maxModificacionUtc = fila.fechaModificacionUtc();
                }
                if (fila.fechaCreacionUtc().isBefore(fechaDesdeUtc)) {
                    return;
                }
                lectura.porPedido.computeIfAbsent(fila.numeroPedido(), k -> new ArrayList<>()).add(fila);
            } catch (FilaInvalidaException e) {
                resumen.conError++;
                resumen.error("Registro " + registro.recordId() + ": " + e.getMessage());
            }
        });
        lectura.completa &= completa;
    }

    /** @return false si FileMaker entregó menos registros de los que dijo haber encontrado */
    private boolean recorrerPaginas(String token, List<Map<String, String>> consultas, Consumer<Registro> procesar) {
        int leidos = 0;
        int totalEsperado = 0;
        int offset = 1;
        while (true) {
            FileMakerRegistrosResponseDTO.Response pagina =
                    apiClient.buscarRegistros(token, layout, consultas, offset, tamanoPagina).response();
            List<Registro> registros = pagina.data() != null ? pagina.data() : List.of();
            if (pagina.dataInfo() != null && pagina.dataInfo().foundCount() != null) {
                totalEsperado = pagina.dataInfo().foundCount();
            }
            registros.forEach(procesar);
            leidos += registros.size();
            offset += registros.size();
            if (registros.isEmpty() || offset > totalEsperado) {
                return leidos >= totalEsperado;
            }
        }
    }

    private static boolean sinCambios(List<FilaPedidoFm> filas, Map<String, ResumenLineaFm> conocidas) {
        return filas.stream().allMatch(fila -> {
            ResumenLineaFm conocida = conocidas.get(fila.recordId());
            return conocida != null
                    && !Boolean.TRUE.equals(conocida.eliminadoEnFm())
                    && fila.huella().equals(conocida.huellaFm());
        });
    }

    /**
     * Marca las líneas que ya no están en FileMaker y los pedidos que se quedaron sin líneas activas.
     * Solo se evalúan las líneas de los pedidos consultados (todas en la lectura completa). Si FileMaker no devolvió
     * ningún registro no se infiere nada: es más probable un problema en FileMaker que un borrado total.
     */
    private int marcarEliminadas(Lectura lectura, Map<String, ResumenLineaFm> conocidas) {
        if (lectura.recordIdsLeidos.isEmpty()) {
            return 0;
        }
        List<String> desaparecidas = conocidas.values().stream()
                .filter(l -> !Boolean.TRUE.equals(l.eliminadoEnFm()))
                .filter(l -> lectura.pedidosConsultados == null || lectura.pedidosConsultados.contains(l.numeroPedidoFm()))
                .map(ResumenLineaFm::filemakerId)
                .filter(id -> !lectura.recordIdsLeidos.contains(id))
                .toList();
        if (desaparecidas.isEmpty()) {
            return 0;
        }
        LocalDateTime ahora = FechaUtc.ahora();
        Integer total = transactionTemplate.execute(status -> {
            int marcadas = 0;
            for (int i = 0; i < desaparecidas.size(); i += LOTE_ELIMINADAS) {
                marcadas += detalleRepository.marcarEliminadas(
                        desaparecidas.subList(i, Math.min(i + LOTE_ELIMINADAS, desaparecidas.size())), ahora);
            }
            pedidoRepository.marcarEliminadosSinLineasActivas(ahora);
            return marcadas;
        });
        return total != null ? total : 0;
    }

    /** @param nuevaMarcaUtc modificación más reciente leída; null = no mover la marca */
    private void registrarEjecucionExitosa(LocalDateTime nuevaMarcaUtc) {
        SyncControl control = syncControlRepository.findById(ENTIDAD).orElseGet(() -> {
            SyncControl nuevo = new SyncControl();
            nuevo.setEntidad(ENTIDAD);
            return nuevo;
        });
        control.setUltimaEjecucionExitosa(FechaUtc.ahora());
        // Nunca retrocede (ej. una lectura completa con datos más viejos que la marca)
        if (nuevaMarcaUtc != null && (control.getUltimaFechaModificacionFm() == null
                || nuevaMarcaUtc.isAfter(control.getUltimaFechaModificacionFm()))) {
            control.setUltimaFechaModificacionFm(nuevaMarcaUtc);
        }
        syncControlRepository.save(control);
    }

    private static SyncLog nuevaBitacora(String disparadoPor, ModoSync modo) {
        return SyncLog.builder()
                .entidad(ENTIDAD)
                .estado(EstadoSync.EN_PROCESO)
                .modo(modo)
                .disparadoPor(disparadoPor)
                .build();
    }

    private static String causaRaiz(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String mensaje = causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName();
        return mensaje.length() > 500 ? mensaje.substring(0, 500) + "…" : mensaje;
    }

    /** Lo leído de FileMaker en una ejecución. */
    private static final class Lectura {
        final Map<String, List<FilaPedidoFm>> porPedido = new LinkedHashMap<>();
        final Set<String> recordIdsLeidos = new HashSet<>();
        /** Pedidos cuyas líneas se leyeron todas; null = lectura completa del rango. */
        Set<String> pedidosConsultados;
        /** False si alguna búsqueda devolvió menos registros de los esperados: no se infieren eliminaciones. */
        boolean completa = true;
        LocalDateTime maxModificacionUtc;
        /** Marca a guardar al terminar; null = no moverla. */
        LocalDateTime nuevaMarcaUtc;
    }

    /** Contadores y mensajes de una ejecución (los mensajes se limitan para no inflar la bitácora). */
    private static final class Resumen {
        int leidos;
        int insertados;
        int actualizados;
        int eliminados;
        /** Líneas de pedidos nuevos que ya estaban en estado final en FileMaker. */
        int omitidos;
        int conError;
        int pedidosFallidos;
        private final List<String> errores = new ArrayList<>();
        private int erroresOmitidos;
        private final List<String> avisos = new ArrayList<>();
        private int avisosOmitidos;

        void error(String mensaje) {
            if (errores.size() < MAX_MENSAJES) {
                errores.add(mensaje);
            } else {
                erroresOmitidos++;
            }
        }

        void aviso(String mensaje) {
            if (avisos.size() < MAX_MENSAJES) {
                avisos.add(mensaje);
            } else {
                avisosOmitidos++;
            }
        }

        String errores() {
            return unir(errores, erroresOmitidos);
        }

        String avisos() {
            return unir(avisos, avisosOmitidos);
        }

        private static String unir(List<String> mensajes, int omitidos) {
            if (mensajes.isEmpty()) {
                return null;
            }
            String texto = String.join("\n", mensajes);
            return omitidos > 0 ? texto + "\n… y " + omitidos + " más" : texto;
        }
    }
}
