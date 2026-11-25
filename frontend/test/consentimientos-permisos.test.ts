import { afterEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, json, SESION_RECEPCION } from './apoyo.ts';
import { abrirConfiguracion, servidorCon } from './apoyo-configuracion.ts';
import {
  prepararEntorno,
  responderDocumento,
  SESION_ADMIN,
  SESION_ANA,
  TITULO_EXTRACCION,
  VERIFICACION_INTEGRA,
  vistaDe,
  visible,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 7.1: solo lectura para el administrador y sin acceso para la recepción y el paciente. */

let entorno: Entorno | undefined;

afterEach(() => {
  entorno?.cerrar();
  entorno = undefined;
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

function botonesDe(raiz: ParentNode): string[] {
  return [...raiz.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

describe('Solo lectura para el administrador', () => {
  it('El administrador consulta', async () => {
    entorno = prepararEntorno(SESION_ADMIN);
    responderDocumento(entorno.servidor, vistaDe(VIGENTE), VERIFICACION_INTEGRA);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(visible(seccion)).toContain(TITULO_EXTRACCION);
    expect(botonesDe(seccion)).not.toContain('Firmar consentimiento');
    expect(botonesDe(seccion)).not.toContain('Revocar');
    expect(visible(seccion)).not.toContain('Firmar consentimiento');
    expect(seccion.querySelector('button[aria-label^="Revocar"]')).toBeNull();

    const ver = seccion.querySelector<HTMLButtonElement>('button[aria-label^="Ver"]');
    ver?.click();
    const dialogo = await vi.waitFor(() => {
      const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
      expect(abierto).not.toBeNull();
      return abierto as HTMLDialogElement;
    });
    await vi.waitFor(() => expect(visible(dialogo)).toContain('Íntegro'));
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(1);
    expect(botonesDe(dialogo)).toEqual(['Cerrar']);
  });

  it('el administrador no pide las plantillas activas al abrir la ficha', async () => {
    entorno = prepararEntorno(SESION_ADMIN);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.servidor.pedidos.filter((p) => p.ruta.startsWith('/consentimientos/plantillas'))).toHaveLength(0);
    expect(entorno.servidor.pedidos.filter((p) => p.metodo !== 'GET')).toHaveLength(0);
  });

  it('el odontólogo, en cambio, sí ve "Firmar consentimiento" y "Revocar"', async () => {
    entorno = prepararEntorno(SESION_ANA);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(botonesDe(seccion)).toContain('Firmar consentimiento');
    expect(botonesDe(seccion)).toContain('Revocar');
  });
});

describe('Sin acceso para la recepción y el paciente', () => {
  it('La recepción no pide consentimientos', async () => {
    const servidor = servidorCon(SESION_RECEPCION);
    servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, []));
    servidor.responder('GET /consentimientos/plantillas', () => json(200, []));

    const { raiz } = await abrirConfiguracion(servidor);
    expect(visible(raiz)).not.toContain('Consentimientos');
    for (const ruta of ['#/agenda', '#/configuracion', '#/pacientes']) {
      window.location.hash = ruta;
      window.dispatchEvent(new HashChangeEvent('hashchange'));
      await new Promise((resolver) => setTimeout(resolver, 20));
    }

    expect(servidor.pedidos.filter((p) => p.ruta.includes('consentimientos'))).toHaveLength(0);
    expect(servidor.pedidos.filter((p) => p.ruta.startsWith('/pacientes/'))).toHaveLength(0);
    expect(raiz.querySelector('.cons-seccion')).toBeNull();
    expect(visible(raiz)).toContain('Esta sección es solo para odontólogos y administradores.');
  });

  it('el paciente tampoco: no ve la ficha ni se piden consentimientos ni plantillas', async () => {
    const servidor = servidorCon({ autenticado: true, username: 'paciente.uno@ejemplo.test', rol: 'PACIENTE' });
    servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, []));
    servidor.responder('GET /consentimientos/plantillas', () => json(200, []));

    const app = abrirApp(servidor, '#/pacientes');
    await vi.waitFor(() => expect(visible(app.raiz)).toContain('Esta sección es solo para el personal del consultorio.'));
    window.location.hash = '#/configuracion';
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await new Promise((resolver) => setTimeout(resolver, 20));

    expect(servidor.pedidos.filter((p) => p.ruta.includes('consentimientos'))).toHaveLength(0);
    expect(app.raiz.querySelector('.cons-seccion')).toBeNull();
    app.router.detener();
  });
});
