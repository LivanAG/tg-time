package com.controlhorario.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controlhorario.TestcontainersConfiguration;
import com.controlhorario.absence.Absence;
import com.controlhorario.absence.AbsenceRepository;
import com.controlhorario.absence.AbsenceType;
import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.calendar.Holiday;
import com.controlhorario.calendar.HolidayRepository;
import com.controlhorario.calendar.HolidayScope;
import com.controlhorario.common.audit.AuditLog;
import com.controlhorario.common.audit.AuditLogRepository;
import com.controlhorario.importexport.dto.DetectedSettingsDto;
import com.controlhorario.importexport.dto.ImportAbsenceDto;
import com.controlhorario.importexport.dto.ImportAction;
import com.controlhorario.importexport.dto.ImportBreakDto;
import com.controlhorario.importexport.dto.ImportCountsDto;
import com.controlhorario.importexport.dto.ImportDayDto;
import com.controlhorario.importexport.dto.ImportDayStatus;
import com.controlhorario.importexport.dto.ImportResultDto;
import com.controlhorario.importexport.dto.ImportSheetDto;
import com.controlhorario.importexport.excel.TestWorkbooks;
import com.controlhorario.period.IntensiveRange;
import com.controlhorario.period.IntensiveRangeRepository;
import com.controlhorario.period.WorkPeriod;
import com.controlhorario.period.WorkPeriodRepository;
import com.controlhorario.user.User;
import com.controlhorario.user.UserRepository;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.Workday;
import com.controlhorario.workday.WorkdayRepository;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInputs;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** POST /api/import/xlsx y GET /api/export/xlsx con el Excel real y "hoy" = 07/10/2026. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class ImportExportIT {

    private static final String XLSX = XlsxUploadValidator.XLSX_CONTENT_TYPE;
    private static final byte[] EXCEL = TestWorkbooks.realExcel();
    private static final LocalDate TODAY = ExcelFixture.TODAY;
    private static final LocalDate MAY_26 = LocalDate.of(2026, 5, 26);
    private static final LocalDate SEPTEMBER_29 = LocalDate.of(2026, 9, 29);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2027, 12, 31);
    private static final WorkdayCalculator CALCULATOR = new WorkdayCalculator(20, 30);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository users;
    @Autowired
    WorkPeriodRepository periods;
    @Autowired
    HolidayRepository holidays;
    @Autowired
    IntensiveRangeRepository intensiveRanges;
    @Autowired
    WorkdayRepository workdays;
    @Autowired
    AbsenceRepository absences;
    @Autowired
    AuditLogRepository auditLogs;
    @Autowired
    PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------ vista previa

    @Test
    void dryRunPreviewsTheRealExcelWithoutWritingAnything() throws Exception {
        User user = newUser();
        WorkPeriod period = createExcelPeriod(user);

        ImportResultDto result = read(upload(user, xlsx(EXCEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.fileName").value("HORAS_IZERTIS_2026-27.xlsx"))
                .andExpect(jsonPath("$.periodId").value(period.getId().toString()))
                .andExpect(jsonPath("$.sheets[1].month").value("2026-06"))
                .andExpect(jsonPath("$.days[?(@.date == '2026-05-26')].workday.startTime").value("07:25"))
                .andExpect(jsonPath("$.days[?(@.date == '2026-05-26')].workday.breaks[1].endTime").value("15:32")));

        assertThat(result.periodId()).isEqualTo(period.getId());
        assertThat(result.sheets()).hasSize(13);
        assertThat(result.sheets()).filteredOn(s -> s.dateCorrections() > 0)
                .extracting(ImportSheetDto::name, ImportSheetDto::dateCorrections)
                .containsExactly(tuple("Mayo 26", 5),
                        tuple("Agosto", 5));
        assertThat(result.sheets().stream().mapToInt(ImportSheetDto::dateCorrections).sum()).isEqualTo(10);

        List<ImportDayDto> days = result.days();
        assertThat(days).hasSize(235);
        assertThat(countByStatus(days)).containsOnly(
                Map.entry(ImportDayStatus.NEW, 84L),
                Map.entry(ImportDayStatus.FUTURE, 138L),
                Map.entry(ImportDayStatus.NOT_REPRESENTABLE, 13L));
        assertThat(days).filteredOn(d -> d.status() == ImportDayStatus.NEW)
                .allMatch(d -> d.action() == ImportAction.IMPORT && !d.date().isAfter(TODAY));
        assertThat(days).filteredOn(d -> d.status() == ImportDayStatus.FUTURE)
                .allMatch(d -> d.action() == ImportAction.SKIP && d.date().isAfter(TODAY));
        assertThat(days).filteredOn(d -> d.status() == ImportDayStatus.NOT_REPRESENTABLE)
                .allMatch(d -> d.action() == ImportAction.SKIP && d.date().isAfter(TODAY)
                        && d.computedWorkedMinutes() == null);

        // Los 222 días representables dan exactamente el Total Día del Excel.
        List<ImportDayDto> representable = days.stream()
                .filter(d -> d.status() != ImportDayStatus.NOT_REPRESENTABLE)
                .toList();
        assertThat(representable).hasSize(222).allSatisfy(d -> {
            assertThat(d.computedWorkedMinutes()).as(d.date().toString()).isNotNull();
            assertThat(d.computedWorkedMinutes()).as(d.date().toString()).isEqualTo(d.excelWorkedMinutes());
        });

        ImportDayDto september29 = day(result, SEPTEMBER_29);
        assertThat(september29.status()).isEqualTo(ImportDayStatus.NEW);
        assertThat(september29.workday().breaks())
                .containsExactly(new ImportBreakDto(BreakType.COMIDA, LocalTime.of(15, 52), LocalTime.of(16, 2)));
        assertThat(september29.computedWorkedMinutes()).isEqualTo(513);

        assertThat(result.absences()).extracting(ImportAbsenceDto::date).containsExactlyElementsOf(expectedVacations());
        assertThat(result.absences()).allMatch(a -> a.type() == AbsenceType.VACACIONES
                && a.action() == ImportAction.IMPORT);

        assertThat(result.counts()).isEqualTo(new ImportCountsDto(84, 0, 138, 0, 0, 13, 0, 13, 0));
        assertThat(result.detectedSettings())
                .isEqualTo(new DetectedSettingsDto(20, 30, 50, 480, 420, 23, 105_600));

        assertThat(workdays.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).isEmpty();
        assertThat(absences.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).isEmpty();
        assertThat(auditLogs.findByUserIdOrderByAtDesc(user.getId())).isEmpty();
    }

    @Test
    void futureDaysAreImportedOnlyWhenRequested() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        ImportResultDto result = read(upload(user, xlsx(EXCEL), "includeFuture", "true").andExpect(status().isOk()));

        assertThat(result.days()).filteredOn(d -> d.status() == ImportDayStatus.FUTURE)
                .hasSize(138)
                .allMatch(d -> d.action() == ImportAction.IMPORT);
        assertThat(result.counts()).isEqualTo(new ImportCountsDto(222, 0, 0, 0, 0, 13, 0, 13, 0));
    }

    @Test
    void vacationsAreOnlyProposedWhenMarkVacationsIsFalse() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        ImportResultDto result = read(upload(user, xlsx(EXCEL), "markVacations", "false", "dryRun", "false")
                .andExpect(status().isOk()));

        assertThat(result.absences()).hasSize(13).allMatch(a -> a.action() == ImportAction.SKIP);
        assertThat(result.counts().vacationsToCreate()).isZero();
        assertThat(result.counts().vacationsCreated()).isZero();
        assertThat(absences.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).isEmpty();
    }

    @Test
    void theDefaultPeriodIsTheOneWithMostDatesAndAnExplicitOneLeavesTheRestOutOfPeriod() throws Exception {
        User user = newUser();
        WorkPeriod excelPeriod = createExcelPeriod(user);
        WorkPeriod next = createPeriod(user, "2027-2028", LocalDate.of(2027, 5, 26), LocalDate.of(2028, 5, 25));

        assertThat(read(upload(user, xlsx(EXCEL))).periodId()).isEqualTo(excelPeriod.getId());

        ImportResultDto result = read(upload(user, xlsx(EXCEL), "periodId", next.getId().toString())
                .andExpect(status().isOk()));
        assertThat(result.periodId()).isEqualTo(next.getId());
        assertThat(result.days()).allMatch(d -> d.status() == ImportDayStatus.OUT_OF_PERIOD
                && d.action() == ImportAction.SKIP);
        assertThat(result.absences()).isEmpty();
        assertThat(result.counts()).isEqualTo(new ImportCountsDto(0, 0, 0, 0, 235, 0, 0, 0, 0));
        assertThat(result.warnings()).anyMatch(w -> w.contains("235 días con fichaje quedan fuera del periodo"));
    }

    // ------------------------------------------------------------------ confirmación

    @Test
    void confirmingPersistsTheNewDaysAndTheVacationsAndASecondImportFindsThemAll() throws Exception {
        User user = newUser();
        WorkPeriod period = createExcelPeriod(user);

        ImportResultDto result = read(upload(user, xlsx(EXCEL), "dryRun", "false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false)));

        assertThat(result.counts()).isEqualTo(new ImportCountsDto(84, 84, 138, 0, 0, 13, 0, 13, 13));
        List<Workday> saved = workdays.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO);
        assertThat(saved).extracting(Workday::getDate).containsExactlyElementsOf(result.days().stream()
                .filter(d -> d.status() == ImportDayStatus.NEW).map(ImportDayDto::date).sorted().toList());

        Workday may26 = saved.get(0);
        assertThat(may26.getDate()).isEqualTo(MAY_26);
        assertThat(may26.getStartTime()).isEqualTo(LocalTime.of(7, 25));
        assertThat(may26.getEndTime()).isEqualTo(LocalTime.of(17, 59));
        assertThat(may26.getLocation()).isEqualTo(Location.CASA);
        assertThat(may26.getBreaks()).extracting(b -> b.getType() + " " + b.getStartTime() + "-" + b.getEndTime())
                .containsExactly("DESAYUNO 12:43-13:02", "COMIDA 15:02-15:32");
        assertThat(CALCULATOR.calculate(WorkdayInputs.from(may26)).workedMinutes()).isEqualTo(604);
        // Todo lo guardado reproduce el Total Día del Excel.
        Map<LocalDate, ImportDayDto> byDate = result.days().stream()
                .collect(Collectors.toMap(ImportDayDto::date, Function.identity()));
        assertThat(saved).allSatisfy(w -> assertThat(CALCULATOR.calculate(WorkdayInputs.from(w)).workedMinutes())
                .isEqualTo(byDate.get(w.getDate()).excelWorkedMinutes()));

        List<Absence> vacations = absences.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO);
        assertThat(vacations).extracting(Absence::getDate).containsExactlyElementsOf(expectedVacations());
        assertThat(vacations).allMatch(a -> a.getType() == AbsenceType.VACACIONES && !a.isHalfDay());

        List<AuditLog> audit = auditLogs.findByUserIdOrderByAtDesc(user.getId());
        assertThat(audit).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("IMPORT");
            assertThat(a.getEntity()).isEqualTo("xlsx");
            assertThat(a.getEntityId()).isEqualTo(period.getId().toString());
        });

        // Segunda importación: lo ya importado aparece como EXISTS y no se vuelve a crear nada.
        ImportResultDto again = read(upload(user, xlsx(EXCEL)).andExpect(status().isOk()));
        assertThat(countByStatus(again.days())).containsOnly(
                Map.entry(ImportDayStatus.EXISTS, 84L),
                Map.entry(ImportDayStatus.FUTURE, 138L),
                Map.entry(ImportDayStatus.NOT_REPRESENTABLE, 13L));
        assertThat(again.days()).filteredOn(d -> d.status() == ImportDayStatus.EXISTS)
                .allMatch(d -> d.action() == ImportAction.SKIP);
        assertThat(again.absences()).hasSize(13).allSatisfy(a -> {
            assertThat(a.action()).isEqualTo(ImportAction.SKIP);
            assertThat(a.reason()).contains("Ya hay una ausencia");
        });
        assertThat(again.counts()).isEqualTo(new ImportCountsDto(0, 0, 138, 84, 0, 13, 0, 0, 0));

        ImportResultDto confirmedAgain = read(upload(user, xlsx(EXCEL), "dryRun", "false")
                .andExpect(status().isOk()));
        assertThat(confirmedAgain.counts().imported()).isZero();
        assertThat(confirmedAgain.counts().vacationsCreated()).isZero();
        assertThat(workdays.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).hasSize(84);
        assertThat(absences.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).hasSize(13);
    }

    @Test
    void overwriteReplacesTheExistingDays() throws Exception {
        User user = newUser();
        createExcelPeriod(user);
        upload(user, xlsx(EXCEL), "dryRun", "false").andExpect(status().isOk());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Workday may26 = workdays.findByUserIdAndDate(user.getId(), MAY_26).orElseThrow();
            may26.setEndTime(LocalTime.of(16, 0));
            may26.replaceBreaks(List.of());
        });

        ImportResultDto preview = read(upload(user, xlsx(EXCEL), "overwrite", "true").andExpect(status().isOk()));
        ImportDayDto may26Preview = day(preview, MAY_26);
        assertThat(may26Preview.status()).isEqualTo(ImportDayStatus.EXISTS);
        assertThat(may26Preview.action()).isEqualTo(ImportAction.IMPORT);
        assertThat(preview.counts().toImport()).isEqualTo(84);

        ImportResultDto result = read(upload(user, xlsx(EXCEL), "overwrite", "true", "dryRun", "false")
                .andExpect(status().isOk()));
        assertThat(result.counts().imported()).isEqualTo(84);

        Workday may26 = workdays.findByUserIdAndDate(user.getId(), MAY_26).orElseThrow();
        assertThat(may26.getEndTime()).isEqualTo(LocalTime.of(17, 59));
        assertThat(may26.getBreaks()).hasSize(2);
        assertThat(CALCULATOR.calculate(WorkdayInputs.from(may26)).workedMinutes()).isEqualTo(604);
        assertThat(workdays.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).hasSize(84);
    }

    // ------------------------------------------------------------------ errores

    @Test
    void rejectsATextFileRenamedToXlsx() throws Exception {
        User user = newUser();
        createExcelPeriod(user);
        byte[] text = "ENTRADA;SALIDA\n07:25;17:59\n".getBytes(StandardCharsets.UTF_8);

        upload(user, new MockMultipartFile("file", "HORAS.xlsx", XLSX, text))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("El fichero no es un Excel .xlsx válido"));
    }

    @Test
    void rejectsAWorkbookWithMacros() throws Exception {
        User user = newUser();
        createExcelPeriod(user);

        upload(user, xlsx(TestWorkbooks.withVbaProject(EXCEL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("macros")));
        assertThat(workdays.findByUserIdAndDateBetweenOrderByDate(user.getId(), FROM, TO)).isEmpty();
    }

    @Test
    void rejectsOtherContentTypesAndExtensions() throws Exception {
        User user = newUser();
        upload(user, new MockMultipartFile("file", "HORAS.xlsx", "text/plain", EXCEL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        upload(user, new MockMultipartFile("file", "HORAS.csv", XLSX, EXCEL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        mvc.perform(multipart("/api/import/xlsx").with(auth(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void filesOverTwoMegabytesAreRejectedWith413() throws Exception {
        User user = newUser();
        byte[] big = new byte[(int) XlsxUploadValidator.MAX_BYTES + 1];
        big[0] = 'P';
        big[1] = 'K';
        big[2] = 3;
        big[3] = 4;

        upload(user, xlsx(big)).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void aUserWithoutPeriodMustCreateItFirst() throws Exception {
        User user = newUser();

        upload(user, xlsx(EXCEL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("periodId"))
                .andExpect(jsonPath("$.errors[0].message").value("Crea primero el periodo del Excel en Ajustes"));
    }

    @Test
    void anotherUsersPeriodIsNotFound() throws Exception {
        User owner = newUser();
        WorkPeriod foreign = createExcelPeriod(owner);
        User user = newUser();
        createExcelPeriod(user);

        upload(user, xlsx(EXCEL), "periodId", foreign.getId().toString()).andExpect(status().isNotFound());
    }

    @Test
    void importAndExportRequireAuthentication() throws Exception {
        mvc.perform(multipart("/api/import/xlsx").file(xlsx(EXCEL))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/export/xlsx").param("year", "2026").param("month", "6"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ exportación

    @Test
    void exportedJuneReimportedByAnotherUserGivesTheSameWorkdays() throws Exception {
        User livan = newUser();
        createExcelPeriod(livan);
        upload(livan, xlsx(EXCEL), "dryRun", "false").andExpect(status().isOk());

        MvcResult export = mvc.perform(get("/api/export/xlsx").param("year", "2026").param("month", "6")
                        .with(auth(livan)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"horas-2026-06.xlsx\""))
                .andReturn();
        byte[] june = export.getResponse().getContentAsByteArray();

        User other = newUser();
        createExcelPeriod(other);
        ImportResultDto result = read(upload(other, new MockMultipartFile("file", "horas-2026-06.xlsx", XLSX, june),
                "dryRun", "false").andExpect(status().isOk()));

        assertThat(result.sheets()).containsExactly(new ImportSheetDto("Junio 2026",
                YearMonth.of(2026, 6), 22, 0));
        // Sin fechas corregidas, ubicaciones vacías ni diferencias: solo el aviso de comida corta del 09/06.
        assertThat(result.days()).hasSize(22).allMatch(d -> d.status() == ImportDayStatus.NEW
                && d.messages().stream().allMatch(m -> m.startsWith("La comida dura")));
        assertThat(result.absences()).isEmpty();
        assertThat(result.counts()).isEqualTo(new ImportCountsDto(22, 22, 0, 0, 0, 0, 0, 0, 0));

        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        List<String> original = snapshot(workdays.findByUserIdAndDateBetweenOrderByDate(livan.getId(), from, to));
        List<String> reimported = snapshot(workdays.findByUserIdAndDateBetweenOrderByDate(other.getId(), from, to));
        assertThat(original).hasSize(22);
        assertThat(reimported).containsExactlyElementsOf(original);
    }

    @Test
    void exportNeedsAPeriodAndAValidMonth() throws Exception {
        User user = newUser();
        mvc.perform(get("/api/export/xlsx").param("year", "2026").param("month", "6").with(auth(user)))
                .andExpect(status().isNotFound());

        createExcelPeriod(user);
        mvc.perform(get("/api/export/xlsx").param("year", "2026").param("month", "13").with(auth(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("month"));
        mvc.perform(get("/api/export/xlsx").param("year", "2026").param("month", "12").with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"horas-2026-12.xlsx\""));
    }

    // ------------------------------------------------------------------ utilidades

    private User newUser() {
        User user = new User();
        user.setEmail("import-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("{noop}no-se-usa");
        user.setName("Livan Aranda");
        user.setCompany("IZERTIS");
        return users.save(user);
    }

    /** Periodo del Excel con los parámetros de docs/API.md, los festivos de ExcelFixture y la intensiva 15/06-15/09. */
    private WorkPeriod createExcelPeriod(User user) {
        WorkPeriod period = createPeriod(user, "2026-2027", LocalDate.of(2026, 5, 26), LocalDate.of(2027, 5, 25));
        ExcelFixture.rules().holidays().forEach((date, name) -> holidays.save(new Holiday(period.getId(), date, name,
                name.contains("convenio") ? HolidayScope.EMPRESA : HolidayScope.NACIONAL)));
        intensiveRanges.save(new IntensiveRange(period.getId(), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 15)));
        return period;
    }

    private WorkPeriod createPeriod(User user, String name, LocalDate start, LocalDate end) {
        WorkPeriod period = new WorkPeriod();
        period.setUserId(user.getId());
        period.setName(name);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setAgreementMinutes(105_600);
        period.setVacationDays(23);
        period.setNormalDayMinutes(480);
        period.setIntensiveDayMinutes(420);
        period.setBreakfastToleranceMin(20);
        period.setMinLunchMin(30);
        period.setRoundingStepMin(15);
        period.setMaxRemotePct(50);
        period.setOpeningBalanceMin(0);
        return periods.save(period);
    }

    private static List<LocalDate> expectedVacations() {
        return ExcelFixture.vacations().stream().map(a -> a.date()).toList();
    }

    private static RequestPostProcessor auth(User user) {
        return jwt().jwt(j -> j.subject(user.getId().toString()).claim("role", "USER"));
    }

    private static MockMultipartFile xlsx(byte[] content) {
        return new MockMultipartFile("file", "HORAS_IZERTIS_2026-27.xlsx", XLSX, content);
    }

    private ResultActions upload(User user, MockMultipartFile file, String... params) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/import/xlsx").file(file);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request.with(auth(user)));
    }

    private ImportResultDto read(ResultActions actions) throws Exception {
        return objectMapper.readValue(actions.andReturn().getResponse().getContentAsByteArray(), ImportResultDto.class);
    }

    private static ImportDayDto day(ImportResultDto result, LocalDate date) {
        return result.days().stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    private static Map<ImportDayStatus, Long> countByStatus(List<ImportDayDto> days) {
        return days.stream().collect(Collectors.groupingBy(ImportDayDto::status, Collectors.counting()));
    }

    /** Fichaje sin identificadores, para comparar dos usuarios. */
    private static List<String> snapshot(List<Workday> list) {
        List<String> rows = new ArrayList<>();
        for (Workday w : list) {
            rows.add(w.getDate() + " " + w.getStartTime() + "-" + w.getEndTime() + " " + w.getLocation() + " remote="
                    + w.getRemoteMinutes() + " "
                    + w.getBreaks().stream().map(b -> b.getType() + " " + b.getStartTime() + "-" + b.getEndTime())
                            .toList());
        }
        return rows;
    }
}
