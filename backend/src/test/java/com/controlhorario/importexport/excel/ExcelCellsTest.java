package com.controlhorario.importexport.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExcelCellsTest {

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Sheet sheet = workbook.createSheet("Hoja");
    private int column;

    @AfterEach
    void close() throws Exception {
        workbook.close();
    }

    private Cell newCell() {
        return sheet.createRow(column).createCell(column++);
    }

    @Test
    void parsesClockTexts() {
        assertThat(ExcelCells.parseClock("7:25")).isEqualTo(445);
        assertThat(ExcelCells.parseClock("07:25:29")).isEqualTo(445);
        assertThat(ExcelCells.parseClock("07:25:30")).isEqualTo(446);
        assertThat(ExcelCells.parseClock("0:00:00")).isZero();
        assertThat(ExcelCells.parseClock("-0:30")).isEqualTo(-30);
        assertThat(ExcelCells.parseClock("1760:00:00")).isEqualTo(105_600);
        assertThat(ExcelCells.parseClock("7:75")).isNull();
        assertThat(ExcelCells.parseClock("siete")).isNull();
    }

    @Test
    void timesOfDayAreFractionsOfADay() {
        Cell cell = newCell();
        cell.setCellValue(445 / 1440.0);
        assertThat(ExcelCells.timeOfDay(cell)).isEqualTo(445);

        Cell almostMidnight = newCell();
        almostMidnight.setCellValue(0.99999);
        assertThat(ExcelCells.timeOfDay(almostMidnight)).isEqualTo(1439);

        Cell text = newCell();
        text.setCellValue("25:00");
        assertThatThrownBy(() -> ExcelCells.timeOfDay(text)).isInstanceOf(CellReadException.class)
                .hasMessageContaining("hora no válida");

        assertThat(ExcelCells.timeOfDay(null)).isNull();
    }

    @Test
    void durationsUseTheTimePartOfDateTimes() {
        Cell duration = newCell();
        duration.setCellValue(30 / 1440.0);
        assertThat(ExcelCells.durationMinutes(duration)).isEqualTo(30);

        Cell dateTime = newCell();
        dateTime.setCellValue(1 + 30 / 1440.0);
        assertThat(ExcelCells.durationMinutes(dateTime)).isEqualTo(30);
        assertThat(ExcelCells.totalMinutes(dateTime)).isEqualTo(1470);

        Cell text = newCell();
        text.setCellValue("0:00:00");
        assertThat(ExcelCells.durationMinutes(text)).isZero();
    }

    @Test
    void cachedFormulaResultsAreReadWithoutEvaluating() {
        Cell formula = newCell();
        formula.setCellFormula("1/0");
        // Sin valor cacheado (nunca se evalúa): se lee como número 0.
        assertThat(ExcelCells.durationMinutes(formula)).isZero();
    }

    @Test
    void datesNeedADateFormat() {
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
        Cell date = newCell();
        date.setCellValue(LocalDate.of(2026, 6, 1));
        date.setCellStyle(dateStyle);
        Cell plainNumber = newCell();
        plainNumber.setCellValue(46174);

        assertThat(ExcelCells.date(date, false)).contains(LocalDate.of(2026, 6, 1));
        assertThat(ExcelCells.date(plainNumber, false)).isEmpty();
    }

    @Test
    void percentagesWithPercentFormatAreScaled() {
        CellStyle percent = workbook.createCellStyle();
        percent.setDataFormat(workbook.createDataFormat().getFormat("0%"));
        Cell cell = newCell();
        cell.setCellValue(0.5);
        cell.setCellStyle(percent);
        assertThat(ExcelCells.integer(cell)).isEqualTo(50);
    }
}
