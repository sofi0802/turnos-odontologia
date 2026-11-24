import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, texto } from './apoyo.ts';
import {
  ESTUDIO_PDF,
  FOTO11,
  prepararEntorno,
  responderArchivo,
  RX36,
  SESION_ANA,
  simularDireccionesTemporales,
  type Entorno,
} from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tarea 4.2: "Ver una imagen" y "Descargar un archivo" del delta
 * `frontend-imagenes-clinicas`. Datos ficticios.
 */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
});

const VER_RX36 = 'Ver radiografía de la pieza 36 del 30/11/2026';

function visor(): HTMLDialogElement | null {
  return document.querySelector<HTMLDialogElement>('dialog.img-visor');
}

function filas(): HTMLElement[] {
  return [...entorno.seccion().querySelectorAll<HTMLElement>('.img-fila')];
}

/** Captura lo que el navegador recibiría al hacer clic en el enlace temporal de la descarga. */
function espiarDescargas(): { nombre: string; direccion: string }[] {
  const descargas: { nombre: string; direccion: string }[] = [];
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    descargas.push({ nombre: this.download, direccion: this.href });
  });
  return descargas;
}

describe('Ver una imagen', () => {
  it('Ver una radiografía', async () => {
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), VER_RX36).click();

    const foto = await vi.waitFor(() => {
      const encontrada = visor()?.querySelector('img') ?? null;
      expect(encontrada).not.toBeNull();
      return encontrada as HTMLImageElement;
    });
    expect(foto.alt).toBe('Radiografía de la pieza 36 del 30/11/2026');
    expect(foto.getAttribute('src')).toBe('blob:ficticio-1');
    expect(direcciones.crear).toHaveBeenCalledTimes(1);
    const pedido = entorno.servidor.ultimo('GET', '/pacientes/1/imagenes/5/archivo');
    expect(pedido.query.has('descarga')).toBe(false);
    const abierto = visor();
    expect(abierto?.open).toBe(true);
    expect(abierto?.querySelector('h2')?.textContent).toBe('Radiografía · pieza 36');
    expect(texto(abierto as HTMLElement)).toContain('Tomada el 30/11/2026 · Cargó Ana Ejemplo · Control');
    expect(abierto?.getAttribute('aria-labelledby')).toBe(abierto?.querySelector('h2')?.id);
  });

  it('Cerrar el visor', async () => {
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), VER_RX36).click();
    await vi.waitFor(() => expect(visor()?.querySelector('img')).not.toBeNull());

    boton(visor() as HTMLElement, 'Cerrar').click();

    expect(visor()).toBeNull();
    expect(document.querySelector('img[alt^="Radiografía"]')).toBeNull();
    expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1');
  });

  it('Falla al abrir el archivo', async () => {
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor, 'image/jpeg', 500);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), VER_RX36).click();

    await vi.waitFor(() =>
      expect(visor()?.querySelector('.img-visor-marco [role="alert"]')?.textContent).toBe('No pudimos abrir el archivo. Intentá de nuevo más tarde.'),
    );
    expect(visor()?.querySelector('img')).toBeNull();
    expect(direcciones.crear).not.toHaveBeenCalled();
    expect(boton(visor() as HTMLElement, 'Cerrar')).toBeDefined();
  });

  it('un solo visor a la vez: abrir otra imagen libera la anterior', async () => {
    const direcciones = simularDireccionesTemporales();
    entorno.imagenes = [RX36, FOTO11];
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), VER_RX36).click();
    await vi.waitFor(() => expect(visor()?.querySelector('img')).not.toBeNull());

    boton(entorno.seccion(), 'Ver fotografía de la pieza 11 del 15/11/2026').click();

    await vi.waitFor(() => expect(visor()?.querySelector('img')?.alt).toBe('Fotografía de la pieza 11 del 15/11/2026'));
    expect(document.querySelectorAll('dialog.img-visor')).toHaveLength(1);
    expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1');
    expect(direcciones.revocar).not.toHaveBeenCalledWith('blob:ficticio-2');
  });

  it('cerrar el visor antes de que llegue el archivo no deja ninguna dirección sin liberar', async () => {
    const direcciones = simularDireccionesTemporales();
    let entregar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/imagenes/{id}/archivo', () => new Promise<Response>((r) => (entregar = r)));
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), VER_RX36).click();
    await vi.waitFor(() => expect(visor()).not.toBeNull());

    boton(visor() as HTMLElement, 'Cerrar').click();
    entregar(new Response(new Uint8Array([1]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } }));
    await new Promise((r) => setTimeout(r, 20));

    expect(visor()).toBeNull();
    expect(direcciones.crear.mock.calls.length).toBe(direcciones.revocar.mock.calls.length);
  });
});

