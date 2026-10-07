package com.controlhorario.importexport;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import com.controlhorario.common.web.ValidationException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Comprobaciones de la subida antes de abrir el fichero con POI: tamaño (≤ 2 MB → si no, 413),
 * extensión .xlsx, tipo MIME permitido y firma zip {@code PK\x03\x04}. Las macros se comprueban al
 * abrir el paquete ({@link com.controlhorario.importexport.excel.ExcelWorkbookReader}).
 */
@Component
public class XlsxUploadValidator {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(XLSX_CONTENT_TYPE, "application/octet-stream");
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final int MAX_FILE_NAME = 255;

    /** Devuelve el contenido del fichero si pasa todas las comprobaciones. */
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "Selecciona un fichero .xlsx");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.trim().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ValidationException("file", "El fichero debe tener la extensión .xlsx");
        }
        if (!isAllowedContentType(file.getContentType())) {
            throw new ValidationException("file", "El tipo de fichero no corresponde a un Excel .xlsx");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("file", "No se ha podido leer el fichero");
        }
        if (content.length > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        if (!hasZipSignature(content)) {
            throw new ValidationException("file", "El fichero no es un Excel .xlsx válido");
        }
        return content;
    }

    static boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        // Solo cuenta el tipo/subtipo; los parámetros (";...") se ignoran.
        int parameters = contentType.indexOf(';');
        String essence = (parameters < 0 ? contentType : contentType.substring(0, parameters))
                .trim().toLowerCase(Locale.ROOT);
        return ALLOWED_CONTENT_TYPES.contains(essence);
    }

    static boolean hasZipSignature(byte[] content) {
        if (content.length < ZIP_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < ZIP_SIGNATURE.length; i++) {
            if (content[i] != ZIP_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** Nombre para mostrar: sin rutas (algunos navegadores envían la ruta completa) ni caracteres de control. */
    public static String displayName(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        String name = originalFilename.substring(Math.max(originalFilename.lastIndexOf('/'),
                originalFilename.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        return name.length() <= MAX_FILE_NAME ? name : name.substring(0, MAX_FILE_NAME);
    }
}
