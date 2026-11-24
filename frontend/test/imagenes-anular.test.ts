import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json, texto } from './apoyo.ts';
import { ANULADA, ESTUDIO_PDF, prepararEntorno, RX36, SESION_ANA, type Entorno } from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tarea 5.3: "Anular una imagen" del delta `frontend-imagenes-clinicas`.
 * Datos ficticios. Hoy es el 2026-12-01.
 */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA, [RX36, ESTUDIO_PDF]);
});

afterEach(() => {
  entorno.cerrar();
});

const ANULAR_RX36 = 'Anular radiografía de la pieza 36 del 30/11/2026';

function dialogo(): HTMLDialogElement {
  const abierto = document.querySelector<HTMLDialogElement>('dialog');
  if (abierto === null) {
    throw new Error('No hay ningún diálogo abierto');
  }
  return abierto;
}

async function abrirAnulacion(nombreDelBoton = ANULAR_RX36): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), nombreDelBoton).click();
  return dialogo();
}

function confirmar(): void {
  boton(dialogo(), 'Anular').click();
}

function anulaciones(): number {
  return entorno.servidor.a('POST', '/pacientes/1/imagenes/5/anulacion').length;
}

describe('Anular una imagen', () => {
  it('Anular con motivo', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => {
      entorno.imagenes = [ESTUDIO_PDF];
      return json(200, { ...RX36, anulacion: { profesional: 'Ana Ejemplo', anuladaEn: '2026-12-01T11:00:00-03:00', motivo: 'x' } });
    });
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), 'Se cargó en el paciente equivocado');

    confirmar();

    await vi.waitFor(() => expect(document.querySelector('dialog')).toBeNull());
    expect(entorno.servidor.ultimo('POST', '/pacientes/1/imagenes/5/anulacion').cuerpo).toEqual({
      motivo: 'Se cargó en el paciente equivocado',
    });
    expect(entorno.seccion().querySelector('[role="status"].aviso')?.textContent).toBe('Imagen anulada.');
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(2));
    await vi.waitFor(() => expect(entorno.seccion().querySelectorAll('.img-fila')).toHaveLength(1));
  });

  it('el diálogo explica que la imagen no se borra y pide el motivo con su contador', async () => {
    const abierto = await abrirAnulacion();

    expect(abierto.querySelector('h2')?.textContent).toBe('¿Anular la radiografía?');
    const resumen = texto(abierto.querySelector('.dialogo-resumen') as HTMLElement);
    expect(resumen).toContain('Radiografía de la pieza 36 del 30/11/2026');
    expect(resumen).toContain('La imagen no se borra');
    expect(abierto.querySelector('.contador')?.textContent).toBe('0/200');
    const motivo = campo(abierto, 'Motivo');
    expect(motivo.tagName).toBe('TEXTAREA');
    expect(document.activeElement).toBe(motivo);
    escribir(motivo, 'Duplicada');
    expect(abierto.querySelector('.contador')?.textContent).toBe('9/200');
    expect(abierto.querySelector('.dialogo-acciones .btn-peligro')?.textContent).toBe('Anular');
    expect(boton(abierto, 'Volver')).toBeDefined();
  });

  it('el título concuerda con la categoría', async () => {
    const abierto = await abrirAnulacion('Anular estudio del 01/10/2026');
    expect(abierto.querySelector('h2')?.textContent).toBe('¿Anular el estudio?');
  });

  it('Anular sin motivo', async () => {
    const abierto = await abrirAnulacion();

    confirmar();

    expect(anulaciones()).toBe(0);
    const motivo = campo(abierto, 'Motivo');
    const error = document.getElementById(motivo.getAttribute('aria-describedby')?.split(' ')[0] ?? '');
    expect(error?.textContent).toBe('Escribí el motivo de la anulación.');
    expect(motivo.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(motivo);
  });

  it('un motivo de solo espacios o de 201 caracteres no se envía', async () => {
    const abierto = await abrirAnulacion();
    const motivo = campo(abierto, 'Motivo');

    escribir(motivo, '    ');
    confirmar();
    escribir(motivo, 'a'.repeat(201));
    confirmar();

    expect(anulaciones()).toBe(0);
    const error = document.getElementById(motivo.getAttribute('aria-describedby')?.split(' ')[0] ?? '');
    expect(error?.textContent).toBe('El motivo puede tener hasta 200 caracteres.');
  });

  it('un motivo de 200 caracteres se envía sin espacios de más', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => json(200, ANULADA));
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), `  ${'a'.repeat(198)}  `);

    confirmar();

    await vi.waitFor(() => expect(anulaciones()).toBe(1));
    expect((entorno.servidor.ultimo('POST', '/pacientes/1/imagenes/5/anulacion').cuerpo as { motivo: string }).motivo).toBe('a'.repeat(198));
  });

  it('La imagen ya estaba anulada', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => {
      entorno.imagenes = [ESTUDIO_PDF];
      return json(409, { status: 409, codigo: 'IMAGEN_YA_ANULADA' });
    });
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), 'Duplicada');

    confirmar();

    await vi.waitFor(() => expect(document.querySelector('dialog')).toBeNull());
    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('La imagen ya estaba anulada.');
    expect(entorno.seccion().querySelector('[role="status"].aviso')?.textContent).toBe('');
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(2));
    await vi.waitFor(() => expect(entorno.seccion().querySelectorAll('.img-fila')).toHaveLength(1));
    // El aviso sobrevive a la recarga del listado.
    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('La imagen ya estaba anulada.');
  });

  it('otro error de la API deja el diálogo abierto con el motivo escrito', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => json(500, { detail: 'traza interna' }));
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), 'Duplicada');

    confirmar();

    await vi.waitFor(() =>
      expect(abierto.querySelector('form > [role="alert"]')?.textContent).toBe(
        'No pudimos completar la operación. Probá de nuevo en unos minutos.',
      ),
    );
    expect(document.querySelector('dialog')).toBe(abierto);
    expect(campo(abierto, 'Motivo').value).toBe('Duplicada');
    expect(texto(abierto)).not.toContain('traza interna');
    expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(1);
  });

  it('"Volver" cierra sin enviar nada y devuelve el foco al botón', async () => {
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), 'Duplicada');

    boton(abierto, 'Volver').click();

    expect(document.querySelector('dialog')).toBeNull();
    expect(anulaciones()).toBe(0);
    expect(document.activeElement).toBe(boton(entorno.seccion(), ANULAR_RX36));
  });

  it('mientras se envía el botón queda deshabilitado y no se envía dos veces', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => new Promise<Response>((r) => (terminar = r)));
    const abierto = await abrirAnulacion();
    escribir(campo(abierto, 'Motivo'), 'Duplicada');

    confirmar();
    const principal = abierto.querySelector<HTMLButtonElement>('.dialogo-acciones .btn-peligro') as HTMLButtonElement;
    await vi.waitFor(() => expect(principal.disabled).toBe(true));
    abierto.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(anulaciones()).toBe(1);

    terminar(json(200, ANULADA));
    await vi.waitFor(() => expect(document.querySelector('dialog')).toBeNull());
  });
});
