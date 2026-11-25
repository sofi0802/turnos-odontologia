import { describe, expect, it } from 'vitest';
import { crearIcono, type NombreIcono } from '../src/shared/ui/iconos.ts';

/** C-49, tarea 1.2 (design D10): "credencial" (Coberturas) y "cuenta" (Mi cuenta del paciente). */

const NUEVOS: readonly NombreIcono[] = ['credencial', 'cuenta'];

describe('Íconos de las coberturas', () => {
  it.each(NUEVOS)('%s es un SVG decorativo con el trazo de los demás íconos', (nombre) => {
    const comparado = crearIcono('reloj');
    const icono = crearIcono(nombre);

    expect(icono.getAttribute('aria-hidden')).toBe('true');
    expect(icono.getAttribute('focusable')).toBe('false');
    expect(icono.getAttribute('class')).toBe('icono');
    for (const atributo of ['viewBox', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin']) {
      expect(icono.getAttribute(atributo)).toBe(comparado.getAttribute(atributo));
    }
    expect(icono.childElementCount).toBeGreaterThan(0);
  });

  it('los dos íconos dibujan algo distinto de los existentes', () => {
    const existentes = (['pacientes', 'llave', 'documento'] as const).map((n) => crearIcono(n).innerHTML);
    const dibujos = NUEVOS.map((n) => crearIcono(n).innerHTML);
    expect(new Set([...dibujos, ...existentes]).size).toBe(dibujos.length + existentes.length);
  });
});
