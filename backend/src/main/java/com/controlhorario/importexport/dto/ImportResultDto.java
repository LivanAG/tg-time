package com.controlhorario.importexport.dto;

import java.util.List;
import java.util.UUID;

/** Respuesta de POST /api/import/xlsx (la misma en el dry-run y al confirmar). */
public record ImportResultDto(
        boolean dryRun,
        String fileName,
        UUID periodId,
        List<ImportSheetDto> sheets,
        List<ImportDayDto> days,
        List<ImportAbsenceDto> absences,
        DetectedSettingsDto detectedSettings,
        List<String> warnings,
        ImportCountsDto counts) {
}
