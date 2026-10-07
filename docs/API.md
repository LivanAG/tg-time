# Contrato de la API

Fuente de verdad para backend y frontend. Todo cuelga de `/api`. Salvo `/api/auth/*`, todo exige
`Authorization: Bearer <accessToken>`, y el usuario sale siempre del token (claim `sub`), nunca de la URL.

## Convenciones

| Tipo | Formato JSON | Ejemplo |
|---|---|---|
| Fecha | `"YYYY-MM-DD"` | `"2026-05-26"` |
| Hora | `"HH:mm"` | `"07:25"` |
| Mes | `"YYYY-MM"` | `"2026-06"` |
| Duración | entero en **minutos** (con signo cuando es un saldo/diferencia) | `604` = 10:04 |
| Porcentaje | número con 1 decimal | `45.6` |
| Medios días | número (0,5 por medio día) | `12.5` |

- Los campos opcionales se envían como `null` (no se omiten en las respuestas).
- Enumerados en mayúsculas: ver cada recurso.
- Recurso de otro usuario o inexistente → **404** (nunca 403).
- Errores en formato **ProblemDetail** (RFC 7807, `Content-Type: application/problem+json`):

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Datos no válidos",
  "errors": [ { "field": "endTime", "message": "La salida debe ser posterior a la entrada" } ] }
```

  `errors` solo aparece en los 400 de validación. Estados usados: 400 validación, 401 sin sesión o
  credenciales incorrectas, 403 sin permiso / Origin no permitido / registro cerrado, 404, 409 conflicto
  (bloqueo optimista o duplicado), 413 fichero grande, 429 rate limit (con cabecera `Retry-After`).

`IssueDto` (avisos de cálculo): `{ "code": "LUNCH_BELOW_MINIMUM", "field": "breaks" | null, "message": "..." }`.
Códigos: `LUNCH_BELOW_MINIMUM`, `MISSING_RECORD`, `JIRA_IZERTIA_MISMATCH`, `JIRA_ROUNDED_MISMATCH`,
`IZERTIA_ROUNDED_MISMATCH`, `REMOTE_PCT_EXCEEDED`, `REMOTE_DAYS_EXCEEDED`, `VACATION_OVERPLANNED`.

---

## Autenticación — `/api/auth`

Sesión = access token JWT corto (15 min, el frontend lo guarda **solo en memoria**) + refresh token
opaco rotativo en cookie `refresh_token` (`HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 7 días;
`Secure` se desactiva solo en local con `APP_COOKIE_SECURE=false`).

`/api/auth/login`, `/register` y `/refresh` tienen rate limit (10/min por IP → 429) y comprueban la
cabecera `Origin` contra `APP_ALLOWED_ORIGINS` (si falta Origin se usa Referer; si faltan ambos → 403).
La cabecera `Authorization` se ignora en `/api/auth/*`.

### `POST /api/auth/login`
```json
{ "email": "livan@example.com", "password": "una-contraseña-larga" }
```
200 → `AuthResponse` + `Set-Cookie: refresh_token=...`
```json
{ "accessToken": "eyJ...", "expiresIn": 900,
  "user": { "id": "uuid", "email": "livan@example.com", "name": "Livan Aranda",
            "company": "IZERTIS", "timezone": "Europe/Madrid", "role": "ADMIN" } }
```
401 `{"detail": "Email o contraseña incorrectos"}` — mismo mensaje si el usuario no existe, está
deshabilitado o bloqueado (5 fallos → 15 min).

### `POST /api/auth/refresh`
Sin cuerpo; usa la cookie. Rota la cookie y devuelve un `AuthResponse` nuevo. 401 si falta, caducó, fue
revocada o reutilizada (reutilizar un token ya rotado revoca toda la familia). En 401 borra la cookie.

### `POST /api/auth/logout`
204. Revoca el refresh token actual y borra la cookie (`Max-Age=0`).

### `POST /api/auth/register`
Solo si `APP_REGISTRATION_OPEN=true` (si no, 403 `"El registro está cerrado"`).
```json
{ "name": "Livan Aranda", "email": "livan@example.com", "password": "...", "company": "IZERTIS", "timezone": "Europe/Madrid" }
```
201 → `UserDto`. 409 si el email ya existe. Política de contraseña (también en `/api/me/password` y
`/api/admin/users`): 12-128 caracteres, no está en la lista de contraseñas comunes, distinta del email.

### Usuario actual — `/api/me`
- `GET /api/me` → `UserDto`
- `PUT /api/me` `{ "name": "...", "company": "..." | null, "timezone": "Europe/Madrid" }` → `UserDto`
- `PUT /api/me/password` `{ "currentPassword": "...", "newPassword": "..." }` → 204. Revoca **todas**
  las sesiones (refresh tokens) del usuario: el frontend debe volver a pedir login.

