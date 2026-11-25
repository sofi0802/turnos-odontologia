import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  enviarFirma,
  escribirTexto,
  prepararEntorno,
  resumen,
  SESION_ANA,
  TITULO_EXTRACCION,
  ultimoDialogo,
  vistaDe,
  responderDocumento,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 7.2: nombres accesibles, foco, avisos anunciados y controles de 44 px (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
  entorno.servidor.responder('GET /public/sesion', () => json(200, SESION_ANA));
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, vistaDe(resumen({ id: 13 }))));
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

describe('Consentimientos accesibles', () => {
  it('Nombre accesible de los botones', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const nombres = [...(entorno.seccion().querySelector('.cons-lista > li') as HTMLElement).querySelectorAll('button')].map((b) =>
      b.getAttribute('aria-label'),
    );

    expect(nombres).toEqual([
      `Ver ${TITULO_EXTRACCION} del 24/11/2026`,
      `Revocar ${TITULO_EXTRACCION} del 24/11/2026`,
    ]);
    const segunda = entorno.seccion().querySelectorAll<HTMLElement>('.cons-lista > li')[1] as HTMLElement;
    expect([...segunda.querySelectorAll('button')].map((b) => b.getAttribute('aria-label'))).toEqual([
      'Ver Consentimiento para blanqueamiento del 01/10/2026',
    ]);
  });

  it('Foco al abrir la vista de firma', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const titulo = pantalla.querySelector('h2');
    expect(document.activeElement).toBe(titulo);
    expect(titulo?.getAttribute('tabindex')).toBe('-1');
    expect(pantalla.getAttribute('aria-labelledby')).toBe(titulo?.id);
    expect(titulo?.textContent).toBe(TITULO_EXTRACCION);
  });

  it('Foco en el bloqueo', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);

    const bloqueo = await vi.waitFor(() => {
      const actual = document.querySelector<HTMLElement>('.bloqueo-pantalla');
      expect(actual).not.toBeNull();
      return actual as HTMLElement;
    });

    expect(document.activeElement).toBe(campo(bloqueo, 'Contraseña'));
    const aviso = bloqueo.querySelector('[role="status"]');
    expect(aviso?.textContent).toBe('Listo: la firma quedó registrada.');
    expect(bloqueo.querySelector('section')?.getAttribute('aria-labelledby')).toBe(bloqueo.querySelector('h1')?.id);
  });

  it('los diálogos devuelven el foco al botón que los abrió', async () => {
    responderDocumento(entorno.servidor, vistaDe(VIGENTE));
    await entorno.abrirFicha();
    await entorno.seccionLista();
    const seccion = entorno.seccion();

    const ver = boton(seccion, `Ver ${TITULO_EXTRACCION} del 24/11/2026`);
    ver.click();
    boton(await ultimoDialogo(), 'Cerrar').click();
    expect(document.activeElement).toBe(ver);

    const revocar = boton(seccion, `Revocar ${TITULO_EXTRACCION} del 24/11/2026`);
    revocar.click();
    boton(await ultimoDialogo(), 'Cancelar').click();
    expect(document.activeElement).toBe(revocar);

    const firmar = boton(seccion, 'Firmar consentimiento');
    firmar.click();
    boton(await ultimoDialogo(), 'Cancelar').click();
    expect(document.activeElement).toBe(firmar);
  });

  it('los avisos de éxito y de error de la sección se anuncian', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(seccion.querySelector('p[role="status"].aviso')).not.toBeNull();
    expect(seccion.querySelector('p[role="alert"].aviso')).not.toBeNull();
  });

  it('los errores de la vista de firma y del bloqueo se anuncian', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    expect(pantalla.querySelector('form [role="alert"]')).not.toBeNull();
    completarFirma(pantalla, { dni: '99A' });
    enviarFirma(pantalla);
    const error = pantalla.querySelector('.campo-error:not(:empty)');
    expect(error?.textContent).toBe('Escribí el DNI sin letras: 7 u 8 números.');
    expect(campo(pantalla, 'DNI').getAttribute('aria-invalid')).toBe('true');
    expect(campo(pantalla, 'DNI').getAttribute('aria-describedby')).toContain(error?.id ?? 'sin-id');
  });

  it('el error de la contraseña del bloqueo se anuncia y queda ligado al campo', async () => {
    entorno.servidor.responder('POST /public/login', () => json(401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'x' }));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    const bloqueo = await vi.waitFor(() => {
      const actual = document.querySelector<HTMLElement>('.bloqueo-pantalla');
      expect(actual).not.toBeNull();
      return actual as HTMLElement;
    });

    escribir(campo(bloqueo, 'Contraseña'), 'clave-ficticia-mala');
    boton(bloqueo, 'Desbloquear').click();

    await vi.waitFor(() => expect(bloqueo.querySelector('.campo-error')?.textContent).toContain('Contraseña incorrecta.'));
    const error = bloqueo.querySelector('.campo-error') as HTMLElement;
    expect(error.getAttribute('role')).toBe('alert');
    expect(campo(bloqueo, 'Contraseña').getAttribute('aria-describedby')).toContain(error.id);
    expect(campo(bloqueo, 'Contraseña').getAttribute('aria-invalid')).toBe('true');
  });

  it('el contador del editor y el del motivo son parte de la descripción del campo', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), `Revocar ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const dialogo = await ultimoDialogo();
    const motivo = campo<HTMLTextAreaElement>(dialogo, 'Motivo');

    escribirTexto(motivo, 'abc');

    const ids = (motivo.getAttribute('aria-describedby') ?? '').split(' ');
    expect(ids.map((id) => document.getElementById(id)?.textContent)).toContain('3/200');
  });
});
