import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { turnoQueChoca } from '../src/agenda/sobreturno.ts';
import { MENSAJE_SOLO_PERSONAL } from '../src/app.ts';
import type { TurnoAgenda } from '../src/agenda/consulta.ts';
import {
  abrirApp,
  boton,
  campo,
  elegirPaciente,
  errorDeCampo,
  escribir,
  json,
  ServidorFalso,
  texto,
  turno,
  type AppDePrueba,
} from './apoyo.ts';

// Sobreturno y extensión desde la agenda del personal (C-45, delta de frontend-agenda-personal).
// Hoy es el martes 2026-12-01 a las 08:00 del consultorio.

const SESION_ODONTOLOGA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const MOTIVO = 'Urgencia por dolor, pedido de recepción';

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function dialogo(): HTMLDialogElement {
  const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (abierto === null) {
    throw new Error('No hay un formulario abierto');
  }
  return abierto;
}

function tituloDelDialogo(): string {
  return dialogo().querySelector('h2')?.textContent ?? '';
}

function avisoDeLaAgenda(rol: 'status' | 'alert'): string {
  return raiz().querySelector(`main > [role="${rol}"]`)?.textContent ?? '';
}

function tarjeta(id: number): HTMLElement {
  const encontrada = raiz().querySelector<HTMLElement>(`.turno[data-id="${id}"]`);
  if (encontrada === null) {
    throw new Error(`No está el turno ${id}`);
  }
  return encontrada;
}

function botonesDe(elemento: ParentNode): string[] {
  return [...elemento.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

async function abrirAgenda(agenda: unknown[] = []): Promise<void> {
  servidor.responder('GET /turnos', () => json(200, agenda));
  app = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(raiz().querySelector('.agenda-lista [aria-busy]')).toBeNull());
  await vi.waitFor(() => expect(servidor.a('GET', '/profesionales').length).toBeGreaterThan(0));
}

async function abrirSobreturno(): Promise<void> {
  await abrirAgenda();
  boton(raiz(), 'Sobreturno').click();
  await vi.waitFor(() => expect(tituloDelDialogo()).toBe('Sobreturno'));
}

async function completarSobreturno({ paciente = true, hora = '10:15', motivo = MOTIVO } = {}): Promise<void> {
  if (paciente) {
    await elegirPaciente(dialogo(), 'uno', 'Uno, Paciente · DNI 99000001');
  }
  escribir(campo<HTMLSelectElement>(dialogo(), 'Prestación'), '1');
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo(), 'Profesional').disabled).toBe(false));
  escribir(campo<HTMLSelectElement>(dialogo(), 'Profesional'), '1');
  escribir(campo(dialogo(), 'Fecha'), '2026-12-01');
  escribir(campo(dialogo(), 'Hora'), hora);
  escribir(campo(dialogo(), 'Motivo'), motivo);
}

function enviar(textoBoton: string): void {
  boton(dialogo(), textoBoton).click();
}

async function abrirExtension(agenda: unknown[], id = 7): Promise<void> {
  await abrirAgenda(agenda);
  await vi.waitFor(() => expect(botonesDe(tarjeta(id))).toContain('Extender'));
  boton(tarjeta(id), 'Extender').click();
  await vi.waitFor(() => expect(tituloDelDialogo()).toBe('Extender turno'));
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.responder('GET /public/profesionales', () => json(200, [{ id: 1, nombre: 'Ana Ejemplo' }]));
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

describe('Botón de sobreturno', () => {
  it('La recepción ve "Sobreturno" junto a "Nuevo turno"', async () => {
    await abrirAgenda();
    const filtros = raiz().querySelector('.agenda-filtros');
    expect(botonesDe(filtros ?? raiz())).toEqual(['Sobreturno', 'Nuevo turno']);
  });

  it('El paciente no ve el sobreturno', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(texto(raiz())).toContain(MENSAJE_SOLO_PERSONAL));

    expect(botonesDe(document.body)).not.toContain('Sobreturno');
    expect(botonesDe(document.body)).not.toContain('Extender');
  });
});

