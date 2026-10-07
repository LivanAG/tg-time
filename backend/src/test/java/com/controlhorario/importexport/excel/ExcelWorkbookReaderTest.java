package com.controlhorario.importexport.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;

class ExcelWorkbookReaderTest {

    @Test
    void hardensPoiAgainstZipBombs() {
        ExcelWorkbookReader.harden();
        assertThat(ZipSecureFile.getMinInflateRatio()).isEqualTo(ExcelWorkbookReader.MIN_INFLATE_RATIO);
        assertThat(ZipSecureFile.getMaxEntrySize()).isEqualTo(100L * 1024 * 1024);
        assertThat(ZipSecureFile.getMaxFileCount()).isEqualTo(ExcelWorkbookReader.MAX_FILE_COUNT);
    }

    @Test
    void readsAPlainWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Junio");
        String name = ExcelWorkbookReader.read(TestWorkbooks.bytes(workbook), wb -> wb.getSheetAt(0).getSheetName());
        assertThat(name).isEqualTo("Junio");
    }

    @Test
    void rejectsAWorkbookWithAVbaProject() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Junio");
        byte[] withMacros = TestWorkbooks.withVbaProject(TestWorkbooks.bytes(workbook));

        assertThatThrownBy(() -> ExcelWorkbookReader.read(withMacros, wb -> wb))
                .isInstanceOf(ExcelFileException.class)
                .hasMessage(ExcelWorkbookReader.MACROS);
    }

    @Test
    void rejectsAMacroEnabledWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook(XSSFWorkbookType.XLSM);
        workbook.createSheet("Junio");
        byte[] xlsm = TestWorkbooks.bytes(workbook);

        assertThatThrownBy(() -> ExcelWorkbookReader.read(xlsm, wb -> wb))
                .isInstanceOf(ExcelFileException.class)
                .hasMessage(ExcelWorkbookReader.MACROS);
    }

    @Test
    void rejectsWorkbooksThatAreTooLargeOnceDecompressed() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Junio");
        byte[] padding = new byte[(int) ExcelWorkbookReader.MAX_TOTAL_UNCOMPRESSED + 1];
        byte[] bomb = TestWorkbooks.withEntry(TestWorkbooks.bytes(workbook), "xl/media/relleno.bin", padding);

        assertThat(bomb.length).isLessThan(2 * 1024 * 1024);
        assertThatThrownBy(() -> ExcelWorkbookReader.read(bomb, wb -> wb))
                .isInstanceOf(ExcelFileException.class)
                .hasMessage(ExcelWorkbookReader.TOO_LARGE);
    }

    @Test
    void theRealExcelPassesTheContainerChecks() {
        ExcelWorkbookReader.checkContainer(TestWorkbooks.realExcel());
    }

    @Test
    void rejectsContentThatIsNotAnOfficeZip() {
        byte[] text = "PK\u0003\u0004 esto no es un zip".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> ExcelWorkbookReader.read(text, wb -> wb))
                .isInstanceOf(ExcelFileException.class)
                .hasMessage(ExcelWorkbookReader.INVALID_FILE);
    }
}