### Administración — `/api/admin` (rol ADMIN)
- `GET /api/admin/users` → `[UserDto]`
- `POST /api/admin/users` `{ "name", "email", "password", "company", "timezone", "role": "USER" | "ADMIN" }`
  → 201 `UserDto` (alta "por invitación").

---

## Periodos — `/api/periods`

```json
PeriodDto {
  "id": "uuid", "name": "2026-2027",
  "startDate": "2026-05-26", "endDate": "2027-05-25",
  "agreementMinutes": 105600, "vacationDays": 23,
  "normalDayMinutes": 480, "intensiveDayMinutes": 420,
  "breakfastToleranceMin": 20, "minLunchMin": 30, "roundingStepMin": 15,
  "maxRemotePct": 50, "maxRemoteDaysMonth": 8, "openingBalanceMin": 0,
  "intensiveRanges": [ { "startDate": "2026-06-15", "endDate": "2026-09-15" } ],
  "version": 0
}
```

- `GET /api/periods` → `[PeriodDto]` (más reciente primero)
- `POST /api/periods` → 201 `PeriodDto`. Cuerpo = `PeriodDto` sin `id`/`version`, más
  `"preloadHolidays": true` (por defecto true: carga los festivos de Madrid del periodo).
- `GET /api/periods/{id}` → `PeriodDto`
- `PUT /api/periods/{id}` → `PeriodDto`. Cuerpo = `PeriodDto` sin `id`, con `version` obligatoria
  (409 si no coincide). `intensiveRanges` se ignora aquí (ver abajo).
- `DELETE /api/periods/{id}` → 204 (borra también sus festivos y rangos; no borra fichajes ni ausencias).

Validación: `name` 1-100; `startDate < endDate`; rangos válidos (minutos > 0, `roundingStepMin` 1-60,
`maxRemotePct` 0-100...); un periodo no puede solaparse con otro del mismo usuario (409); los rangos de
intensiva deben estar dentro del periodo y no solaparse entre sí (400).

Valores por defecto que propone el frontend al crear (los del Excel): los del ejemplo de arriba.

### Rangos de intensiva
- `GET /api/periods/{id}/intensive-ranges` → `[{ "startDate", "endDate" }]`
- `PUT /api/periods/{id}/intensive-ranges` cuerpo `[{ "startDate", "endDate" }]` → la lista guardada (sustituye todo).

### Festivos
`HolidayDto { "id": "uuid", "date": "2026-10-12", "name": "Fiesta Nacional de España", "scope": "NACIONAL" | "AUTONOMICO" | "LOCAL" | "EMPRESA" }`
- `GET /api/periods/{id}/holidays` → `[HolidayDto]` por fecha
- `POST /api/periods/{id}/holidays` `{ "date", "name", "scope" }` → 201 `HolidayDto` (400 fuera del periodo, 409 fecha repetida)
- `DELETE /api/periods/{id}/holidays/{holidayId}` → 204
- `POST /api/periods/{id}/holidays/preload` → `[HolidayDto]` (añade los precargados que falten; devuelve todos)

### Calendario
`GET /api/periods/{id}/calendar` → un elemento por cada día del periodo:
```json
CalendarDayDto { "date": "2026-10-12", "dayType": "LABORABLE" | "FIN_DE_SEMANA" | "FESTIVO",
  "intensive": false, "dayMinutes": 0, "holidayName": "Fiesta Nacional de España" | null,
  "absence": AbsenceDto | null, "hasWorkday": false }
```

---

## Registro diario — `/api/workdays`

```json
WorkdayDto {
  "date": "2026-05-26", "startTime": "07:25", "endTime": "17:59",
  "breaks": [ { "type": "DESAYUNO", "startTime": "12:43", "endTime": "13:02" },
              { "type": "COMIDA",   "startTime": "15:02", "endTime": "15:32" } ],
  "location": "OFICINA" | "CASA" | "MIXTO",
  "remoteMinutes": null, "jiraMinutes": 660, "izertiaMinutes": 660, "notes": null,
  "version": 3,
  "totals": { "grossMinutes": 634, "breakfastMinutes": 19, "breakfastDeductedMinutes": 0,
              "lunchMinutes": 30, "lunchDeductedMinutes": 30, "otherBreakMinutes": 0,
              "workedMinutes": 604, "officeMinutes": 604, "remoteMinutes": 0 },
  "warnings": [ IssueDto ]
}
```
`breaks[].type`: `DESAYUNO` | `COMIDA` | `OTRA`. Máximo un desayuno y una comida.

