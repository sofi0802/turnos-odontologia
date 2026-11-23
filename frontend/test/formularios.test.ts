import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

function hayDialogo(): boolean {
  return document.querySelector('dialog[open]') !== null;
}

async function abrirAgenda(): Promise<void> {
  app = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(1));
  await vi.waitFor(() => expect(raiz().querySelector('.agenda-lista[aria-busy="true"]')).toBeNull());
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(raiz(), 'Profesional').options.length).toBeGreaterThan(1));
}

async function abrirNuevoTurnoCompleto(): Promise<void> {
  await abrirAgenda();
  boton(raiz(), 'Nuevo turno').click();
  const formulario = dialogo();
  await elegirPaciente(formulario, '99000001', 'Uno, Paciente · DNI 99000001');
  escribir(campo<HTMLSelectElement>(formulario, 'Profesional'), '1');
  escribir(campo<HTMLSelectElement>(formulario, 'Sillón'), '1');
  escribir(campo<HTMLSelectElement>(formulario, 'Prestación'), '1');
  escribir(campo(formulario, 'Fecha'), '2026-12-01');
  escribir(campo(formulario, 'Hora'), '10:00');
}

async function enviarConRechazo(estado: number, cuerpo: unknown): Promise<void> {
  servidor.responder('POST /turnos', () => json(estado, cuerpo));
  await abrirNuevoTurnoCompleto();
  boton(dialogo(), 'Crear turno').click();
  await vi.waitFor(() => expect(servidor.a('POST', '/turnos')).toHaveLength(1));
  await vi.waitFor(() => expect(dialogo().querySelector('[role="alert"]')?.textContent ?? '').not.toBe(''));
}

function alertaDelFormulario(): string {
  return [...dialogo().querySelectorAll('[role="alert"]')].map((a) => a.textContent).join(' ');
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.responder('GET /turnos', () => json(200, [turno()]));
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

describe('Crear un turno', () => {
  it('Recepción crea un turno', async () => {
    servidor.responder('POST /turnos', () =>
      json(201, { ...turno(), numeroReserva: 'R-0001', estado: 'RESERVADO' }),
    );
    await abrirNuevoTurnoCompleto();

    boton(dialogo(), 'Crear turno').click();

    await vi.waitFor(() => expect(texto(raiz())).toContain('Turno creado. Número de reserva: R-0001.'));
    expect(servidor.ultimo('POST', '/turnos').cuerpo).toEqual({
      pacienteId: 1,
      profesionalId: 1,
      sillonId: 1,
      prestacionId: 1,
      inicio: '2026-12-01T10:00:00-03:00',
    });
    expect(hayDialogo()).toBe(false);
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
    expect(Object.fromEntries(servidor.ultimo('GET', '/turnos').query)).toEqual({
      desde: '2026-12-01T00:00:00-03:00',
      hasta: '2026-12-02T00:00:00-03:00',
    });
  });

  it('Faltan datos en el formulario', async () => {
    await abrirAgenda();
    boton(raiz(), 'Nuevo turno').click();
    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Profesional'), '1');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Prestación'), '1');
    escribir(campo(dialogo(), 'Hora'), '10:00');

    boton(dialogo(), 'Crear turno').click();

    expect(errorDeCampo(dialogo(), 'Sillón')).toBe('Elegí un sillón.');
    expect(errorDeCampo(dialogo(), 'Profesional')).toBe('');
    expect(servidor.a('POST', '/turnos')).toHaveLength(0);
  });

  it('Paciente escrito pero no elegido', async () => {
    await abrirNuevoTurnoCompleto();
    boton(dialogo(), 'Cambiar paciente').click();
    escribir(campo(dialogo(), 'Paciente'), 'fict');

    boton(dialogo(), 'Crear turno').click();

    expect(servidor.a('POST', '/turnos')).toHaveLength(0);
    expect(errorDeCampo(dialogo(), 'Paciente')).toBe('Elegí un paciente de la lista.');
  });

  it('Ya no se pide el id del paciente', async () => {
    await abrirAgenda();

    boton(raiz(), 'Nuevo turno').click();

    expect(texto(dialogo())).not.toContain('Id del paciente');
    const primero = dialogo().querySelector<HTMLInputElement>('form input, form select');
    expect(primero?.getAttribute('role')).toBe('combobox');
    expect(document.activeElement).toBe(primero);
    expect(campo(dialogo(), 'Paciente')).toBe(primero);
  });

  it('el formulario solo ofrece profesionales activos y propone el día mostrado', async () => {
    servidor.responder('GET /profesionales', () =>
      json(200, [
        { id: 1, nombre: 'Ana', apellido: 'Ejemplo', activo: true },
        { id: 3, nombre: 'Carla', apellido: 'Inactiva', activo: false },
      ]),
    );
    await abrirAgenda();

    boton(raiz(), 'Nuevo turno').click();

    const opciones = [...campo<HTMLSelectElement>(dialogo(), 'Profesional').options].map((o) => o.textContent);
    expect(opciones).toEqual(['Elegí un profesional', 'Ana Ejemplo']);
    expect(campo(dialogo(), 'Fecha').value).toBe('2026-12-01');
  });
});

