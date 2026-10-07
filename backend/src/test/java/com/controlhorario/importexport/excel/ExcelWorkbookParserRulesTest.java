package com.controlhorario.importexport.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Consumer;

import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Reglas de lectura con libros construidos a medida (junio de 2026: la fila 7 es el lunes 1). */
class ExcelWorkbookParserRulesTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @Test
    void handwrittenLunchBeforeTwoIsNotRepresentable() {
        ParsedDay day = singleDay(s -> {
            time(s, "B7", "07:00");
            time(s, "C7", "11:00");
            time(s, "D7", "11:20");
            time(s, "I7", "15:00");
            time(s, "J7", "00:30");
            text(s, "M7", "O");
        });
        assertThat(day.representable()).isFalse();
        assertThat(day.breaks()).containsExactly(new BreakInput(BreakType.DESAYUNO, t("11:00"), t("11:20")));
    }

    @Test
    void lunchWithTimesIgnoresTheHandwrittenTotal() {
        ParsedDay day = singleDay(s -> {
            time(s, "B7", "07:00");
            time(s, "E7", "14:00");
            time(s, "F7", "14:30");
            time(s, "I7", "17:00");
            time(s, "J7", "00:30");
            text(s, "M7", "O");
        });
        assertThat(day.representable()).isTrue();
        assertThat(day.breaks()).containsExactly(new BreakInput(BreakType.COMIDA, t("14:00"), t("14:30")));
    }

    @Test
    void incompleteBreakIsAnError() {
        ParsedDay day = singleDay(s -> {
            time(s, "B7", "07:00");
            time(s, "C7", "10:00");
            time(s, "I7", "15:00");
            text(s, "M7", "O");
        });
        assertThat(day.errors()).containsExactly("Pausa incompleta en C/D: falta el fin");
        assertThat(day.breaks()).isEmpty();
    }

    @Test
    void acceptsTextTimesAndDateTimesOf1900() {
        ParsedDay day = singleDay(s -> {
            text(s, "B7", "7:25");
            number(s, "I7", 1.75); // 1900-01-01 18:00
            text(s, "M7", "c");
        });
        assertThat(day.startTime()).isEqualTo(t("07:25"));
        assertThat(day.endTime()).isEqualTo(t("18:00"));
        assertThat(day.location()).isEqualTo(Location.CASA);
        assertThat(day.errors()).isEmpty();
    }

    @Test
    void roundsSecondsToTheNearestMinute() {
        ParsedDay day = singleDay(s -> {
            number(s, "B7", (7 * 3600 + 25 * 60 + 40) / 86_400.0); // 07:25:40
            number(s, "I7", (15 * 3600 + 10) / 86_400.0); // 15:00:10
            text(s, "M7", "O");
        });
        assertThat(day.startTime()).isEqualTo(t("07:26"));
        assertThat(day.endTime()).isEqualTo(t("15:00"));
    }

    @Test
    void emptyOrUnknownLocationIsImportedAsOfficeWithAMessage() {
        ParsedWorkbook parsed = parse(s -> {
            time(s, "B7", "07:00");
            time(s, "I7", "15:00");
            time(s, "B8", "07:00");
            time(s, "I8", "15:00");
            text(s, "M8", "X");
        });
        ParsedDay empty = parsed.days().get(0);
        ParsedDay unknown = parsed.days().get(1);
        assertThat(empty.location()).isEqualTo(Location.OFICINA);
        assertThat(empty.messages()).containsExactly("Ubicación vacía (M): se importa como OFICINA");
        assertThat(unknown.location()).isEqualTo(Location.OFICINA);
        assertThat(unknown.messages()).containsExactly("Ubicación «X» desconocida (M): se importa como OFICINA");
    }

    @Test
    void mixedLocationReadsTheRemoteStretchFromColumnsNAndO() {
        ParsedDay day = singleDay(s -> {
            time(s, "B7", "08:00");
            time(s, "I7", "16:00");
            text(s, "M7", "M");
            time(s, "N7", "13:00");
            time(s, "O7", "16:00");
        });
        assertThat(day.location()).isEqualTo(Location.MIXTO);
        assertThat(day.remoteMinutes()).isEqualTo(180);
    }

    @Test
    void unreadableCellsMakeTheDayInvalid() {
        ParsedDay day = singleDay(s -> {
            text(s, "B7", "abc");
            time(s, "I7", "15:00");
            text(s, "M7", "O");
        });
        assertThat(day.errors()).containsExactly("Celda B7: hora no válida («abc»)");
    }

    @Test
    void theDateOfEachRowComesFromItsPosition() {
        ParsedWorkbook parsed = parse(s -> {
            date(s, "A14", LocalDate.of(2025, 6, 8));
            time(s, "B14", "07:00");
            time(s, "I14", "15:00");
            text(s, "M14", "O");
        });
        ParsedSheet sheet = parsed.sheets().get(0);
        assertThat(sheet.month()).isEqualTo(JUNE);
        assertThat(sheet.rows()).isEqualTo(22);
        assertThat(sheet.dateCorrections()).isEqualTo(1);
        ParsedDay day = sheet.days().get(0);
        assertThat(day.date()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(day.messages()).containsExactly(
                "La columna A dice 08/06/2025: se usa el 08/06/2026 por la posición de la fila");
        assertThat(parsed.warnings()).containsExactly("Hoja: 1 fila tiene otra fecha en la columna A (fila 14); "
                + "se usa la fecha que corresponde a su posición en la hoja");
    }

    @Test
    void rowsWithoutEntryTimeAreNotWorkedDays() {
        ParsedWorkbook parsed = parse(s -> {
            time(s, "I7", "15:00");
            time(s, "V7", "07:00");
            text(s, "M7", "O");
        });
        assertThat(parsed.days()).isEmpty();
    }

    @Test
    void sheetsWithoutDatesAreNotMonthlySheets() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet summary = workbook.createSheet("Horas");
        time(summary, "B1", "08:00");
        time(summary, "C1", "07:00");
        number(summary, "O3", 22);
        number(summary, "Q8", 1700 / 24.0);
        Sheet notes = workbook.createSheet("Notas");
        text(notes, "A7", "lunes");
        fillMonth(workbook.createSheet("Junio"), JUNE);

        ParsedWorkbook parsed = ExcelWorkbookReader.read(TestWorkbooks.bytes(workbook), new ExcelWorkbookParser()::parse);

        assertThat(parsed.sheets()).extracting(ParsedSheet::name).containsExactly("Junio");
        assertThat(parsed.settings().normalDayMinutes()).isEqualTo(480);
        assertThat(parsed.settings().intensiveDayMinutes()).isEqualTo(420);
        assertThat(parsed.settings().vacationDays()).isEqualTo(22);
        assertThat(parsed.settings().agreementMinutes()).isEqualTo(102_000);
    }

    @Test
    void settingsComeFromTheMonthlyHeadersWhenThereIsNoHorasSheet() {
        ParsedWorkbook parsed = parse(s -> {
            time(s, "F1", "00:30");
            time(s, "F2", "00:15");
            number(s, "F3", 40);
            number(s, "J1", 10);
            time(s, "J2", "08:00");
            number(s, "L1", 12);
            time(s, "L2", "07:00");
        });
        assertThat(parsed.settings()).isEqualTo(new DetectedSettings(15, 30, 40, 480, 420, null, null));
    }

    @Test
    void rangesAreCompacted() {
        assertThat(ExcelWorkbookParser.ranges(List.of(21, 22, 23, 24, 25, 30, 32, 33))).isEqualTo("21-25, 30, 32-33");
    }

    // ------------------------------------------------------------------ utilidades

    private static ParsedDay singleDay(Consumer<Sheet> filler) {
        ParsedWorkbook parsed = parse(filler);
        assertThat(parsed.days()).hasSize(1);
        return parsed.days().get(0);
    }

    private static ParsedWorkbook parse(Consumer<Sheet> filler) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Hoja");
        fillMonth(sheet, JUNE);
        filler.accept(sheet);
        return ExcelWorkbookReader.read(TestWorkbooks.bytes(workbook), new ExcelWorkbookParser()::parse);
    }

    /** Fechas de la columna A de todas las filas de datos del mes, como en la plantilla. */
    private static void fillMonth(Sheet sheet, YearMonth month) {
        for (int row : ExcelLayout.dataRows()) {
            LocalDate date = ExcelLayout.dateOf(month, row);
            if (YearMonth.from(date).equals(month)) {
                date(sheet, "A" + row, date);
            }
        }
    }

    private static void date(Sheet sheet, String ref, LocalDate value) {
        Cell cell = cell(sheet, ref);
        cell.setCellValue(value);
        cell.setCellStyle(style(sheet, "dd/mm/yyyy"));
    }

    private static void time(Sheet sheet, String ref, String hhmm) {
        Cell cell = cell(sheet, ref);
        LocalTime time = t(hhmm);
        cell.setCellValue((time.getHour() * 60 + time.getMinute()) / 1440.0);
        cell.setCellStyle(style(sheet, "h:mm"));
    }

    private static void number(Sheet sheet, String ref, double value) {
        cell(sheet, ref).setCellValue(value);
    }

    private static void text(Sheet sheet, String ref, String value) {
        cell(sheet, ref).setCellValue(value);
    }

    private static CellStyle style(Sheet sheet, String format) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat(format));
        return style;
    }

    private static Cell cell(Sheet sheet, String ref) {
        CellReference reference = new CellReference(ref);
        Row row = sheet.getRow(reference.getRow());
        if (row == null) {
            row = sheet.createRow(reference.getRow());
        }
        return row.createCell(reference.getCol());
    }

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }
}
