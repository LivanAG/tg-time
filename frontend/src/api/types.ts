// Tipos de la API: reflejo exacto de docs/API.md. Fechas "YYYY-MM-DD", horas "HH:mm", meses "YYYY-MM"
// y duraciones en minutos enteros (con signo en saldos y diferencias). Los opcionales llegan como null.

/** Fecha "YYYY-MM-DD". */
export type IsoDate = string
/** Hora "HH:mm". */
export type TimeOfDay = string
/** Mes "YYYY-MM". */
export type YearMonth = string

// ---------------------------------------------------------------------------------------------
// Errores (ProblemDetail, RFC 7807) y avisos de cálculo
// ---------------------------------------------------------------------------------------------

export interface FieldErrorDto {
  field: string
  message: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: FieldErrorDto[]
}

export type IssueCode =
  | 'LUNCH_BELOW_MINIMUM'
  | 'MISSING_RECORD'
  | 'REMOTE_PCT_EXCEEDED'
  | 'VACATION_OVERPLANNED'

export interface IssueDto {
  code: IssueCode
  field: string | null
  message: string
}

// ---------------------------------------------------------------------------------------------
// Autenticación y usuarios
// ---------------------------------------------------------------------------------------------

export type Role = 'USER' | 'ADMIN'

export interface UserDto {
  id: string
  email: string
  name: string
  company: string | null
  timezone: string
  role: Role
}

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: UserDto
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
  company: string | null
  timezone: string
}