describe('Formulario de sobreturno', () => {
  it('El sobreturno no pide sillón', async () => {
    await abrirSobreturno();
    expect(() => campo(dialogo(), 'Sillón')).toThrow();
    expect(texto(dialogo())).toContain('El sillón lo asigna el sistema');
  });

  it('El odontólogo carga un sobreturno con su profesional', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));
    servidor.responder('POST /turnos/sobreturno', () =>
      json(201, { ...turno({ id: 42, sillonId: 2, esSobreturno: true }), numeroReserva: 'R-0042' }),
    );
    await abrirSobreturno();

    const profesional = campo<HTMLSelectElement>(dialogo(), 'Profesional');
    expect(profesional.disabled).toBe(true);
    expect(profesional.selectedOptions[0]?.textContent).toBe('Ana Ejemplo');

    await elegirPaciente(dialogo(), 'uno', 'Uno, Paciente · DNI 99000001');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Prestación'), '1');
    escribir(campo(dialogo(), 'Hora'), '10:15');
    escribir(campo(dialogo(), 'Motivo'), MOTIVO);
    enviar('Cargar sobreturno');

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/sobreturno')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/turnos/sobreturno').cuerpo).toMatchObject({ profesionalId: 1 });
  });

  it('El motivo pide no escribir datos de salud', async () => {
    await abrirSobreturno();
    const motivo = campo(dialogo(), 'Motivo');
    expect(texto(motivo.closest('.campo') ?? dialogo())).toContain('Motivo administrativo. No escribas datos de salud.');
    expect(motivo.maxLength).toBe(500);
  });
});

describe('Enviar un sobreturno', () => {
  it('Recepción carga un sobreturno', async () => {
    servidor.responder('POST /turnos/sobreturno', () =>
      json(201, { ...turno({ id: 42, sillonId: 2, esSobreturno: true }), numeroReserva: 'R-0042' }),
    );
    await abrirSobreturno();
    const pedidosDeAgenda = servidor.a('GET', '/turnos').length;
    await completarSobreturno({ motivo: `  ${MOTIVO}  ` });
    enviar('Cargar sobreturno');

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/sobreturno')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/turnos/sobreturno').cuerpo).toEqual({
      pacienteId: 1,
      prestacionId: 1,
      profesionalId: 1,
      inicio: '2026-12-01T10:15:00-03:00',
      motivo: MOTIVO,
    });
    await vi.waitFor(() =>
      expect(avisoDeLaAgenda('status')).toBe('Sobreturno cargado en Sillón 2. Número de reserva: R-0042.'),
    );
    expect(document.querySelector('dialog[open]')).toBeNull();
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(pedidosDeAgenda));
  });

  it('Motivo obligatorio', async () => {
    await abrirSobreturno();
    await completarSobreturno({ motivo: '   ' });
    enviar('Cargar sobreturno');

    expect(errorDeCampo(dialogo(), 'Motivo')).toBe('Escribí el motivo del sobreturno.');
    expect(servidor.a('POST', '/turnos/sobreturno')).toHaveLength(0);
  });

  it('Faltan datos del sobreturno', async () => {
    await abrirSobreturno();
    await completarSobreturno({ paciente: false, hora: '' });
    enviar('Cargar sobreturno');

    expect(errorDeCampo(dialogo(), 'Paciente')).toBe('Elegí un paciente de la lista.');
    expect(errorDeCampo(dialogo(), 'Hora')).toBe('Elegí la hora.');
    expect(servidor.a('POST', '/turnos/sobreturno')).toHaveLength(0);
  });
});

