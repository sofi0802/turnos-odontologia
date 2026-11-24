import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { accionesDeAsistencia, etiquetaDeAsistencia } from '../src/agenda/asistencia.ts';
import type { TurnoAgenda } from '../src/agenda/consulta.ts';
import { abrirApp, boton, json, ServidorFalso, texto, turno, type AppDePrueba } from './apoyo.ts';

/**
 * C-43 registro-asistencia: un test por escenario del delta de `frontend-agenda-personal`. El día es el martes
 * 2026-12-01 en Buenos Aires y el turno 7 es de "Paciente Uno", de 10:00 a 10:30.
 */

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

const SESION_ODONTOLOGA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function ahoraEs(hora: string): void {
  vi.setSystemTime(new Date(`2026-12-01T${hora}:00-03:00`));
}

function tarjetas(): HTMLElement[] {
  return [...raiz().querySelectorAll<HTMLElement>('.turno')];
}

function tarjeta(id = 7): HTMLElement {
  const encontrada = raiz().querySelector<HTMLElement>(`.turno[data-id="${id}"]`);
  if (encontrada === null) {
    throw new Error(`No se ve el turno ${id}`);
  }
  return encontrada;
}

function botonesDe(elemento: HTMLElement): string[] {
  return [...elemento.querySelectorAll('button')].map((b) => b.textContent ?? '');
}

async function abrirAgenda(): Promise<void> {
  app = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(raiz().querySelector('.agenda-lista[aria-busy="true"]')).toBeNull());
  await vi.waitFor(() => expect(tarjetas().length).toBeGreaterThan(0));
}

function aviso(rol: 'alert' | 'status'): string {
  return raiz().querySelector(`main [role="${rol}"]`)?.textContent ?? '';
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  ahoraEs('11:00');
  servidor = new ServidorFalso();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
});

describe('Etiquetas de asistencia', () => {
  it('Turnos con asistencia registrada', async () => {
    servidor.responder('GET /turnos', () =>
      json(200, [
        turno({ id: 1, estado: 'ATENDIDO', inicio: '2026-12-01T09:00:00-03:00', fin: '2026-12-01T09:30:00-03:00' }),
        turno({ id: 2, estado: 'AUSENTE', inicio: '2026-12-01T09:30:00-03:00', fin: '2026-12-01T10:00:00-03:00' }),
        turno({
          id: 3,
          inicio: '2026-12-01T10:40:00-03:00',
          fin: '2026-12-01T11:10:00-03:00',
          llegadaEn: '2026-12-01T10:35:00-03:00',
        }),
      ]),
    );

    await abrirAgenda();

    expect(texto(tarjeta(1))).toContain('Atendido');
    expect(texto(tarjeta(2))).toContain('Ausente');
    expect(texto(tarjeta(3))).toContain('Llegó 10:35');
    for (const id of [1, 2]) {
      expect(botonesDe(tarjeta(id))).not.toContain('Cancelar turno');
      expect(botonesDe(tarjeta(id))).not.toContain('Reprogramar');
    }
    // la diferencia no es solo el color: cada estado tiene su texto en una etiqueta
    expect(tarjeta(1).querySelector('.etiqueta-atendido')?.textContent).toBe('Atendido');
    expect(tarjeta(3).querySelector('.etiqueta-llegada')?.textContent).toBe('Llegó 10:35');
  });

  it('Turno sin registrar en la agenda', async () => {
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    expect(tarjeta().querySelector('.etiqueta-sin-registrar')?.textContent).toBe('Sin registrar');
    expect(tarjeta().classList.contains('turno-sin-registrar')).toBe(true);
  });

  it('las etiquetas se ven para todos los roles del personal', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    expect(texto(tarjeta())).toContain('Sin registrar');
  });
});