export interface UpdateProfileRequest {
  name: string
  company: string | null
  timezone: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface CreateUserRequest {
  name: string
  email: string
  password: string
  company: string | null
  timezone: string
  role: Role
}

// ---------------------------------------------------------------------------------------------
// Periodos, intensiva y festivos
// ---------------------------------------------------------------------------------------------

export interface IntensiveRangeDto {
  startDate: IsoDate
  endDate: IsoDate
}

export interface PeriodDto {
  id: string
  name: string
  startDate: IsoDate
  endDate: IsoDate
  agreementMinutes: number
  vacationDays: number
  normalDayMinutes: number
  intensiveDayMinutes: number
  breakfastToleranceMin: number
  minLunchMin: number
  roundingStepMin: number
  maxRemotePct: number
  openingBalanceMin: number
  intensiveRanges: IntensiveRangeDto[]
  /** Periodo con el que trabaja la app (selector global). La API marca exactamente uno si hay alguno. */
  selected: boolean
  version: number
}

/** Parámetros editables de un periodo (sin id, version ni selected). */
export type PeriodParameters = Omit<PeriodDto, 'id' | 'version' | 'selected'>

/** Cuerpo de POST /api/periods: los parámetros más preloadHolidays. */
export type CreatePeriodRequest = PeriodParameters & { preloadHolidays: boolean }

/** Cuerpo de PUT /api/periods/{id}: PeriodDto sin id, con version obligatoria. */
export type UpdatePeriodRequest = PeriodParameters & { version: number }

export type HolidayScope = 'NACIONAL' | 'AUTONOMICO' | 'LOCAL' | 'EMPRESA'

export interface HolidayDto {
  id: string
  date: IsoDate
  name: string
  scope: HolidayScope
}

export type HolidayRequest = Omit<HolidayDto, 'id'>

export type DayType = 'LABORABLE' | 'FIN_DE_SEMANA' | 'FESTIVO'

export interface CalendarDayDto {
  date: IsoDate
  dayType: DayType
  intensive: boolean
  dayMinutes: number
  holidayName: string | null
  absence: AbsenceDto | null
  hasWorkday: boolean
}

// ---------------------------------------------------------------------------------------------
// Registro diario
// ---------------------------------------------------------------------------------------------

export type BreakType = 'DESAYUNO' | 'COMIDA' | 'OTRA'

/** Ubicación del día (no se llama Location para no chocar con el tipo global del DOM). */
export type WorkLocation = 'OFICINA' | 'CASA' | 'MIXTO'

export interface BreakDto {
  type: BreakType
  startTime: TimeOfDay
  endTime: TimeOfDay
}

export interface WorkdayTotalsDto {
  grossMinutes: number
  breakfastMinutes: number
  breakfastDeductedMinutes: number
  lunchMinutes: number
  lunchDeductedMinutes: number
  otherBreakMinutes: number
  workedMinutes: number
  officeMinutes: number
  remoteMinutes: number
}

export interface WorkdayDto {
  date: IsoDate
  startTime: TimeOfDay
  endTime: TimeOfDay
  breaks: BreakDto[]
  location: WorkLocation
  /** Solo con MIXTO (null en otro caso): tramo de oficina y de casa. startTime/endTime son el primero y el último. */
  officeStart: TimeOfDay | null
  officeEnd: TimeOfDay | null
  homeStart: TimeOfDay | null
  homeEnd: TimeOfDay | null
  notes: string | null
  version: number
  totals: WorkdayTotalsDto
  warnings: IssueDto[]
}

/** Cuerpo de PUT /api/workdays/{date}. version: null al crear, la del último GET al actualizar. */
export interface WorkdayRequest {
  startTime: TimeOfDay | null
  endTime: TimeOfDay | null
  breaks: BreakDto[]
  location: WorkLocation
  /** Solo con MIXTO; startTime/endTime se calculan a partir de los tramos. */
  officeStart: TimeOfDay | null
  officeEnd: TimeOfDay | null
  homeStart: TimeOfDay | null
  homeEnd: TimeOfDay | null
  notes: string | null
  version: number | null
}

// ---------------------------------------------------------------------------------------------
// Ausencias
// ---------------------------------------------------------------------------------------------

export type AbsenceType = 'VACACIONES' | 'PUENTE' | 'PERMISO' | 'BAJA'

export interface AbsenceDto {
  date: IsoDate
  type: AbsenceType
  halfDay: boolean
  note: string | null
}

export interface AbsenceRequest {
  type: AbsenceType
  halfDay: boolean
  note: string | null
}

// ---------------------------------------------------------------------------------------------
// Resúmenes
// ---------------------------------------------------------------------------------------------

export type MonthStatus = 'PAST' | 'CURRENT' | 'FUTURE'

export type SummaryDayType = DayType | 'FUERA_DE_PERIODO'

export interface WeekSummaryDto {
  weekStart: IsoDate
  weekEnd: IsoDate
  theoreticalMinutes: number
  workedMinutes: number
  roundedMinutes: number
}

export interface DayDto {
  date: IsoDate
  dayType: SummaryDayType
  intensive: boolean
  dayMinutes: number
  holidayName: string | null
  absence: AbsenceDto | null
  workday: WorkdayDto | null
  theoreticalMinutes: number
  workedMinutes: number
  roundedMinutes: number
  countdownMinutes: number
  warnings: IssueDto[]
}

export interface MonthSummaryDto {
  periodId: string
  month: YearMonth
  status: MonthStatus
  workingDays: number
  normalDays: number
  intensiveDays: number
  calendarMinutes: number
  theoreticalMinutes: number
  vacationDays: number
  vacationMinutes: number
  bridgeDays: number
  bridgeMinutes: number
  workedMinutes: number
  roundedMinutes: number
  differenceMinutes: number
  theoreticalToDateMinutes: number
  workedToDateMinutes: number
  /** Suma de los redondeados de los días ya pasados (hoy cuenta si está fichado). */
  roundedToDateMinutes: number
  differenceToDateMinutes: number
  openingBalanceMinutes: number
  closingBalanceMinutes: number
  remoteMinutes: number
  officeMinutes: number
  remotePct: number
  remoteDays: number
  maxRemotePct: number
  warnings: IssueDto[]
  weeks: WeekSummaryDto[]
  days: DayDto[]
}

export interface VacationSummaryDto {
  totalDays: number
  plannedDays: number
  plannedMinutes: number
  takenDays: number
  takenMinutes: number
  pendingPlannedDays: number
  pendingPlannedMinutes: number
  remainingDays: number
  remainingMinutes: number
  unplannedDays: number
  unplannedMinutes: number
  valueMinutes: number
}

export interface MonthRowDto {
  month: YearMonth
  status: MonthStatus
  workingDays: number
  normalDays: number
  intensiveDays: number
  calendarMinutes: number
  vacationDays: number
  vacationMinutes: number
  theoreticalMinutes: number
  workedMinutes: number
  differenceMinutes: number
  bridgeMinutes: number
  cumulativeBalanceMinutes: number
}

export interface PeriodSummaryDto {
  periodId: string
  name: string
  startDate: IsoDate
  endDate: IsoDate
  today: IsoDate
  workingDays: number
  normalDays: number
  intensiveDays: number
  calendarMinutes: number
  agreementMinutes: number
  marginMinutes: number
  vacations: VacationSummaryDto
  hoursToRecoverMinutes: number
  remainingMarginMinutes: number
  workedMinutes: number
  theoreticalRemainingMinutes: number
  projectionMinutes: number
  openingBalanceMinutes: number
  balanceToDateMinutes: number
  bridgeMinutes: number
  months: MonthRowDto[]
  warnings: IssueDto[]
}

export interface DashboardPeriodDto {
  id: string
  name: string
  startDate: IsoDate
  endDate: IsoDate
}

export interface DashboardMonthDto {
  month: YearMonth
  theoreticalMinutes: number
  workedMinutes: number
  differenceMinutes: number
  theoreticalToDateMinutes: number
  workedToDateMinutes: number
  differenceToDateMinutes: number
  remotePct: number
  remoteDays: number
  maxRemotePct: number
  warnings: IssueDto[]
}

export interface DashboardVacationsDto {
  totalDays: number
  takenDays: number
  remainingDays: number
  pendingPlannedDays: number
}

export interface DashboardDto {
  today: IsoDate
  period: DashboardPeriodDto | null
  balanceToDateMinutes: number
  currentMonth: DashboardMonthDto | null
  vacations: DashboardVacationsDto | null
  hoursToRecoverMinutes: number
  projectionMinutes: number
  todayIsWorkingDay: boolean
  todayDayMinutes: number
  todayWorkday: WorkdayDto | null
}

// ---------------------------------------------------------------------------------------------
// Importar y exportar Excel
// ---------------------------------------------------------------------------------------------

export type ImportDayStatus = 'NEW' | 'EXISTS' | 'FUTURE' | 'OUT_OF_PERIOD' | 'INVALID' | 'NOT_REPRESENTABLE'

export type ImportAction = 'IMPORT' | 'SKIP'

export interface ImportSheetDto {
  name: string
  month: YearMonth
  rows: number
  dateCorrections: number
}

export interface ImportWorkdayDto {
  startTime: TimeOfDay | null
  endTime: TimeOfDay | null
  breaks: BreakDto[]
  location: WorkLocation | null
  officeStart: TimeOfDay | null
  officeEnd: TimeOfDay | null
  homeStart: TimeOfDay | null
  homeEnd: TimeOfDay | null
  notes: string | null
}

export interface ImportDayDto {
  date: IsoDate
  sheet: string
  row: number
  status: ImportDayStatus
  action: ImportAction
  workday: ImportWorkdayDto | null
  excelWorkedMinutes: number | null
  computedWorkedMinutes: number | null
  messages: string[]
}

/** Ausencia deducida del Excel de la empresa (vacaciones) o escrita en un Excel exportado por la app. */
export interface ImportAbsenceDto {
  date: IsoDate
  type: AbsenceType
  halfDay: boolean
  action: ImportAction
  reason: string | null
}

export interface DetectedSettingsDto {
  breakfastToleranceMin: number | null
  minLunchMin: number | null
  maxRemotePct: number | null
  normalDayMinutes: number | null
  intensiveDayMinutes: number | null
  vacationDays: number | null
  agreementMinutes: number | null
}

export interface ImportCountsDto {
  toImport: number
  imported: number
  skippedFuture: number
  skippedExisting: number
  skippedOutOfPeriod: number
  invalid: number
  mismatches: number
  vacationsToCreate: number
  vacationsCreated: number
}

export interface ImportResultDto {
  dryRun: boolean
  fileName: string
  periodId: string | null
  sheets: ImportSheetDto[]
  days: ImportDayDto[]
  absences: ImportAbsenceDto[]
  detectedSettings: DetectedSettingsDto | null
  warnings: string[]
  counts: ImportCountsDto
}

/** Campos del formulario multipart de POST /api/import/xlsx (salvo el fichero). */
export interface ImportOptions {
  dryRun: boolean
  periodId: string | null
  includeFuture: boolean
  markVacations: boolean
  overwrite: boolean
}
