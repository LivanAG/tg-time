import { apiDownload, apiRequest, apiRequestOrNull } from './client'
import type {
  AbsenceDto,
  AbsenceRequest,
  AuthResponse,
  CalendarDayDto,
  ChangePasswordRequest,
  CreatePeriodRequest,
  CreateUserRequest,
  DashboardDto,
  HolidayDto,
  HolidayRequest,
  ImportOptions,
  ImportResultDto,
  IntensiveRangeDto,
  IsoDate,
  LoginRequest,
  MonthSummaryDto,
  PeriodDto,
  PeriodSummaryDto,
  UpdatePeriodRequest,
  UpdateProfileRequest,
  UserDto,
  WorkdayDto,
  WorkdayRequest,
} from './types'

// Autenticación ------------------------------------------------------------------------------

export const authApi = {
  login: (body: LoginRequest) => apiRequest<AuthResponse>('/auth/login', { method: 'POST', body }),
  logout: () => apiRequest<void>('/auth/logout', { method: 'POST' }),
}

// Usuario actual y administración -----------------------------------------------------------

export const meApi = {
  get: () => apiRequest<UserDto>('/me'),
  update: (body: UpdateProfileRequest) => apiRequest<UserDto>('/me', { method: 'PUT', body }),
  changePassword: (body: ChangePasswordRequest) => apiRequest<void>('/me/password', { method: 'PUT', body }),
}

export const adminApi = {
  listUsers: () => apiRequest<UserDto[]>('/admin/users'),
  createUser: (body: CreateUserRequest) => apiRequest<UserDto>('/admin/users', { method: 'POST', body }),
}

// Periodos, intensiva, festivos y calendario ------------------------------------------------

export const periodsApi = {
  list: () => apiRequest<PeriodDto[]>('/periods'),
  get: (id: string) => apiRequest<PeriodDto>(`/periods/${id}`),
  create: (body: CreatePeriodRequest) => apiRequest<PeriodDto>('/periods', { method: 'POST', body }),
  update: (id: string, body: UpdatePeriodRequest) => apiRequest<PeriodDto>(`/periods/${id}`, { method: 'PUT', body }),
  remove: (id: string) => apiRequest<void>(`/periods/${id}`, { method: 'DELETE' }),
  intensiveRanges: (id: string) => apiRequest<IntensiveRangeDto[]>(`/periods/${id}/intensive-ranges`),
  saveIntensiveRanges: (id: string, ranges: IntensiveRangeDto[]) =>
    apiRequest<IntensiveRangeDto[]>(`/periods/${id}/intensive-ranges`, { method: 'PUT', body: ranges }),
  holidays: (id: string) => apiRequest<HolidayDto[]>(`/periods/${id}/holidays`),
  addHoliday: (id: string, body: HolidayRequest) =>
    apiRequest<HolidayDto>(`/periods/${id}/holidays`, { method: 'POST', body }),
  removeHoliday: (id: string, holidayId: string) =>
    apiRequest<void>(`/periods/${id}/holidays/${holidayId}`, { method: 'DELETE' }),
  preloadHolidays: (id: string) => apiRequest<HolidayDto[]>(`/periods/${id}/holidays/preload`, { method: 'POST' }),
  calendar: (id: string) => apiRequest<CalendarDayDto[]>(`/periods/${id}/calendar`),
}

// Registro diario y ausencias ---------------------------------------------------------------

export const workdaysApi = {
  list: (from: IsoDate, to: IsoDate) => apiRequest<WorkdayDto[]>('/workdays', { query: { from, to } }),
  /** null si ese día no tiene registro (404). */
  get: (date: IsoDate) => apiRequestOrNull<WorkdayDto>(`/workdays/${date}`),
  save: (date: IsoDate, body: WorkdayRequest) => apiRequest<WorkdayDto>(`/workdays/${date}`, { method: 'PUT', body }),
  remove: (date: IsoDate) => apiRequest<void>(`/workdays/${date}`, { method: 'DELETE' }),
}

export const absencesApi = {
  list: (from: IsoDate, to: IsoDate) => apiRequest<AbsenceDto[]>('/absences', { query: { from, to } }),
  /** null si ese día no tiene ausencia (404). */
  get: (date: IsoDate) => apiRequestOrNull<AbsenceDto>(`/absences/${date}`),
  save: (date: IsoDate, body: AbsenceRequest) => apiRequest<AbsenceDto>(`/absences/${date}`, { method: 'PUT', body }),
  remove: (date: IsoDate) => apiRequest<void>(`/absences/${date}`, { method: 'DELETE' }),
}

// Resúmenes ---------------------------------------------------------------------------------

export const summaryApi = {
  month: (year: number, month: number, periodId?: string | null) =>
    apiRequest<MonthSummaryDto>('/summary/month', { query: { year, month, periodId } }),
  period: (id: string) => apiRequest<PeriodSummaryDto>(`/summary/period/${id}`),
  dashboard: () => apiRequest<DashboardDto>('/summary/dashboard'),
}

// Importar y exportar Excel -----------------------------------------------------------------

export const importExportApi = {
  importXlsx: (file: File, options: ImportOptions) => {
    const form = new FormData()
    form.append('file', file)
    form.append('dryRun', String(options.dryRun))
    if (options.periodId) {
      form.append('periodId', options.periodId)
    }
    form.append('includeFuture', String(options.includeFuture))
    form.append('includeImputations', String(options.includeImputations))
    form.append('markVacations', String(options.markVacations))
    form.append('overwrite', String(options.overwrite))
    return apiRequest<ImportResultDto>('/import/xlsx', { method: 'POST', body: form })
  },
  exportXlsx: (year: number, month: number) =>
    apiDownload('/export/xlsx', { year, month }, `horas-${year}-${String(month).padStart(2, '0')}.xlsx`),
}
