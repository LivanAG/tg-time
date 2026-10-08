package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelReportLayout.FIRST_DATA_ROW;
import static com.controlhorario.importexport.excel.ExcelReportLayout.GROUP_HEADER_ROW;
import static com.controlhorario.importexport.excel.ExcelReportLayout.HEADER_ROW;
import static com.controlhorario.importexport.excel.ExcelSheets.*;
import static com.controlhorario.importexport.excel.ExcelStyles.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.summary.calc.MonthRow;
import com.controlhorario.summary.calc.MonthStatus;
import com.controlhorario.summary.calc.PeriodSummary;
import com.controlhorario.summary.calc.VacationSummary;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Libro de un periodo completo (docs/EXCEL.md): una hoja "Resumen" con la tabla por meses y los saldos de la
 * pantalla Resumen, y después una hoja por mes con el mismo diseño que {@link ExcelMonthWriter} (que la
 * importación vuelve a leer; la hoja Resumen la ignora). Valores calculados, sin fórmulas.
 */
public final class ExcelPeriodWriter {

    /** Nombre de la primera hoja. */
    public static final String SUMMARY_SHEET = "Resumen";

    /**
     * Datos del periodo.
     *
     * @param name       nombre del periodo, para el título
     * @param periodName nombre y fechas, p. ej. "2026-2027 (26/05/2026 – 25/05/2027)"
     * @param summary    resumen del periodo (PeriodSummaryService): meses, margen, vacaciones y saldos
     * @param months     hojas de cada mes, en orden
     */
    public record PeriodData(String userName, String company, String name, String periodName, PeriodRules rules,
            PeriodSummary summary, List<ExcelMonthWriter.MonthData> months) {
    }

    // Columnas de la tabla por meses.
    private static final int COL_MONTH = 0;
    private static final int COL_DAYS = 1;
    private static final int COL_NORMAL = 2;
    private static final int COL_INTENSIVE = 3;
    private static final int COL_CALENDAR = 4;
    private static final int COL_VACATION_DAYS = 5;
    private static final int COL_VACATION_HOURS = 6;
    private static final int COL_THEORETICAL = 7;
    private static final int COL_WORKED = 8;
    private static final int COL_DIFFERENCE = 9;
    private static final int COL_BRIDGES = 10;
    private static final int COL_BALANCE = 11;
    private static final int LAST_COL = COL_BALANCE;
    private static final int[] WIDTHS = {18, 10, 10, 10, 12, 9, 9, 11, 11, 11, 10, 15};

    // Bloques bajo la tabla: Margen (etiqueta A:C, valor D:E) y Vacaciones y horas (etiqueta G:J, valor K:L).
    private static final int LEFT_LABEL = 0;
    private static final int LEFT_LABEL_END = 2;
    private static final int LEFT_VALUE = 3;
    private static final int LEFT_VALUE_END = 4;
    private static final int RIGHT_LABEL = 6;
    private static final int RIGHT_LABEL_END = 9;
    private static final int RIGHT_VALUE = 10;
    private static final int RIGHT_VALUE_END = 11;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExcelMonthWriter monthWriter = new ExcelMonthWriter();

    public byte[] write(PeriodData data) {
        return workbook((workbook, styles) -> {
            writeSummarySheet(workbook, styles, data);
            for (ExcelMonthWriter.MonthData month : data.months()) {
                monthWriter.addSheet(workbook, styles, month);
            }
        });
    }

    private static void writeSummarySheet(XSSFWorkbook workbook, ExcelStyles styles, PeriodData data) {
        Sheet sheet = workbook.createSheet(SUMMARY_SHEET);
        PeriodSummary s = data.summary();
        writeHeader(sheet, styles, "Resumen del periodo · " + data.name(), data.userName(), data.company(),
                data.periodName(), data.rules());
        writeTableHeader(sheet, styles);
        int r = FIRST_DATA_ROW;
        for (MonthRow month : s.months()) {
            writeMonth(sheet, styles, row(sheet, r++), month);
        }
        writeTotal(sheet, styles, row(sheet, r), s);
        int end = writeBoxes(sheet, styles, s, r + 2);
        writeFootnote(sheet, styles, s, end + 1);
        writeWarnings(sheet, styles, s.warnings(), end + 3);
        setColumnWidths(sheet, WIDTHS);
        setUpPrint(sheet, (short) 1);
    }

