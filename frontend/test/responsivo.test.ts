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
  for (const abierto of document.querySelectorAll('dialog')) {
    abierto.remove();
  }
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

describe('Páginas del paciente en el celular (C-12)', () => {
  it('Reserva en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.sinSesion();
    servidor.responder('GET /public/prestaciones', () => json(200, [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }]));
    servidor.responder('GET /public/profesionales', () => json(200, []));
    servidor.responder('GET /public/disponibilidad', () =>
      json(200, [{ inicio: '2026-12-01T10:00:00-03:00', fin: '2026-12-01T10:30:00-03:00' }]),
    );
    app = abrirApp(servidor, '#/reserva');
    await vi.waitFor(() => expect(app?.raiz.querySelector('input[name="prestacion"]')).not.toBeNull());
    app.raiz.querySelector<HTMLInputElement>('input[name="prestacion"]')?.click();
    boton(app.raiz, 'Continuar').click();
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.chip')).toHaveLength(1));

    // Los horarios son botones .chip de al menos 44 px: todo botón mide 44 px y .chip usa
    // --ds-alto-control, que tokens.css fija en 44px.
    expect(app.raiz.querySelector('.chip')?.tagName).toBe('BUTTON');
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.chip')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    expect(regla(reglasDeCelular(), '.btn')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    // Controles uno debajo del otro: opciones en una columna y acciones apiladas hasta 768 px.
    expect(regla(reglasDeCelular(), '.opciones')).toMatch(/display:\s*grid/);
    expect(regla(reglasDeCelular(), '.opciones')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.acciones')).toMatch(/flex-direction:\s*column-reverse/);
    expect(regla(reglasDeCelular(), '.pagina-paciente')).toMatch(/max-width:\s*36rem/);
    // Ningún ancho fijo en píxeles mayor a 320 (sin desplazamiento horizontal).
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('Mis turnos en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () =>
      json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }),
    );
    servidor.responder('GET /paciente/turnos', () =>
      json(200, [
        {
          numeroReserva: 'R-0002',
          inicio: '2026-12-03T10:00:00-03:00',
          fin: '2026-12-03T10:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          profesional: { id: 1, nombre: 'Ana Ejemplo' },
          modificableOnline: true,
        },
      ]),
    );
    app = abrirApp(servidor, '#/mis-turnos');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno-paciente')).toHaveLength(1));

    // Tarjetas a lo ancho, una debajo de la otra, con los botones apilados.
    expect(boton(app.raiz, 'Cancelar turno').classList.contains('btn')).toBe(true);
    expect(boton(app.raiz, 'Cambiar horario').classList.contains('btn')).toBe(true);
    expect(regla(reglasDeCelular(), '.lista-turnos')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeCelular(), '.turno-paciente .turno-acciones')).toMatch(/flex-direction:\s*column/);
    expect(regla(reglasDeEscritorio(), '.turno-paciente .turno-acciones')).toMatch(/flex-direction:\s*row/);
  });
});

describe('Reprogramar turno en el celular (C-33)', () => {
  it('Horarios en el celular', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    servidor.responder('GET /public/profesionales', () => json(200, [{ id: 1, nombre: 'Ana Ejemplo' }]));
    servidor.responder('GET /public/disponibilidad', () =>
      json(200, [
        { inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' },
        { inicio: '2026-12-01T11:30:00-03:00', fin: '2026-12-01T12:00:00-03:00' },
      ]),
    );
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(app?.raiz.querySelectorAll('.turno')).toHaveLength(1));
    boton(app.raiz.querySelector('.turno') ?? app.raiz, 'Reprogramar').click();
    await vi.waitFor(() => expect(document.querySelectorAll('dialog[open] .chip')).toHaveLength(2));

    // Los horarios son los botones .chip de C-12, dentro de una grilla que se ajusta al ancho.
    const grilla = document.querySelector('dialog[open] .chips');
    expect(grilla?.getAttribute('role')).toBe('group');
    expect(document.querySelector('dialog[open] .chip')?.tagName).toBe('BUTTON');
    expect(regla(reglasDeCelular(), 'button')).toMatch(/min-height:\s*44px/);
    expect(regla(reglasDeCelular(), '.chip')).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
    expect(regla(reglasDeCelular(), '.chips')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(5\.5rem, 1fr\)\)/);
    // Con 320 px de ancho caben tres columnas de 5,5 rem: la grilla no fuerza desplazamiento horizontal.
    expect(regla(reglasDeCelular(), '.dialogo fieldset')).toMatch(/min-width:\s*0/);
    // El diálogo cabe en la pantalla y se desplaza por dentro, sin ancho fijo.
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/max-height:\s*calc\(100dvh - 2 \* var\(--espacio-2\)\)/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/overflow-y:\s*auto/);
    expect(regla(reglasDeCelular(), '.dialogo')).toMatch(/width:\s*calc\(100% - /);
    // DNI y número de reserva uno debajo del otro en el celular, lado a lado desde 768 px.
    expect(regla(reglasDeCelular(), '.dos-campos')).not.toMatch(/grid-template-columns/);
    expect(regla(reglasDeEscritorio(), '.dos-campos')).toMatch(/grid-template-columns:\s*1fr 1fr/);
    // Estados de la zona de horarios (mockups de C-33).
    expect(regla(reglasDeCelular(), '.sin-horarios')).toMatch(/border:\s*1px dashed/);
    expect(regla(reglasDeCelular(), '.buscando')).toMatch(/color:\s*var\(--ds-texto-suave\)/);
    // Ningún ancho fijo en píxeles mayor a 320.
    const anchosFijos = [...estilos.matchAll(/(?<!-)width:\s*(\d+)px/g)].map((m) => Number(m[1]));
    expect(anchosFijos.filter((ancho) => ancho > 320)).toEqual([]);
  });

  it('Los botones de horario entran en el diálogo en la computadora', () => {
    // Verificación visual de C-33 (1440 × 900): con 6 columnas fijas de 1fr los botones no entraban en los 32 rem del
    // diálogo y aparecía una barra de desplazamiento horizontal. El diálogo con horarios mide 34 rem, como en los
    // mockups aprobados, y la grilla del diálogo se ajusta al ancho en lugar de fijar las columnas.
    expect(regla(reglasDeEscritorio(), '.dialogo:has(.chips)')).toMatch(/max-width:\s*34rem/);
    expect(regla(reglasDeEscritorio(), '.dialogo .chips')).toMatch(/grid-template-columns:\s*repeat\(auto-fill, minmax\(4\.5rem, 1fr\)\)/);
    expect(regla(reglasDeEscritorio(), '.dialogo .chips')).not.toMatch(/repeat\(6/);
  });
});
