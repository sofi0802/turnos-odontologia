import { afterEach, describe, expect, it } from 'vitest';
import { crearSelectorBuscable } from '../src/shared/ui/selector-buscable.ts';

// Datos ficticios: las prestaciones del consultorio de ejemplo.
const PRESTACIONES = [
  { id: '1', nombre: 'Consulta', detalle: '30 minutos' },
  { id: '2', nombre: 'Limpieza dental', detalle: '45 minutos' },
  { id: '3', nombre: 'Extracción', detalle: '30 minutos' },
  { id: '4', nombre: 'Niñez: control', detalle: '20 minutos' },
];

let cambios: (string | null)[] = [];

function montar(elegida: string | null = null): HTMLInputElement {
  cambios = [];
  const selector = crearSelectorBuscable({
    etiqueta: 'Prestación',
    marcador: 'Buscá una prestación',
    opciones: PRESTACIONES,
    elegida,
    sinCoincidencias: (texto) => `No hay prestaciones que coincidan con «${texto}».`,
    alCambiar: (id) => cambios.push(id),
  });
  document.body.replaceChildren(selector.contenedor);
  return selector.entrada;
}

function campo(): HTMLInputElement {
  const entrada = document.querySelector<HTMLInputElement>('input[role="combobox"]');
  if (entrada === null) {
    throw new Error('No existe el selector');
  }
  return entrada;
}

function abrir(): void {
  campo().focus();
  campo().dispatchEvent(new Event('focus'));
}

function escribir(texto: string): void {
  campo().value = texto;
  campo().dispatchEvent(new Event('input', { bubbles: true }));
}

function tecla(key: string): KeyboardEvent {
  const evento = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
  campo().dispatchEvent(evento);
  return evento;
}

function opciones(): string[] {
  return [...document.querySelectorAll('[role="option"]')].map((o) => o.textContent ?? '');
}

function mensajeSinCoincidencias(): string {
  return document.querySelector('.selector-vacio')?.textContent ?? '';
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('Buscar la prestación', () => {
  it('Buscar por parte del nombre', () => {
    montar();
    abrir();
    expect(opciones()).toHaveLength(4);

    escribir('limp');

    expect(opciones()).toEqual(['Limpieza dental · 45 minutos']);
  });

  it('Buscar sin mayúsculas ni tildes', () => {
    montar();
    abrir();

    escribir('EXTRACCION');

    expect(opciones()).toEqual(['Extracción · 30 minutos']);
  });

  it('Sin coincidencias', () => {
    montar();
    abrir();

    escribir('xyz');

    expect(opciones()).toEqual([]);
    expect(mensajeSinCoincidencias()).toBe('No hay prestaciones que coincidan con «xyz».');
    expect(document.querySelector('[role="listbox"]')).toBeNull();
  });

  it('Borrar la búsqueda muestra todas', () => {
    montar();
    abrir();
    escribir('limp');

    escribir('');

    expect(opciones()).toHaveLength(4);
    expect(mensajeSinCoincidencias()).toBe('');
  });

  it('ignora los espacios de los bordes y la ñ', () => {
    montar();
    abrir();

    escribir('  nin  ');

    expect(opciones()).toEqual(['Niñez: control · 20 minutos']);
  });

  it('con una elegida, al abrir ofrece todas y la marca', () => {
    montar('2');

    abrir();

    expect(opciones()).toHaveLength(4);
    expect(document.querySelector('[role="option"][aria-selected="true"]')?.textContent).toBe('Limpieza dental · 45 minutos');
    expect(campo().value).toBe('Limpieza dental');
  });
});

describe('Elegir la prestación con el teclado y con el toque', () => {
  it('Elegir con el teclado', () => {
    montar();
    abrir();

    tecla('ArrowDown');
    tecla('ArrowDown');
    tecla('Enter');

    expect(campo().value).toBe('Limpieza dental');
    expect(campo().getAttribute('aria-expanded')).toBe('false');
    expect(document.querySelector('[role="listbox"]')).toBeNull();
    expect(cambios).toEqual(['2']);
  });

  it('Cerrar sin elegir', () => {
    montar();
    abrir();
    expect(campo().getAttribute('aria-expanded')).toBe('true');

    tecla('Escape');

    expect(campo().getAttribute('aria-expanded')).toBe('false');
    expect(document.querySelector('[role="listbox"]')).toBeNull();
    expect(cambios).toEqual([]);
    expect(campo().value).toBe('');
  });

  it('elige con el toque en una opción', () => {
    montar();
    abrir();

    document.querySelectorAll<HTMLElement>('[role="option"]')[0]?.click();

    expect(campo().value).toBe('Consulta');
    expect(cambios).toEqual(['1']);
    expect(document.querySelector('[role="listbox"]')).toBeNull();
  });

  it('cambiar el texto de una elegida avisa que ya no hay elección', () => {
    montar('1');

    escribir('Consulta ');

    expect(cambios).toEqual([null]);
  });

  it('Enter sin una opción activa no elige', () => {
    montar();
    abrir();

    tecla('Enter');

    expect(cambios).toEqual([]);
    expect(campo().getAttribute('aria-expanded')).toBe('true');
  });

  it('la flecha hacia arriba sube y no pasa de la primera', () => {
    montar();
    abrir();
    tecla('ArrowDown');
    tecla('ArrowDown');
    tecla('ArrowUp');
    tecla('ArrowUp');
    tecla('ArrowUp');
    tecla('Enter');

    expect(cambios).toEqual(['1']);
  });

  it('la flecha hacia abajo no pasa de la última', () => {
    montar();
    abrir();
    for (let i = 0; i < 10; i += 1) {
      tecla('ArrowDown');
    }
    tecla('Enter');

    expect(cambios).toEqual(['4']);
  });

  it('la flecha hacia abajo con la lista cerrada la abre', () => {
    montar();
    expect(campo().getAttribute('aria-expanded')).toBe('false');

    tecla('ArrowDown');

    expect(campo().getAttribute('aria-expanded')).toBe('true');
  });

  it('al salir del campo con Tab la lista se cierra sin elegir', () => {
    montar();
    abrir();

    campo().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }));

    expect(campo().getAttribute('aria-expanded')).toBe('false');
    expect(cambios).toEqual([]);
  });

  it('con el puntero apretado fuera del campo la lista se cierra al soltarlo, no antes', async () => {
    montar();
    abrir();

    document.dispatchEvent(new Event('pointerdown'));
    campo().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }));
    expect(campo().getAttribute('aria-expanded')).toBe('true');

    document.dispatchEvent(new Event('pointerup'));
    await new Promise((resolver) => setTimeout(resolver, 5));

    expect(campo().getAttribute('aria-expanded')).toBe('false');
  });

  it('escribir con la lista cerrada la abre y también espera a soltar el puntero', async () => {
    montar('1');
    expect(campo().getAttribute('aria-expanded')).toBe('false');

    escribir('limp');
    document.dispatchEvent(new Event('pointerdown'));
    campo().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }));
    expect(campo().getAttribute('aria-expanded')).toBe('true');

    document.dispatchEvent(new Event('pointerup'));
    await new Promise((resolver) => setTimeout(resolver, 5));
    expect(campo().getAttribute('aria-expanded')).toBe('false');
  });

  it('la flecha de la derecha abre y cierra la lista', () => {
    montar();
    const flecha = document.querySelector<HTMLButtonElement>('.selector-flecha');

    flecha?.click();
    expect(campo().getAttribute('aria-expanded')).toBe('true');
    flecha?.click();
    expect(campo().getAttribute('aria-expanded')).toBe('false');
  });
});

