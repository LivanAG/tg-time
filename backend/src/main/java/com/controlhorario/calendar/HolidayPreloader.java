package com.controlhorario.calendar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precarga de festivos de la Comunidad de Madrid y Madrid capital.
 * <p>
 * Lee {@code holidays/es-madrid.csv} (fecha,nombre,ámbito; las líneas con {@code #} son
 * comentarios). Un año que aparece en el CSV se toma tal cual (incluye los traslados que publica
 * el BOCM). Para los años que no están en el CSV se usa un cálculo de respaldo: nacionales de fecha
 * fija, Viernes Santo (Pascua por el algoritmo de Gauss/Meeus), Jueves Santo y 2 de mayo
 * (autonómicos), San Isidro y la Almudena (locales) y 24 y 31 de diciembre (convenio de empresa).
 */
@Component
public class HolidayPreloader {

    static final String CSV_LOCATION = "holidays/es-madrid.csv";

    private final HolidayRepository holidays;
    private final Map<Integer, List<HolidaySeed>> csvByYear;

    @Autowired
    public HolidayPreloader(HolidayRepository holidays) {
        this(holidays, loadCsv());
    }

    HolidayPreloader(HolidayRepository holidays, Map<Integer, List<HolidaySeed>> csvByYear) {
        this.holidays = holidays;
        this.csvByYear = Map.copyOf(csvByYear);
    }

    /** Años que vienen del CSV (el resto se calculan). */
    public Set<Integer> csvYears() {
        return csvByYear.keySet();
    }

    /** Festivos entre dos fechas (inclusive), ordenados por fecha y sin fechas repetidas. */
    public List<HolidaySeed> holidaysBetween(LocalDate from, LocalDate to) {
        Map<LocalDate, HolidaySeed> byDate = new LinkedHashMap<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            List<HolidaySeed> ofYear = csvByYear.containsKey(year) ? csvByYear.get(year) : fallback(year);
            for (HolidaySeed seed : ofYear) {
                if (!seed.date().isBefore(from) && !seed.date().isAfter(to)) {
                    byDate.putIfAbsent(seed.date(), seed);
                }
            }
        }
        List<HolidaySeed> result = new ArrayList<>(byDate.values());
        result.sort(Comparator.comparing(HolidaySeed::date));
        return List.copyOf(result);
    }

    /**
     * Añade al periodo los festivos precargados de sus fechas que todavía no tenga (no toca los que
     * ya existen, aunque el usuario los haya renombrado). Devuelve los añadidos.
     */
    @Transactional
    public List<Holiday> preload(UUID periodId, LocalDate from, LocalDate to) {
        Set<LocalDate> existing = new HashSet<>();
        for (Holiday h : holidays.findByPeriodIdOrderByDate(periodId)) {
            existing.add(h.getDate());
        }
        List<Holiday> missing = holidaysBetween(from, to).stream()
                .filter(seed -> !existing.contains(seed.date()))
                .map(seed -> new Holiday(periodId, seed.date(), seed.name(), seed.scope()))
                .toList();
        return holidays.saveAll(missing);
    }

    /** Cálculo de respaldo para un año que no está en el CSV. */
    public static List<HolidaySeed> fallback(int year) {
        LocalDate easter = easterSunday(year);
        List<HolidaySeed> seeds = new ArrayList<>();
        seeds.add(fixed(year, 1, 1, "Año Nuevo", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 1, 6, "Epifanía del Señor", HolidayScope.NACIONAL));
        seeds.add(new HolidaySeed(easter.minusDays(3), "Jueves Santo", HolidayScope.AUTONOMICO));
        seeds.add(new HolidaySeed(easter.minusDays(2), "Viernes Santo", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 5, 1, "Fiesta del Trabajo", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 5, 2, "Fiesta de la Comunidad de Madrid", HolidayScope.AUTONOMICO));
        seeds.add(fixed(year, 5, 15, "San Isidro", HolidayScope.LOCAL));
        seeds.add(fixed(year, 8, 15, "Asunción de la Virgen", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 10, 12, "Fiesta Nacional de España", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 11, 1, "Todos los Santos", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 11, 9, "Nuestra Señora de la Almudena", HolidayScope.LOCAL));
        seeds.add(fixed(year, 12, 6, "Día de la Constitución Española", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 12, 8, "Inmaculada Concepción", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 12, 24, "Nochebuena (convenio)", HolidayScope.EMPRESA));
        seeds.add(fixed(year, 12, 25, "Natividad del Señor", HolidayScope.NACIONAL));
        seeds.add(fixed(year, 12, 31, "Nochevieja (convenio)", HolidayScope.EMPRESA));
        seeds.sort(Comparator.comparing(HolidaySeed::date));
        return List.copyOf(seeds);
    }

    /** Domingo de Pascua (calendario gregoriano) por el algoritmo de Gauss en la versión de Meeus. */
    public static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    private static HolidaySeed fixed(int year, int month, int day, String name, HolidayScope scope) {
        return new HolidaySeed(MonthDay.of(month, day).atYear(year), name, scope);
    }

    private static Map<Integer, List<HolidaySeed>> loadCsv() {
        try (InputStream in = new ClassPathResource(CSV_LOCATION).getInputStream()) {
            return parseCsv(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + CSV_LOCATION, e);
        }
    }

    /** Líneas "fecha,nombre,ámbito" agrupadas por año. Las vacías y las que empiezan por # se ignoran. */
    static Map<Integer, List<HolidaySeed>> parseCsv(List<String> lines) {
        Map<Integer, List<HolidaySeed>> byYear = new HashMap<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int first = line.indexOf(',');
            int last = line.lastIndexOf(',');
            if (first < 0 || first == last) {
                throw new IllegalStateException(CSV_LOCATION + ", línea " + (n + 1) + ": formato fecha,nombre,ámbito");
            }
            try {
                LocalDate date = LocalDate.parse(line.substring(0, first).strip());
                String name = line.substring(first + 1, last).strip();
                HolidayScope scope = HolidayScope.valueOf(line.substring(last + 1).strip());
                if (name.isEmpty() || name.length() > 100) {
                    throw new IllegalStateException(CSV_LOCATION + ", línea " + (n + 1) + ": nombre vacío o demasiado largo");
                }
                byYear.computeIfAbsent(date.getYear(), y -> new ArrayList<>()).add(new HolidaySeed(date, name, scope));
            } catch (DateTimeException | IllegalArgumentException e) {
                throw new IllegalStateException(CSV_LOCATION + ", línea " + (n + 1) + ": " + e.getMessage(), e);
            }
        }
        Map<Integer, List<HolidaySeed>> result = new HashMap<>();
        byYear.forEach((year, seeds) -> {
            seeds.sort(Comparator.comparing(HolidaySeed::date));
            result.put(year, List.copyOf(seeds));
        });
        return result;
    }
}