describe('Acción Extender', () => {
  it('Un sobreturno no ofrece extender', async () => {
    await abrirAgenda([turno({ id: 8, esSobreturno: true, inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' })]);
    await vi.waitFor(() => expect(botonesDe(tarjeta(8))).toContain('Reprogramar'));
    expect(botonesDe(tarjeta(8))).not.toContain('Extender');
  });

  it('Un turno cancelado no ofrece extender', async () => {
    await abrirAgenda([turno({ id: 9, estado: 'CANCELADO' })]);
    await vi.waitFor(() => expect(tarjeta(9)).toBeDefined());
    expect(botonesDe(tarjeta(9))).not.toContain('Extender');
  });

  it('Un turno confirmado ofrece extender, después de reprogramar', async () => {
    await abrirAgenda([turno({ estado: 'CONFIRMADO' })]);
    await vi.waitFor(() => expect(botonesDe(tarjeta(7).querySelector('.turno-acciones') ?? tarjeta(7))).toEqual([
      'Cancelar turno',
      'Reprogramar',
      'Extender',
    ]));
  });
});

describe('Extender un turno', () => {
  it('Recepción extiende un turno', async () => {
    servidor.responder('POST /turnos/{id}/extender', () =>
      json(200, { ...turno({ fin: '2026-12-01T10:45:00-03:00' }), extendido: true, numeroReserva: 'R-0007' }),
    );
    await abrirExtension([turno()]);
    expect(texto(dialogo())).toContain('Paciente Uno, 2026-12-01 de 10:00 a 10:30.');
    expect(campo(dialogo(), 'Nuevo fin').value).toBe('10:45');
    const pedidosDeAgenda = servidor.a('GET', '/turnos').length;

    escribir(campo(dialogo(), 'Motivo'), 'Se demoró la atención');
    enviar('Extender turno');

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/7/extender')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/turnos/7/extender').cuerpo).toEqual({
      fin: '2026-12-01T10:45:00-03:00',
      motivo: 'Se demoró la atención',
    });
    await vi.waitFor(() => expect(avisoDeLaAgenda('status')).toBe('Turno extendido hasta las 10:45.'));
    expect(document.querySelector('dialog[open]')).toBeNull();
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(pedidosDeAgenda));
  });

  it.each(['10:30', '10:20'])('El nuevo fin no es posterior al actual (%s)', async (hora) => {
    await abrirExtension([turno()]);
    escribir(campo(dialogo(), 'Nuevo fin'), hora);
    escribir(campo(dialogo(), 'Motivo'), 'Se demoró la atención');
    enviar('Extender turno');

    expect(errorDeCampo(dialogo(), 'Nuevo fin')).toBe('Elegí una hora posterior a las 10:30.');
    expect(servidor.a('POST', '/turnos/7/extender')).toHaveLength(0);
  });

  it('Motivo obligatorio al extender', async () => {
    await abrirExtension([turno()]);
    enviar('Extender turno');

    expect(errorDeCampo(dialogo(), 'Motivo')).toBe('Escribí el motivo de la extensión.');
    expect(servidor.a('POST', '/turnos/7/extender')).toHaveLength(0);
  });
});

const TURNO_7 = turno({ id: 7 });
const TURNO_10 = turno({
  id: 10,
  pacienteId: 3,
  pacienteNombre: 'Carla',
  pacienteApellido: 'Ficticia',
  inicio: '2026-12-01T10:40:00-03:00',
  fin: '2026-12-01T11:10:00-03:00',
});

function rechazarExtension(codigo: string): void {
  servidor.responder('POST /turnos/{id}/extender', () =>
    json(409, { title: 'Conflicto', status: 409, detail: 'detalle del servidor', codigo }),
  );
}

async function chocarAlExtender(agenda: unknown[], codigo: string, fin = '11:00'): Promise<void> {
  rechazarExtension(codigo);
  await abrirExtension(agenda);
  escribir(campo(dialogo(), 'Nuevo fin'), fin);
  escribir(campo(dialogo(), 'Motivo'), 'Se demoró la atención');
  enviar('Extender turno');
  await vi.waitFor(() => expect(texto(dialogo())).toContain('La extensión choca con otro turno. No se movió nada.'));
}

describe('Choque al extender un turno', () => {
  it('La extensión choca con el turno siguiente', async () => {
    await chocarAlExtender([TURNO_7, TURNO_10], 'SOLAPAMIENTO_SILLON');

    const aviso = dialogo().querySelector<HTMLElement>('.aviso-choque');
    expect(texto(aviso ?? dialogo())).toContain('El sillón ya está ocupado en ese horario.');
    expect(botonesDe(aviso ?? dialogo())).toEqual(['Reprogramar el turno de las 10:40', 'Cargar como sobreturno']);
    expect(campo(dialogo(), 'Nuevo fin').value).toBe('11:00');
    expect(servidor.a('POST', '/turnos/10/reprogramar')).toHaveLength(0);
  });

  it('Cargar la extensión como sobreturno', async () => {
    await chocarAlExtender([TURNO_7, TURNO_10], 'SOLAPAMIENTO_SILLON');
    boton(dialogo(), 'Cargar como sobreturno').click();

    await vi.waitFor(() => expect(tituloDelDialogo()).toBe('Sobreturno'));
    expect(document.querySelectorAll('dialog[open]')).toHaveLength(1);
    expect(texto(dialogo())).toContain('Uno, Paciente');
    expect(campo<HTMLSelectElement>(dialogo(), 'Prestación').value).toBe('1');
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo(), 'Profesional').value).toBe('1'));
    expect(campo(dialogo(), 'Fecha').value).toBe('2026-12-01');
    expect(campo(dialogo(), 'Hora').value).toBe('10:30');
    expect(campo(dialogo(), 'Motivo').value).toBe('Se demoró la atención');

    servidor.responder('POST /turnos/sobreturno', () =>
      json(201, { ...turno({ id: 43, sillonId: 2, esSobreturno: true }), numeroReserva: 'R-0043' }),
    );
    enviar('Cargar sobreturno');
    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/sobreturno')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/turnos/sobreturno').cuerpo).toMatchObject({
      pacienteId: 1,
      inicio: '2026-12-01T10:30:00-03:00',
    });
  });
});

