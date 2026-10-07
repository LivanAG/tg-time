package com.controlhorario.importexport.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abre un .xlsx subido por el usuario de forma segura:
 * <ul>
 *   <li>primera pasada por el zip con límites de entradas y de tamaño total descomprimido;</li>
 *   <li>POI endurecido contra bombas zip: ratio mínimo de inflado, tamaño máximo descomprimido por
 *       entrada y número máximo de entradas;</li>
 *   <li>rechaza los ficheros con macros (entrada {@code xl/vbaProject.bin} o tipo de contenido
 *       {@code macroEnabled}) antes de leer las hojas, tanto en el zip como en el paquete OPC;</li>
 *   <li>el libro se abre solo para leer: nunca se evalúan fórmulas ni macros y no se guarda nada.</li>
 * </ul>
 */
public final class ExcelWorkbookReader {

    /** Un .xlsx comprimido no puede inflarse más de 100 veces (bomba zip). */
    public static final double MIN_INFLATE_RATIO = 0.01;
    /** Tamaño máximo descomprimido de cada entrada del zip. */
    public static final long MAX_ENTRY_SIZE = 100L * 1024 * 1024;
    /** Número máximo de entradas del zip. */
    public static final long MAX_FILE_COUNT = 1000;
    /**
     * Tamaño total descomprimido. El Excel real ocupa 1,1 MB descomprimido; el límite evita que un
     * fichero de 2 MB se convierta en cientos de MB en memoria al construir el libro.
     */
    public static final long MAX_TOTAL_UNCOMPRESSED = 20L * 1024 * 1024;

    public static final String INVALID_FILE = "El fichero no es un Excel .xlsx válido o está dañado";
    public static final String MACROS = "El fichero contiene macros: guárdalo como libro de Excel (.xlsx) sin macros";
    public static final String TOO_LARGE = "El contenido del Excel es demasiado grande una vez descomprimido";
    public static final String UNREADABLE = "No se ha podido leer el contenido del Excel";

    private static final String CONTENT_TYPES = "[content_types].xml";

    private static final Logger log = LoggerFactory.getLogger(ExcelWorkbookReader.class);

    static {
        harden();
    }

    private ExcelWorkbookReader() {
    }

    /** Límites de POI (estáticos y globales). Idempotente. */
    public static synchronized void harden() {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE);
        ZipSecureFile.setMaxFileCount(MAX_FILE_COUNT);
    }

    /**
     * Abre el libro, comprueba que no tiene macros y aplica {@code reader}. El paquete se descarta al
     * terminar sin guardar cambios.
     *
     * @throws ExcelFileException si no es un .xlsx válido, tiene macros o no se puede leer
     */
    public static <T> T read(byte[] content, Function<Workbook, T> reader) {
        checkContainer(content);
        OPCPackage pkg;
        try {
            pkg = OPCPackage.open(new ByteArrayInputStream(content));
        } catch (InvalidFormatException | IOException | RuntimeException e) {
            log.debug("Excel no válido", e);
            throw new ExcelFileException(INVALID_FILE, e);
        }
        try {
            if (hasMacros(pkg)) {
                throw new ExcelFileException(MACROS);
            }
            XSSFWorkbook workbook;
            try {
                workbook = new XSSFWorkbook(pkg);
            } catch (IOException | RuntimeException e) {
                log.debug("Excel no válido", e);
                throw new ExcelFileException(INVALID_FILE, e);
            }
            try {
                return reader.apply(workbook);
            } catch (ExcelFileException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("No se ha podido leer el Excel subido", e);
                throw new ExcelFileException(UNREADABLE, e);
            }
        } finally {
            pkg.revert();
        }
    }

    /**
     * Primera pasada sobre el zip, antes de que POI cargue nada en memoria: número de entradas, tamaño
     * total descomprimido (se deja de descomprimir al pasar el límite, así que el coste está acotado) y
     * macros (entrada {@code vbaProject.bin} o {@code macroEnabled} en {@code [Content_Types].xml}).
     */
    static void checkContainer(byte[] content) {
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(content),
                StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_FILE_COUNT) {
                    throw new ExcelFileException(INVALID_FILE);
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith("vbaproject.bin")) {
                    throw new ExcelFileException(MACROS);
                }
                boolean contentTypes = name.equals(CONTENT_TYPES);
                ByteArrayOutputStream types = contentTypes ? new ByteArrayOutputStream() : null;
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_TOTAL_UNCOMPRESSED) {
                        throw new ExcelFileException(TOO_LARGE);
                    }
                    if (types != null) {
                        types.write(buffer, 0, n);
                    }
                }
                if (types != null && types.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT)
                        .contains("macroenabled")) {
                    throw new ExcelFileException(MACROS);
                }
            }
        } catch (ExcelFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.debug("Excel no válido", e);
            throw new ExcelFileException(INVALID_FILE, e);
        }
        if (entries == 0) {
            throw new ExcelFileException(INVALID_FILE);
        }
    }

    /** Parte {@code vbaProject.bin} o cualquier tipo de contenido de macros (xlsm, xltm, vbaProject...). */
    static boolean hasMacros(OPCPackage pkg) {
        try {
            for (PackagePart part : pkg.getParts()) {
                String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
                String type = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
                if (name.endsWith("vbaproject.bin") || name.endsWith("vbadata.xml")
                        || type.contains("macroenabled") || type.contains("vbaproject")) {
                    return true;
                }
            }
            return false;
        } catch (InvalidFormatException | RuntimeException e) {
            log.debug("Excel no válido", e);
            throw new ExcelFileException(INVALID_FILE, e);
        }
    }
}
