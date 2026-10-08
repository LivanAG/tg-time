package com.controlhorario.importexport;

import java.util.UUID;

import com.controlhorario.common.security.CurrentUser;
import com.controlhorario.importexport.dto.ImportResultDto;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Importar el Excel actual (vista previa y confirmación) y exportar un mes. Ver docs/API.md. */
@RestController
@RequestMapping("/api")
public class ImportExportController {

    static final MediaType XLSX = MediaType.parseMediaType(XlsxUploadValidator.XLSX_CONTENT_TYPE);

    private final ImportService importService;
    private final ExportService exportService;
    private final XlsxUploadValidator uploadValidator;
    private final CurrentUser currentUser;

    public ImportExportController(ImportService importService, ExportService exportService,
            XlsxUploadValidator uploadValidator, CurrentUser currentUser) {
        this.importService = importService;
        this.exportService = exportService;
        this.uploadValidator = uploadValidator;
        this.currentUser = currentUser;
    }

    @PostMapping(path = "/import/xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResultDto importXlsx(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(required = false) UUID periodId,
            @RequestParam(defaultValue = "false") boolean includeFuture,
            @RequestParam(defaultValue = "true") boolean markVacations,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        UUID userId = currentUser.id();
        byte[] content = uploadValidator.validate(file);
        ImportOptions options = new ImportOptions(dryRun, periodId, includeFuture, markVacations, overwrite);
        return importService.importXlsx(userId, XlsxUploadValidator.displayName(file.getOriginalFilename()), content,
                options);
    }

    @GetMapping("/export/xlsx")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) UUID periodId) {
        ExportService.ExportedFile file = exportService.exportMonth(currentUser.id(), year, month, periodId);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