- `GET /api/workdays?from=2026-06-01&to=2026-06-30` → `[WorkdayDto]` (rango máximo 400 días)
- `GET /api/workdays/{date}` → `WorkdayDto` | 404
- `PUT /api/workdays/{date}` → 200 `WorkdayDto` (crea o actualiza; idempotente). Cuerpo:
  ```json
  { "startTime": "07:25", "endTime": "17:59", "breaks": [...], "location": "MIXTO",
    "remoteMinutes": 240, "jiraMinutes": null, "izertiaMinutes": null, "notes": null, "version": 3 }
  ```
  `version`: null al crear; la del último GET al actualizar. 409 si no coincide o si se intenta crear
  (version null) un día que ya existe. 400 si no hay periodo que incluya la fecha
  (`field: "date"`), o con los errores de validación del cálculo (`field`: `startTime`, `endTime`,
  `breaks[i]`, `remoteMinutes`). `notes` máx. 500; minutos ≥ 0. `remoteMinutes` solo se guarda con MIXTO.
- `DELETE /api/workdays/{date}` → 204 | 404

## Ausencias — `/api/absences`

`AbsenceDto { "date": "2026-07-10", "type": "VACACIONES" | "PUENTE" | "PERMISO" | "BAJA", "halfDay": false, "note": null }`

- `GET /api/absences?from=&to=` → `[AbsenceDto]`
- `GET /api/absences/{date}` → `AbsenceDto` | 404
- `PUT /api/absences/{date}` `{ "type", "halfDay", "note" }` → `AbsenceDto`. 400 si el día no es
  laborable dentro de un periodo.
- `DELETE /api/absences/{date}` → 204 | 404

---

## Resúmenes — `/api/summary`

### `GET /api/summary/month?year=2026&month=6[&periodId=uuid]`
Periodo por defecto: el que más días tenga dentro de ese mes (si empata, el más reciente). 404 si
ningún periodo toca el mes.
```json
MonthSummaryDto {
  "periodId": "uuid", "month": "2026-06", "status": "PAST" | "CURRENT" | "FUTURE",
  "workingDays": 22, "normalDays": 10, "intensiveDays": 12, "calendarMinutes": 9840,
  "theoreticalMinutes": 9840, "vacationDays": 0, "vacationMinutes": 0, "bridgeDays": 0, "bridgeMinutes": 0,
  "workedMinutes": 9960, "roundedMinutes": 9960, "differenceMinutes": 120,
  "theoreticalToDateMinutes": 9840, "workedToDateMinutes": 9960, "differenceToDateMinutes": 120,
  "openingBalanceMinutes": 90, "closingBalanceMinutes": 210,
  "remoteMinutes": 4545, "officeMinutes": 5415, "remotePct": 45.6, "remoteDays": 10,
  "maxRemotePct": 50, "maxRemoteDaysMonth": 8,
  "jiraMinutes": null, "izertiaMinutes": null, "imputationWarningDays": 0,
  "warnings": [ IssueDto ],
  "weeks": [ { "weekStart": "2026-06-01", "weekEnd": "2026-06-07", "theoreticalMinutes": 2400,
               "workedMinutes": 2356, "roundedMinutes": 2355 } ],
  "days": [ DayDto ]
}
DayDto {
  "date": "2026-06-01", "dayType": "LABORABLE" | "FIN_DE_SEMANA" | "FESTIVO" | "FUERA_DE_PERIODO",
  "intensive": false, "dayMinutes": 480, "holidayName": null,
  "absence": AbsenceDto | null, "workday": WorkdayDto | null,
  "theoreticalMinutes": 480, "workedMinutes": 466, "roundedMinutes": 465, "countdownMinutes": 9374,
  "warnings": [ IssueDto ]
}
```
`days` trae todos los días naturales del mes. `countdownMinutes` = teóricas del mes − trabajado acumulado
hasta ese día (columna T del Excel). `roundedMinutes` = redondeo sin deriva (columna P).

### `GET /api/summary/period/{id}`
```json
PeriodSummaryDto {
  "periodId": "uuid", "name": "2026-2027", "startDate": "2026-05-26", "endDate": "2027-05-25", "today": "2026-10-07",
  "workingDays": 248, "normalDays": 181, "intensiveDays": 67,
  "calendarMinutes": 115020, "agreementMinutes": 105600, "marginMinutes": 9420,
  "vacations": { "totalDays": 23, "plannedDays": 13, "plannedMinutes": 5460, "takenDays": 13, "takenMinutes": 5460,
                 "pendingPlannedDays": 0, "pendingPlannedMinutes": 0, "remainingDays": 10, "remainingMinutes": 4800,
                 "unplannedDays": 10, "unplannedMinutes": 4800, "valueMinutes": 10260 },
  "hoursToRecoverMinutes": 840, "remainingMarginMinutes": 3960,
  "workedMinutes": 37674, "theoreticalRemainingMinutes": 72480, "projectionMinutes": -246,
  "openingBalanceMinutes": 0, "balanceToDateMinutes": 594, "bridgeMinutes": 0,
  "months": [ { "month": "2026-05", "status": "PAST", "workingDays": 4, "normalDays": 4, "intensiveDays": 0,
                "calendarMinutes": 1920, "vacationDays": 0, "vacationMinutes": 0, "theoreticalMinutes": 1920,
                "workedMinutes": 2010, "differenceMinutes": 90, "bridgeMinutes": 0, "cumulativeBalanceMinutes": 90 } ],
  "warnings": [ IssueDto ]
}
```

