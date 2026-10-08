package com.controlhorario.importexport.dto;

/**
 * Recuento de la importación. {@code toImport + skippedFuture + skippedExisting + skippedOutOfPeriod + invalid}
 * es el número de días con fichaje del fichero.
 *
 * @param toImport           días con acción IMPORT
 * @param imported           días guardados (0 en el dry-run)
 * @param skippedFuture      días futuros que no se importan
 * @param skippedExisting    días que ya tenían registro y no se sobrescriben
 * @param skippedOutOfPeriod días fuera del periodo
 * @param invalid            días INVALID o NOT_REPRESENTABLE
 * @param mismatches         días cuyo cálculo no coincide con el Total Día del Excel
 * @param vacationsToCreate  ausencias con acción IMPORT: vacaciones deducidas del Excel de la empresa o
 *                           ausencias escritas en un Excel exportado por la app
 * @param vacationsCreated   ausencias guardadas (0 en el dry-run)
 */
public record ImportCountsDto(
        int toImport,
        int imported,
        int skippedFuture,
        int skippedExisting,
        int skippedOutOfPeriod,
        int invalid,
        int mismatches,
        int vacationsToCreate,
        int vacationsCreated) {
}