    private static void writeTableHeader(Sheet sheet, ExcelStyles styles) {
        Row group = row(sheet, GROUP_HEADER_ROW);
        Row sub = row(sheet, HEADER_ROW);
        group.setHeightInPoints(20);
        sub.setHeightInPoints(18);
        for (int c = 0; c <= LAST_COL; c++) {
            text(group, c, null, styles.header());
            text(sub, c, null, styles.header());
        }
        Object[][] single = {
            {COL_MONTH, "Mes"}, {COL_CALENDAR, "Horas del mes"}, {COL_THEORETICAL, "Teóricas"},
            {COL_WORKED, "Hechas"}, {COL_DIFFERENCE, "Diferencia"}, {COL_BRIDGES, "Puentes"},
            {COL_BALANCE, "Saldo acumulado"}};
        for (Object[] h : single) {
            int c = (int) h[0];
            text(group, c, (String) h[1], styles.header());
            sheet.addMergedRegion(new CellRangeAddress(GROUP_HEADER_ROW, HEADER_ROW, c, c));
        }
        text(group, COL_DAYS, "Días laborables", styles.header());
        sheet.addMergedRegion(new CellRangeAddress(GROUP_HEADER_ROW, GROUP_HEADER_ROW, COL_DAYS, COL_INTENSIVE));
        text(sub, COL_DAYS, "Total", styles.header());
        text(sub, COL_NORMAL, "Normal", styles.header());
        text(sub, COL_INTENSIVE, "Intensiva", styles.header());
        text(group, COL_VACATION_DAYS, "Vacaciones", styles.header());
        sheet.addMergedRegion(new CellRangeAddress(GROUP_HEADER_ROW, GROUP_HEADER_ROW, COL_VACATION_DAYS,
                COL_VACATION_HOURS));
        text(sub, COL_VACATION_DAYS, "Días", styles.header());
        text(sub, COL_VACATION_HOURS, "Horas", styles.header());
    }

    /** Un mes: el actual resaltado, los futuros atenuados; el nombre enlaza con su hoja. */
    private static void writeMonth(Sheet sheet, ExcelStyles styles, Row row, MonthRow m) {
        row.setHeightInPoints(17);
        Fill fill = m.status() == MonthStatus.CURRENT ? Fill.CURRENT
                : m.status() == MonthStatus.FUTURE ? Fill.FUTURE : Fill.NONE;
        boolean future = m.status() == MonthStatus.FUTURE;
        styleRow(row, styles, fill, false);

        String sheetName = ExcelMonthWriter.sheetName(m.month());
        Cell name = text(row, COL_MONTH, sheetName + (m.status() == MonthStatus.CURRENT ? " (actual)" : ""),
                styles.link(fill, false));
        Hyperlink link = sheet.getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
        link.setAddress("'" + sheetName + "'!A1");
        name.setHyperlink(link);

        text(row, COL_DAYS, null, null).setCellValue(m.workingDays());
        text(row, COL_NORMAL, null, null).setCellValue(m.normalDays());
        text(row, COL_INTENSIVE, null, null).setCellValue(m.intensiveDays());
        minutes(row, COL_CALENDAR, m.calendarMinutes(), null);
        if (m.vacationDays() > 0) {
            text(row, COL_VACATION_DAYS, null, null).setCellValue(m.vacationDays());
            minutes(row, COL_VACATION_HOURS, m.vacationMinutes(), null);
        }
        minutes(row, COL_THEORETICAL, m.theoreticalMinutes(), null);
        minutes(row, COL_WORKED, m.workedMinutes(), null);
        signedCell(row, COL_DIFFERENCE, m.differenceMinutes(), styles, fill, false, !future);
        if (m.bridgeMinutes() != 0) {
            minutes(row, COL_BRIDGES, m.bridgeMinutes(), null);
        }
        signedCell(row, COL_BALANCE, m.cumulativeBalanceMinutes(), styles, fill, true, !future);
    }

