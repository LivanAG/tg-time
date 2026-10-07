// Datos de ejemplo con la forma exacta de docs/API.md (periodo 2026-2027 del Excel, hoy = 2026-10-07).

import type {
  AbsenceDto,
  AuthResponse,
  BreakDto,
  DashboardDto,
  DayDto,
  ImportResultDto,
  IssueDto,
  MonthSummaryDto,
  PeriodDto,
  UserDto,
  WeekSummaryDto,
  WorkLocation,
  WorkdayDto,
} from '../api/types'
import { calculateWorkday } from '../lib/workdayCalc'

export const TODAY = '2026-10-07'

export const user: UserDto = {
  id: '6f1c2c1e-8d2a-4b8e-9d7e-1a2b3c4d5e6f',
  email: 'livan@example.com',
  name: 'Livan Aranda',
  company: 'IZERTIS',
  timezone: 'Europe/Madrid',
  role: 'ADMIN',
}

export function authResponse(accessToken = 'token-1', overrides: Partial<UserDto> = {}): AuthResponse {
  return { accessToken, expiresIn: 900, user: { ...user, ...overrides } }
}

export const period: PeriodDto = {
  id: '0d9a5f3e-3c1b-4f5e-8a7d-9b8c7d6e5f4a',
  name: '2026-2027',
  startDate: '2026-05-26',
  endDate: '2027-05-25',
  agreementMinutes: 105600,
  vacationDays: 23,
  normalDayMinutes: 480,
  intensiveDayMinutes: 420,
  breakfastToleranceMin: 20,
  minLunchMin: 30,
  roundingStepMin: 15,
  maxRemotePct: 50,
  openingBalanceMin: 0,
  intensiveRanges: [{ startDate: '2026-06-15', endDate: '2026-09-15' }],
  version: 0,
}

const RULES = { breakfastToleranceMin: 20, minLunchMin: 30 }

export function workday(
  date: string,
  startTime: string,
  endTime: string,
  breaks: BreakDto[] = [],
  extra: Partial<
    Pick<WorkdayDto, 'location' | 'remoteMinutes' | 'notes' | 'version'>
  > = {},
): WorkdayDto {
  const location: WorkLocation = extra.location ?? 'OFICINA'
  const remoteMinutes = location === 'MIXTO' ? (extra.remoteMinutes ?? null) : null
  const result = calculateWorkday({ startTime, endTime, breaks, location, remoteMinutes }, RULES)
  const { warnings, ...totals } = result
  return {
    date,
    startTime,
    endTime,
    breaks,
    location,
    remoteMinutes,
    notes: extra.notes ?? null,
    version: extra.version ?? 0,
    totals,
    warnings: warnings.map((w) => ({ code: 'LUNCH_BELOW_MINIMUM', field: w.field, message: w.message })),
  }
}

/** Día de referencia de la especificación: 07:25-17:59, desayuno 19 min, comida 30 min → 10:04. */
export const referenceDay = workday(
  '2026-10-01',
  '07:25',
  '17:59',
  [
    { type: 'DESAYUNO', startTime: '12:43', endTime: '13:02' },
    { type: 'COMIDA', startTime: '15:02', endTime: '15:32' },
  ],
  { version: 3 },
)

export function dashboard(overrides: Partial<DashboardDto> = {}): DashboardDto {
  return {
    today: TODAY,
    period: { id: period.id, name: period.name, startDate: period.startDate, endDate: period.endDate },
    balanceToDateMinutes: 594,
    currentMonth: {
      month: '2026-10',
      theoreticalMinutes: 10080,
      workedMinutes: 2406,
      differenceMinutes: -7674,
      theoreticalToDateMinutes: 2400,
      workedToDateMinutes: 2406,
      differenceToDateMinutes: 6,
      remotePct: 39.7,
      remoteDays: 2,
      maxRemotePct: 50,
      warnings: [],
    },
    vacations: { totalDays: 23, takenDays: 13, remainingDays: 10, pendingPlannedDays: 0 },
    hoursToRecoverMinutes: 840,
    projectionMinutes: -246,
    todayIsWorkingDay: true,
    todayDayMinutes: 480,
    todayWorkday: null,
    ...overrides,
  }
}

export function dashboardWithoutPeriod(): DashboardDto {
  return dashboard({
    period: null,
    balanceToDateMinutes: 0,
    currentMonth: null,
    vacations: null,
    hoursToRecoverMinutes: 0,
    projectionMinutes: 0,
    todayIsWorkingDay: false,
    todayDayMinutes: 0,
  })
}

// ---------------------------------------------------------------------------------------------
// Octubre de 2026: 21 laborables (el 12 es festivo), vacaciones el 9 y fichajes el 1, 2, 5 y 6.
// ---------------------------------------------------------------------------------------------

const vacation: AbsenceDto = { date: '2026-10-09', type: 'VACACIONES', halfDay: false, note: null }

const octoberWorkdays: Record<string, WorkdayDto> = {
  '2026-10-01': referenceDay,
  '2026-10-02': workday('2026-10-02', '08:00', '15:00', [], { location: 'CASA', version: 1 }),
  '2026-10-05': workday('2026-10-05', '08:00', '16:30', [{ type: 'COMIDA', startTime: '13:00', endTime: '13:26' }], {
    version: 2,
  }),
  '2026-10-06': workday('2026-10-06', '08:00', '16:30', [{ type: 'COMIDA', startTime: '13:00', endTime: '13:30' }], {
    location: 'MIXTO',
    remoteMinutes: 240,
    version: 0,
  }),
}

function round15(minutes: number): number {
  return Math.floor((minutes + 7.5) / 15) * 15
}

