import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  aIsoConZona,
  diaDeLaSemana,
  fechaDeInstante,
  formatearHora,
  hoy,
  lunesDeLaSemana,
  sumarDias,
} from '../src/shared/fecha/zona.ts';

describe('Fechas en America/Argentina/Buenos_Aires', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('hoy es el día del consultorio aunque en UTC ya sea mañana', () => {
    vi.setSystemTime(new Date('2026-12-02T01:30:00Z'));
    expect(hoy()).toBe('2026-12-01');
  });

  it('hoy cambia a medianoche del consultorio', () => {
    vi.setSystemTime(new Date('2026-12-02T03:00:00Z'));
    expect(hoy()).toBe('2026-12-02');
  });

  it('inicio del día con la zona horaria', () => {
    expect(aIsoConZona('2026-12-01', '00:00')).toBe('2026-12-01T00:00:00-03:00');
    expect(aIsoConZona('2026-12-01', '10:30')).toBe('2026-12-01T10:30:00-03:00');
  });

  it('sumar días cruza meses y años', () => {
    expect(sumarDias('2026-12-01', 1)).toBe('2026-12-02');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-12-01', -1)).toBe('2026-11-30');
    expect(sumarDias('2026-12-01', 7)).toBe('2026-12-08');
  });

  it('la semana empieza el lunes', () => {
    expect(lunesDeLaSemana('2026-12-01')).toBe('2026-11-30');
    expect(lunesDeLaSemana('2026-11-30')).toBe('2026-11-30');
    expect(lunesDeLaSemana('2026-12-06')).toBe('2026-11-30');
    expect(lunesDeLaSemana('2026-12-07')).toBe('2026-12-07');
  });

  it('día de la semana en español', () => {
    expect(diaDeLaSemana('2026-11-30')).toBe('lunes');
    expect(diaDeLaSemana('2026-12-06')).toBe('domingo');
  });

  it('formatear la hora de un instante en UTC', () => {
    expect(formatearHora('2026-12-01T13:00:00Z')).toBe('10:00');
    expect(formatearHora('2026-12-01T10:30:00-03:00')).toBe('10:30');
  });

  it('fecha de un instante en la zona del consultorio', () => {
    expect(fechaDeInstante('2026-12-02T02:00:00Z')).toBe('2026-12-01');
    expect(fechaDeInstante('2026-12-01T10:00:00-03:00')).toBe('2026-12-01');
  });
});