describe('Acciones de asistencia de la recepción', () => {
  it('Registrar la llegada', async () => {
    ahoraEs('10:05');
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    servidor.responder('POST /turnos/{id}/llegada', () => json(200, turno({ llegadaEn: '2026-12-01T10:05:00-03:00' })));
    await abrirAgenda();

    boton(tarjeta(), 'Llegó').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/7/llegada')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('Ausente antes de la tolerancia', async () => {
    ahoraEs('10:10');
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    const ausente = boton(tarjeta(), 'Ausente');
    expect(ausente.disabled).toBe(true);
    const pista = document.getElementById(ausente.getAttribute('aria-describedby') ?? '');
    expect(pista?.textContent).toBe('Desde las 10:15');
  });

  it('Marcar ausente', async () => {
    ahoraEs('10:20');
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    servidor.responder('POST /turnos/{id}/ausencia', () => json(200, turno({ estado: 'AUSENTE' })));
    await abrirAgenda();

    const ausente = boton(tarjeta(), 'Ausente');
    expect(ausente.disabled).toBe(false);
    ausente.click();

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/7/ausencia')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('Deshacer la ausencia', async () => {
    ahoraEs('10:22');
    servidor.responder('GET /turnos', () => json(200, [turno({ estado: 'AUSENTE' })]));
    servidor.responder('POST /turnos/{id}/deshacer-ausencia', () => json(200, turno()));
    await abrirAgenda();

    boton(tarjeta(), 'Deshacer ausencia').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/turnos/7/deshacer-ausencia')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  });

  it('El odontólogo no ve las acciones de asistencia', async () => {
    ahoraEs('10:20');
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGA));
    servidor.responder('GET /turnos', () => json(200, [turno()]));

    await abrirAgenda();

    const botones = botonesDe(tarjeta());
    expect(botones).not.toContain('Llegó');
    expect(botones).not.toContain('Ausente');
    expect(botones).not.toContain('Deshacer ausencia');
  });

  it('el administrador tampoco ve las acciones de asistencia', async () => {
    ahoraEs('10:22');
    servidor.responder('GET /public/sesion', () => json(200, SESION_ADMIN));
    servidor.responder('GET /turnos', () =>
      json(200, [turno(), turno({ id: 8, estado: 'AUSENTE', inicio: '2026-12-01T10:00:00-03:00' })]),
    );

    await abrirAgenda();

    for (const id of [7, 8]) {
      expect(botonesDe(tarjeta(id))).not.toContain('Llegó');
      expect(botonesDe(tarjeta(id))).not.toContain('Ausente');
      expect(botonesDe(tarjeta(id))).not.toContain('Deshacer ausencia');
    }
  });
});

