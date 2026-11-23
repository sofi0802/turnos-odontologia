import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  abrirApp,
  boton,
  campo,
  elegirPaciente,
  escribir,
  json,
  PROFESIONALES,
  ServidorFalso,
  texto,
  type AppDePrueba,
} from './apoyo.ts';

// Formulario guiado de "Nuevo turno" (spec frontend-agenda-personal, C-40).
// Hoy es el martes 2026-12-01 a las 08:00 del consultorio.

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;
let turnosDelSillon: unknown[] = [];
let horariosOfrecidos: string[] = ['10:00', '10:30', '11:00'];

function dialogo(): HTMLDialogElement {
  const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (abierto === null) {
    throw new Error('No hay un formulario abierto');
  }
  return abierto;
}

function selectorDe(etiqueta: string): HTMLSelectElement {
  return campo<HTMLSelectElement>(dialogo(), etiqueta);
}

function opciones(etiqueta: string): string[] {
  return [...selectorDe(etiqueta).options].map((o) => o.textContent ?? '');
}

function diasDelCalendario(): HTMLButtonElement[] {
  return [...dialogo().querySelectorAll<HTMLButtonElement>('.cal-dia')];
}

function diaDelCalendario(fecha: string): HTMLButtonElement {
  const encontrado = dialogo().querySelector<HTMLButtonElement>(`.cal-dia[data-fecha="${fecha}"]`);
  if (encontrado === null) {
    throw new Error(`No existe el día ${fecha}`);
  }
  return encontrado;
}

function botonesDeHorario(): string[] {
  return [...dialogo().querySelectorAll('.chips button')].map((b) => b.textContent ?? '');
}

function textoSillon(): string {
  return texto(dialogo().querySelector<HTMLElement>('.sillon-nuevo-turno') ?? dialogo());
}

function mesDelCalendario(): string {
  return texto(dialogo().querySelector<HTMLElement>('.cal-mes') ?? dialogo());
}

function finDe(hora: string, minutos = 30): string {
  const [h, m] = hora.split(':').map(Number);
  const total = (h ?? 0) * 60 + (m ?? 0) + minutos;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

const TURNO_DEL_SILLON_1 = {
  id: 9,
  pacienteNombre: 'Carla',
  pacienteApellido: 'Ficticia',
  profesionalId: 2,
  sillonId: 1,
  prestacionId: 1,
  inicio: '2026-12-01T11:00:00-03:00',
  fin: '2026-12-01T11:30:00-03:00',
  estado: 'RESERVADO',
  esSobreturno: false,
};

function prepararServidor(): void {
  servidor.responder('GET /prestaciones', () =>
    json(200, [
      { id: 1, nombre: 'Consulta', duracionMinutos: 30, activo: true },
      { id: 2, nombre: 'Limpieza', duracionMinutos: 45, activo: true },
      { id: 3, nombre: 'Vieja', duracionMinutos: 30, activo: false },
    ]),
  );
  servidor.responder('GET /public/profesionales', (pedido) =>
    json(
      200,
      pedido.query.get('prestacionId') === '1' ? [{ id: 1, nombre: 'Ana Ejemplo' }] : [{ id: 2, nombre: 'Bruno Prueba' }],
    ),
  );
  servidor.responder('GET /public/disponibilidad', (pedido) => {
    const fecha = pedido.query.get('fecha') ?? '';
    return json(
      200,
      horariosOfrecidos.map((h) => ({ inicio: `${fecha}T${h}:00-03:00`, fin: `${fecha}T${finDe(h)}:00-03:00` })),
    );
  });
  servidor.responder('GET /configuracion/margen', () => json(200, { minutos: 10 }));
  servidor.responder('GET /turnos', (pedido) => json(200, pedido.query.has('sillonId') ? turnosDelSillon : []));
}

async function abrirNuevoTurno(): Promise<void> {
  app = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(0));
  await vi.waitFor(() =>
    expect(campo<HTMLSelectElement>(app?.raiz ?? document.body, 'Profesional').options.length).toBeGreaterThan(1),
  );
  boton(app.raiz, 'Nuevo turno').click();
}

async function elegirPrestacion(id: string): Promise<void> {
  escribir(selectorDe('Prestación'), id);
  await vi.waitFor(() => expect(selectorDe('Profesional').disabled).toBe(false));
}

