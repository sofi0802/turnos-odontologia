import { expect, vi } from 'vitest';
import { abrirApp, json, ServidorFalso, type AppDePrueba } from './apoyo.ts';

/** Sesiones ficticias de los escenarios de C-34. */
export const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
export const SESION_RECEPCION_CONFIG = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };
export const SESION_ODONTOLOGA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
export const SESION_ODONTOLOGO_SIN_PROFESIONAL = { autenticado: true, username: 'odontologo.suelto', rol: 'ODONTOLOGO' };

/** Prestaciones del seed de la KB (`apoyo.ts` tiene una sola, para la agenda). */
export const PRESTACIONES_SEED = [
  { id: 1, nombre: 'Consulta o control', duracionMinutos: 30, activo: true },
  { id: 2, nombre: 'Limpieza (profilaxis)', duracionMinutos: 45, activo: true },
  { id: 3, nombre: 'Tratamiento de conducto', duracionMinutos: 60, activo: true },
  { id: 4, nombre: 'Extracción simple', duracionMinutos: 30, activo: true },
];

/** Servidor falso con la sesión dada y las prestaciones del seed. */
export function servidorCon(sesion: object): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.responder('GET /public/sesion', () => json(200, sesion));
  servidor.responder('GET /prestaciones', () => json(200, PRESTACIONES_SEED));
  return servidor;
}

/** Abre `#/configuracion` y espera a que se dibujen las pestañas. */
export async function abrirConfiguracion(servidor: ServidorFalso): Promise<AppDePrueba> {
  const app = abrirApp(servidor, '#/configuracion');
  await vi.waitFor(() => expect(app.raiz.querySelector('[role="tablist"]')).not.toBeNull());
  return app;
}

export function pestanas(raiz: ParentNode): HTMLButtonElement[] {
  return [...raiz.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
}

export function pestana(raiz: ParentNode, nombre: string): HTMLButtonElement {
  const encontrada = pestanas(raiz).find((p) => p.textContent === nombre);
  if (encontrada === undefined) {
    throw new Error(`No existe la pestaña ${nombre}`);
  }
  return encontrada;
}

/** Panel visible de la pestaña elegida. */
export function panelVisible(raiz: ParentNode): HTMLElement {
  const panel = [...raiz.querySelectorAll<HTMLElement>('[role="tabpanel"]')].find((p) => !p.hidden);
  if (panel === undefined) {
    throw new Error('No hay un panel visible');
  }
  return panel;
}

/** Elige una pestaña y devuelve su panel. */
export function elegirPestana(raiz: ParentNode, nombre: string): HTMLElement {
  pestana(raiz, nombre).click();
  return panelVisible(raiz);
}

/** El `<dialog>` abierto (los diálogos se agregan a `document.body`). */
export async function dialogoAbierto(): Promise<HTMLDialogElement> {
  return vi.waitFor(() => {
    const dialogo = document.querySelector<HTMLDialogElement>('dialog[open]');
    expect(dialogo).not.toBeNull();
    return dialogo as HTMLDialogElement;
  });
}

/** Envía el formulario de un diálogo como lo haría el botón de enviar. */
export function enviar(dialogo: HTMLDialogElement): void {
  const formulario = dialogo.querySelector('form');
  if (formulario === null) {
    throw new Error('El diálogo no tiene formulario');
  }
  formulario.requestSubmit();
}