describe('Mensajes de los rechazos de asistencia', () => {
  async function rechazo(
    estado: string,
    hora: string,
    accion: string,
    ruta: string,
    codigo: string,
  ): Promise<void> {
    ahoraEs(hora);
    servidor.responder('GET /turnos', () => json(200, [turno({ estado })]));
    servidor.responder(`POST /turnos/{id}/${ruta}`, () => json(409, { status: 409, codigo, detail: codigo }));
    await abrirAgenda();

    boton(tarjeta(), accion).click();

    await vi.waitFor(() => expect(servidor.a('POST', `/turnos/7/${ruta}`)).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
  }

  it('Deshacer con el horario ocupado', async () => {
    await rechazo('AUSENTE', '10:22', 'Deshacer ausencia', 'deshacer-ausencia', 'SOLAPAMIENTO_SILLON');

    await vi.waitFor(() => expect(aviso('alert')).toBe('El horario ya se ocupó. Dale otro turno al paciente.'));
  });

  it.each(['SOLAPAMIENTO_PROFESIONAL', 'MARGEN_INSUFICIENTE'])(
    'al deshacer, %s muestra el mismo mensaje',
    async (codigo) => {
      await rechazo('AUSENTE', '10:22', 'Deshacer ausencia', 'deshacer-ausencia', codigo);

      await vi.waitFor(() => expect(aviso('alert')).toBe('El horario ya se ocupó. Dale otro turno al paciente.'));
    },
  );

  it('Llegada ya registrada en otra pantalla', async () => {
    await rechazo('RESERVADO', '10:05', 'Llegó', 'llegada', 'LLEGADA_REGISTRADA');

    await vi.waitFor(() => expect(aviso('alert')).toBe('El paciente ya tiene la llegada registrada.'));
  });

  it('Rechazo AUSENCIA_ANTES_DE_TOLERANCIA', async () => {
    await rechazo('RESERVADO', '10:20', 'Ausente', 'ausencia', 'AUSENCIA_ANTES_DE_TOLERANCIA');

    await vi.waitFor(() => expect(aviso('alert')).toBe('Todavía no pasaron 15 minutos desde el inicio del turno.'));
  });

  it('Rechazo LLEGADA_ANTICIPADA', async () => {
    await rechazo('RESERVADO', '10:05', 'Llegó', 'llegada', 'LLEGADA_ANTICIPADA');

    await vi.waitFor(() => expect(aviso('alert')).toBe('La llegada se registra el día del turno.'));
  });

  it('Rechazo TURNO_NO_AUSENTE', async () => {
    await rechazo('AUSENTE', '10:22', 'Deshacer ausencia', 'deshacer-ausencia', 'TURNO_NO_AUSENTE');

    await vi.waitFor(() => expect(aviso('alert')).toBe('El turno no está marcado como ausente.'));
  });

  it('Rechazo TURNO_TERMINADO', async () => {
    await rechazo('AUSENTE', '10:22', 'Deshacer ausencia', 'deshacer-ausencia', 'TURNO_TERMINADO');

    await vi.waitFor(() => expect(aviso('alert')).toBe('El turno ya terminó.'));
  });
});

describe('etiquetaDeAsistencia y accionesDeAsistencia', () => {
  function turnoAgenda(datos: Partial<TurnoAgenda> = {}): TurnoAgenda {
    return {
      id: 7,
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
      ...datos,
    };
  }

  const a = (hora: string): Date => new Date(`2026-12-01T${hora}:00-03:00`);

  it('un turno futuro o en curso sin llegada no tiene etiqueta de asistencia', () => {
    expect(etiquetaDeAsistencia(turnoAgenda(), a('09:00'))).toBeNull();
    expect(etiquetaDeAsistencia(turnoAgenda(), a('10:29'))).toBeNull();
    expect(etiquetaDeAsistencia(turnoAgenda(), a('10:30'))?.texto).toBe('Sin registrar');
  });

  it('la recepción ve "Llegó" desde el día del turno, y "Ausente" habilitado desde los 15 minutos', () => {
    expect(accionesDeAsistencia(turnoAgenda(), 'RECEPCIONISTA', new Date('2026-11-30T18:00:00-03:00'))).toEqual({
      llego: false,
      ausente: { habilitado: false, desde: '10:15' },
      deshacer: false,
    });
    expect(accionesDeAsistencia(turnoAgenda(), 'RECEPCIONISTA', a('00:00'))).toEqual({
      llego: true,
      ausente: { habilitado: false, desde: '10:15' },
      deshacer: false,
    });
    expect(accionesDeAsistencia(turnoAgenda(), 'RECEPCIONISTA', a('10:15'))).toEqual({
      llego: true,
      ausente: { habilitado: true, desde: '10:15' },
      deshacer: false,
    });
  });

  it('con la llegada registrada no hay acciones de asistencia', () => {
    expect(
      accionesDeAsistencia(turnoAgenda({ llegadaEn: '2026-12-01T10:05:00-03:00' }), 'RECEPCIONISTA', a('10:20')),
    ).toEqual({ llego: false, ausente: null, deshacer: false });
  });

  it('"Deshacer ausencia" solo antes del fin', () => {
    const ausente = turnoAgenda({ estado: 'AUSENTE' });
    expect(accionesDeAsistencia(ausente, 'RECEPCIONISTA', a('10:29')).deshacer).toBe(true);
    expect(accionesDeAsistencia(ausente, 'RECEPCIONISTA', a('10:30')).deshacer).toBe(false);
  });

  it('otro rol no recibe acciones', () => {
    for (const rol of ['ADMIN', 'ODONTOLOGO', null] as const) {
      expect(accionesDeAsistencia(turnoAgenda(), rol, a('10:20'))).toEqual({
        llego: false,
        ausente: null,
        deshacer: false,
      });
    }
  });
});
