# Formato del Excel HORAS_IZERTIS (importación y exportación)

Referencia para `importexport`. El fichero real está en
`backend/src/test/resources/excel/HORAS_IZERTIS_2026-27.xlsx` y su contenido, ya interpretado, en
`backend/src/test/resources/excel/days.csv` (una fila por día; columnas explicadas abajo).

## Hojas

- `Horas`: resumen anual. Parámetros útiles: `B1` jornada normal (08:00), `C1` jornada intensiva (07:00),
  `O3` días de vacaciones (23), `Q8` horas de convenio (1760 h, guardado como duración de Excel: días
  decimales × 1440 = minutos).
- Una hoja por mes (`Mayo 26`, `Junio`, ..., `Mayo`): todas con el mismo diseño. Se reconocen porque la
  columna A de las filas de datos contiene fechas. **No te fíes del nombre de la hoja ni de `B3`**
  (la hoja `Mayo` de 2027 tiene `B3 = 2026-05-01`).

## Cabecera de cada hoja mensual

| Celda | Significado | Valor típico |
|---|---|---|
| `F1` | Tiempo mínimo de comida | 0:30 |
| `F2` | Tolerancia de desayuno | 0:20 |
| `F3` | % máximo de teletrabajo | 50 |
| `J1` / `J2` | días a jornada normal / horas por día | 21 / 8:00 |
| `L1` / `L2` | días a jornada intensiva / horas por día (solo meses mixtos) | 12 / 7:00 |
| `J3` | días máximos de teletrabajo al mes (la app no lo usa: el límite es solo el % de `F3`) | 8 |

## Filas de datos

Cinco semanas de lunes a viernes: filas **7-11, 14-18, 21-25, 28-32, 35-39** (las filas 12, 19, 26, 33 y 40
son subtotales semanales; 43-54 el pie del mes).

**La fecha de cada fila se calcula por su posición**, no se lee de la columna A (la plantilla tiene
fechas erróneas: en `Mayo 26` las filas 21-25 dicen 11-15/06 y en `Agosto` las filas 21-25 dicen 2025):

1. Mes de la hoja = el (año, mes) más frecuente entre las fechas de la columna A de las filas de datos.
2. Primer día laborable del mes (si el día 1 cae en fin de semana, el lunes siguiente).
3. Inicio de la rejilla = lunes de esa semana. Fila `r` → `inicio + 7·semana + díaSemana`, con
   `semana = (r - 7) / 7` y `díaSemana = (r - 7) % 7` (0 = lunes ... 4 = viernes).
4. Las filas cuya fecha calculada no es de ese mes se ignoran. Si la columna A tiene otra fecha, se
   cuenta en `dateCorrections` y se añade un mensaje.

| Col. | Contenido | Uso |
|---|---|---|
| A | fecha (poco fiable) | solo para detectar el mes |
| B | ENTRADA | `startTime` (vacía = no se trabajó) |
| C / D | salida / entrada de desayuno | pausa `DESAYUNO` |
| E / F | salida / entrada de comida | pausa `COMIDA` |
| G / H | salida / entrada de otra pausa | pausa `OTRA` |
| I | SALIDA | `endTime` |
| J | total comida (a mano) | solo para la regla especial de abajo |
| K | desayuno descontado (fórmula) | — |
| L | Total Día (fórmula) | `excelWorkedMinutes`, para comparar con el cálculo |
| M | ubicación: `O` oficina, `C` casa, `M` mixto | `location` (vacía → OFICINA con mensaje) |
| N / O | inicio / fin del tramo en casa (MIXTO, "teletrabajo tardes") | `remoteMinutes = O - N` |
| P | redondeado (MROUND por día) | no se importa: se recalcula sin deriva |
| V / AC | JIRA / IZERTIA (imputaciones) | no se importan: la app no las usa |

Las horas son fracciones de día en Excel (o fechas-hora con la parte de fecha a 1899/1900): se convierten
a minutos redondeando al minuto. Se leen los **valores cacheados** de las celdas; nunca se evalúan fórmulas
ni macros.

### Regla especial: comida escrita a mano sin horas
Si `J > 0` pero E/F están vacías:
- si C/D existen y C ≥ 14:00, el tramo C/D era la comida → pausa `COMIDA` (caso real del 29/09/2026);
- si no, el día no se puede representar con pausas reales → estado `NOT_REPRESENTABLE`, acción `SKIP`
  (en el Excel solo pasa en días futuros precargados).

Con las reglas anteriores, **los 222 días representables del Excel dan exactamente el Total Día (L)**
(ver `WorkdayCalculatorTest`). Si `computedWorkedMinutes ≠ excelWorkedMinutes` se cuenta en `mismatches`.

## Datos que no son reales

- **Días futuros** (fecha > hoy en la zona del usuario): el Excel trae octubre-mayo ya rellenos copiando
  semanas anteriores. Estado `FUTURE`, se saltan salvo `includeFuture=true`.
- **Vacaciones**: el Excel no las marca; se deducen. Día laborable del periodo (según los festivos del
  periodo), ≤ hoy, sin fichaje → se propone `VACACIONES` (acción IMPORT si `markVacations`). En el Excel
  real salen 13: 10/07 y 13-14, 17-21 y 24-28 de agosto.

## Estados de cada día en la vista previa

| Estado | Cuándo | Acción por defecto |
|---|---|---|
| `NEW` | hay fichaje, fecha ≤ hoy, dentro del periodo y sin registro previo | IMPORT |
| `EXISTS` | ya hay un registro ese día | SKIP (IMPORT con `overwrite`) |
| `FUTURE` | fecha > hoy | SKIP (IMPORT con `includeFuture`) |
| `OUT_OF_PERIOD` | fuera del periodo elegido | SKIP |
| `INVALID` | no pasa `WorkdayCalculator.validate` (mensajes = errores) | SKIP |
| `NOT_REPRESENTABLE` | comida sin horas (regla especial) | SKIP |

Las ausencias propuestas siguen la misma lógica (`EXISTS` si ya hay ausencia o fichaje ese día).

## Exportación

`GET /api/export/xlsx?year=&month=` genera una hoja con el mismo diseño (cabecera F1-F3, J1/J2, L1/L2;
filas 7-39 por la misma rejilla; columnas B-I, M, N/O; L y P con los valores calculados; subtotales
semanales en L/P de las filas 12, 19, 26, 33, 40; pie: C46 teóricas, C47 hechas, C48 faltan, C49 sobran
y las vacaciones en C53). Debe poder **reimportarse** y dar los mismos datos (test de ida y vuelta).
