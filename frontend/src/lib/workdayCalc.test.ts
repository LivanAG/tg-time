import { describe, expect, it } from 'vitest'

import type { BreakType, WorkLocation } from '../api/types'
import {
  BREAK_INVALID,
  BREAK_OUTSIDE_WORKDAY,
  BREAKS_OVERLAP,
  calculateWorkday,
  DUPLICATE_BREAKFAST,
  DUPLICATE_LUNCH,
  END_BEFORE_START,
  liveCalculation,
  LUNCH_BELOW_MINIMUM,
  REMOTE_MINUTES_EXCEED_WORKED,
  REMOTE_MINUTES_REQUIRED,
  validateWorkday,
  type CalcBreak,
  type CalcWorkday,
} from './workdayCalc'

// Mismos casos que WorkdayCalculatorTest (backend): tolerancia de desayuno 20 y comida mínima 30.
const rules = { breakfastToleranceMin: 20, minLunchMin: 30 }

function br(type: BreakType, startTime: string, endTime: string): CalcBreak {
  return { type, startTime, endTime }
}

function day(startTime: string | null, endTime: string | null, ...breaks: CalcBreak[]): CalcWorkday {
  return { startTime, endTime, breaks, location: 'OFICINA', remoteMinutes: null }
}

function codes(input: CalcWorkday): string[] {
  return validateWorkday(input, rules).map((e) => e.code)
}

describe('calculateWorkday', () => {
  it('día de referencia: 07:25-17:59, desayuno 12:43-13:02, comida 15:02-15:32 → 604 min (10:04)', () => {
    const input = day('07:25', '17:59', br('DESAYUNO', '12:43', '13:02'), br('COMIDA', '15:02', '15:32'))
    expect(validateWorkday(input, rules)).toEqual([])
    const result = calculateWorkday(input, rules)
    expect(result.grossMinutes).toBe(634)
    expect(result.breakfastMinutes).toBe(19)
    expect(result.breakfastDeductedMinutes).toBe(0)
    expect(result.lunchDeductedMinutes).toBe(30)
    expect(result.workedMinutes).toBe(604)
    expect(result.warnings).toEqual([])
  })

  it('un desayuno de 25 min descuenta solo los 5 que pasan de la tolerancia', () => {
    const result = calculateWorkday(day('08:00', '16:00', br('DESAYUNO', '10:00', '10:25')), rules)
    expect(result.breakfastMinutes).toBe(25)
    expect(result.breakfastDeductedMinutes).toBe(5)
    expect(result.workedMinutes).toBe(475)
  })

  it('una comida de 26 min descuenta el mínimo de 30 y avisa (09/06/2026 del Excel)', () => {
    const result = calculateWorkday(
      day('07:49', '17:35', br('DESAYUNO', '13:09', '13:28'), br('COMIDA', '15:53', '16:19')),
      rules,
    )
    expect(result.lunchMinutes).toBe(26)
    expect(result.lunchDeductedMinutes).toBe(30)
    expect(result.workedMinutes).toBe(9 * 60 + 16)
    expect(result.warnings).toEqual([
      {
        code: LUNCH_BELOW_MINIMUM,
        field: 'breaks',
        message: 'La comida dura 26 min: se descuenta el mínimo de 30 min',
      },
    ])
  })

  it('una comida larga descuenta su duración real', () => {
    expect(calculateWorkday(day('07:28', '17:45', br('COMIDA', '15:27', '15:59')), rules).lunchDeductedMinutes).toBe(32)
  })

  it('sin comida no se aplica el mínimo', () => {
    const result = calculateWorkday(day('08:00', '15:00'), rules)
    expect(result.lunchDeductedMinutes).toBe(0)
    expect(result.workedMinutes).toBe(420)
  })

  it('las otras pausas se descuentan enteras', () => {
    const result = calculateWorkday(
      day('08:00', '16:00', br('OTRA', '11:00', '11:10'), br('OTRA', '12:00', '12:05')),
      rules,
    )
    expect(result.otherBreakMinutes).toBe(15)
    expect(result.workedMinutes).toBe(465)
  })

  it('reparte oficina y casa según la ubicación', () => {
    const at = (location: WorkLocation, remoteMinutes: number | null) =>
      calculateWorkday({ startTime: '08:00', endTime: '16:00', breaks: [], location, remoteMinutes }, rules)
    expect(at('OFICINA', null)).toMatchObject({ officeMinutes: 480, remoteMinutes: 0 })
    expect(at('CASA', null)).toMatchObject({ officeMinutes: 0, remoteMinutes: 480 })
    expect(at('MIXTO', 180)).toMatchObject({ officeMinutes: 300, remoteMinutes: 180 })
  })
})

