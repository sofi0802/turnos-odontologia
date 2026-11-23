import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, texto, turno, type AppDePrueba } from './apoyo.ts';

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

const SESION_ODONTOLOGA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };

function titulo(): HTMLElement {
  const encabezado = raiz().querySelector<HTMLElement>('main h1');
  if (encabezado === null) {
    throw new Error('La agenda no tiene título');
  }
  return encabezado;
}

function turnosVisibles(): HTMLElement[] {
  return [...raiz().querySelectorAll<HTMLElement>('.turno')];
}

async function abrirAgenda(): Promise<void> {
  app = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(raiz().querySelector('.agenda-lista[aria-busy="true"]')).toBeNull());
}

function ultimaConsulta(): Record<string, string> {
  return Object.fromEntries(servidor.ultimo('GET', '/turnos').query);
}

async function esperarConsultas(cantidad: number): Promise<void> {
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(cantidad));
  await vi.waitFor(() => expect(raiz().querySelector('.agenda-lista[aria-busy="true"]')).toBeNull());
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
});

describe('Agenda del día', () => {
  it('Ver los turnos de hoy', async () => {
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    expect(ultimaConsulta()).toEqual({ desde: '2026-12-01T00:00:00-03:00', hasta: '2026-12-02T00:00:00-03:00' });
    await vi.waitFor(() => expect(turnosVisibles()).toHaveLength(1));
    const visible = texto(turnosVisibles()[0] ?? raiz());
    expect(visible).toContain('10:00 – 10:30');
    expect(visible).toContain('Paciente Uno');
    expect(visible).toContain('Ana Ejemplo');
    expect(visible).toContain('Sillón 1');
    expect(visible).toContain('Consulta');
  });

  it('Día sin turnos', async () => {
    await abrirAgenda();

    expect(turnosVisibles()).toHaveLength(0);
    expect(texto(raiz())).toContain('No hay turnos para este día.');
  });

  it('Hora mostrada en la zona del consultorio', async () => {
    servidor.responder('GET /turnos', () =>
      json(200, [turno({ inicio: '2026-12-01T13:00:00Z', fin: '2026-12-01T13:30:00Z' })]),
    );

    await abrirAgenda();

    await vi.waitFor(() => expect(texto(turnosVisibles()[0] ?? raiz())).toContain('10:00 – 10:30'));
  });

  it('ordena por inicio aunque la API los mande desordenados', async () => {
    servidor.responder('GET /turnos', () =>
      json(200, [
        turno({ id: 2, inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' }),
        turno({ id: 1 }),
      ]),
    );

    await abrirAgenda();

    await vi.waitFor(() => expect(turnosVisibles()).toHaveLength(2));
    expect(turnosVisibles().map((t) => t.dataset['id'])).toEqual(['1', '2']);
  });
});

describe('Agenda de la semana', () => {
  it('Cambiar a la vista semanal', async () => {
    servidor.responder('GET /turnos', (pedido) =>
      pedido.query.get('hasta') === '2026-12-07T00:00:00-03:00'
        ? json(200, [
            turno({ id: 1 }),
            turno({ id: 2, inicio: '2026-12-03T09:00:00-03:00', fin: '2026-12-03T09:30:00-03:00' }),
          ])
        : json(200, []),
    );
    await abrirAgenda();

    boton(raiz(), 'Semana').click();
    await esperarConsultas(2);

    expect(ultimaConsulta()).toEqual({ desde: '2026-11-30T00:00:00-03:00', hasta: '2026-12-07T00:00:00-03:00' });
    const dias = [...raiz().querySelectorAll('.agenda-dia h2')].map((h) => h.textContent);
    expect(dias).toHaveLength(7);
    expect(dias[0]).toContain('lunes');
    expect(dias[6]).toContain('domingo');
    const martes = raiz().querySelectorAll('.agenda-dia')[1];
    const jueves = raiz().querySelectorAll('.agenda-dia')[3];
    expect(martes?.querySelectorAll('.turno')).toHaveLength(1);
    expect(jueves?.querySelectorAll('.turno')).toHaveLength(1);
    expect(boton(raiz(), 'Semana').getAttribute('aria-pressed')).toBe('true');
  });

  it('Ir al día siguiente', async () => {
    await abrirAgenda();

    boton(raiz(), 'Siguiente').click();
    await esperarConsultas(2);

    expect(ultimaConsulta()).toEqual({ desde: '2026-12-02T00:00:00-03:00', hasta: '2026-12-03T00:00:00-03:00' });
  });

  it('ir a la semana anterior', async () => {
    await abrirAgenda();
    boton(raiz(), 'Semana').click();
    await esperarConsultas(2);

    boton(raiz(), 'Anterior').click();
    await esperarConsultas(3);

    expect(ultimaConsulta()).toEqual({ desde: '2026-11-23T00:00:00-03:00', hasta: '2026-11-30T00:00:00-03:00' });
  });

  it('Volver a hoy', async () => {
    await abrirAgenda();
    boton(raiz(), 'Semana').click();
    await esperarConsultas(2);
    boton(raiz(), 'Siguiente').click();
    await esperarConsultas(3);
    boton(raiz(), 'Siguiente').click();
    await esperarConsultas(4);
    expect(ultimaConsulta()['desde']).toBe('2026-12-14T00:00:00-03:00');

    boton(raiz(), 'Hoy').click();
    await esperarConsultas(5);

    expect(ultimaConsulta()).toEqual({ desde: '2026-11-30T00:00:00-03:00', hasta: '2026-12-07T00:00:00-03:00' });
  });
});

describe('Filtros por profesional y por sillón', () => {
  it('Filtrar por profesional', async () => {
    await abrirAgenda();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Profesional').options).toHaveLength(3));

    escribir(campo<HTMLSelectElement>(raiz(), 'Profesional'), '1');
    await esperarConsultas(2);

    expect(ultimaConsulta()).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
      profesionalId: '1',
    });
    const opciones = [...campo<HTMLSelectElement>(raiz(), 'Profesional').options].map((o) => o.textContent);
    expect(opciones).toEqual(['Todos', 'Ana Ejemplo', 'Bruno Prueba']);
  });

  it('Filtrar por sillón y profesional a la vez', async () => {
    await abrirAgenda();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Sillón').options).toHaveLength(3));
    escribir(campo<HTMLSelectElement>(raiz(), 'Profesional'), '1');
    await esperarConsultas(2);

    escribir(campo<HTMLSelectElement>(raiz(), 'Sillón'), '2');
    await esperarConsultas(3);

    expect(ultimaConsulta()).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
      profesionalId: '1',
      sillonId: '2',
    });
  });

  it('Catálogos no disponibles', async () => {
    servidor.responder('GET /profesionales', () => json(404));
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    await vi.waitFor(() => expect(texto(raiz())).toContain('No pudimos cargar la lista de profesionales.'));
    const opciones = [...campo<HTMLSelectElement>(raiz(), 'Profesional').options].map((o) => o.textContent);
    expect(opciones).toEqual(['Todos']);
    await vi.waitFor(() => expect(turnosVisibles()).toHaveLength(1));
    expect(texto(turnosVisibles()[0] ?? raiz())).toContain('Profesional 1');
  });

  it('El odontólogo ve solo su agenda', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));

    await abrirAgenda();

    expect(raiz().querySelector('select[name="profesionalId"]')).toBeNull();
    await vi.waitFor(() => expect(texto(titulo())).toContain('Ana Ejemplo'));
    expect(titulo().firstChild?.textContent).toBe('Mi agenda');
    expect(ultimaConsulta()).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
      profesionalId: '1',
    });
  });

  it('El odontólogo filtra por sillón', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));
    await abrirAgenda();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Sillón').options).toHaveLength(3));

    escribir(campo<HTMLSelectElement>(raiz(), 'Sillón'), '2');
    await esperarConsultas(2);

    expect(ultimaConsulta()).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
      profesionalId: '1',
      sillonId: '2',
    });
  });

  it('el odontólogo sin catálogo de profesionales ve solo "Mi agenda"', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));
    servidor.responder('GET /profesionales', () => json(404));

    await abrirAgenda();

    await vi.waitFor(() => expect(texto(raiz())).toContain('No pudimos cargar la lista de profesionales.'));
    expect(texto(titulo())).toBe('Mi agenda');
    expect(ultimaConsulta()['profesionalId']).toBe('1');
  });

  it('Recepción conserva el filtro por profesional', async () => {
    await abrirAgenda();

    expect(texto(titulo())).toBe('Agenda');
    expect(campo<HTMLSelectElement>(raiz(), 'Profesional')).toBeDefined();
    expect(campo<HTMLSelectElement>(raiz(), 'Sillón')).toBeDefined();
  });
});

