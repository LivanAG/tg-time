# Formato de los Excel: HORAS_IZERTIS (importación) y el que exporta la app

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
| N / O | inicio / fin del tramo en casa (MIXTO, "teletrabajo tardes") | tramo `homeStart`-`homeEnd`; la oficina es el resto de B-I (ver abajo) |
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

### Días mixtos (M)
El tramo en casa N/O debe empezar a la entrada (B) o terminar a la salida (I); la oficina es el resto de
la jornada. Si una "otra pausa" (G/H) está pegada al tramo en casa, es el hueco entre los dos tramos (no
trabajado): se quita de las pausas y la oficina termina (o empieza) donde ella. Un tramo en casa en mitad de
la jornada (oficina antes y después) no se puede representar: el día queda `INVALID`.

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

`GET /api/export/xlsx?year=&month=` genera una hoja (`Octubre 2026`) con un **diseño propio**, pensado para
leerse e imprimirse, que la importación también reconoce (`ExcelReportLayout`, `ExcelMonthWriter`,
`ExcelReportParser`). Todos los valores son calculados, sin fórmulas.

| Fila | Contenido |
|---|---|
| 1 | Título: `Registro de jornada · Octubre de 2026` |
| 2 | `Nombre`, `Empresa`, `Periodo` (etiquetas en A, E, I; valores en C, G, K) |
| 3 | `Jornada normal` (C), `Jornada intensiva` (G), `Teletrabajo máx.` (K, número 50 con formato `0 %`) |
| 4 | `Tolerancia desayuno` (C), `Comida mínima` (G), `Redondeo` (K) |
| 6-7 | Cabecera de la tabla en dos niveles (abajo) |
| 8… | Un día por fila (todos los del mes), con un subtotal debajo de cada semana y al final `Total del mes` |
| tras el total | `Resumen del mes` (al imprimir, en una segunda página) |

Columnas de la tabla:

| Col | Cabecera | Contenido |
|---|---|---|
| A | Fecha | fecha (`dd/mm/yyyy`) |
| B | Día | `Lunes`… |
| C | Ubicación | `Oficina`, `Casa`, `Mixto`; sin fichaje: `Fin de semana`, `Festivo: <nombre>`, `Fuera del periodo` o vacío |
| D | Ausencia | `Vacaciones`, `Puente recuperable`, `Permiso`, `Baja`; con ` (medio día)` si es medio día |
| E/F | Oficina · Entrada/Salida | entrada y salida si el día es de oficina, o el tramo de oficina si es mixto |
| G/H | Casa · Entrada/Salida | ídem para casa (cada hora aparece una sola vez) |
| I/J | Desayuno · Inicio/Fin | |
| K/L | Comida · Inicio/Fin | |
| M | Otras pausas | texto `11:00–11:15, 17:00–17:10` |
| N | Total | trabajado del día (`[h]:mm`) |
| O | Redondeado | redondeado del día, junto al total |
| P | Notas | notas del día |

Las filas de subtotal (`Semana 1 jun – 7 jun · teóricas 40:00 · diferencia +0:15`, total y redondeado en
N/O) y la de `Total del mes` no tienen fecha en A, así que la importación las salta. El resumen repite el
cierre del mes de la app: Horas (teóricas, hechas sin redondear y redondeadas, diferencia; mes completo y
hasta hoy), Saldo (apertura + diferencia − puentes = cierre), Ausencias y Teletrabajo.

Fines de semana en gris, festivos en rojo claro y ausencias de día completo en verde claro. Al abrirlo la
cabecera de la tabla queda fija al desplazarse; se imprime en A4 apaisado ajustado al ancho.

### Volver a importarlo

Una hoja se trata como exportada por la app si en la fila 6 están `Fecha` (A), `Oficina` (E) y `Casa` (G).
Se lee cada fila con fecha: la ubicación decide qué horas valen (con `Oficina` solo E/F, con `Casa` solo
G/H —las otras se ignoran con un aviso—, con `Mixto` las cuatro), las pausas de I-M, el total de N (para
avisar si no coincide con el calculado) y las notas de P. La columna Ausencia crea esa ausencia (también
las futuras, que son vacaciones planificadas) si el día es laborable en el periodo; si ya había otra
distinta solo se sustituye con `overwrite`. En estas hojas **no se deducen vacaciones** (ya vienen
escritas). Los parámetros de las filas 3-4 se proponen como en el Excel de la empresa. Exportar un mes e
importarlo en otra cuenta da los mismos fichajes y ausencias (test de ida y vuelta).

### Periodo completo

`GET /api/export/xlsx/period` (botón "Exportar a Excel" de la pantalla Resumen) genera un libro con:

1. Hoja `Resumen` (`ExcelPeriodWriter`): la misma cabecera de datos y parámetros (filas 1-4) y la tabla por
   meses de la pantalla Resumen: Mes (enlaza con su hoja; el actual marcado como «(actual)» y resaltado, los
   futuros en gris), Días laborables (total, normal, intensiva), Horas del mes, Vacaciones (días y horas),
   Teóricas, Hechas, Diferencia, Puentes y Saldo acumulado, con su fila Total. Debajo, los bloques «Margen
   sobre el convenio» y «Vacaciones y horas» (saldo hasta hoy y proyección), una nota con las fórmulas y los
   avisos del periodo. Cabe en una página A4 apaisada.
2. Una hoja por cada mes que toca el periodo (`Mayo 2026` … `Mayo 2027`), igual que la exportación de un mes.
   Cada una abre con el saldo acumulado al cerrar la anterior.

Al importarlo, la hoja `Resumen` se ignora (no tiene la cabecera de las hojas mensuales ni fechas en la
columna A) y se leen todas las hojas de mes.
