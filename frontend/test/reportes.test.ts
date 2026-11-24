import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, campo, errorDeCampo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

// Pantalla Reportes (spec frontend-reportes, C-32). Hoy es el miércoles 2026-12-09 en el consultorio y la sesión es
// de `recepcion`. Datos ficticios: Ana Ejemplo (1), Bruno Prueba (2) y los sillones 1 y 2.

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;
let respuesta: Record<string, unknown>;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function reporte(cambios: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    desde: '2026-12-07',
    hasta: '2026-12-13',
    profesionalId: null,
    ocupacion: {
      profesionales: [
        { profesionalId: 1, nombre: 'Ana Ejemplo', minutosOcupados: 90, minutosAtencion: 1200, porcentaje: 8 },
        { profesionalId: 2, nombre: 'Bruno Prueba', minutosOcupados: 90, minutosAtencion: 60, porcentaje: 150 },
      ],
      sillones: [
        { sillonId: 1, nombre: 'Sillón 1', minutosOcupados: 120, minutosAtencion: 1200, porcentaje: 10 },
        { sillonId: 3, nombre: 'Sillón 3', minutosOcupados: 30, minutosAtencion: 0, porcentaje: null },
      ],
    },
    origen: { online: 3, consultorio: 3, sinDato: 0 },
    ausencias: {
      pacientes: { ausentes: 2, atendidos: 6, tasa: 25, sinRegistrar: 1 },
      profesionales: [{ profesionalId: 2, nombre: 'Bruno Prueba', tipo: 'LICENCIA', cantidad: 1, minutos: 1440 }],
    },
    cancelaciones: { porPaciente: 2, porConsultorio: 1, porReprogramacion: 1, sinDato: 0 },
    ...cambios,
  };
}

async function abrirReportes(): Promise<void> {
  app = abrirApp(servidor, '#/reportes');
  await vi.waitFor(() => expect(servidor.a('GET', '/reportes').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(raiz().querySelector('.reportes-resultado[aria-busy]')).toBeNull());
}

function pedidosDeReportes(): string[] {
  return servidor.a('GET', '/reportes').map((p) => p.query.toString());
}

/** Fila de ocupación por su nombre. */
function filaDe(nombre: string): HTMLElement {
  const fila = [...raiz().querySelectorAll<HTMLElement>('.reporte-fila')].find(
    (f) => f.querySelector('.reporte-nombre')?.textContent === nombre,
  );
  if (fila === undefined) {
    throw new Error(`No hay fila de ${nombre}`);
  }
  return fila;
}

/** Cada cifra de un bloque como "Etiqueta: valor". */
function cifras(): string[] {
  return [...raiz().querySelectorAll<HTMLElement>('.reporte-cifra')].map(
    (c) =>
      `${texto(c.querySelector<HTMLElement>('dt') ?? c).trim()}: ${texto(c.querySelector<HTMLElement>('dd') ?? c).trim()}`,
  );
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-09T15:00:00Z'));
  servidor = new ServidorFalso();
  respuesta = reporte();
  servidor.responder('GET /reportes', () => json(200, respuesta));
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Período y filtro de los reportes', () => {
  it('Semana en curso al abrir', async () => {
    await abrirReportes();

    expect(campo(raiz(), 'Desde').value).toBe('2026-12-07');
    expect(campo(raiz(), 'Hasta').value).toBe('2026-12-13');
    expect(campo<HTMLSelectElement>(raiz(), 'Profesional').selectedOptions[0]?.textContent).toBe('Todos');
    expect(pedidosDeReportes()[0]).toBe('desde=2026-12-07&hasta=2026-12-13');
  });

  it('Filtrar por profesional', async () => {
    await abrirReportes();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Profesional').options.length).toBeGreaterThan(1));

    escribir(campo<HTMLSelectElement>(raiz(), 'Profesional'), '2');

    await vi.waitFor(() =>
      expect(pedidosDeReportes().at(-1)).toBe('desde=2026-12-07&hasta=2026-12-13&profesionalId=2'),
    );
  });

  it('Fin anterior al inicio', async () => {
    await abrirReportes();
    const pedidos = pedidosDeReportes().length;

    escribir(campo(raiz(), 'Hasta'), '2026-12-01');

    expect(errorDeCampo(raiz(), 'Hasta')).toBe('La fecha final tiene que ser igual o posterior a la inicial.');
    expect(pedidosDeReportes()).toHaveLength(pedidos);
  });

  it('Período de más de 92 días', async () => {
    await abrirReportes();
    const pedidos = pedidosDeReportes().length;

    escribir(campo(raiz(), 'Desde'), '2026-12-01');
    escribir(campo(raiz(), 'Hasta'), '2027-03-03');

    expect(errorDeCampo(raiz(), 'Hasta')).toBe('Elegí un período de hasta 92 días.');
    // "Desde" 2026-12-01 con "Hasta" 2026-12-13 es válido y se pide; "Hasta" 2027-03-03 ya no.
    await vi.waitFor(() => expect(pedidosDeReportes()).toHaveLength(pedidos + 1));
    expect(pedidosDeReportes().at(-1)).toBe('desde=2026-12-01&hasta=2026-12-13');
  });
});

