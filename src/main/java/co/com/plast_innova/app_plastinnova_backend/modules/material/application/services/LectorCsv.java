package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV exportado del ERP: separado por comas, con comillas dobles para los valores que contienen comas o saltos de
 * línea ("" dentro de comillas es una comilla). La primera fila trae los nombres de las columnas. Los valores se
 * devuelven sin los espacios de relleno del ERP.
 */
final class LectorCsv {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final String archivo;
    private final Map<String, Integer> columnas = new HashMap<>();
    private final List<String[]> filas = new ArrayList<>();

    private LectorCsv(String archivo) {
        this.archivo = archivo;
    }

    /**
     * @param archivo nombre que se muestra en los errores, ej. "items.csv"
     * @param columnasRequeridas columnas que debe traer el encabezado (sin importar mayúsculas)
     */
    static LectorCsv leer(String archivo, byte[] contenido, String... columnasRequeridas) {
        LectorCsv lector = new LectorCsv(archivo);
        List<List<String>> registros = separar(decodificar(contenido));
        if (registros.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo " + archivo + " está vacío.");
        }
        List<String> encabezado = registros.getFirst();
        for (int i = 0; i < encabezado.size(); i++) {
            lector.columnas.put(encabezado.get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        for (String requerida : columnasRequeridas) {
            if (!lector.columnas.containsKey(requerida.toLowerCase(Locale.ROOT))) {
                throw new SolicitudInvalidaException("El archivo " + archivo + " no tiene la columna " + requerida
                        + ". Columnas encontradas: " + String.join(", ", encabezado) + ".");
            }
        }
        for (List<String> registro : registros.subList(1, registros.size())) {
            if (registro.stream().allMatch(String::isBlank)) {
                continue;
            }
            lector.filas.add(registro.stream().map(String::strip).toArray(String[]::new));
        }
        return lector;
    }

    String archivo() {
        return archivo;
    }

    int cantidadFilas() {
        return filas.size();
    }

    /** Valor de la columna en la fila (0 = primera fila de datos); null si viene vacío o la fila es más corta. */
    String valor(int fila, String columna) {
        String[] valores = filas.get(fila);
        int indice = columnas.get(columna.toLowerCase(Locale.ROOT));
        if (indice >= valores.length || valores[indice].isEmpty()) {
            return null;
        }
        return valores[indice];
    }

    /** Número de línea del archivo para los mensajes (el encabezado es la línea 1). */
    static int linea(int fila) {
        return fila + 2;
    }

    /** UTF-8 (con o sin BOM); si no es UTF-8 válido, el ERP lo exportó en Windows-1252. */
    private static String decodificar(byte[] contenido) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido))
                    .toString();
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (CharacterCodingException e) {
            return new String(contenido, WINDOWS_1252);
        }
    }

    private static List<List<String>> separar(String texto) {
        List<List<String>> registros = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ',') {
                actual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                actual.add(campo.toString());
                campo.setLength(0);
                registros.add(actual);
                actual = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        if (!campo.isEmpty() || !actual.isEmpty()) {
            actual.add(campo.toString());
            registros.add(actual);
        }
        return registros;
    }
}