function elegirProfesional(id: string): void {
  escribir(selectorDe('Profesional'), id);
}

async function elegirDia(fecha: string): Promise<void> {
  await vi.waitFor(() => expect(diaDelCalendario(fecha).disabled).toBe(false));
  diaDelCalendario(fecha).click();
}

async function hastaLosHorarios(fecha = '2026-12-01'): Promise<void> {
  await abrirNuevoTurno();
  await elegirPrestacion('1');
  elegirProfesional('1');
  await elegirDia(fecha);
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  turnosDelSillon = [];
  horariosOfrecidos = ['10:00', '10:30', '11:00'];
  prepararServidor();
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  for (const abierto of document.querySelectorAll('dialog')) {
    abierto.remove();
  }
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Pasos del nuevo turno en orden', () => {
  it('Al abrir solo se elige la prestación', async () => {
    await abrirNuevoTurno();

    expect(opciones('Prestación')).toEqual(['Elegí una prestación', 'Consulta', 'Limpieza']);
    expect(selectorDe('Profesional').disabled).toBe(true);
    expect(diasDelCalendario().every((d) => d.disabled)).toBe(true);
    expect(botonesDeHorario()).toEqual([]);
    expect(texto(dialogo())).toContain('Elegí un día para ver los horarios libres.');
  });

  it('Cambiar la prestación vacía los pasos siguientes', async () => {
    await hastaLosHorarios();
    await vi.waitFor(() => expect(botonesDeHorario()).toEqual(['10:00', '10:30', '11:00']));
    boton(dialogo(), '10:00').click();

    await elegirPrestacion('2');

    expect(opciones('Profesional')).toEqual(['Elegí un profesional', 'Bruno Prueba']);
    expect(selectorDe('Profesional').value).toBe('');
    expect(diasDelCalendario().every((d) => d.disabled)).toBe(true);
    expect(diasDelCalendario().some((d) => d.getAttribute('aria-pressed') === 'true')).toBe(false);
    expect(botonesDeHorario()).toEqual([]);
  });

  it('Cambiar el día vacía el horario', async () => {
    await hastaLosHorarios();
    await vi.waitFor(() => expect(botonesDeHorario()).toHaveLength(3));
    boton(dialogo(), '10:00').click();

    await elegirDia('2026-12-02');

    await vi.waitFor(() =>
      expect(servidor.ultimo('GET', '/public/disponibilidad').query.get('fecha')).toBe('2026-12-02'),
    );
    await vi.waitFor(() => expect(botonesDeHorario()).toHaveLength(3));
    expect(dialogo().querySelectorAll('.chips button[aria-pressed="true"]')).toHaveLength(0);
  });
});

describe('Profesionales habilitados al crear un turno', () => {
  it('Solo los habilitados para la prestación', async () => {
    await abrirNuevoTurno();

    await elegirPrestacion('1');

    expect(Object.fromEntries(servidor.ultimo('GET', '/public/profesionales').query)).toEqual({ prestacionId: '1' });
    expect(opciones('Profesional')).toEqual(['Elegí un profesional', 'Ana Ejemplo']);
  });

  it('No se pueden cargar los profesionales', async () => {
    servidor.responder('GET /public/profesionales', () => json(500));
    await abrirNuevoTurno();

    escribir(selectorDe('Prestación'), '1');

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('No pudimos cargar los profesionales. Probá elegir la prestación de nuevo.'),
    );
    expect(selectorDe('Profesional').disabled).toBe(true);
  });
});

