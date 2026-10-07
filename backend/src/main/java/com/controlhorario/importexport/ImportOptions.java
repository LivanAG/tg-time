package com.controlhorario.importexport;

import java.util.UUID;

/**
 * Opciones de POST /api/import/xlsx.
 *
 * @param dryRun        solo vista previa, no guarda nada (por defecto true)
 * @param periodId      periodo elegido; null = el que contiene más fechas del fichero
 * @param includeFuture importar también los días posteriores a hoy (por defecto false)
 * @param markVacations crear las vacaciones deducidas (por defecto true)
 * @param overwrite     sustituir los días que ya tienen registro (por defecto false)
 */
public record ImportOptions(
        boolean dryRun,
        UUID periodId,
        boolean includeFuture,
        boolean markVacations,
        boolean overwrite) {
}
