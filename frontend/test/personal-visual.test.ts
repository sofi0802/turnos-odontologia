import { afterEach, describe, expect, it, vi } from 'vitest';
import { CATALOGOS_VACIOS } from '../src/agenda/catalogos.ts';
import type { TurnoAgenda } from '../src/agenda/consulta.ts';
import { periodoDe } from '../src/agenda/periodo.ts';
import { dibujarAgenda } from '../src/agenda/vista.ts';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';
import { textoDeModulo } from './estilos.ts';
import type { Router } from '../src/shared/ui/router.ts';

// C-39, etapa D: las pantallas del personal con el fondo suave, la tarjeta del turno y los botones de la etapa B
// (mockups aprobados en mockups/D).

let router: Router | undefined;

afterEach(() => {
  router?.detener();
  router = undefined;
  document.body.replaceChildren();
});

function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

function turno(): TurnoAgenda {
  return {
    id: 1,
    pacienteId: 1,
    pacienteNombre: 'Paciente',
    pacienteApellido: 'Uno',
    profesionalId: 1,
    sillonId: 1,
    prestacionId: 1,
    inicio: '2026-12-01T10:00:00-03:00',
    fin: '2026-12-01T10:30:00-03:00',
    estado: 'RESERVADO',
    esSobreturno: false,
    llegadaEn: null,
  };
}

describe('Pantallas del personal', () => {
  it('Fondo suave en las pantallas del personal', async () => {
    for (const hash of ['#/agenda', '#/configuracion']) {
      router?.detener();
      const servidor = new ServidorFalso();
      servidor.responder('GET /public/sesion', () => json(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' }));
      const app = abrirApp(servidor, hash);
      router = app.router;
      await vi.waitFor(() => expect(app.raiz.querySelector('main h1')).not.toBeNull());
      expect(app.raiz.querySelector('.cuerpo')?.classList.contains('fondo-suave'), hash).toBe(true);
    }
  });

  it('La tarjeta del turno y sus acciones', () => {
    const contenedor = document.createElement('div');
    document.body.append(contenedor);
    dibujarAgenda(contenedor, periodoDe('dia', '2026-12-01'), [turno()], CATALOGOS_VACIOS, {
      cancelar: () => undefined,
      reprogramar: () => undefined,
    });

    const tarjeta = contenedor.querySelector('.turno');
    // El paciente ya no usa la tarjeta de "Mis turnos".
    expect(tarjeta?.querySelector('.turno-paciente')).toBeNull();
    expect(tarjeta?.querySelector('.turno-persona')?.textContent).toBe('Paciente Uno');
    const botones = [...(tarjeta?.querySelectorAll<HTMLButtonElement>('.turno-acciones button') ?? [])];
    expect(botones.map((b) => [b.textContent, b.className])).toEqual([
      ['Cancelar turno', 'btn btn-peligro'],
      ['Reprogramar', 'btn'],
    ]);

    const agenda = textoDeModulo('03-agenda.css');
    expect(regla(agenda, '.turno')).toMatch(/background:\s*var\(--ds-fondo\)/);
    expect(regla(agenda, '.turno')).toMatch(/box-shadow:\s*var\(--ds-sombra-1\)/);
    expect(regla(agenda, '.turno-persona')).toMatch(/font-size:\s*var\(--ds-texto-lg\)/);
    expect(regla(agenda, '.turno-persona')).toMatch(/font-weight:\s*var\(--ds-peso-fuerte\)/);
    expect(regla(agenda, '.turno-horario')).toMatch(/font-size:\s*var\(--ds-texto-sm\)/);
    expect(regla(agenda, '.turno-horario')).toMatch(/color:\s*var\(--ds-acento-700\)/);
    expect(regla(agenda, '.turno-acciones')).toMatch(/margin-top:\s*var\(--ds-espacio-2\)/);
  });
});
