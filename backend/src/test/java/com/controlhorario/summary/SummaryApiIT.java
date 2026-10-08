package com.controlhorario.summary;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.controlhorario.calc.ExcelFixture;
import com.controlhorario.period.DomainApiTestSupport;
import com.controlhorario.summary.calc.AbsenceInput;
import com.controlhorario.user.User;
import com.controlhorario.workday.calc.WorkdayInput;

import org.junit.jupiter.api.Test;

class SummaryApiIT extends DomainApiTestSupport {

    /** Periodo del Excel con festivos precargados, los fichajes reales hasta hoy y las 13 vacaciones. */
    private UUID loadExcel(User user) throws Exception {
        UUID periodId = createExcelPeriod(user);
        for (WorkdayInput day : ExcelFixture.realWorkdays()) {
            putWorkday(user, day.date(), workdayBody(day, null)).andExpect(status().isOk());
        }
        for (AbsenceInput vacation : ExcelFixture.vacations()) {
            putAbsence(user, vacation.date(), vacation.type().name(), vacation.halfDay()).andExpect(status().isOk());
        }
        return periodId;
    }

    @Test
    void excelScenarioReproducesTheHorasSheetTheMonthAndTheDashboard() throws Exception {
        User user = newUser();
        UUID periodId = loadExcel(user);

        mvc.perform(get("/api/summary/period/" + periodId).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(periodId.toString()))
                .andExpect(jsonPath("$.name").value("2026-2027"))
                .andExpect(jsonPath("$.startDate").value("2026-05-26"))
                .andExpect(jsonPath("$.endDate").value("2027-05-25"))
                .andExpect(jsonPath("$.today").value("2026-10-07"))
                .andExpect(jsonPath("$.workingDays").value(248))
                .andExpect(jsonPath("$.normalDays").value(181))
                .andExpect(jsonPath("$.intensiveDays").value(67))
                .andExpect(jsonPath("$.calendarMinutes").value(115020))
                .andExpect(jsonPath("$.agreementMinutes").value(105600))
                .andExpect(jsonPath("$.marginMinutes").value(9420))
                .andExpect(jsonPath("$.vacations.totalDays").value(23))
                .andExpect(jsonPath("$.vacations.plannedDays").value(13.0))
                .andExpect(jsonPath("$.vacations.takenDays").value(13.0))
                .andExpect(jsonPath("$.vacations.takenMinutes").value(5460))
                .andExpect(jsonPath("$.vacations.pendingPlannedDays").value(0.0))
                .andExpect(jsonPath("$.vacations.remainingDays").value(10.0))
                .andExpect(jsonPath("$.vacations.remainingMinutes").value(4800))
                .andExpect(jsonPath("$.vacations.unplannedDays").value(10.0))
                .andExpect(jsonPath("$.vacations.valueMinutes").value(10260))
                .andExpect(jsonPath("$.hoursToRecoverMinutes").value(840))
                .andExpect(jsonPath("$.remainingMarginMinutes").value(3960))
                .andExpect(jsonPath("$.workedMinutes").value(37674))
                .andExpect(jsonPath("$.theoreticalRemainingMinutes").value(72480))
                .andExpect(jsonPath("$.projectionMinutes").value(-246))
                .andExpect(jsonPath("$.openingBalanceMinutes").value(0))
                .andExpect(jsonPath("$.balanceToDateMinutes").value(594))
                .andExpect(jsonPath("$.bridgeMinutes").value(0))
                .andExpect(jsonPath("$.months", hasSize(13)))
                .andExpect(jsonPath("$.months[0].month").value("2026-05"))
                .andExpect(jsonPath("$.months[0].status").value("PAST"))
                .andExpect(jsonPath("$.months[0].workingDays").value(4))
                .andExpect(jsonPath("$.months[0].calendarMinutes").value(1920))
                .andExpect(jsonPath("$.months[0].workedMinutes").value(2010))
                .andExpect(jsonPath("$.months[0].differenceMinutes").value(90))
                .andExpect(jsonPath("$.months[0].cumulativeBalanceMinutes").value(90))
                .andExpect(jsonPath("$.months[1].cumulativeBalanceMinutes").value(210))
                .andExpect(jsonPath("$.months[5].month").value("2026-10"))
                .andExpect(jsonPath("$.months[5].status").value("CURRENT"))
                .andExpect(jsonPath("$.months[12].status").value("FUTURE"))
                .andExpect(jsonPath("$.warnings", hasSize(0)));

        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(periodId.toString()))
                .andExpect(jsonPath("$.month").value("2026-06"))
                .andExpect(jsonPath("$.status").value("PAST"))
                .andExpect(jsonPath("$.workingDays").value(22))
                .andExpect(jsonPath("$.normalDays").value(10))
                .andExpect(jsonPath("$.intensiveDays").value(12))
                .andExpect(jsonPath("$.calendarMinutes").value(9840))
                .andExpect(jsonPath("$.theoreticalMinutes").value(9840))
                .andExpect(jsonPath("$.workedMinutes").value(9960))
                .andExpect(jsonPath("$.roundedMinutes").value(9960))
                .andExpect(jsonPath("$.roundedToDateMinutes").value(9960))
                .andExpect(jsonPath("$.differenceMinutes").value(120))
                .andExpect(jsonPath("$.openingBalanceMinutes").value(90))
                .andExpect(jsonPath("$.closingBalanceMinutes").value(210))
                .andExpect(jsonPath("$.remoteDays").value(10))
                .andExpect(jsonPath("$.maxRemotePct").value(50))
                // 10 días en casa sin superar el 50 %: sin aviso (el límite es solo el porcentaje).
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.maxRemoteDaysMonth").doesNotExist())
                .andExpect(jsonPath("$.jiraMinutes").doesNotExist())
                .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-06-01"))
                .andExpect(jsonPath("$.weeks[0].weekEnd").value("2026-06-07"))
                .andExpect(jsonPath("$.weeks[0].theoreticalMinutes").value(2400))
                .andExpect(jsonPath("$.weeks[0].workedMinutes").value(2356))
                .andExpect(jsonPath("$.weeks[0].roundedMinutes").value(2355))
                .andExpect(jsonPath("$.weeks[1].roundedMinutes").value(2400))
                .andExpect(jsonPath("$.days", hasSize(30)))
                .andExpect(jsonPath("$.days[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.days[0].dayType").value("LABORABLE"))
                .andExpect(jsonPath("$.days[0].dayMinutes").value(480))
                .andExpect(jsonPath("$.days[0].workday.version").value(0))
                .andExpect(jsonPath("$.days[0].workday.totals.workedMinutes").isNumber())
                .andExpect(jsonPath("$.days[5].dayType").value("FIN_DE_SEMANA"))
                .andExpect(jsonPath("$.days[5].workday").isEmpty())
                .andExpect(jsonPath("$.days[14].intensive").value(true))
                .andExpect(jsonPath("$.days[29].countdownMinutes").value(-120));

        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "5").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalanceMinutes").value(0))
                .andExpect(jsonPath("$.closingBalanceMinutes").value(90))
                .andExpect(jsonPath("$.days", hasSize(31)))
                .andExpect(jsonPath("$.days[0].dayType").value("FUERA_DE_PERIODO"))
                .andExpect(jsonPath("$.days[25].date").value("2026-05-26"))
                .andExpect(jsonPath("$.days[25].workday.startTime").value("07:25"))
                .andExpect(jsonPath("$.days[25].workday.totals.workedMinutes").value(604))
                .andExpect(jsonPath("$.days[25].workedMinutes").value(604));

        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "7")
                        .param("periodId", periodId.toString()).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacationDays").value(1.0))
                .andExpect(jsonPath("$.vacationMinutes").value(420))
                .andExpect(jsonPath("$.theoreticalMinutes").value(154 * 60))
                .andExpect(jsonPath("$.openingBalanceMinutes").value(210))
                .andExpect(jsonPath("$.days[9].date").value("2026-07-10"))
                .andExpect(jsonPath("$.days[9].absence.type").value("VACACIONES"))
                .andExpect(jsonPath("$.days[9].theoreticalMinutes").value(0));

        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-07"))
                .andExpect(jsonPath("$.period.id").value(periodId.toString()))
                .andExpect(jsonPath("$.period.name").value("2026-2027"))
                .andExpect(jsonPath("$.period.startDate").value("2026-05-26"))
                .andExpect(jsonPath("$.period.endDate").value("2027-05-25"))
                .andExpect(jsonPath("$.balanceToDateMinutes").value(594))
                .andExpect(jsonPath("$.currentMonth.month").value("2026-10"))
                .andExpect(jsonPath("$.currentMonth.theoreticalMinutes").value(10080))
                .andExpect(jsonPath("$.currentMonth.workedMinutes").value(2406))
                .andExpect(jsonPath("$.currentMonth.differenceMinutes").value(-7674))
                .andExpect(jsonPath("$.currentMonth.theoreticalToDateMinutes").value(2400))
                .andExpect(jsonPath("$.currentMonth.workedToDateMinutes").value(2406))
                .andExpect(jsonPath("$.currentMonth.differenceToDateMinutes").value(6))
                .andExpect(jsonPath("$.currentMonth.remotePct").value(43.0))
                .andExpect(jsonPath("$.currentMonth.remoteDays").value(2))
                .andExpect(jsonPath("$.currentMonth.maxRemotePct").value(50))
                .andExpect(jsonPath("$.currentMonth.warnings", hasSize(0)))
                .andExpect(jsonPath("$.vacations.totalDays").value(23))
                .andExpect(jsonPath("$.vacations.takenDays").value(13.0))
                .andExpect(jsonPath("$.vacations.remainingDays").value(10.0))
                .andExpect(jsonPath("$.vacations.pendingPlannedDays").value(0.0))
                .andExpect(jsonPath("$.hoursToRecoverMinutes").value(840))
                .andExpect(jsonPath("$.projectionMinutes").value(-246))
                .andExpect(jsonPath("$.todayIsWorkingDay").value(true))
                .andExpect(jsonPath("$.todayDayMinutes").value(480))
                .andExpect(jsonPath("$.todayWorkday.date").value("2026-10-07"))
                .andExpect(jsonPath("$.todayWorkday.totals.workedMinutes").value(457))
                .andExpect(jsonPath("$.todayWorkday.version").value(0));
    }

    @Test
    void monthPrefersTheSelectedPeriodAndOtherwiseTheOneWithMoreDays() throws Exception {
        User user = newUser();
        UUID current = createExcelPeriod(user);
        UUID next = createPeriod(user, period("2027-2028", "2027-05-26", "2028-05-25"));

        // Mayo de 2027: 25 días en el periodo actual y 6 en el siguiente, que está seleccionado (último creado).
        mvc.perform(get("/api/summary/month").param("year", "2027").param("month", "5").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(next.toString()));
        // El seleccionado no toca junio de 2026: el que tiene más días.
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(current.toString()));
        mvc.perform(put("/api/periods/" + current + "/select").with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/summary/month").param("year", "2027").param("month", "5").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(current.toString()))
                .andExpect(jsonPath("$.status").value("FUTURE"))
                .andExpect(jsonPath("$.days[25].dayType").value("FUERA_DE_PERIODO"));
        mvc.perform(get("/api/summary/month").param("year", "2027").param("month", "5")
                        .param("periodId", next.toString()).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodId").value(next.toString()))
                .andExpect(jsonPath("$.days[0].dayType").value("FUERA_DE_PERIODO"))
                .andExpect(jsonPath("$.days[25].dayType").value("LABORABLE"))
                .andExpect(jsonPath("$.workingDays").value(4));
        // Un periodo que no toca el mes.
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6")
                        .param("periodId", next.toString()).with(as(user)))
                .andExpect(status().isNotFound());
        // Ningún periodo toca el mes.
        mvc.perform(get("/api/summary/month").param("year", "2025").param("month", "6").with(as(user)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "13").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("month"));
    }

    @Test
    void summariesOfOtherUsersAreNotFound() throws Exception {
        User owner = newUser();
        User intruder = newUser();
        UUID periodId = createExcelPeriod(owner);

        mvc.perform(get("/api/summary/period/" + periodId).with(as(intruder))).andExpect(status().isNotFound());
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6")
                        .param("periodId", periodId.toString()).with(as(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/summary/month").param("year", "2026").param("month", "6").with(as(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/summary/period/" + periodId).with(as(owner))).andExpect(status().isOk());
    }

    @Test
    void dashboardWithoutPeriodAsksForOne() throws Exception {
        User user = newUser();
        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-07"))
                .andExpect(jsonPath("$.period").isEmpty())
                .andExpect(jsonPath("$.currentMonth").isEmpty())
                .andExpect(jsonPath("$.vacations").isEmpty())
                .andExpect(jsonPath("$.balanceToDateMinutes").value(0))
                .andExpect(jsonPath("$.todayIsWorkingDay").value(false))
                .andExpect(jsonPath("$.todayWorkday").isEmpty());
    }

    @Test
    void dashboardFollowsTheSelectedPeriodEvenIfItDoesNotIncludeToday() throws Exception {
        User user = newUser();
        UUID current = createExcelPeriod(user);
        UUID next = createPeriod(user, period("2027-2028", "2027-05-26", "2028-05-25"));

        // El nuevo queda seleccionado y aún no ha empezado: su primer mes y nada hecho hasta hoy.
        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-07"))
                .andExpect(jsonPath("$.period.id").value(next.toString()))
                .andExpect(jsonPath("$.currentMonth.month").value("2027-05"))
                .andExpect(jsonPath("$.currentMonth.theoreticalToDateMinutes").value(0))
                .andExpect(jsonPath("$.balanceToDateMinutes").value(0))
                .andExpect(jsonPath("$.todayIsWorkingDay").value(false));

        mvc.perform(put("/api/periods/" + current + "/select").with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(jsonPath("$.period.id").value(current.toString()))
                .andExpect(jsonPath("$.currentMonth.month").value("2026-10"))
                .andExpect(jsonPath("$.todayIsWorkingDay").value(true));

        // Un periodo ya terminado: su último mes.
        UUID past = createPeriod(user, period("2025-2026", "2025-05-26", "2026-05-25"));
        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(jsonPath("$.period.id").value(past.toString()))
                .andExpect(jsonPath("$.currentMonth.month").value("2026-05"))
                .andExpect(jsonPath("$.todayWorkday").isEmpty());
    }

    @Test
    void todayIsComputedInTheTimezoneOfTheUser() throws Exception {
        User user = newUser();
        // 2026-10-07 10:00 en Madrid son las 22:00 del día 6 en Honolulu.
        user.setTimezone("Pacific/Honolulu");
        users.save(user);
        createExcelPeriod(user);

        mvc.perform(get("/api/summary/dashboard").with(as(user)))
                .andExpect(jsonPath("$.today").value("2026-10-06"))
                .andExpect(jsonPath("$.currentMonth.theoreticalToDateMinutes").value(3 * 480));
    }
}
