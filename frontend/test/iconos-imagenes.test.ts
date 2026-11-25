import { describe, expect, it } from 'vitest';
import { crearIcono, type NombreIcono } from '../src/shared/ui/iconos.ts';

/** C-47, tarea 3.3 (design D10): los cinco íconos de las imágenes tienen el mismo trazo que los existentes. */

const NUEVOS: readonly NombreIcono[] = ['imagen', 'documento', 'descarga', 'ver', 'subir'];

describe('Íconos de las imágenes clínicas', () => {
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

  it('cada ícono nuevo dibuja algo distinto', () => {
    const dibujos = NUEVOS.map((n) => crearIcono(n).innerHTML);
    expect(new Set(dibujos).size).toBe(NUEVOS.length);
  });
});
