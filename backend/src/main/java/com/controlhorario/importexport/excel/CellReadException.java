package com.controlhorario.importexport.excel;

/** Una celda no contiene un valor del tipo esperado. El mensaje (en español) incluye la celda. */
public class CellReadException extends RuntimeException {

    public CellReadException(String message) {
        super(message);
    }
}