describe('Reprogramar el turno que choca', () => {
  it('Reprogramar el turno que choca', async () => {
    await chocarAlExtender([TURNO_7, TURNO_10], 'SOLAPAMIENTO_SILLON');
    boton(dialogo(), 'Reprogramar el turno de las 10:40').click();

    await vi.waitFor(() => expect(tituloDelDialogo()).toBe('Reprogramar turno'));
    expect(document.querySelectorAll('dialog[open]')).toHaveLength(1);
    expect(texto(dialogo())).toContain('Carla Ficticia, 2026-12-01 de 10:40 a 11:10.');
  });

  it('Choque sin turno identificable', async () => {
    await chocarAlExtender([TURNO_7], 'MARGEN_INSUFICIENTE');

    const aviso = dialogo().querySelector<HTMLElement>('.aviso-choque');
    expect(texto(aviso ?? dialogo())).toContain('No queda el margen mínimo entre turnos.');
    expect(botonesDe(aviso ?? dialogo())).toEqual(['Cargar como sobreturno']);
  });
});

describe('turnoQueChoca', () => {
  function agenda(datos: Partial<TurnoAgenda> = {}): TurnoAgenda {
    return {
      id: 10,
      pacienteId: 3,
      pacienteNombre: 'Carla',
      pacienteApellido: 'Ficticia',
      profesionalId: 2,
      sillonId: 1,
      prestacionId: 1,
      inicio: '2026-12-01T10:40:00-03:00',
      fin: '2026-12-01T11:10:00-03:00',
      estado: 'RESERVADO',
      esSobreturno: false,
      llegadaEn: null,
      ...datos,
    };
  }
  const extendido = agenda({ id: 7, pacienteId: 1, profesionalId: 1, inicio: '2026-12-01T10:00:00-03:00', fin: '2026-12-01T10:30:00-03:00' });
  const nuevoFin = '2026-12-01T11:00:00-03:00';

  it('encuentra el turno del mismo sillón que empieza antes del nuevo fin', () => {
    expect(turnoQueChoca(extendido, nuevoFin, [extendido, agenda()])?.id).toBe(10);
  });

  it('encuentra el turno del mismo profesional en otro sillón', () => {
    expect(turnoQueChoca(extendido, nuevoFin, [agenda({ sillonId: 2, profesionalId: 1 })])?.id).toBe(10);
  });

  it('cuenta el margen mínimo después del nuevo fin', () => {
    expect(turnoQueChoca(extendido, nuevoFin, [agenda({ inicio: '2026-12-01T11:05:00-03:00' })])?.id).toBe(10);
    expect(turnoQueChoca(extendido, nuevoFin, [agenda({ inicio: '2026-12-01T11:10:00-03:00' })])).toBeNull();
  });

  it('ignora otros sillones y profesionales, los cancelados y el propio turno', () => {
    expect(turnoQueChoca(extendido, nuevoFin, [agenda({ sillonId: 2 })])).toBeNull();
    expect(turnoQueChoca(extendido, nuevoFin, [agenda({ estado: 'CANCELADO' })])).toBeNull();
    expect(turnoQueChoca(extendido, nuevoFin, [extendido])).toBeNull();
  });

  it('elige el que empieza primero', () => {
    const turnos = [agenda({ id: 12, inicio: '2026-12-01T10:50:00-03:00' }), agenda({ id: 11, inicio: '2026-12-01T10:35:00-03:00' })];
    expect(turnoQueChoca(extendido, nuevoFin, turnos)?.id).toBe(11);
  });
});