### `GET /api/summary/dashboard`
Usa el periodo que contiene hoy (en la zona horaria del usuario). Si no hay, `period` es null y el
frontend muestra el asistente para crear el periodo.
```json
DashboardDto {
  "today": "2026-10-07",
  "period": { "id": "uuid", "name": "2026-2027", "startDate": "2026-05-26", "endDate": "2027-05-25" } | null,
  "balanceToDateMinutes": 594,
  "currentMonth": { "month": "2026-10", "theoreticalMinutes": 10080, "workedMinutes": 2406, "differenceMinutes": -7674,
                    "theoreticalToDateMinutes": 2400, "workedToDateMinutes": 2406, "differenceToDateMinutes": 6,
                    "remotePct": 39.7, "remoteDays": 2, "maxRemotePct": 50, "maxRemoteDaysMonth": 8,
                    "imputationWarningDays": 0, "warnings": [ IssueDto ] } | null,
  "vacations": { "totalDays": 23, "takenDays": 13, "remainingDays": 10, "pendingPlannedDays": 0 } | null,
  "hoursToRecoverMinutes": 840, "projectionMinutes": -246,
  "todayIsWorkingDay": true, "todayDayMinutes": 480, "todayWorkday": WorkdayDto | null
}
```

---

## Importar y exportar Excel

### `POST /api/import/xlsx` (`multipart/form-data`)
Campos: `file` (obligatorio, .xlsx ≤ 2 MB), `dryRun` (por defecto `true`), `periodId` (opcional; por
defecto el periodo que contiene más fechas del fichero), `includeFuture` (`false`), `includeImputations`
(`false`), `markVacations` (`true`), `overwrite` (`false`).

Flujo: el frontend sube con `dryRun=true`, enseña la vista previa, y al confirmar vuelve a subir el mismo
fichero con `dryRun=false` y las opciones elegidas. Respuesta (en ambos casos):
```json
ImportResultDto {
  "dryRun": true, "fileName": "HORAS_IZERTIS_2026-27.xlsx", "periodId": "uuid",
  "sheets": [ { "name": "Junio", "month": "2026-06", "rows": 22, "dateCorrections": 0 } ],
  "days": [ { "date": "2026-06-01", "sheet": "Junio", "row": 7,
              "status": "NEW" | "EXISTS" | "FUTURE" | "OUT_OF_PERIOD" | "INVALID" | "NOT_REPRESENTABLE",
              "action": "IMPORT" | "SKIP",
              "workday": { "startTime", "endTime", "breaks", "location", "remoteMinutes", "jiraMinutes",
                           "izertiaMinutes", "notes" },
              "excelWorkedMinutes": 466, "computedWorkedMinutes": 466, "messages": [ "..." ] } ],
  "absences": [ { "date": "2026-07-10", "type": "VACACIONES", "action": "IMPORT" | "SKIP", "reason": "..." } ],
  "detectedSettings": { "breakfastToleranceMin": 20, "minLunchMin": 30, "maxRemotePct": 50,
                        "maxRemoteDaysMonth": 8, "normalDayMinutes": 480, "intensiveDayMinutes": 420,
                        "vacationDays": 23, "agreementMinutes": 105600 },
  "warnings": [ "Las columnas JIRA/IZERTIA parecen copiadas de la plantilla: no se importan" ],
  "counts": { "toImport": 168, "imported": 0, "skippedFuture": 67, "skippedExisting": 0,
              "skippedOutOfPeriod": 0, "invalid": 0, "mismatches": 0,
              "vacationsToCreate": 13, "vacationsCreated": 0 }
}
```
400 si no es un .xlsx válido (tipo MIME, firma `PK\x03\x04`, sin macros) o no hay periodo; 413 si pasa de 2 MB.

### `GET /api/export/xlsx?year=2026&month=6`
Descarga `horas-2026-06.xlsx` con el formato de la hoja mensual del Excel original (mismas columnas,
por lo que se puede volver a importar).
