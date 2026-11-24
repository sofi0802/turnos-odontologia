import { afterEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';
import { textoDeModulo } from './estilos.ts';
import type { Router } from '../src/shared/ui/router.ts';

// C-39, etapa C: las pantallas del paciente con fondo suave y la jerarquía de títulos (mockups aprobados).

let router: Router | undefined;

afterEach(() => {
  router?.detener();
  router = undefined;
});

function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

function sinSesion(): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  return servidor;
}

function conPaciente(): ServidorFalso {
  const servidor = sinSesion();
  servidor.responder('GET /public/sesion', () =>
    json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }),
  );
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  return servidor;
}

async function abrirYEsperar(hash: string, servidor: ServidorFalso): Promise<HTMLElement> {
  const app = abrirApp(servidor, hash);
  router = app.router;
  await vi.waitFor(() => expect(app.raiz.querySelector('main h1, main h2')).not.toBeNull());
  return app.raiz;
}

describe('Pantallas del paciente', () => {
  it('Fondo suave en las pantallas del paciente', async () => {
    const paginas: [string, () => ServidorFalso][] = [
      ['#/inicio', sinSesion],
      ['#/reserva', sinSesion],
      ['#/mi-cuenta', sinSesion],
      ['#/mis-turnos', conPaciente],
    ];
    for (const [hash, servidor] of paginas) {
      router?.detener();
      const raiz = await abrirYEsperar(hash, servidor());
      expect(raiz.querySelector('.cuerpo')?.classList.contains('fondo-suave'), hash).toBe(true);
    }
    // Las pantallas del personal lo llevan desde la etapa D (personal-visual.test.ts).
    const layout = textoDeModulo('13-layout.css');
    expect(regla(layout, '.cuerpo.fondo-suave')).toMatch(/background:\s*var\(--ds-superficie\)/);
  });

  it('Jerarquía de títulos', () => {
    const base = textoDeModulo('01-base.css');
    expect(regla(base, '.contenido h1')).toMatch(/font-size:\s*var\(--ds-texto-xl\)/);
    expect(regla(base, ':where(.contenido) h2')).toMatch(/font-size:\s*var\(--ds-texto-lg\)/);
    expect(regla(base, ':where(.contenido) h3')).toMatch(/font-size:\s*var\(--ds-texto-base\)/);
    expect(regla(base, '.contenido h1')).toMatch(/line-height:\s*var\(--ds-interlineado-titulo\)/);
    // El usuario del encabezado va en texto chico, como en los mockups, para que entre el email completo.
    expect(regla(textoDeModulo('13-layout.css'), '.usuario-nombre')).toMatch(/font-size:\s*var\(--ds-texto-sm\)/);
  });

  it('las páginas del paciente tienen una columna de 44 rem y los turnos anteriores no compiten con los próximos', () => {
    const paciente = textoDeModulo('08-paciente.css');
    expect(regla(paciente, '.pagina-paciente')).toMatch(/max-width:\s*44rem/);
    expect(regla(paciente, '.turno-anterior')).toMatch(/background:\s*transparent/);
  });
});
