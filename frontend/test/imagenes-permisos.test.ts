import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton } from './apoyo.ts';
import {
  imagen,
  prepararEntorno,
  responderArchivo,
  RX36,
  SESION_ADMIN,
  SESION_ANA,
  simularDireccionesTemporales,
  type Entorno,
} from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias, tareas 6.1 y 6.2: "Solo lectura para el administrador" e "Imágenes solo
 * en la pantalla" del delta `frontend-imagenes-clinicas`. Datos ficticios. Hoy es el 2026-12-01.
 */

let entorno: Entorno;

afterEach(() => {
  entorno.cerrar();
});

function textosDeBotones(raiz: ParentNode): string[] {
  return [...raiz.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

function etiquetasDeBotones(raiz: ParentNode): string[] {
  return [...raiz.querySelectorAll('button')].map((b) => b.getAttribute('aria-label') ?? '');
}

describe('Solo lectura para el administrador', () => {
  it('El administrador consulta', async () => {
    entorno = prepararEntorno(SESION_ADMIN, [RX36]);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(etiquetasDeBotones(seccion)).toContain('Ver radiografía de la pieza 36 del 30/11/2026');
    expect(etiquetasDeBotones(seccion)).toContain('Descargar radiografía de la pieza 36 del 30/11/2026');
    expect(textosDeBotones(seccion)).not.toContain('Cargar imagen');
    expect(textosDeBotones(seccion)).not.toContain('Anular');
    expect(etiquetasDeBotones(seccion).some((e) => e.startsWith('Anular'))).toBe(false);
    // También puede filtrar.
    expect(seccion.querySelector('.img-filtros select')).not.toBeNull();
    expect(seccion.querySelector('.img-cabecera-acciones')?.children).toHaveLength(0);
  });

  it('sin imágenes el administrador tampoco ve "Cargar imagen"', async () => {
    entorno = prepararEntorno(SESION_ADMIN, []);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(textosDeBotones(entorno.seccion())).not.toContain('Cargar imagen');
  });

  it('el odontólogo sí ve "Cargar imagen" y "Anular"', async () => {
    entorno = prepararEntorno(SESION_ANA, [RX36]);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(textosDeBotones(entorno.seccion())).toContain('Cargar imagen');
    expect(textosDeBotones(entorno.seccion())).toContain('Anular');
  });
});

describe('Imágenes solo en la pantalla', () => {
  const secreto = imagen({ id: 5, descripcion: 'Control' });

  /** Espía todos los métodos de la consola y junta lo que se escribió en ellos. */
  function espiarConsola(): () => string {
    const espias = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) =>
      vi.spyOn(console, metodo).mockImplementation(() => undefined),
    );
    return () => JSON.stringify(espias.flatMap((e) => e.mock.calls.map((llamada) => String(llamada.join(' ')))));
  }

  it('Nada queda guardado en el navegador', async () => {
    entorno = prepararEntorno(SESION_ANA, [secreto]);
    simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    const descargas: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      descargas.push(this.download);
    });
    const consola = espiarConsola();
    await entorno.abrirFicha();
    await entorno.seccionLista();

    boton(entorno.seccion(), 'Ver radiografía de la pieza 36 del 30/11/2026').click();
    await vi.waitFor(() => expect(document.querySelector('dialog.img-visor img')).not.toBeNull());
    boton(document.querySelector('dialog.img-visor') as HTMLElement, 'Descargar').click();
    await vi.waitFor(() => expect(descargas).toHaveLength(1));

    const direccion = window.location.href;
    expect(direccion).not.toContain('Control');
    expect(direccion).not.toMatch(/blob|imagen|descarga|archivo/i);
    expect(window.location.hash).toBe('#/pacientes');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(consola()).not.toContain('Control');
    expect(consola()).not.toMatch(/255|216|blob:/);
  });

  it('una anulación tampoco deja datos en la dirección ni en el navegador', async () => {
    entorno = prepararEntorno(SESION_ANA, [secreto]);
    const consola = espiarConsola();
    entorno.servidor.responder('POST /pacientes/{id}/imagenes/{id}/anulacion', () => new Response(null, { status: 409 }));
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), 'Anular radiografía de la pieza 36 del 30/11/2026').click();
    const motivo = document.querySelector<HTMLTextAreaElement>('dialog textarea') as HTMLTextAreaElement;
    motivo.value = 'Motivo confidencial';
    motivo.dispatchEvent(new Event('input', { bubbles: true }));
    boton(document.querySelector('dialog') as HTMLElement, 'Anular').click();
    await vi.waitFor(() => expect(entorno.servidor.a('POST', '/pacientes/1/imagenes/5/anulacion')).toHaveLength(1));
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/imagenes').length).toBeGreaterThanOrEqual(1));

    expect(window.location.href).not.toMatch(/Control|confidencial/);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(consola()).not.toMatch(/Control|confidencial/);
  });

  it('Se liberan los archivos al cerrar el visor', async () => {
    entorno = prepararEntorno(SESION_ANA, [RX36]);
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), 'Ver radiografía de la pieza 36 del 30/11/2026').click();
    await vi.waitFor(() => expect(document.querySelector('dialog.img-visor img')).not.toBeNull());
    expect(direcciones.crear).toHaveBeenCalledTimes(1);
    expect(direcciones.revocar).not.toHaveBeenCalled();

    boton(document.querySelector('dialog.img-visor') as HTMLElement, 'Cerrar').click();

    expect(direcciones.revocar).toHaveBeenCalledTimes(1);
    expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1');
  });

  it('al cambiar de paciente se libera el archivo del visor', async () => {
    entorno = prepararEntorno(SESION_ANA, [RX36]);
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), 'Ver radiografía de la pieza 36 del 30/11/2026').click();
    await vi.waitFor(() => expect(document.querySelector('dialog.img-visor img')).not.toBeNull());

    boton(entorno.main(), 'Cambiar de paciente').click();

    expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1');
    expect(document.querySelector('dialog')).toBeNull();
    expect(entorno.main().querySelector('.img-seccion')).toBeNull();
  });

  it('al salir de la ficha hacia otra pantalla se libera el archivo y se cierra el visor', async () => {
    entorno = prepararEntorno(SESION_ANA, [RX36]);
    const direcciones = simularDireccionesTemporales();
    responderArchivo(entorno.servidor);
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), 'Ver radiografía de la pieza 36 del 30/11/2026').click();
    await vi.waitFor(() => expect(document.querySelector('dialog.img-visor img')).not.toBeNull());

    window.location.hash = '#/agenda';

    await vi.waitFor(() => expect(direcciones.revocar).toHaveBeenCalledWith('blob:ficticio-1'));
    expect(document.querySelector('dialog')).toBeNull();
  });

  it('un archivo que llega después de salir de la ficha no deja ninguna dirección sin liberar', async () => {
    entorno = prepararEntorno(SESION_ANA, [RX36]);
    const direcciones = simularDireccionesTemporales();
    let entregar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/imagenes/{id}/archivo', () => new Promise<Response>((r) => (entregar = r)));
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), 'Ver radiografía de la pieza 36 del 30/11/2026').click();
    await vi.waitFor(() => expect(document.querySelector('dialog.img-visor')).not.toBeNull());

    boton(entorno.main(), 'Cambiar de paciente').click();
    entregar(new Response(new Uint8Array([1]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } }));
    await new Promise((r) => setTimeout(r, 20));

    expect(direcciones.crear).not.toHaveBeenCalled();
    expect(document.querySelector('dialog')).toBeNull();
  });
});