describe('Anterior y Siguiente como flechas', () => {
  it('Anterior y Siguiente como flechas', async () => {
    await abrirAgenda();

    for (const nombre of ['Anterior', 'Siguiente']) {
      const elegido = boton(raiz(), nombre);
      expect(elegido.textContent?.trim()).toBe('');
      expect(elegido.getAttribute('aria-label')).toBe(nombre);
      expect(elegido.title).toBe(nombre);
      expect(elegido.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    }
  });
});

describe('Indicadores de confirmado y sobreturno', () => {
  function etiquetas(): string[] {
    return [...(turnosVisibles()[0]?.querySelectorAll('.etiqueta') ?? [])].map((e) => e.textContent ?? '');
  }

  it('Turno confirmado', async () => {
    servidor.responder('GET /turnos', () => json(200, [turno({ estado: 'CONFIRMADO' })]));
    await abrirAgenda();
    await vi.waitFor(() => expect(etiquetas()).toEqual(['Confirmado']));
  });

  it('Sobreturno', async () => {
    servidor.responder('GET /turnos', () => json(200, [turno({ esSobreturno: true })]));
    await abrirAgenda();
    await vi.waitFor(() => expect(etiquetas()).toEqual(['Sobreturno']));
  });

  it('Turno común sin etiquetas', async () => {
    servidor.responder('GET /turnos', () => json(200, [turno({ estado: 'RESERVADO', esSobreturno: false })]));
    await abrirAgenda();
    await vi.waitFor(() => expect(turnosVisibles()).toHaveLength(1));
    expect(etiquetas()).toEqual([]);
  });
});