describe('Mensajes por código de rechazo', () => {
  it('Solapamiento con el profesional', async () => {
    await enviarConRechazo(409, { status: 409, detail: 'x', codigo: 'SOLAPAMIENTO_PROFESIONAL' });
    expect(alertaDelFormulario()).toContain('El profesional ya tiene un turno en ese horario.');
    expect(texto(dialogo())).toContain('Uno, Paciente · DNI 99000001');
    expect(campo(dialogo(), 'Hora').value).toBe('10:00');
  });

  it('Margen insuficiente', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'MARGEN_INSUFICIENTE' });
    expect(alertaDelFormulario()).toContain('No queda el margen mínimo entre turnos.');
  });

  it('Sillón ocupado', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'SOLAPAMIENTO_SILLON' });
    expect(alertaDelFormulario()).toContain('El sillón ya está ocupado en ese horario.');
  });

  it('Inicio en el pasado', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'INICIO_EN_EL_PASADO' });
    expect(alertaDelFormulario()).toContain('El horario elegido ya pasó.');
  });

  it('Recurso inactivo', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'RECURSO_INACTIVO' });
    expect(alertaDelFormulario()).toContain('El profesional, el sillón o la prestación está inactivo.');
  });

  it('Fuera del horario del profesional', async () => {
    servidor.responder('POST /turnos', () => json(409, { status: 409, codigo: 'FUERA_DE_HORARIO' }));
    await abrirNuevoTurnoCompleto();
    escribir(campo(dialogo(), 'Hora'), '15:00');

    boton(dialogo(), 'Crear turno').click();

    await vi.waitFor(() => expect(alertaDelFormulario()).toContain('El profesional no atiende en ese horario.'));
    expect(campo(dialogo(), 'Hora').value).toBe('15:00');
    expect(campo<HTMLSelectElement>(dialogo(), 'Profesional').value).toBe('1');
  });

  it('Profesional ausente', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'PROFESIONAL_AUSENTE' });
    expect(alertaDelFormulario()).toContain('El profesional tiene registrada una ausencia en ese horario.');
  });

  it('Agenda bloqueada', async () => {
    await enviarConRechazo(409, { status: 409, codigo: 'AGENDA_BLOQUEADA' });
    expect(alertaDelFormulario()).toContain('La agenda del profesional está bloqueada en ese horario.');
  });

  it('Código sin mensaje propio', async () => {
    await enviarConRechazo(409, { status: 409, detail: 'Regla ficticia', codigo: 'OTRO_CODIGO' });
    expect(alertaDelFormulario()).toContain('Regla ficticia');
  });
});

describe('Errores de campo ante un 422', () => {
  it('Profesional inexistente', async () => {
    servidor.responder('POST /turnos', () =>
      json(422, {
        status: 422,
        detail: 'Hay datos inválidos en el pedido',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'profesionalId', mensaje: 'no existe' }],
      }),
    );
    await abrirNuevoTurnoCompleto();

    boton(dialogo(), 'Crear turno').click();

    await vi.waitFor(() => expect(errorDeCampo(dialogo(), 'Profesional')).toBe('no existe'));
    expect(hayDialogo()).toBe(true);
  });

  it('Error de la API en el paciente', async () => {
    servidor.responder('POST /turnos', () =>
      json(422, {
        status: 422,
        detail: 'Hay datos inválidos en el pedido',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'pacienteId', mensaje: 'no existe' }],
      }),
    );
    await abrirNuevoTurnoCompleto();
    boton(dialogo(), 'Cambiar paciente').click();
    await elegirPaciente(dialogo(), 'fict', 'Ficticia, Carla · DNI 99000003');

    boton(dialogo(), 'Crear turno').click();

    await vi.waitFor(() => expect(errorDeCampo(dialogo(), 'Paciente')).toBe('no existe'));
    expect(hayDialogo()).toBe(true);
  });

  it('un error de un campo que el formulario no muestra va al mensaje general', async () => {
    servidor.responder('POST /turnos', () =>
      json(422, {
        status: 422,
        detail: 'Hay datos inválidos en el pedido',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'otroCampo', mensaje: 'es inválido' }],
      }),
    );
    await abrirNuevoTurnoCompleto();

    boton(dialogo(), 'Crear turno').click();

    await vi.waitFor(() => expect(alertaDelFormulario()).toContain('es inválido'));
  });
});