describe('validateWorkday', () => {
  it('mismos códigos que el backend', () => {
    expect(codes(day('16:00', '08:00'))).toEqual([END_BEFORE_START])
    expect(codes(day('08:00', '16:00', br('OTRA', '07:30', '08:15')))).toEqual([BREAK_OUTSIDE_WORKDAY])
    expect(codes(day('08:00', '16:00', br('OTRA', '10:00', '10:30'), br('COMIDA', '10:15', '11:00')))).toEqual([
      BREAKS_OVERLAP,
    ])
    expect(codes(day('08:00', '16:00', br('DESAYUNO', '10:00', '10:10'), br('DESAYUNO', '11:00', '11:10')))).toEqual([
      DUPLICATE_BREAKFAST,
    ])
    expect(codes(day('08:00', '18:00', br('COMIDA', '13:00', '13:30'), br('COMIDA', '14:00', '14:30')))).toEqual([
      DUPLICATE_LUNCH,
    ])
    expect(codes(day('08:00', '16:00', br('OTRA', '10:00', '10:00')))).toEqual([BREAK_INVALID])
    expect(codes({ ...day('08:00', '16:00'), location: 'MIXTO' })).toEqual([REMOTE_MINUTES_REQUIRED])
    expect(codes({ ...day('08:00', '16:00'), location: 'MIXTO', remoteMinutes: 500 })).toEqual([
      REMOTE_MINUTES_EXCEED_WORKED,
    ])
    expect(codes(day('07:25', '17:59', br('DESAYUNO', '12:43', '13:02')))).toEqual([])
  })

  it('mismos campos y mensajes que el backend', () => {
    expect(validateWorkday(day(null, '17:00'), rules)).toEqual([
      { code: END_BEFORE_START, field: 'startTime', message: 'La entrada y la salida son obligatorias' },
    ])
    expect(validateWorkday(day('17:00', '17:00'), rules)).toEqual([
      { code: END_BEFORE_START, field: 'endTime', message: 'La salida debe ser posterior a la entrada' },
    ])
    expect(
      validateWorkday(day('08:00', '16:00', br('OTRA', '10:00', '10:30'), br('COMIDA', '10:15', '11:00')), rules),
    ).toEqual([{ code: BREAKS_OVERLAP, field: 'breaks[1]', message: 'Las pausas no pueden solaparse' }])
    expect(validateWorkday({ ...day('08:00', '16:00'), location: 'MIXTO' }, rules)[0].message).toBe(
      'Indica cuántos minutos has trabajado en casa',
    )
  })

  it('el solape se informa en la pausa que empieza después, aunque esté antes en la lista', () => {
    const errors = validateWorkday(
      day('08:00', '16:00', br('COMIDA', '13:00', '13:30'), br('OTRA', '12:50', '13:10')),
      rules,
    )
    expect(errors).toEqual([{ code: BREAKS_OVERLAP, field: 'breaks[0]', message: 'Las pausas no pueden solaparse' }])
  })

  it('una pausa sin horas es BREAK_INVALID y no se compara con las demás', () => {
    const errors = validateWorkday(
      day('08:00', '16:00', br('OTRA', '', '10:00'), br('COMIDA', '13:00', '13:30')),
      rules,
    )
    expect(errors.map((e) => [e.code, e.field])).toEqual([[BREAK_INVALID, 'breaks[0]']])
  })
})

describe('liveCalculation', () => {
  it('calcula aunque falten los minutos en casa de MIXTO', () => {
    const live = liveCalculation({ ...day('08:00', '16:00'), location: 'MIXTO' }, rules)
    expect(live.errors.map((e) => e.code)).toEqual([REMOTE_MINUTES_REQUIRED])
    expect(live.result?.workedMinutes).toBe(480)
  })

  it('no calcula si faltan horas o la salida es anterior', () => {
    expect(liveCalculation(day('08:00', null), rules).result).toBeNull()
    expect(liveCalculation(day('16:00', '08:00'), rules).result).toBeNull()
  })
})
