package com.controlhorario.importexport.excel;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.workday.Location;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Diseño del Excel que exporta la app (una hoja por mes, docs/EXCEL.md), compartido por el que lo escribe
 * ({@link ExcelMonthWriter}) y el que lo vuelve a leer ({@link ExcelReportParser}). Filas y columnas en
 * base 0 (como en POI).
 *
 * <pre>
 * 1  Registro de jornada · Octubre de 2026
 * 2  Nombre | Empresa | Periodo                                  (etiquetas en A, E, I; valores en C, G, K)
 * 3  Jornada normal | Jornada intensiva | Teletrabajo máx. (%)
 * 4  Tolerancia desayuno | Comida mínima | Redondeo
 * 6  A Fecha, B Día, C Ubicación, D Ausencia, E:F Oficina, G:H Casa, I:J Desayuno, K:L Comida,
 *    M Otras pausas, N Total, O Redondeado, P Notas
 * 7  Entrada/Salida bajo Oficina y Casa; Inicio/Fin bajo Desayuno y Comida
 * 8… un día por fila; subtotales por semana; "Total del mes"; resumen (en otra página al imprimir)
 * </pre>
 */
public final class ExcelReportLayout {

    private ExcelReportLayout() {
    }

    public static final int TITLE_ROW = 0;
    public static final int INFO_ROW = 1;
    public static final int PARAMS_ROW_1 = 2;
    public static final int PARAMS_ROW_2 = 3;
    public static final int GROUP_HEADER_ROW = 5;
    public static final int HEADER_ROW = 6;
    public static final int FIRST_DATA_ROW = 7;

    public static final int COL_DATE = 0;
    public static final int COL_WEEKDAY = 1;
    public static final int COL_LOCATION = 2;
    public static final int COL_ABSENCE = 3;
    public static final int COL_OFFICE_START = 4;
    public static final int COL_OFFICE_END = 5;
    public static final int COL_HOME_START = 6;
    public static final int COL_HOME_END = 7;
    public static final int COL_BREAKFAST_START = 8;
    public static final int COL_BREAKFAST_END = 9;
    public static final int COL_LUNCH_START = 10;
    public static final int COL_LUNCH_END = 11;
    public static final int COL_OTHER_BREAKS = 12;
    public static final int COL_WORKED = 13;
    public static final int COL_ROUNDED = 14;
    public static final int COL_NOTES = 15;
    public static final int LAST_COL = COL_NOTES;

    // Parámetros (celda del valor; la etiqueta ocupa las dos columnas anteriores).
    public static final int COL_PARAM_1 = 2;
    public static final int COL_PARAM_2 = 6;
    public static final int COL_PARAM_3 = 10;

    public static final String HEADER_DATE = "Fecha";
    public static final String HEADER_OFFICE = "Oficina";
    public static final String HEADER_HOME = "Casa";
    public static final String TOTAL_LABEL = "Total del mes";
    public static final String HALF_DAY_SUFFIX = " (medio día)";

    public static final Map<Location, String> LOCATION_LABELS = Map.of(
            Location.OFICINA, "Oficina", Location.CASA, "Casa", Location.MIXTO, "Mixto");

    public static final Map<AbsenceType, String> ABSENCE_LABELS = Map.of(
            AbsenceType.VACACIONES, "Vacaciones", AbsenceType.PUENTE, "Puente recuperable",
            AbsenceType.PERMISO, "Permiso", AbsenceType.BAJA, "Baja");

    /** ¿Es una hoja exportada por la app? Se reconoce por su cabecera (Fecha … Oficina … Casa). */
    public static boolean matches(Sheet sheet) {
        Row header = sheet.getRow(GROUP_HEADER_ROW);
        return header != null
                && HEADER_DATE.equals(text(header, COL_DATE))
                && HEADER_OFFICE.equals(text(header, COL_OFFICE_START))
                && HEADER_HOME.equals(text(header, COL_HOME_START));
    }

    public static Optional<Location> location(String label) {
        return LOCATION_LABELS.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(label.strip()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** "Vacaciones" o "Vacaciones (medio día)". */
    public static String absenceLabel(AbsenceType type, boolean halfDay) {
        return ABSENCE_LABELS.get(type) + (halfDay ? HALF_DAY_SUFFIX : "");
    }

    public static Optional<AbsenceType> absenceType(String label) {
        String base = label.strip().toLowerCase(Locale.ROOT).replace(HALF_DAY_SUFFIX.strip(), "").strip();
        return ABSENCE_LABELS.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).equals(base))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static boolean isHalfDay(String label) {
        return label.strip().toLowerCase(Locale.ROOT).endsWith(HALF_DAY_SUFFIX.strip());
    }

    private static String text(Row row, int column) {
        try {
            String value = ExcelCells.text(ExcelCells.cell(row, column));
            return value == null ? null : value.strip();
        } catch (CellReadException e) {
            return null;
        }
    }
}
