import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CambioOdontograma, RegistroOdontograma } from '../src/ficha/api.ts';
import { crearOdontograma, type OpcionesOdontograma } from '../src/ficha/odontograma.ts';
import { ErrorValidacion } from '../src/shared/api/errores.ts';
import { mensajeDeError } from '../src/shared/ui/mensajes.ts';
import { boton, texto } from './apoyo.ts';
import { textoDeModulo } from './estilos.ts';

// C-44: escenarios de "Odontograma", "Estado de pieza completa", "Odontograma accesible" y "Registrar cambios en
// el odontograma" sobre el componente. Los pedidos a la API se prueban en `ficha-pacientes.test.ts`.

let raiz: HTMLElement;

function registro(datos: Partial<RegistroOdontograma> & Pick<RegistroOdontograma, 'pieza'>): RegistroOdontograma {
  return { id: 1, cara: 'O', estado: 'SANO', profesional: 'Ana Ejemplo', registradoEn: '2026-12-01T10:00:00-03:00', ...datos };
}

function montar(opciones: Partial<OpcionesOdontograma> = {}): ReturnType<typeof crearOdontograma> {
  const odontograma = crearOdontograma({ registros: [], editable: true, mensajeDeError, ...opciones });
  raiz = odontograma.elemento;
  document.body.replaceChildren(raiz);
  return odontograma;
}

function cara(pieza: number, nombre: string): SVGPolygonElement {
  const encontrada = raiz.querySelector<SVGPolygonElement>(`polygon[data-pieza="${pieza}"][data-cara="${nombre}"]`);
  if (encontrada === null) {
    throw new Error(`No se ve la pieza ${pieza}, cara ${nombre}`);
  }
  return encontrada;
}

function piezasVisibles(): number[] {
  return [...new Set([...raiz.querySelectorAll<SVGPolygonElement>('polygon.cara')].map((c) => Number(c.dataset['pieza'])))].sort(
    (a, b) => a - b,
  );
}

function opcion(nombre: 'cara' | 'estado', textoVisible: string): HTMLInputElement {
  const etiqueta = [...raiz.querySelectorAll<HTMLLabelElement>('label.opcion')].find(
    (l) => l.querySelector(`input[name="${nombre}"]`) !== null && texto(l).trim().endsWith(textoVisible),
  );
  const entrada = etiqueta?.querySelector('input');
  if (entrada === null || entrada === undefined) {
    throw new Error(`No existe la opción ${textoVisible}`);
  }
  return entrada;
}

function elegirOpcion(nombre: 'cara' | 'estado', textoVisible: string): void {
  const entrada = opcion(nombre, textoVisible);
  entrada.checked = true;
  entrada.dispatchEvent(new Event('change', { bubbles: true }));
}

function sumar(pieza: number, nombreCara: string, estado: string, completa = false): void {
  cara(pieza, nombreCara).dispatchEvent(new MouseEvent('click', { bubbles: true }));
  if (completa) {
    elegirOpcion('cara', 'Pieza completa');
  }
  elegirOpcion('estado', estado);
  boton(raiz, 'Sumar cambio').click();
}

function pendientes(): string[] {
  return [...raiz.querySelectorAll('.pendientes li span')].map((s) => s.textContent ?? '');
}