export function octoberSummary(): MonthSummaryDto {
  const days: DayDto[] = []
  let accumulated = 0
  let previousRounded = 0
  const theoreticalMonth = 9600
  for (let d = 1; d <= 31; d++) {
    const date = `2026-10-${String(d).padStart(2, '0')}`
    const weekday = new Date(2026, 9, d).getDay() // 0 domingo, 6 sábado
    const weekend = weekday === 0 || weekday === 6
    const holiday = d === 12
    const dayType = weekend ? 'FIN_DE_SEMANA' : holiday ? 'FESTIVO' : 'LABORABLE'
    const dayMinutes = dayType === 'LABORABLE' ? 480 : 0
    const absence = date === vacation.date ? vacation : null
    const w = octoberWorkdays[date] ?? null
    const worked = w?.totals.workedMinutes ?? 0
    accumulated += worked
    const rounded = round15(accumulated) - previousRounded
    previousRounded = round15(accumulated)
    const warnings: IssueDto[] = [...(w?.warnings ?? [])]
    if (date === '2026-10-01') {
      warnings.push({
        code: 'LUNCH_BELOW_MINIMUM',
        field: 'breaks',
        message: 'La comida dura 20 min: se descuenta el mínimo de 30 min',
      })
    }
    days.push({
      date,
      dayType,
      intensive: false,
      dayMinutes,
      holidayName: holiday ? 'Fiesta Nacional de España' : null,
      absence,
      workday: w,
      theoreticalMinutes: absence ? 0 : dayMinutes,
      workedMinutes: worked,
      roundedMinutes: rounded,
      countdownMinutes: theoreticalMonth - accumulated,
      warnings,
    })
  }
  const weekRanges: [string, string][] = [
    ['2026-10-01', '2026-10-04'],
    ['2026-10-05', '2026-10-11'],
    ['2026-10-12', '2026-10-18'],
    ['2026-10-19', '2026-10-25'],
    ['2026-10-26', '2026-10-31'],
  ]
  const weeks: WeekSummaryDto[] = weekRanges.map(([weekStart, weekEnd]) => {
    const inWeek = days.filter((d) => d.date >= weekStart && d.date <= weekEnd)
    return {
      weekStart,
      weekEnd,
      theoreticalMinutes: inWeek.reduce((t, d) => t + d.theoreticalMinutes, 0),
      workedMinutes: inWeek.reduce((t, d) => t + d.workedMinutes, 0),
      roundedMinutes: inWeek.reduce((t, d) => t + d.roundedMinutes, 0),
    }
  })
  return {
    periodId: period.id,
    month: '2026-10',
    status: 'CURRENT',
    workingDays: 21,
    normalDays: 21,
    intensiveDays: 0,
    calendarMinutes: 10080,
    theoreticalMinutes: 9600,
    vacationDays: 1,
    vacationMinutes: 480,
    bridgeDays: 0,
    bridgeMinutes: 0,
    workedMinutes: 1984,
    roundedMinutes: 1980,
    differenceMinutes: -7616,
    theoreticalToDateMinutes: 1920,
    workedToDateMinutes: 1984,
    differenceToDateMinutes: 64,
    openingBalanceMinutes: 530,
    closingBalanceMinutes: -7086,
    remoteMinutes: 660,
    officeMinutes: 1324,
    remotePct: 33.3,
    remoteDays: 2,
    maxRemotePct: 50,
    warnings: [],
    weeks,
    days,
  }
}

export function importPreview(overrides: Partial<ImportResultDto> = {}): ImportResultDto {
  return {
    dryRun: true,
    fileName: 'HORAS_IZERTIS_2026-27.xlsx',
    periodId: period.id,
    sheets: [
      { name: 'Mayo 26', month: '2026-05', rows: 4, dateCorrections: 5 },
      { name: 'Junio', month: '2026-06', rows: 22, dateCorrections: 0 },
    ],
    days: [
      {
        date: '2026-05-26',
        sheet: 'Mayo 26',
        row: 9,
        status: 'NEW',
        action: 'IMPORT',
        workday: {
          startTime: '07:25',
          endTime: '17:59',
          breaks: [
            { type: 'DESAYUNO', startTime: '12:43', endTime: '13:02' },
            { type: 'COMIDA', startTime: '15:02', endTime: '15:32' },
          ],
          location: 'OFICINA',
          remoteMinutes: null,
          notes: null,
        },
        excelWorkedMinutes: 604,
        computedWorkedMinutes: 604,
        messages: [],
      },
      {
        date: '2026-10-08',
        sheet: 'Octubre',
        row: 10,
        status: 'FUTURE',
        action: 'SKIP',
        workday: {
          startTime: '08:00',
          endTime: '16:00',
          breaks: [],
          location: 'OFICINA',
          remoteMinutes: null,
          notes: null,
        },
        excelWorkedMinutes: 480,
        computedWorkedMinutes: 480,
        messages: ['Día futuro: se omite salvo que incluyas los días futuros'],
      },
    ],
    absences: [{ date: '2026-07-10', type: 'VACACIONES', action: 'IMPORT', reason: 'Laborable sin fichaje' }],
    detectedSettings: {
      breakfastToleranceMin: 20,
      minLunchMin: 30,
      maxRemotePct: 50,
      normalDayMinutes: 480,
      intensiveDayMinutes: 420,
      vacationDays: 23,
      agreementMinutes: 105600,
    },
    warnings: ['1 día con fichaje queda fuera del periodo «2026-2027»'],
    counts: {
      toImport: 168,
      imported: 0,
      skippedFuture: 67,
      skippedExisting: 0,
      skippedOutOfPeriod: 0,
      invalid: 0,
      mismatches: 0,
      vacationsToCreate: 13,
      vacationsCreated: 0,
    },
    ...overrides,
  }
}
