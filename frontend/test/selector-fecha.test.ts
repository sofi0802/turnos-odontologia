import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearCampo, crearEntrada } from '../src/shared/ui/campos.ts';
import { usarSelectorFecha } from '../src/shared/ui/selector-fecha.ts';
import { textoDeModulo } from './estilos.ts';

// C-39: los campos de fecha usan el calendario de la aplicación en lugar del selector nativo del navegador.

afterEach(() => {
  document.body.replaceChildren();
  vi.useRealTimers();
});

function armar(valor: string, opciones: Parameters<typeof usarSelectorFecha>[1] = {}) {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T15:00:00Z'));
  const campo = crearCampo('Día', crearEntrada('date', 'fecha'));
  campo.control.value = valor;
  if (opciones.minimo !== undefined && opciones.minimo !== null) {
    campo.control.min = opciones.minimo;
  }
  document.body.append(campo.contenedor);
  usarSelectorFecha(campo, opciones);
  const boton = campo.contenedor.querySelector<HTMLButtonElement>('button.selector-fecha-boton')!;
  const panel = campo.contenedor.querySelector<HTMLElement>('.selector-fecha-panel')!;
  return { campo, boton, panel };
}

describe('Selector de fecha', () => {
  it('muestra la fecha elegida en el botón y deja el valor en el campo', () => {
    const { campo, boton, panel } = armar('2026-12-05');
    expect(boton.textContent).toContain('05/12/2026');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(panel.hidden).toBe(true);
    expect(campo.control.value).toBe('2026-12-05');
    expect(campo.control.tabIndex).toBe(-1);
  });

  it('abre el calendario del mes elegido y al tocar un día cambia el valor y avisa', () => {
    const { campo, boton, panel } = armar('2026-12-05', { minimo: '2026-12-01' });
    const avisos: string[] = [];
    campo.control.addEventListener('change', () => avisos.push(campo.control.value));

    boton.click();
    expect(panel.hidden).toBe(false);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(panel.querySelector('.cal-mes')?.textContent?.toLowerCase()).toBe('diciembre de 2026');
    expect(panel.querySelector('[data-fecha="2026-12-05"]')?.getAttribute('aria-pressed')).toBe('true');

    panel.querySelector<HTMLButtonElement>('[data-fecha="2026-12-10"]')!.click();
    expect(campo.control.value).toBe('2026-12-10');
    expect(avisos).toEqual(['2026-12-10']);
    expect(panel.hidden).toBe(true);
    expect(boton.textContent).toContain('10/12/2026');
  });

  it('no deja elegir días anteriores al mínimo', () => {
    const { boton, panel } = armar('2026-12-05', { minimo: '2026-12-01' });
    boton.click();
    expect(panel.querySelector<HTMLButtonElement>('[data-fecha="2026-11-30"]')).toBeNull();
    expect(panel.querySelector<HTMLButtonElement>('[data-fecha="2026-12-01"]')?.disabled).toBe(false);
    expect(panel.querySelector<HTMLButtonElement>('.cal-nav[aria-label="Mes anterior"]')?.disabled).toBe(true);
  });

  it('con el campo opcional permite borrar la fecha', () => {
    const { campo, boton, panel } = armar('2026-12-05', { opcional: true });
    boton.click();
    panel.querySelector<HTMLButtonElement>('.selector-fecha-borrar')!.click();
    expect(campo.control.value).toBe('');
    expect(boton.textContent).toContain('Elegí un día');
  });

  it('se cierra con Escape sin cerrar lo que lo contiene, y al tocar afuera', () => {
    const { boton, panel } = armar('2026-12-05');
    const fuera = vi.fn();
    document.body.addEventListener('keydown', fuera);
    boton.click();
    panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    expect(panel.hidden).toBe(true);
    expect(fuera).not.toHaveBeenCalled();
    boton.click();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(panel.hidden).toBe(true);
  });

  it('sigue el valor cuando otro código lo cambia y avisa', async () => {
    const { campo, boton } = armar('2026-12-05');
    campo.control.value = '2026-12-20';
    campo.control.dispatchEvent(new Event('change', { bubbles: true }));
    await Promise.resolve();
    expect(boton.textContent).toContain('20/12/2026');
  });

  it('la etiqueta lleva el foco al botón', () => {
    const { campo, boton } = armar('2026-12-05');
    campo.control.dispatchEvent(new Event('focus'));
    expect(document.activeElement).toBe(boton);
  });

  it('se usa en todos los campos de fecha de la aplicación', () => {
    const base = textoDeModulo('11-nuevo-turno.css');
    expect(base).toMatch(/\.selector-fecha-panel/);
    expect(base).toMatch(/\.fecha-nativa/);
  });
});
