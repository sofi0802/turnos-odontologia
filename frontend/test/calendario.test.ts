import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearCalendario, type Calendario } from '../src/shared/ui/calendario.ts';
import { boton } from './apoyo.ts';

let elegidas: string[] = [];

function abrir(hoyIso: string, habilitado = true): Calendario {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(hoyIso));
  elegidas = [];
  const calendario = crearCalendario({ leyenda: 'Fecha', alElegir: (fecha) => elegidas.push(fecha) });
  document.body.replaceChildren(calendario.contenedor);
  calendario.habilitar(habilitado);
  return calendario;
}

function dias(calendario: Calendario): HTMLButtonElement[] {
  return [...calendario.contenedor.querySelectorAll<HTMLButtonElement>('.cal-dia:not(.otro-mes)')];
}

function dia(calendario: Calendario, numero: number): HTMLButtonElement {
  const encontrado = dias(calendario).find((b) => b.textContent === String(numero));
  if (encontrado === undefined) {
    throw new Error(`No existe el día ${numero}`);
  }
  return encontrado;
}

function mes(calendario: Calendario): string {
  return calendario.contenedor.querySelector('.cal-mes')?.textContent ?? '';
}

afterEach(() => {
  vi.useRealTimers();
});

describe('Calendario mensual', () => {
  it('empieza en el mes de hoy con los días anteriores deshabilitados', () => {
    const calendario = abrir('2026-12-15T15:00:00Z');

    expect(mes(calendario)).toBe('diciembre de 2026');
    expect(dias(calendario)).toHaveLength(31);
    expect(dia(calendario, 14).disabled).toBe(true);
    expect(dia(calendario, 15).disabled).toBe(false);
    expect(dia(calendario, 31).disabled).toBe(false);
    expect(dia(calendario, 15).getAttribute('aria-label')).toBe('martes 15 de diciembre de 2026, hoy');
    expect(boton(calendario.contenedor, 'Mes anterior').disabled).toBe(true);
  });

  it('los días de otro mes que completan la semana están deshabilitados', () => {
    const calendario = abrir('2026-12-01T15:00:00Z');

    const relleno = [...calendario.contenedor.querySelectorAll<HTMLButtonElement>('.cal-dia.otro-mes')];
    expect(relleno.map((b) => b.textContent)).toEqual(['30', '1', '2', '3']);
    expect(relleno.every((b) => b.disabled)).toBe(true);
    expect(dia(calendario, 1).disabled).toBe(false);
  });

  it('un mes que empieza en domingo deja seis días de relleno antes', () => {
    const calendario = abrir('2026-11-01T15:00:00Z');

    const botones = [...calendario.contenedor.querySelectorAll<HTMLButtonElement>('.cal-dia')];
    expect(botones.slice(0, 6).every((b) => b.classList.contains('otro-mes'))).toBe(true);
    expect(botones[6]?.textContent).toBe('1');
    expect(botones[6]?.disabled).toBe(false);
  });

  it('pasa de diciembre a enero y vuelve', () => {
    const calendario = abrir('2026-12-01T15:00:00Z');

    boton(calendario.contenedor, 'Mes siguiente').click();
    expect(mes(calendario)).toBe('enero de 2027');
    expect(dias(calendario).every((b) => !b.disabled)).toBe(true);
    expect(boton(calendario.contenedor, 'Mes anterior').disabled).toBe(false);

    boton(calendario.contenedor, 'Mes anterior').click();
    expect(mes(calendario)).toBe('diciembre de 2026');
    expect(boton(calendario.contenedor, 'Mes anterior').disabled).toBe(true);
  });

  it('elegir un día lo marca y avisa la fecha', () => {
    const calendario = abrir('2026-12-01T15:00:00Z');

    dia(calendario, 3).click();

    expect(elegidas).toEqual(['2026-12-03']);
    expect(calendario.elegido()).toBe('2026-12-03');
    expect(dia(calendario, 3).getAttribute('aria-pressed')).toBe('true');
    expect(dias(calendario).filter((b) => b.getAttribute('aria-pressed') === 'true')).toHaveLength(1);
  });

  it('el día elegido sigue marcado al volver a su mes', () => {
    const calendario = abrir('2026-12-01T15:00:00Z');
    dia(calendario, 3).click();

    boton(calendario.contenedor, 'Mes siguiente').click();
    boton(calendario.contenedor, 'Mes anterior').click();

    expect(dia(calendario, 3).getAttribute('aria-pressed')).toBe('true');
  });

  it('deshabilitado no deja elegir días ni cambiar de mes', () => {
    const calendario = abrir('2026-12-01T15:00:00Z', false);

    expect(dias(calendario).every((b) => b.disabled)).toBe(true);
    expect(boton(calendario.contenedor, 'Mes siguiente').disabled).toBe(true);
    expect(calendario.contenedor.disabled).toBe(true);
  });

  it('limpiar quita el día elegido y vuelve al mes de hoy', () => {
    const calendario = abrir('2026-12-01T15:00:00Z');
    boton(calendario.contenedor, 'Mes siguiente').click();
    dia(calendario, 5).click();

    calendario.limpiar();

    expect(calendario.elegido()).toBeNull();
    expect(mes(calendario)).toBe('diciembre de 2026');
    expect(dias(calendario).some((b) => b.getAttribute('aria-pressed') === 'true')).toBe(false);
  });

  it('la ayuda del calendario se puede cambiar', () => {
    const calendario = abrir('2026-12-01T15:00:00Z', false);

    calendario.habilitar(false, 'Elegí primero un profesional.');

    expect(calendario.contenedor.textContent).toContain('Elegí primero un profesional.');
  });
});