function aviso(rol: 'alert' | 'status'): string {
  return raiz.querySelector(`[role="${rol}"]`)?.textContent ?? '';
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('Odontograma', () => {
  it('Estado vigente dibujado', () => {
    montar({
      editable: false,
      registros: [registro({ pieza: 16, cara: 'O', estado: 'OBTURADO' }), registro({ id: 2, pieza: 26, cara: 'M', estado: 'A_TRATAR' })],
    });

    expect(cara(16, 'O').dataset['estado']).toBe('OBTURADO');
    expect(cara(26, 'M').dataset['estado']).toBe('A_TRATAR');
    expect(cara(16, 'M').dataset['estado']).toBeUndefined();
    expect(raiz.querySelectorAll('polygon.cara[data-estado]')).toHaveLength(2);
    expect([...raiz.querySelectorAll('.leyenda li')].map((l) => l.lastChild?.textContent)).toEqual([
      'Sano',
      'Caries',
      'Obturado',
      'A tratar',
      'Ausente',
      'Corona',
      'Endodoncia',
    ]);
  });

  it('Ver las piezas temporales', () => {
    montar();
    expect(piezasVisibles()).toHaveLength(32);

    boton(raiz, 'Temporales').click();

    expect(piezasVisibles()).toEqual([51, 52, 53, 54, 55, 61, 62, 63, 64, 65, 71, 72, 73, 74, 75, 81, 82, 83, 84, 85]);
    expect(boton(raiz, 'Temporales').getAttribute('aria-pressed')).toBe('true');
  });

  it('Odontograma vacío', () => {
    montar();

    expect(raiz.querySelectorAll('polygon.cara[data-estado]')).toHaveLength(0);
    expect(texto(raiz)).toContain('Sin registros en el odontograma.');
  });
});

describe('Estado de pieza completa', () => {
  it('Pieza ausente', () => {
    montar({ editable: false, registros: [registro({ pieza: 36, cara: null, estado: 'AUSENTE' })] });

    for (const nombre of ['O', 'M', 'D', 'V', 'L']) {
      expect(cara(36, nombre).dataset['estado']).toBe('AUSENTE');
    }
    expect(raiz.querySelectorAll('.marca-ausente')).toHaveLength(2);
    expect(texto(raiz)).toContain('Pieza 36 · pieza completa');
  });
});

describe('Odontograma accesible', () => {
  it('Nombre accesible de una cara', () => {
    montar({ registros: [registro({ pieza: 16, cara: 'O', estado: 'OBTURADO' })] });

    expect(cara(16, 'O').getAttribute('aria-label')).toBe('Pieza 16, cara oclusal: Obturado');
    expect(cara(16, 'O').getAttribute('role')).toBe('button');
  });

  it('Elegir una cara con el teclado', () => {
    montar();
    const oclusal = cara(16, 'O');
    oclusal.focus();

    oclusal.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(raiz.querySelector('.editor-pieza h3')?.textContent).toBe('Pieza 16 · superior derecha');
    expect(opcion('cara', 'Oclusal').checked).toBe(true);
    expect(cara(16, 'O').getAttribute('aria-pressed')).toBe('true');
  });

  it('una sola cara en el orden de tabulación y las flechas la mueven', () => {
    montar();
    expect(raiz.querySelectorAll('polygon.cara[tabindex="0"]')).toHaveLength(1);
    const primera = raiz.querySelector<SVGPolygonElement>('polygon.cara[tabindex="0"]');

    primera?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));

    expect(primera?.getAttribute('tabindex')).toBe('-1');
    expect(raiz.querySelectorAll('polygon.cara[tabindex="0"]')).toHaveLength(1);
  });
});

