import { afterEach, describe, expect, it, vi } from 'vitest';
import { permisos } from '../src/configuracion/permisos.ts';
import { crearPestanas } from '../src/configuracion/pestanas.ts';
import { abrirApp, campo, escribir, json, ServidorFalso } from './apoyo.ts';
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
/** El administrador suma Usuarios (C-41), Obras sociales (C-49) y Señas (C-23), requisito modificado "Secciones según el rol". */
const DEL_ADMIN = [...TODAS, 'Usuarios', 'Obras sociales', 'Señas'];

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Secciones según el rol', () => {
  it('Pestañas del administrador', async () => {
    const { raiz } = await abrirConfiguracion(servidorCon(SESION_ADMIN));

    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(DEL_ADMIN);
    expect(pestana(raiz, 'Profesionales').getAttribute('aria-selected')).toBe('true');
    const panel = raiz.querySelector<HTMLElement>('[role="tabpanel"]:not([hidden])');
    expect(panel?.getAttribute('aria-labelledby')).toBe(pestana(raiz, 'Profesionales').id);
  });

  it('Recepción no ve la pestaña Usuarios', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = await abrirConfiguracion(servidor);

    // Siete pestañas, sin "Usuarios" ni "Obras sociales" (C-49).
    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(TODAS);
    expect(servidor.a('GET', '/usuarios')).toHaveLength(0);
    expect(servidor.a('GET', '/senas/configuracion')).toHaveLength(0);
  });

  it('El odontólogo no ve Obras sociales', async () => {
    const servidor = servidorCon(SESION_ODONTOLOGA);
    const { raiz } = await abrirConfiguracion(servidor);

    expect(pestanas(raiz).map((p) => p.textContent)).not.toContain('Obras sociales');
    expect(servidor.a('GET', '/obras-sociales')).toHaveLength(0);
  });

  it('Recepción ve los bloqueos sin poder registrarlos', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('GET /profesionales/{id}/bloqueos', () => json(200, []));
    const { raiz } = await abrirConfiguracion(servidor);
    expect(pestanas(raiz).map((p) => p.textContent)).toEqual(TODAS);

    const panel = elegirPestana(raiz, 'Bloqueos');
    await vi.waitFor(() => expect(servidor.a('GET', '/profesionales/1/bloqueos')).toHaveLength(1));
    escribir(campo<HTMLSelectElement>(panel, 'Profesional'), '1');

    await vi.waitFor(() => expect(servidor.a('GET', '/profesionales/1/bloqueos')).toHaveLength(2));
    await vi.waitFor(() => expect(panel.textContent).toContain('No hay bloqueos registrados.'));
    const botones = [...panel.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones).not.toContain('Bloquear horario');
    expect(botones).not.toContain('Borrar');
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

    // La última pestaña del administrador es Señas (C-23, después de Obras sociales de C-49).
    expect(pestana(raiz, 'Señas').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestana(raiz, 'Señas'));
    pestana(raiz, 'Señas').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
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

    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
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
      reasigna: true,
      veBloqueos: true,
      gestionaBloqueos: true,
      veTodos: true,
      profesionalFijo: false,
      profesionalFijoId: null,
      gestionaUsuarios: true,
      gestionaCoberturas: true,
      configuraSenas: true,
    });
  });

  it('RECEPCIONISTA gestiona ausencias y reasigna, y ve los bloqueos sin gestionarlos', () => {
    expect(permisos({ autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' })).toEqual({
      editaCatalogos: false,
      editaMargen: false,
      editaHorarios: false,
      gestionaAusencias: true,
      reasigna: true,
      veBloqueos: true,
      gestionaBloqueos: false,
      veTodos: true,
      profesionalFijo: false,
      profesionalFijoId: null,
      gestionaUsuarios: false,
      gestionaCoberturas: false,
      configuraSenas: false,
    });
  });

  it('ODONTOLOGO gestiona sus bloqueos sobre su profesional fijo', () => {
    expect(permisos({ autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 })).toEqual({
      editaCatalogos: false,
      editaMargen: false,
      editaHorarios: false,
      gestionaAusencias: false,
      reasigna: false,
      veBloqueos: true,
      gestionaBloqueos: true,
      veTodos: false,
      profesionalFijo: true,
      profesionalFijoId: 1,
      gestionaUsuarios: false,
      gestionaCoberturas: false,
      configuraSenas: false,
    });
    expect(permisos({ autenticado: true, username: 'otro', rol: 'ODONTOLOGO' }).profesionalFijoId).toBeNull();
  });
});
