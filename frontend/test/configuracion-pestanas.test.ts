import { afterEach, describe, expect, it, vi } from 'vitest';
import { permisos } from '../src/configuracion/permisos.ts';
import { crearPestanas } from '../src/configuracion/pestanas.ts';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  elegirPestana,
  pestana,
  pestanas,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';

const TODAS = ['Profesionales', 'Sillones', 'Prestaciones', 'Margen', 'Horarios', 'Ausencias', 'Bloqueos'];

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Secciones según el rol', () => {
  it('Pestañas del administrador', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_ADMIN));

    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(TODAS);
    expect(pestana(raiz, 'Profesionales').getAttribute('aria-selected')).toBe('true');
    const panel = raiz.querySelector<HTMLElement>('[role="tabpanel"]:not([hidden])');
    expect(panel?.getAttribute('aria-labelledby')).toBe(pestana(raiz, 'Profesionales').id);
  });

  it('Recepción no ve los bloqueos', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_RECEPCION_CONFIG));

    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(TODAS.filter((t) => t !== 'Bloqueos'));
  });

  it('El odontólogo ve las siete pestañas', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_ODONTOLOGA));

    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(TODAS);
  });

  it('Cambiar de pestaña', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_ADMIN));
    const profesionales = raiz.querySelector<HTMLElement>('[role="tabpanel"]:not([hidden])');

    const panel = elegirPestana(raiz, 'Margen');

    expect(pestana(raiz, 'Margen').getAttribute('aria-selected')).toBe('true');
    expect(pestana(raiz, 'Profesionales').getAttribute('aria-selected')).toBe('false');
    expect(panel.getAttribute('aria-labelledby')).toBe(pestana(raiz, 'Margen').id);
    expect(profesionales?.hidden).toBe(true);
  });

  it('Las flechas del teclado mueven entre pestañas', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_ADMIN));

    pestana(raiz, 'Profesionales').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));

    expect(pestana(raiz, 'Bloqueos').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestana(raiz, 'Bloqueos'));
    pestana(raiz, 'Bloqueos').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(pestana(raiz, 'Profesionales').getAttribute('aria-selected')).toBe('true');
  });

  it('Cada panel se monta la primera vez que se muestra', () => {
    const montajes: string[] = [];
    const raiz = crearPestanas(
      ['Uno', 'Dos'].map((titulo) => ({ titulo, montar: () => montajes.push(titulo) })),
      'Prueba',
    );
    document.body.replaceChildren(raiz);

    expect(montajes).toEqual(['Uno']);
    elegirPestana(raiz, 'Dos');
    elegirPestana(raiz, 'Uno');
    elegirPestana(raiz, 'Dos');
    expect(montajes).toEqual(['Uno', 'Dos']);
  });

  it('Abrir Configuración sin sesión', async () => {
    const servidor = new ServidorFalso();
    servidor.sinSesion();

    abrirApp(servidor, '#/configuracion');

    await vi.waitFor(() => expect(window.location.hash).toBe('#/ingreso'));
    expect(servidor.a('GET', '/profesionales')).toHaveLength(0);
  });

  it('Un paciente no ve Configuración', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () =>
      json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }),
    );

    const { raiz } = abrirApp(servidor, '#/configuracion');

    await vi.waitFor(() => expect(raiz.querySelector('[role="tablist"]')).toBeNull());
    expect(servidor.a('GET', '/profesionales')).toHaveLength(0);
  });
});

describe('Permisos por rol (design D3)', () => {
  it('ADMIN edita todo', () => {
    expect(permisos({ autenticado: true, username: 'admin', rol: 'ADMIN' })).toEqual({
      editaCatalogos: true,
      editaMargen: true,
      editaHorarios: true,
      gestionaAusencias: true,
      veBloqueos: true,
      gestionaBloqueos: true,
      profesionalFijo: false,
      profesionalFijoId: null,
    });
  });

  it('RECEPCIONISTA solo gestiona ausencias y no ve bloqueos', () => {
    expect(permisos({ autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' })).toEqual({
      editaCatalogos: false,
      editaMargen: false,
      editaHorarios: false,
      gestionaAusencias: true,
      veBloqueos: false,
      gestionaBloqueos: false,
      profesionalFijo: false,
      profesionalFijoId: null,
    });
  });

  it('ODONTOLOGO gestiona sus bloqueos sobre su profesional fijo', () => {
    expect(permisos({ autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 })).toEqual({
      editaCatalogos: false,
      editaMargen: false,
      editaHorarios: false,
      gestionaAusencias: false,
      veBloqueos: true,
      gestionaBloqueos: true,
      profesionalFijo: true,
      profesionalFijoId: 1,
    });
    expect(permisos({ autenticado: true, username: 'otro', rol: 'ODONTOLOGO' }).profesionalFijoId).toBeNull();
  });
});
