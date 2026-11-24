import { afterEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';
import { textoDeModulo } from './estilos.ts';
import { crearCargando, crearEstadoVacio } from '../src/shared/ui/estado-vacio.ts';
import type { Router } from '../src/shared/ui/router.ts';

// C-39, etapa B: estados vacíos con ilustración y carga con indicador (D7).

let router: Router | undefined;

afterEach(() => {
  router?.detener();
  router = undefined;
  document.body.replaceChildren();
});

const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };

function conPaciente(turnos: () => Response | Promise<Response>): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  servidor.responder('GET /paciente/turnos', turnos);
  return servidor;
}

describe('Estados vacíos y de carga', () => {
  it('Lista vacía con ilustración', async () => {
    const app = abrirApp(conPaciente(() => json(200, [])), '#/mis-turnos');
    router = app.router;

    await vi.waitFor(() => expect(app.raiz.querySelector('.estado-vacio')).not.toBeNull());

    const vacio = app.raiz.querySelector('.estado-vacio');
    expect(vacio?.textContent).toContain('Todavía no tenés turnos.');
    const ilustracion = vacio?.querySelector('svg');
    expect(ilustracion?.getAttribute('aria-hidden')).toBe('true');
    const accion = vacio?.querySelector<HTMLAnchorElement>('a');
    expect(accion?.textContent).toBe('Reservar turno');
    expect(accion?.getAttribute('href')).toBe('#/reserva');
  });

  it('Carga con indicador', async () => {
    let responder: (respuesta: Response) => void = () => undefined;
    const pendiente = new Promise<Response>((resolver) => {
      responder = resolver;
    });
    const app = abrirApp(
      conPaciente(() => pendiente),
      '#/mis-turnos',
    );
    router = app.router;

    await vi.waitFor(() => expect(app.raiz.querySelector('main [aria-busy="true"]')).not.toBeNull());

    const cargando = app.raiz.querySelector('main [aria-busy="true"] .cargando');
    expect(cargando?.getAttribute('role')).toBe('status');
    expect(cargando?.textContent).toContain('Cargando');
    expect(cargando?.querySelector('.rueda')?.getAttribute('aria-hidden')).toBe('true');

    responder(json(200, []));
    await vi.waitFor(() => expect(app.raiz.querySelector('main [aria-busy="true"]')).toBeNull());
    expect(app.raiz.querySelector('.cargando')).toBeNull();
  });

  it('el estado vacío chico no repite la acción y la carga acepta otro texto', () => {
    const vacio = crearEstadoVacio({ texto: 'No hay turnos para este día.', chico: true });
    const cargando = crearCargando('Cargando prestaciones…');

    expect(vacio.classList.contains('chico')).toBe(true);
    expect(vacio.querySelector('a')).toBeNull();
    expect(vacio.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(cargando.textContent).toBe('Cargando prestaciones…');
  });

  it('los estilos del estado vacío y de la carga usan la rueda con movimiento reducido', () => {
    const estados = textoDeModulo('15-estados-vacios.css');
    expect(estados).toMatch(/\.estado-vacio\s*\{/);
    expect(estados).toMatch(/\.rueda\s*\{[^}]*animation:\s*girar calc\(var\(--ds-duracion\) \* 4\.5\) linear infinite/);
    expect(estados).toMatch(/prefers-reduced-motion:\s*reduce/);
  });
});