describe('Calendario desde hoy', () => {
  it('Los días pasados no se pueden elegir', async () => {
    await abrirNuevoTurno();
    await elegirPrestacion('1');
    elegirProfesional('1');

    await vi.waitFor(() => expect(diaDelCalendario('2026-12-01').disabled).toBe(false));
    expect(mesDelCalendario()).toBe('diciembre de 2026');
    const noviembre = diasDelCalendario().filter((d) => d.classList.contains('otro-mes') && d.textContent === '30');
    expect(noviembre).toHaveLength(1);
    expect(noviembre.every((d) => d.disabled)).toBe(true);
    expect(boton(dialogo(), 'Mes anterior').disabled).toBe(true);
  });

  it('Ir al mes siguiente', async () => {
    await abrirNuevoTurno();
    await elegirPrestacion('1');
    elegirProfesional('1');
    await vi.waitFor(() => expect(boton(dialogo(), 'Mes siguiente').disabled).toBe(false));

    boton(dialogo(), 'Mes siguiente').click();

    expect(mesDelCalendario()).toBe('enero de 2027');
    expect(diasDelCalendario().filter((d) => d.dataset['fecha'] !== undefined).every((d) => !d.disabled)).toBe(true);
    expect(boton(dialogo(), 'Mes anterior').disabled).toBe(false);
  });

  it('Elegir un día', async () => {
    await hastaLosHorarios('2026-12-03');

    expect(diaDelCalendario('2026-12-03').getAttribute('aria-pressed')).toBe('true');
    await vi.waitFor(() =>
      expect(Object.fromEntries(servidor.ultimo('GET', '/public/disponibilidad').query)).toEqual({
        prestacionId: '1',
        fecha: '2026-12-03',
        profesionalId: '1',
      }),
    );
  });
});

describe('Horarios libres al crear un turno', () => {
  it('Horarios del día como botones', async () => {
    await hastaLosHorarios();

    await vi.waitFor(() => expect(botonesDeHorario()).toEqual(['10:00', '10:30', '11:00']));
  });

  it('Se ocultan los horarios que pisan el sillón habitual', async () => {
    horariosOfrecidos = ['10:00', '10:30', '11:00', '11:30', '11:45'];
    turnosDelSillon = [TURNO_DEL_SILLON_1];

    await hastaLosHorarios();

    await vi.waitFor(() => expect(botonesDeHorario()).toEqual(['10:00', '11:45']));
    expect(Object.fromEntries(servidor.ultimo('GET', '/turnos').query)).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
      sillonId: '1',
    });
  });

  it('si falla la consulta de turnos del sillón se muestran todos los horarios', async () => {
    servidor.responder('GET /turnos', (pedido) => (pedido.query.has('sillonId') ? json(500) : json(200, [])));

    await hastaLosHorarios();

    await vi.waitFor(() => expect(botonesDeHorario()).toEqual(['10:00', '10:30', '11:00']));
  });

  it('sin el margen de la configuración se usa el mínimo de 10 minutos', async () => {
    servidor.responder('GET /configuracion/margen', () => json(500));
    horariosOfrecidos = ['10:20', '10:21'];
    turnosDelSillon = [TURNO_DEL_SILLON_1];

    await hastaLosHorarios();

    await vi.waitFor(() => expect(botonesDeHorario()).toEqual(['10:20']));
  });

  it('Día sin horarios', async () => {
    horariosOfrecidos = [];

    await hastaLosHorarios('2026-12-05');

    await vi.waitFor(() => expect(texto(dialogo())).toContain('No hay horarios libres ese día. Probá con otro día.'));
    expect(diaDelCalendario('2026-12-04').disabled).toBe(false);
  });

  it('No se pueden cargar los horarios', async () => {
    servidor.responder('GET /public/disponibilidad', () => json(500));

    await hastaLosHorarios();

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('No pudimos cargar los horarios. Elegí el día de nuevo para volver a intentar.'),
    );
  });
});

describe('Sillón habitual del profesional', () => {
  it('Se muestra el sillón habitual sin poder cambiarlo', async () => {
    await abrirNuevoTurno();
    await elegirPrestacion('1');

    elegirProfesional('1');

    expect(textoSillon()).toContain('Sillón: Sillón 1');
    expect(() => campo(dialogo(), 'Sillón')).toThrow();
    expect(dialogo().querySelector('select[name="sillonId"], input[name="sillonId"]')).toBeNull();
  });

  it('Profesional sin sillón habitual', async () => {
    servidor.responder('GET /profesionales', () =>
      json(200, [{ ...PROFESIONALES[0], sillonHabitualId: null }, PROFESIONALES[1]]),
    );
    await abrirNuevoTurno();
    await elegirPrestacion('1');

    elegirProfesional('1');

    expect(textoSillon()).toContain(
      'Ana Ejemplo no tiene un sillón asignado. Pedile al administrador que lo asigne en Configuración.',
    );
    expect(diasDelCalendario().every((d) => d.disabled)).toBe(true);
    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');
    boton(dialogo(), 'Crear turno').click();
    expect(servidor.a('POST', '/turnos')).toHaveLength(0);
  });
});