    private static void writeTotal(Sheet sheet, ExcelStyles styles, Row row, PeriodSummary s) {
        row.setHeightInPoints(20);
        styleRow(row, styles, Fill.TOTAL, true);
        text(row, COL_MONTH, "Total", null);
        text(row, COL_DAYS, null, null).setCellValue(s.workingDays());
        text(row, COL_NORMAL, null, null).setCellValue(s.normalDays());
        text(row, COL_INTENSIVE, null, null).setCellValue(s.intensiveDays());
        minutes(row, COL_CALENDAR, s.calendarMinutes(), null);
        double vacationDays = s.months().stream().mapToDouble(MonthRow::vacationDays).sum();
        if (vacationDays > 0) {
            text(row, COL_VACATION_DAYS, null, null).setCellValue(vacationDays);
            minutes(row, COL_VACATION_HOURS, s.months().stream().mapToInt(MonthRow::vacationMinutes).sum(), null);
        }
        minutes(row, COL_THEORETICAL, s.months().stream().mapToInt(MonthRow::theoreticalMinutes).sum(), null);
        minutes(row, COL_WORKED, s.workedMinutes(), null);
        signedCell(row, COL_DIFFERENCE, s.months().stream().mapToInt(MonthRow::differenceMinutes).sum(), styles,
                Fill.TOTAL, true, true);
        if (s.bridgeMinutes() != 0) {
            minutes(row, COL_BRIDGES, s.bridgeMinutes(), null);
        }
    }

    /** Estilo de cada celda de una fila de la tabla según su columna. */
    private static void styleRow(Row row, ExcelStyles styles, Fill fill, boolean bold) {
        for (int c = 0; c <= LAST_COL; c++) {
            Kind kind = switch (c) {
                case COL_MONTH -> Kind.TEXT;
                case COL_DAYS, COL_NORMAL, COL_INTENSIVE, COL_VACATION_DAYS, COL_DIFFERENCE, COL_BALANCE -> Kind.RIGHT;
                default -> Kind.DURATION;
            };
            text(row, c, null, styles.cell(kind, fill, bold));
        }
    }

    /** Diferencia o saldo con signo, en verde o rojo salvo en los meses futuros (atenuados). */
    private static void signedCell(Row row, int column, int minutes, ExcelStyles styles, Fill fill, boolean bold,
            boolean colored) {
        text(row, column, signed(minutes), styles.cell(Kind.RIGHT, fill, bold, colored ? signColor(minutes) : null));
    }

