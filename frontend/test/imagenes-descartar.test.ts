import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, texto } from './apoyo.ts';
import { prepararEntorno, SESION_ANA, type Entorno } from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tarea 5.2: "Descartar una carga sin guardar" del delta
 * `frontend-imagenes-clinicas`, con "Cancelar" y con "Cambiar de paciente". Datos ficticios.
 */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA, []);
});

afterEach(() => {
  entorno.cerrar();
});

function dialogos(): HTMLDialogElement[] {
  return [...document.querySelectorAll<HTMLDialogElement>('dialog')];
}

function dialogoDeCarga(): HTMLDialogElement | undefined {
  return dialogos().find((d) => d.querySelector('input[type="file"]'));
}

function confirmacion(): HTMLDialogElement | undefined {
  return dialogos().find((d) => !d.querySelector('input[type="file"]'));
}

async function abrirCarga(): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), 'Cargar imagen').click();
  const abierto = dialogoDeCarga();
  if (abierto === undefined) {
    throw new Error('No se abrió el diálogo de carga');
  }
  return abierto;
}

function elegirArchivo(): void {
  const entrada = dialogoDeCarga()?.querySelector<HTMLInputElement>('input[type="file"]');
  if (entrada === null || entrada === undefined) {
    throw new Error('No está el campo del archivo');
  }
  const archivo = new File([new Uint8Array(300)], 'rx.jpg', { type: 'image/jpeg' });
  Object.defineProperty(entrada, 'files', { configurable: true, value: [archivo] });
  entrada.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('Descartar una carga sin guardar', () => {
  it('Cancelar con un archivo elegido', async () => {
    const abierto = await abrirCarga();
    elegirArchivo();

    boton(abierto, 'Cancelar').click();

    expect(dialogoDeCarga()).toBeDefined();
    const pregunta = confirmacion();
    expect(pregunta?.querySelector('h2')?.textContent).toBe('¿Descartar la imagen?');
    expect(texto(pregunta as HTMLElement)).toContain('Hay una imagen sin cargar. ¿Descartarla?');

    boton(pregunta as HTMLElement, 'Descartar').click();

    expect(dialogos()).toHaveLength(0);
    expect(document.activeElement).toBe(boton(entorno.seccion(), 'Cargar imagen'));
  });

  it('"Seguir editando" conserva el diálogo y los datos', async () => {
    const abierto = await abrirCarga();
    elegirArchivo();
    escribir(campo(abierto, 'Pieza (opcional)'), '36');
    boton(abierto, 'Cancelar').click();

    boton(confirmacion() as HTMLElement, 'Seguir editando').click();

    expect(confirmacion()).toBeUndefined();
    expect(dialogoDeCarga()).toBe(abierto);
    expect(abierto.open).toBe(true);
    expect(texto(abierto)).toContain('rx.jpg');
    expect(campo(abierto, 'Pieza (opcional)').value).toBe('36');
  });

  it('Cancelar sin datos', async () => {
    const abierto = await abrirCarga();

    boton(abierto, 'Cancelar').click();

    expect(dialogos()).toHaveLength(0);
    expect(document.activeElement).toBe(boton(entorno.seccion(), 'Cargar imagen'));
  });

  it.each([
    ['la pieza', 'Pieza (opcional)', '36'],
    ['la descripción', 'Descripción (opcional)', 'Control'],
  ])('escribir %s también cuenta como datos sin cargar', async (_nombre, etiqueta, valor) => {
    const abierto = await abrirCarga();
    escribir(campo(abierto, etiqueta), valor);

    boton(abierto, 'Cancelar').click();

    expect(confirmacion()).toBeDefined();
  });

  it('cambiar solo la categoría o la fecha no cuenta como datos', async () => {
    const abierto = await abrirCarga();
    escribir(campo<HTMLSelectElement>(abierto, 'Categoría'), 'ESTUDIO');
    escribir(campo(abierto, 'Fecha de la toma'), '2026-11-01');

    boton(abierto, 'Cancelar').click();

    expect(dialogos()).toHaveLength(0);
  });

  it('un espacio en blanco en la pieza o la descripción no cuenta como datos', async () => {
    const abierto = await abrirCarga();
    escribir(campo(abierto, 'Pieza (opcional)'), '  ');
    escribir(campo(abierto, 'Descripción (opcional)'), '   ');

    boton(abierto, 'Cancelar').click();

    expect(dialogos()).toHaveLength(0);
  });

  it('Escape con datos pregunta antes de cerrar y sin datos cierra', async () => {
    const abierto = await abrirCarga();
    const sinDatos = new Event('cancel', { cancelable: true });
    abierto.dispatchEvent(sinDatos);
    expect(sinDatos.defaultPrevented).toBe(false);

    elegirArchivo();
    const conDatos = new Event('cancel', { cancelable: true });
    abierto.dispatchEvent(conDatos);

    expect(conDatos.defaultPrevented).toBe(true);
    expect(texto(confirmacion() as HTMLElement)).toContain('Hay una imagen sin cargar. ¿Descartarla?');
  });

  it('Cambiar de paciente con una carga sin guardar', async () => {
    await abrirCarga();
    elegirArchivo();

    boton(entorno.main(), 'Cambiar de paciente').click();

    const pregunta = confirmacion();
    expect(pregunta?.querySelector('h2')?.textContent).toBe('¿Descartar la imagen?');
    expect(texto(pregunta as HTMLElement)).toContain('Hay una imagen sin cargar. ¿Descartarla?');
    expect(entorno.main().querySelector('.img-seccion')).not.toBeNull();

    boton(pregunta as HTMLElement, 'Descartar').click();

    await vi.waitFor(() => expect(entorno.main().querySelector('.img-seccion')).toBeNull());
    expect(dialogos()).toHaveLength(0);
    expect(texto(entorno.main())).toContain('Buscar paciente');
  });

  it('Cambiar de paciente sin datos no pregunta', async () => {
    await abrirCarga();
    boton(dialogoDeCarga() as HTMLElement, 'Cancelar').click();

    boton(entorno.main(), 'Cambiar de paciente').click();

    expect(dialogos()).toHaveLength(0);
    expect(texto(entorno.main())).toContain('Buscar paciente');
  });

  it('"Seguir editando" al cambiar de paciente deja la ficha y la carga como estaban', async () => {
    const abierto = await abrirCarga();
    elegirArchivo();
    boton(entorno.main(), 'Cambiar de paciente').click();

    boton(confirmacion() as HTMLElement, 'Seguir editando').click();

    expect(entorno.main().querySelector('.img-seccion')).not.toBeNull();
    expect(dialogoDeCarga()).toBe(abierto);
    expect(texto(abierto)).toContain('rx.jpg');
  });
});
