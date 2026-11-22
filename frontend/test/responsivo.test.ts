import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import estilos from '../src/styles.css?raw';
import { abrirApp, boton, json, ServidorFalso, turno, type AppDePrueba } from './apoyo.ts';

// jsdom no aplica media queries ni calcula el diseño: estos tests comprueban la
// estructura y las reglas de estilos.css; lo visual se verifica con capturas reales
// a 390 × 844 y 1440 × 900 (tarea 6.2).

let app: AppDePrueba | undefined;

/** Texto de las reglas que están dentro de `@media (min-width: 768px)`. */
function reglasDeEscritorio(): string {
  const inicio = estilos.indexOf('@media (min-width: 768px)');
  return inicio === -1 ? '' : estilos.slice(inicio);
}

/** Reglas mobile first: todo lo anterior al primer media query. */
function reglasDeCelular(): string {
  const inicio = estilos.indexOf('@media');
  return inicio === -1 ? estilos : estilos.slice(0, inicio);
}

function regla(css: string, selector: string): string {
  const escapado = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(^|\\n\\s*|,\\s*)${escapado}\\s*(,[^{]*)?\\{([^}]*)\\}`).exec(css)?.[3] ?? '';
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
});

describe('Agenda usable en el celular', () => {
  it('Agenda en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno')).toHaveLength(1));
    boton(app.raiz, 'Semana').click();
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.agenda-dia')).toHaveLength(7));

    // Una sola columna: los días y los turnos se apilan sin grilla hasta 768 px.
    expect(regla(reglasDeCelular(), '.agenda-lista')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.turnos')).not.toMatch(/grid-template-columns/);
    // Filtros y formularios a todo el ancho; controles de al menos 44 px.
    expect(regla(reglasDeCelular(), '.agenda-filtros')).toMatch(/flex-direction:\s*column/);
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), 'select')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/width:\s*calc\(100% - /);
    // Nada fuerza desplazamiento horizontal: ningún ancho fijo en píxeles mayor a 320.
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
    expect(regla(estilos, 'body')).toMatch(/min-width:\s*320px/);
  });

  it('Agenda en la computadora', () => {
    // Desde 768 px, filtros en fila y los días de la semana en columnas que se ajustan al ancho.
    expect(regla(reglasDeEscritorio(), '.agenda-filtros')).toMatch(/flex-direction:\s*row/);
    expect(regla(reglasDeEscritorio(), '.agenda-lista')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(/);
    expect(regla(estilos, '.contenido')).toMatch(/max-width:\s*var\(--ancho-maximo\)/);
  });
});