    /** Margen sobre el convenio y Vacaciones y horas, uno al lado del otro; devuelve la última fila usada. */
    private static int writeBoxes(Sheet sheet, ExcelStyles styles, PeriodSummary s, int start) {
        VacationSummary v = s.vacations();
        int left = start;
        leftRow(sheet, styles, left++, true, false, "Margen sobre el convenio", Value.text(""));
        leftRow(sheet, styles, left++, false, false, "Horas calendario (" + s.workingDays() + " laborables)",
                Value.duration(s.calendarMinutes()));
        leftRow(sheet, styles, left++, false, false, "Convenio", Value.duration(s.agreementMinutes()));
        leftRow(sheet, styles, left++, false, true, "Margen (calendario − convenio)",
                Value.signed(s.marginMinutes()));
        leftRow(sheet, styles, left++, false, false, "Valor de las vacaciones", Value.duration(v.valueMinutes()));
        leftRow(sheet, styles, left++, false, false, "Horas a recuperar", Value.duration(s.hoursToRecoverMinutes()));
        leftRow(sheet, styles, left, false, false, "Margen restante", Value.signed(s.remainingMarginMinutes()));

        int right = start;
        rightRow(sheet, styles, right++, true, false, "Vacaciones y horas", Value.text(""));
        rightRow(sheet, styles, right++, false, false, "Vacaciones del periodo", Value.text(days(v.totalDays())));
        rightRow(sheet, styles, right++, false, false, "Disfrutadas", Value.text(days(v.takenDays()) + " · "
                + Minutes.format(v.takenMinutes())));
        rightRow(sheet, styles, right++, false, false, "Restantes", Value.text(days(v.remainingDays()) + " · "
                + Minutes.format(v.remainingMinutes())));
        rightRow(sheet, styles, right++, false, false, "Planificadas", Value.text(days(v.plannedDays()) + " ("
                + days(v.pendingPlannedDays()) + " pendientes)"));
        rightRow(sheet, styles, right++, false, false, "Horas hechas", Value.duration(s.workedMinutes()));
        rightRow(sheet, styles, right++, false, false, "Teóricas restantes",
                Value.duration(s.theoreticalRemainingMinutes()));
        rightRow(sheet, styles, right++, false, false, "Saldo inicial", Value.signed(s.openingBalanceMinutes()));
        rightRow(sheet, styles, right++, false, true, "Saldo hasta hoy", Value.signed(s.balanceToDateMinutes()));
        rightRow(sheet, styles, right, false, true, "Proyección a fin de periodo",
                Value.signed(s.projectionMinutes()));
        return Math.max(left, right);
    }

    private static void leftRow(Sheet sheet, ExcelStyles styles, int r, boolean head, boolean bold, String label,
            Value value) {
        Row row = row(sheet, r);
        row.setHeightInPoints(17);
        boxLabel(sheet, styles, row, LEFT_LABEL, LEFT_LABEL_END, head, bold, label);
        boxValue(sheet, styles, row, LEFT_VALUE, LEFT_VALUE_END, head, bold, value);
    }

    private static void rightRow(Sheet sheet, ExcelStyles styles, int r, boolean head, boolean bold, String label,
            Value value) {
        Row row = row(sheet, r);
        row.setHeightInPoints(17);
        boxLabel(sheet, styles, row, RIGHT_LABEL, RIGHT_LABEL_END, head, bold, label);
        boxValue(sheet, styles, row, RIGHT_VALUE, RIGHT_VALUE_END, head, bold, value);
    }

    private static void writeFootnote(Sheet sheet, ExcelStyles styles, PeriodSummary s, int r) {
        Row note = row(sheet, r);
        note.setHeightInPoints(40);
        text(note, COL_MONTH, "Datos a " + DATE.format(s.today()) + ". Diferencias y saldos con las horas sin "
                + "redondear. Margen = horas calendario − convenio; horas a recuperar = máx(0, valor de las "
                + "vacaciones − margen); proyección = saldo inicial + hechas + teóricas restantes − vacaciones sin "
                + "planificar − convenio (positivo: sobran horas; negativo: faltan). Cada mes tiene su hoja con el "
                + "detalle por día: pulsa el nombre del mes en la tabla.", styles.footnote());
        sheet.addMergedRegion(new CellRangeAddress(r, r, COL_MONTH, LAST_COL));
    }

    private static void writeWarnings(Sheet sheet, ExcelStyles styles, List<CalcIssue> warnings, int start) {
        if (warnings.isEmpty()) {
            return;
        }
        text(row(sheet, start), COL_MONTH, "Avisos", styles.plain(true, 11, NAVY));
        int r = start + 1;
        for (CalcIssue warning : warnings) {
            text(row(sheet, r++), COL_MONTH, "· " + warning.message(), styles.plain(false, 10, null));
        }
    }
}
