import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Duration } from '../components/Duration'
import {
  currentTime,
  durationInputValue,
  formatMinutes,
  formatOptionalMinutes,
  minutesToTime,
  normalizeTime,
  parseDuration,
  parseTime,
} from './time'

describe('formatMinutes', () => {
  it('formatea minutos como h:mm', () => {
    expect(formatMinutes(0)).toBe('0:00')
    expect(formatMinutes(45)).toBe('0:45')
    expect(formatMinutes(90)).toBe('1:30')
    expect(formatMinutes(604)).toBe('10:04')
    expect(formatMinutes(105600)).toBe('1760:00')
  })

  it('los negativos llevan siempre "-"', () => {
    expect(formatMinutes(-90)).toBe('-1:30')
    expect(formatMinutes(-5)).toBe('-0:05')
    expect(formatMinutes(-90, { signed: true })).toBe('-1:30')
  })

  it('con signo, los positivos llevan "+" y el cero no', () => {
    expect(formatMinutes(45, { signed: true })).toBe('+0:45')
    expect(formatMinutes(594, { signed: true })).toBe('+9:54')
    expect(formatMinutes(0, { signed: true })).toBe('0:00')
  })

  it('pinta "—" si no hay valor', () => {
    expect(formatOptionalMinutes(null)).toBe('—')
    expect(formatOptionalMinutes(undefined)).toBe('—')
    expect(formatOptionalMinutes(660)).toBe('11:00')
  })
})

describe('parseTime', () => {
  it('convierte "HH:mm" y "H:mm" en minutos desde las 00:00', () => {
    expect(parseTime('07:25')).toBe(445)
    expect(parseTime('7:25')).toBe(445)
    expect(parseTime('00:00')).toBe(0)
    expect(parseTime('23:59')).toBe(1439)
    expect(parseTime(' 17:59 ')).toBe(1079)
  })

  it('devuelve null si no es una hora válida', () => {
    expect(parseTime('')).toBeNull()
    expect(parseTime(null)).toBeNull()
    expect(parseTime('24:00')).toBeNull()
    expect(parseTime('12:60')).toBeNull()
    expect(parseTime('12')).toBeNull()
    expect(parseTime('ab:cd')).toBeNull()
  })

  it('ida y vuelta con minutesToTime y normalizeTime', () => {
    expect(minutesToTime(445)).toBe('07:25')
    expect(minutesToTime(parseTime('17:59') as number)).toBe('17:59')
    expect(normalizeTime('7:05')).toBe('07:05')
    expect(normalizeTime('basura')).toBe('basura')
  })

  it('currentTime usa la hora local', () => {
    expect(currentTime(new Date(2026, 9, 7, 8, 5))).toBe('08:05')
  })
})

describe('parseDuration', () => {
  it('admite horas sin límite y signo', () => {
    expect(parseDuration('10:04')).toBe(604)
    expect(parseDuration('1760:00')).toBe(105600)
    expect(parseDuration('-1:30')).toBe(-90)
    expect(parseDuration('+0:45')).toBe(45)
    expect(parseDuration('0:00')).toBe(0)
  })

  it('rechaza formatos no válidos', () => {
    expect(parseDuration('')).toBeNull()
    expect(parseDuration('10')).toBeNull()
    expect(parseDuration('1:5')).toBeNull()
    expect(parseDuration('1:60')).toBeNull()
  })

  it('durationInputValue es el inverso para los campos', () => {
    expect(durationInputValue(660)).toBe('11:00')
    expect(durationInputValue(null)).toBe('')
  })
})

describe('Duration', () => {
  it('pinta los negativos en rojo con su signo', () => {
    render(<Duration minutes={-90} />)
    const value = screen.getByText('-1:30')
    expect(value).toHaveClass('text-red-600')
  })

  it('pinta los positivos en verde con "+"', () => {
    render(<Duration minutes={45} />)
    const value = screen.getByText('+0:45')
    expect(value).toHaveClass('text-emerald-700')
  })

  it('sin signo no colorea', () => {
    render(<Duration minutes={604} signed={false} />)
    const value = screen.getByText('10:04')
    expect(value).not.toHaveClass('text-emerald-700')
    expect(value).not.toHaveClass('text-red-600')
  })

  it('el cero es neutro', () => {
    render(<Duration minutes={0} />)
    expect(screen.getByText('0:00')).not.toHaveClass('text-red-600')
  })
})
