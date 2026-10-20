package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.mapper;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FilaPedidoFm;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.FileMakerRegistrosResponseDTO.Registro;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convierte un registro del layout "PedidosLineas-Api" en una {@link FilaPedidoFm} validada.
 * Lanza {@link FilaInvalidaException} cuando falta un dato obligatorio o un valor no se puede interpretar.
 */
public final class FilaPedidoFmParser {

    public static final String FECHA_CREACION = "LinPed_FechaCreacion";
    /** Marca de modificación del registro en FileMaker. Opcional: vacía = sin dato. */
    public static final String FECHA_MODIFICACION = "zMarcaFechaYHoraModificación";
    public static final String ALISTAMIENTO = "LinPed_AlistamArea";
    public static final String ESTADO_LINEA = "LinPed_Estado";
    public static final String POSICION = "LinPed_PosPed";
    public static final String COD_PRODUCTO = "LinPed_CodPT";
    public static final String CANTIDAD = "LinPed_CantPedido";
    public static final String NUM_PEDIDO = "LinPed_NumPedido";
    public static final String CLIENTE = "LinPed_Cliente";
    public static final String ANEXO = "LinPed_Anexo";
    // Campos de tablas relacionadas: la Data API los nombra "OcurrenciaDeTabla::Campo"
    public static final String ESTADO_PEDIDO = "Pedidos::Ped_Estado";
    public static final String CIUDAD = "_Clientes_LnsPeds_CodCliente::Cliente_Ciudad";
    public static final String PAIS = "_Clientes_LnsPeds_CodCliente::Cliente_Pais";
    public static final String VENDEDOR = "_Clientes_LnsPeds_CodCliente::Cliente_Distribuidor";

    /** FileMaker guarda la hora local de la planta. */
    public static final ZoneId ZONA_FILEMAKER = FechaUtc.ZONA_COLOMBIA;
    // Formatos de la Data API de FileMaker (independientes del idioma del archivo)
    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private FilaPedidoFmParser() {
    }

    public static FilaPedidoFm parsear(Registro registro) {
        Map<String, Object> originales = originales(registro);
        Map<String, Object> campos = normalizados(originales);
        String fechaModificacion = texto(campos, FECHA_MODIFICACION);

        return new FilaPedidoFm(
                registro.recordId(),
                aEntero(registro.modId(), "modId"),
                obligatorio(texto(campos, NUM_PEDIDO), NUM_PEDIDO),
                aEntero(campos.get(POSICION), POSICION),
                obligatorio(texto(campos, COD_PRODUCTO), COD_PRODUCTO),
                cantidad(campos.get(CANTIDAD)),
                fechaUtc(obligatorio(texto(campos, FECHA_CREACION), FECHA_CREACION), FECHA_CREACION),
                fechaModificacion != null ? fechaUtc(fechaModificacion, FECHA_MODIFICACION) : null,
                tipoAlistamiento(texto(campos, ALISTAMIENTO)),
                esAnexo(texto(campos, ANEXO)),
                texto(campos, ESTADO_LINEA),
                texto(campos, ESTADO_PEDIDO),
                obligatorio(texto(campos, CLIENTE), CLIENTE),
                texto(campos, VENDEDOR),
                texto(campos, CIUDAD),
                texto(campos, PAIS),
                huella(originales)
        );
    }

    /** Número de pedido sin validar el resto del registro; null si viene vacío. */
    public static String numeroPedido(Registro registro) {
        return texto(normalizados(originales(registro)), NUM_PEDIDO);
    }

    /** Marca de modificación en UTC sin validar el resto del registro; null si viene vacía o no se puede leer. */
    public static LocalDateTime fechaModificacionUtc(Registro registro) {
        return fechaUtcOpcional(texto(normalizados(originales(registro)), FECHA_MODIFICACION));
    }

    /** Fecha de creación en UTC sin validar el resto del registro; null si viene vacía o no se puede leer. */
    public static LocalDateTime fechaCreacionUtc(Registro registro) {
        return fechaUtcOpcional(texto(normalizados(originales(registro)), FECHA_CREACION));
    }

    private static LocalDateTime fechaUtcOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return fechaUtc(valor, "");
        } catch (FilaInvalidaException e) {
            return null;
        }
    }

    private static Map<String, Object> originales(Registro registro) {
        return registro.fieldData() != null ? registro.fieldData() : Map.of();
    }

    /** Los nombres con tilde pueden llegar con la tilde como carácter aparte (NFD): se comparan normalizados. */
    private static Map<String, Object> normalizados(Map<String, Object> originales) {
        Map<String, Object> campos = new HashMap<>();
        originales.forEach((nombre, valor) -> campos.put(Normalizer.normalize(nombre, Normalizer.Form.NFC), valor));
        return campos;
    }

    private static String texto(Map<String, Object> campos, String nombre) {
        Object valor = campos.get(nombre);
        return valor == null ? null : Texto.limpiar(valor.toString());
    }

    private static String obligatorio(String valor, String campo) {
        if (valor == null) {
            throw new FilaInvalidaException(campo + " está vacío");
        }
        return valor;
    }

    private static Integer aEntero(Object valor, String campo) {
        String texto = valor == null ? null : Texto.limpiar(valor.toString());
        if (texto == null) {
            return null;
        }
        try {
            return new BigDecimal(texto).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new FilaInvalidaException(campo + " no es un número entero: '" + texto + "'");
        }
    }

    private static int cantidad(Object valor) {
        Integer cantidad = aEntero(valor, CANTIDAD);
        if (cantidad == null) {
            throw new FilaInvalidaException(CANTIDAD + " está vacío");
        }
        if (cantidad < 0) {
            throw new FilaInvalidaException(CANTIDAD + " es negativa: " + cantidad);
        }
        return cantidad;
    }

    static LocalDateTime fechaUtc(String valor, String campo) {
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(valor, FORMATO_TIMESTAMP);
        } catch (DateTimeParseException e) {
            try {
                local = LocalDate.parse(valor, FORMATO_FECHA).atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new FilaInvalidaException(campo + " no tiene un formato válido (MM/dd/yyyy): '" + valor + "'");
            }
        }
        return local.atZone(ZONA_FILEMAKER).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * FileMaker maneja varias áreas (Nacionales, Internacionales, PuntoDeVenta, TapaTuerca, Locales...).
     * Solo "Internacional(es)" es internacional; cualquier otra, incluso vacía, se toma como nacional.
     */
    private static TipoAlistamiento tipoAlistamiento(String valor) {
        String clave = Texto.clave(valor);
        return "INTERNACIONAL".equals(clave) || "INTERNACIONALES".equals(clave)
                ? TipoAlistamiento.INTERNACIONAL
                : TipoAlistamiento.NACIONAL;
    }

    /** FileMaker envía "SI" / "No": solo "Sí" marca la línea como anexo; vacío u otro valor = no es anexo. */
    private static boolean esAnexo(String valor) {
        return "SI".equals(Texto.clave(valor));
    }

    /** SHA-256 de los campos ordenados por nombre: cambia si cambia cualquier valor del layout. */
    private static String huella(Map<String, Object> campos) {
        StringBuilder contenido = new StringBuilder();
        new TreeMap<>(campos).forEach((k, v) -> contenido.append(k).append('=').append(v).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class FilaInvalidaException extends RuntimeException {
        public FilaInvalidaException(String mensaje) {
            super(mensaje);
        }
    }
}