describe('Selector accesible', () => {
  it('Roles y estados del selector', () => {
    montar();
    expect(campo().getAttribute('aria-expanded')).toBe('false');
    expect(campo().hasAttribute('aria-activedescendant')).toBe(false);

    abrir();
    tecla('ArrowDown');

    const etiqueta = [...document.querySelectorAll('label')].find((l) => l.textContent === 'Prestación');
    expect(etiqueta?.control).toBe(campo());
    expect(campo().getAttribute('role')).toBe('combobox');
    expect(campo().getAttribute('aria-expanded')).toBe('true');
    const lista = document.querySelector('[role="listbox"]');
    expect(lista?.id).toBe(campo().getAttribute('aria-controls'));
    const activa = document.getElementById(campo().getAttribute('aria-activedescendant') ?? '');
    expect(activa?.getAttribute('role')).toBe('option');
    expect(lista?.contains(activa)).toBe(true);
    expect(activa?.textContent).toBe('Consulta · 30 minutos');
  });

  it('anuncia la cantidad de resultados y la falta de coincidencias', () => {
    montar();
    abrir();
    const estado = document.querySelector('.solo-lectores[role="status"]');
    expect(estado?.textContent).toBe('4 resultados');

    escribir('limp');
    expect(estado?.textContent).toBe('1 resultado');

    escribir('xyz');
    expect(estado?.textContent).toBe('No hay prestaciones que coincidan con «xyz».');
  });

  it('dos selectores en la misma página no comparten identificadores', () => {
    montar();
    const primero = campo().id;
    const otro = crearSelectorBuscable({
      etiqueta: 'Otra',
      marcador: '',
      opciones: PRESTACIONES,
      elegida: null,
      sinCoincidencias: () => '',
      alCambiar: () => undefined,
    });
    document.body.append(otro.contenedor);

    expect(otro.entrada.id).not.toBe(primero);
    expect(otro.entrada.getAttribute('aria-controls')).not.toBe(campo().getAttribute('aria-controls'));
  });
});