describe('Bloque de ocupación', () => {
  it('Ocupación por profesional', async () => {
    await abrirReportes();

    const ana = filaDe('Ana Ejemplo');
    expect(texto(ana)).toContain('8 %');
    expect(texto(ana)).toContain('90 de 1.200 min');
    expect(ana.querySelector('[role="meter"]')?.getAttribute('aria-valuenow')).toBe('8');
  });

  it('Más del 100 %', async () => {
    await abrirReportes();

    const bruno = filaDe('Bruno Prueba');
    expect(texto(bruno)).toContain('150 %');
    const medidor = bruno.querySelector<HTMLElement>('[role="meter"]');
    expect(medidor?.getAttribute('aria-valuenow')).toBe('150');
    expect(medidor?.querySelector<HTMLElement>('.medidor-relleno')?.style.width).toBe('100%');
  });

  it('Sin horario', async () => {
    await abrirReportes();

    const sillon3 = filaDe('Sillón 3');
    expect(texto(sillon3)).toContain('Sin horario');
    expect(texto(sillon3)).toContain('30 min ocupados');
    expect(sillon3.querySelector('[role="meter"]')).toBeNull();
  });

  it('Sillones con filtro de profesional', async () => {
    respuesta = reporte({
      profesionalId: 1,
      ocupacion: {
        profesionales: [
          { profesionalId: 1, nombre: 'Ana Ejemplo', minutosOcupados: 90, minutosAtencion: 1200, porcentaje: 8 },
        ],
        sillones: [],
      },
    });
    await abrirReportes();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Profesional').options.length).toBeGreaterThan(1));

    escribir(campo<HTMLSelectElement>(raiz(), 'Profesional'), '1');

    await vi.waitFor(() =>
      expect(texto(raiz())).toContain('La ocupación por sillón se ve sin filtro de profesional.'),
    );
  });
});

describe('Bloques de origen, ausencias y cancelaciones', () => {
  it('Origen de los turnos', async () => {
    await abrirReportes();

    expect(cifras()).toContain('Online: 3 (50 %)');
    expect(cifras()).toContain('Del consultorio: 3 (50 %)');
    expect(cifras().some((c) => c.startsWith('Sin dato'))).toBe(false);
  });

  it('Ausencias', async () => {
    await abrirReportes();

    expect(cifras()).toEqual(expect.arrayContaining(['Ausentes: 2', 'Ausentismo: 25 %', 'Sin registrar: 1']));
    expect(texto(raiz())).toContain('Bruno Prueba · Licencia · 24 h');
  });

  it('Sin ausentismo calculable', async () => {
    respuesta = reporte({
      ausencias: { pacientes: { ausentes: 0, atendidos: 0, tasa: null, sinRegistrar: 0 }, profesionales: [] },
    });
    await abrirReportes();

    expect(cifras()).toContain('Ausentismo: sin turnos atendidos ni ausentes');
  });

  it('Cancelaciones', async () => {
    await abrirReportes();

    expect(cifras()).toEqual(
      expect.arrayContaining(['Por el paciente: 2', 'Por el consultorio: 1', 'Por reprogramación: 1']),
    );
  });
});

describe('Período sin turnos y errores', () => {
  it('Período vacío', async () => {
    respuesta = reporte({
      ocupacion: {
        profesionales: [
          { profesionalId: 1, nombre: 'Ana Ejemplo', minutosOcupados: 0, minutosAtencion: 1200, porcentaje: 0 },
        ],
        sillones: [],
      },
      origen: { online: 0, consultorio: 0, sinDato: 0 },
      ausencias: { pacientes: { ausentes: 0, atendidos: 0, tasa: null, sinRegistrar: 0 }, profesionales: [] },
      cancelaciones: { porPaciente: 0, porConsultorio: 0, porReprogramacion: 0, sinDato: 0 },
    });
    await abrirReportes();

    expect(texto(raiz())).toContain('No hay turnos en este período.');
    expect(raiz().querySelector('.reporte-fila')).toBeNull();
  });

  it('Error de la API', async () => {
    await abrirReportes();
    servidor.responder('GET /reportes', () =>
      json(422, {
        status: 422,
        codigo: 'DATOS_INVALIDOS',
        detail: 'Hay datos inválidos en el pedido',
        errores: [{ campo: 'hasta', mensaje: 'El período no puede superar los 92 días.' }],
      }),
    );

    escribir(campo(raiz(), 'Hasta'), '2026-12-20');

    await vi.waitFor(() => expect(errorDeCampo(raiz(), 'Hasta')).toBe('El período no puede superar los 92 días.'));
    expect(campo(raiz(), 'Desde').value).toBe('2026-12-07');
    expect(campo(raiz(), 'Hasta').value).toBe('2026-12-20');
  });

  it('Otro error de la API va al aviso general', async () => {
    servidor.responder('GET /reportes', () => json(500, { status: 500, detail: 'falla' }));
    app = abrirApp(servidor, '#/reportes');

    await vi.waitFor(() => expect(raiz().querySelector('main > [role="alert"]')?.textContent).not.toBe(''));
  });
});