describe('Cancelar un turno', () => {
  async function abrirCancelacion(): Promise<void> {
    await abrirAgenda();
    await vi.waitFor(() => expect(raiz().querySelectorAll('.turno')).toHaveLength(1));
    boton(raiz().querySelector('.turno') ?? raiz(), 'Cancelar turno').click();
    escribir(campo(dialogo(), 'DNI del paciente'), '99000001');
    escribir(campo(dialogo(), 'Número de reserva'), 'R-0001');
  }

  it('Recepción cancela un turno', async () => {
    servidor.responder('POST /turnos/{id}/cancelar', () => json(200, { ...turno(), estado: 'CANCELADO' }));
    await abrirCancelacion();

    boton(dialogo(), 'Confirmar cancelación').click();

    await vi.waitFor(() => expect(texto(raiz())).toContain('Turno cancelado.'));
    expect(servidor.ultimo('POST', '/turnos/7/cancelar').cuerpo).toEqual({ dni: '99000001', numeroReserva: 'R-0001' });
    expect(hayDialogo()).toBe(false);
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('Turno ya cancelado', async () => {
    servidor.responder('POST /turnos/{id}/cancelar', () => json(409, { status: 409, codigo: 'TURNO_NO_ACTIVO' }));
    await abrirCancelacion();

    boton(dialogo(), 'Confirmar cancelación').click();

    await vi.waitFor(() => expect(alertaDelFormulario()).toContain('El turno ya no está activo.'));
  });

  it('Turno que ya no existe', async () => {
    servidor.responder('POST /turnos/{id}/cancelar', () => json(404, { status: 404, codigo: 'TURNO_INEXISTENTE' }));
    await abrirCancelacion();

    boton(dialogo(), 'Confirmar cancelación').click();

    await vi.waitFor(() => expect(texto(raiz())).toContain('El turno ya no existe.'));
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('la cancelación pide DNI y número de reserva', async () => {
    await abrirAgenda();
    await vi.waitFor(() => expect(raiz().querySelectorAll('.turno')).toHaveLength(1));
    boton(raiz().querySelector('.turno') ?? raiz(), 'Cancelar turno').click();

    boton(dialogo(), 'Confirmar cancelación').click();

    expect(errorDeCampo(dialogo(), 'DNI del paciente')).toBe('Ingresá el DNI.');
    expect(errorDeCampo(dialogo(), 'Número de reserva')).toBe('Ingresá el número de reserva.');
    expect(servidor.a('POST', '/turnos/7/cancelar')).toHaveLength(0);
  });
});

describe('Reprogramar un turno', () => {
  async function abrirReprogramacion(): Promise<void> {
    await abrirAgenda();
    await vi.waitFor(() => expect(raiz().querySelectorAll('.turno')).toHaveLength(1));
    boton(raiz().querySelector('.turno') ?? raiz(), 'Reprogramar').click();
    escribir(campo(dialogo(), 'DNI del paciente'), '99000001');
    escribir(campo(dialogo(), 'Número de reserva'), 'R-0001');
    escribir(campo(dialogo(), 'Hora'), '11:00');
  }

  it('Recepción reprograma un turno', async () => {
    servidor.responder('POST /turnos/{id}/reprogramar', () =>
      json(200, { ...turno({ id: 8, inicio: '2026-12-01T11:00:00-03:00' }), numeroReserva: 'R-0002' }),
    );
    await abrirReprogramacion();
    expect(campo(dialogo(), 'Fecha').value).toBe('2026-12-01');

    boton(dialogo(), 'Confirmar reprogramación').click();

    await vi.waitFor(() => expect(texto(raiz())).toContain('Turno reprogramado. Nuevo número de reserva: R-0002.'));
    expect(servidor.ultimo('POST', '/turnos/7/reprogramar').cuerpo).toEqual({
      dni: '99000001',
      numeroReserva: 'R-0001',
      inicio: '2026-12-01T11:00:00-03:00',
    });
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('reprogramar con otro profesional y otro sillón', async () => {
    servidor.responder('POST /turnos/{id}/reprogramar', () => json(200, { ...turno(), numeroReserva: 'R-0003' }));
    await abrirReprogramacion();
    escribir(campo<HTMLSelectElement>(dialogo(), 'Profesional'), '2');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Sillón'), '2');

    boton(dialogo(), 'Confirmar reprogramación').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/7/reprogramar')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/turnos/7/reprogramar').cuerpo).toMatchObject({ profesionalId: 2, sillonId: 2 });
  });

  it('Reprogramación rechazada por el sillón', async () => {
    servidor.responder('POST /turnos/{id}/reprogramar', () =>
      json(409, { status: 409, codigo: 'SOLAPAMIENTO_SILLON' }),
    );
    await abrirReprogramacion();

    boton(dialogo(), 'Confirmar reprogramación').click();

    await vi.waitFor(() => expect(alertaDelFormulario()).toContain('El sillón ya está ocupado en ese horario.'));
    expect(hayDialogo()).toBe(true);
  });
});