describe('Sección de imágenes accesible (visor)', () => {
  it('El foco vuelve al cerrar el visor', async () => {
    simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    const ver = boton(entorno.seccion(), VER_RX36);
    ver.focus();
    ver.click();
    await vi.waitFor(() => expect(visor()?.querySelector('img')).not.toBeNull());
    expect(document.activeElement).not.toBe(ver);

    visor()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(visor()).toBeNull();
    expect(document.activeElement).toBe(ver);
  });

  it('el foco entra al visor al abrirse, en "Cerrar", y el visor es un diálogo modal con nombre', async () => {
    simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), VER_RX36).click();

    await vi.waitFor(() => expect(visor()).not.toBeNull());
    expect(document.activeElement?.textContent).toBe('Cerrar');
    expect(visor()?.getAttribute('aria-labelledby')).not.toBeNull();
  });
});

describe('Descargar un archivo', () => {
  it('Descargar un estudio en PDF', async () => {
    const direcciones = simularDireccionesTemporales();
    const descargas = espiarDescargas();
    entorno.imagenes = [ESTUDIO_PDF];
    responderArchivo(entorno.servidor, 'application/pdf');
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), 'Descargar estudio del 01/10/2026').click();

    await vi.waitFor(() => expect(descargas).toHaveLength(1));
    expect(descargas[0]?.nombre).toBe('imagen-7.pdf');
    expect(descargas[0]?.direccion).toBe('blob:ficticio-1');
    expect(entorno.servidor.ultimo('GET', '/pacientes/1/imagenes/7/archivo').query.get('descarga')).toBe('true');
    // El enlace temporal no queda en la página y la dirección se libera apenas empieza la descarga.
    expect(document.querySelector('a[download]')).toBeNull();
    await vi.waitFor(() => expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1'));
    expect(document.querySelector('dialog')).toBeNull();
  });

  it('el nombre de la descarga no lleva datos del paciente ni la pieza', async () => {
    simularDireccionesTemporales();
    const descargas = espiarDescargas();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), 'Descargar radiografía de la pieza 36 del 30/11/2026').click();

    await vi.waitFor(() => expect(descargas).toHaveLength(1));
    expect(descargas[0]?.nombre).toBe('imagen-5.jpg');
    expect(descargas[0]?.nombre).not.toMatch(/paciente|uno|36/i);
  });

  it('una falla al descargar se avisa en la sección y no baja nada', async () => {
    const direcciones = simularDireccionesTemporales();
    const descargas = espiarDescargas();
    responderArchivo(entorno.servidor, 'image/jpeg', 500);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), 'Descargar radiografía de la pieza 36 del 30/11/2026').click();

    await vi.waitFor(() =>
      expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe(
        'No pudimos descargar el archivo. Intentá de nuevo más tarde.',
      ),
    );
    expect(descargas).toHaveLength(0);
    expect(direcciones.crear).not.toHaveBeenCalled();
  });

  it('el visor también ofrece descargar la imagen abierta', async () => {
    simularDireccionesTemporales();
    const descargas = espiarDescargas();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), VER_RX36).click();
    await vi.waitFor(() => expect(visor()?.querySelector('img')).not.toBeNull());

    boton(visor() as HTMLElement, 'Descargar').click();

    await vi.waitFor(() => expect(descargas).toHaveLength(1));
    expect(descargas[0]?.nombre).toBe('imagen-5.jpg');
    // La descarga no cierra el visor.
    expect(visor()?.open).toBe(true);
  });

  it('Un PDF no se muestra en la página', async () => {
    simularDireccionesTemporales();
    espiarDescargas();
    entorno.imagenes = [ESTUDIO_PDF];
    responderArchivo(entorno.servidor, 'application/pdf');
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const fila = filas()[0] as HTMLElement;
    const nombres = [...fila.querySelectorAll('button')].map((b) => b.getAttribute('aria-label'));
    expect(nombres).toContain('Descargar estudio del 01/10/2026');
    expect(nombres.some((n) => n?.startsWith('Ver'))).toBe(false);

    boton(fila, 'Descargar estudio del 01/10/2026').click();
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/imagenes/7/archivo')).toHaveLength(1));
    expect(document.querySelector('iframe, embed, object, dialog.img-visor')).toBeNull();
  });
});