describe('Mensajes de los rechazos de sobreturno y extensión', () => {
  it('Sin sillón libre para el sobreturno', async () => {
    servidor.responder('POST /turnos/sobreturno', () =>
      json(409, { title: 'Conflicto', status: 409, detail: 'detalle', codigo: 'SIN_SILLON_DISPONIBLE' }),
    );
    await abrirSobreturno();
    await completarSobreturno();
    enviar('Cargar sobreturno');

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('No hay ningún sillón libre en ese horario. Probá con otra hora.'),
    );
    expect(campo(dialogo(), 'Hora').value).toBe('10:15');
    expect(campo(dialogo(), 'Motivo').value).toBe(MOTIVO);
  });

  it('Turno que no se puede extender', async () => {
    rechazarExtension('TURNO_NO_EXTENSIBLE');
    await abrirExtension([turno()]);
    escribir(campo(dialogo(), 'Motivo'), 'Se demoró la atención');
    enviar('Extender turno');

    await vi.waitFor(() => expect(texto(dialogo())).toContain('Un sobreturno no se puede extender.'));
    expect(dialogo().querySelector('.aviso-choque')).toBeNull();
  });

  it('Turno que ya no está activo', async () => {
    rechazarExtension('TURNO_NO_ACTIVO');
    await abrirExtension([turno()]);
    escribir(campo(dialogo(), 'Motivo'), 'Se demoró la atención');
    enviar('Extender turno');

    await vi.waitFor(() => expect(texto(dialogo())).toContain('El turno ya no está activo.'));
  });

  it('Motivo rechazado por la API', async () => {
    servidor.responder('POST /turnos/sobreturno', () =>
      json(422, {
        title: 'Datos inválidos',
        status: 422,
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'motivo', mensaje: 'El motivo no puede superar los 500 caracteres.' }],
      }),
    );
    await abrirSobreturno();
    await completarSobreturno();
    enviar('Cargar sobreturno');

    await vi.waitFor(() =>
      expect(errorDeCampo(dialogo(), 'Motivo')).toBe('El motivo no puede superar los 500 caracteres.'),
    );
  });
});
