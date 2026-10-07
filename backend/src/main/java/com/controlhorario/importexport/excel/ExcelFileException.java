package com.controlhorario.importexport.excel;

/** El fichero no se puede procesar (no es un .xlsx válido, está dañado o tiene macros). Mensaje en español. */
public class ExcelFileException extends RuntimeException {

    public ExcelFileException(String message) {
        super(message);
    }

    public ExcelFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