describe('Registrar cambios en el odontograma', () => {
  it('Guardar varios cambios juntos', async () => {
    const guardar = vi.fn<(cambios: readonly CambioOdontograma[]) => Promise<readonly RegistroOdontograma[]>>(async (cambios) =>
      cambios.map((c, i) => registro({ id: 10 + i, pieza: c.pieza, cara: c.cara, estado: c.estado })),
    );
    const alGuardar = vi.fn();
    const odontograma = montar({ guardar, alGuardar });

    sumar(16, 'O', 'Caries');
    sumar(36, 'O', 'Ausente', true);
    expect(odontograma.pendientes()).toBe(2);
    boton(raiz, 'Guardar cambios (2)').click();

    await vi.waitFor(() => expect(aviso('status')).toBe('Odontograma guardado.'));
    expect(guardar).toHaveBeenCalledWith([
      { pieza: 16, cara: 'O', estado: 'CARIES' },
      { pieza: 36, cara: null, estado: 'AUSENTE' },
    ]);
    expect(odontograma.pendientes()).toBe(0);
    expect(pendientes()).toEqual([]);
    expect(cara(16, 'O').dataset['estado']).toBe('CARIES');
    expect(cara(36, 'L').dataset['estado']).toBe('AUSENTE');
    expect(alGuardar).toHaveBeenCalledOnce();
  });

  it('Quitar un cambio pendiente', () => {
    const guardar = vi.fn<(cambios: readonly CambioOdontograma[]) => Promise<readonly RegistroOdontograma[]>>();
    const odontograma = montar({ guardar });
    sumar(16, 'O', 'Caries');
    sumar(26, 'M', 'Obturado');

    boton(raiz, 'Quitar el cambio de la pieza 16, oclusal').click();

    expect(odontograma.pendientes()).toBe(1);
    expect(texto(raiz)).toContain('Cambios sin guardar (1)');
    expect(guardar).not.toHaveBeenCalled();
  });

  it('Límite de cambios', () => {
    const odontograma = montar();
    const piezas = MITADES_PERMANENTES;
    for (const pieza of piezas) {
      sumar(pieza, 'O', 'Sano');
    }
    expect(odontograma.pendientes()).toBe(32);

    sumar(16, 'M', 'Caries');

    expect(aviso('alert')).toBe('Guardá los cambios antes de sumar más (máximo 32).');
    expect(odontograma.pendientes()).toBe(32);
  });

  it('Caras según el maxilar', () => {
    montar();

    cara(16, 'O').dispatchEvent(new MouseEvent('click', { bubbles: true }));

    const ofrecidas = [...raiz.querySelectorAll<HTMLLabelElement>('label.opcion')]
      .filter((l) => l.querySelector('input[name="cara"]') !== null)
      .map((l) => texto(l).trim());
    expect(ofrecidas).toEqual(['Oclusal', 'Mesial', 'Distal', 'Vestibular', 'Palatina', 'Pieza completa']);
  });

  it('El servidor rechaza un cambio', async () => {
    const guardar = vi.fn(async () => {
      throw new ErrorValidacion('Datos inválidos', 'CARA_INVALIDA');
    });
    const odontograma = montar({ guardar });
    sumar(16, 'O', 'Caries');

    boton(raiz, 'Guardar cambios (1)').click();

    await vi.waitFor(() => expect(aviso('alert')).toBe('Hay una cara que no corresponde a esa pieza.'));
    expect(odontograma.pendientes()).toBe(1);
  });

  it('el administrador no ve el panel de cambios', () => {
    montar({ editable: false });

    cara(16, 'O').dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(raiz.querySelector('.editor-pieza')).toBeNull();
    expect(cara(16, 'O').getAttribute('role')).toBe('img');
  });
});

describe('Estados de la pieza completa', () => {
  it('al elegir corona la cara pasa sola a "Pieza completa"', () => {
    const odontograma = montar();
    cara(16, 'O').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(opcion('cara', 'Oclusal').checked).toBe(true);

    elegirOpcion('estado', 'Corona');

    expect(opcion('cara', 'Pieza completa').checked).toBe(true);
    expect(opcion('estado', 'Corona').checked).toBe(true);
    boton(raiz, 'Sumar cambio').click();
    expect(odontograma.pendientes()).toBe(1);
    expect(pendientes()[0]).toBe('CoPieza 16 · pieza completa → Corona');
    expect(raiz.querySelectorAll('.marca-corona')).toHaveLength(1);
  });

  it('elegir después una cara descarta el estado de pieza completa', () => {
    montar();
    cara(16, 'O').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    elegirOpcion('estado', 'Endodoncia');

    elegirOpcion('cara', 'Mesial');

    expect(opcion('cara', 'Mesial').checked).toBe(true);
    expect(opcion('estado', 'Endodoncia').checked).toBe(false);
  });

  it('con endodoncia elegida, tocar otra pieza la elige completa', () => {
    montar();
    cara(16, 'O').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    elegirOpcion('estado', 'Endodoncia');

    cara(26, 'M').dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(raiz.querySelector('.editor-pieza h3')?.textContent).toBe('Pieza 26 · superior izquierda');
    expect(opcion('cara', 'Pieza completa').checked).toBe(true);
  });

  it('las caras de ausente, corona y endodoncia llevan un tinte de su color', () => {
    const css = textoDeModulo('17-ficha.css');
    for (const [estado, token] of [
      ['AUSENTE', 'ausente'],
      ['CORONA', 'corona'],
      ['ENDODONCIA', 'endodoncia'],
    ]) {
      expect(css).toContain(`.cara[data-estado='${estado}'] { fill: color-mix(in srgb, var(--ds-odonto-${token})`);
    }
  });
});

/** Las 32 piezas permanentes. */
const MITADES_PERMANENTES = [
  11, 12, 13, 14, 15, 16, 17, 18, 21, 22, 23, 24, 25, 26, 27, 28, 31, 32, 33, 34, 35, 36, 37, 38, 41, 42, 43, 44, 45, 46,
  47, 48,
];
