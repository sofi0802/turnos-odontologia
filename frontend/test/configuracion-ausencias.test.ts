import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json, PROFESIONALES, texto, turno, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  enviar,
  PRESTACIONES_SEED,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';

/** Ausencia 4 de la Dra. Ana Ejemplo, el lunes 7 de diciembre de 08:00 a 13:00. */
const AUSENCIA_4 = {
  id: 4,
  profesionalId: 1,
  tipo: 'AUSENCIA',
  inicio: '2026-12-07T08:00:00-03:00',
  fin: '2026-12-07T13:00:00-03:00',
  motivo: 'Trámite personal',
};

const LICENCIA = {
  id: 5,
  profesionalId: 1,
  tipo: 'LICENCIA',
  inicio: '2026-12-14T08:00:00-03:00',
  fin: '2026-12-18T13:00:00-03:00',
  motivo: 'Congreso',
};

/** Prestaciones habilitadas por profesional; el 3 (Tratamiento de conducto) solo lo hace Bruno. */
const ESPECIALIDADES: Record<number, number[]> = { 1: [1, 2], 2: [1, 3], 3: [1, 2] };

function conEspecialidades(servidor: ServidorFalso): void {
  servidor.responder('GET /profesionales/{id}/prestaciones', (pedido) => {
    const id = Number(pedido.ruta.split('/')[2]);
    const ids = ESPECIALIDADES[id] ?? [];
    return json(200, PRESTACIONES_SEED.filter((p) => ids.includes(p.id)));
  });
}

/** Servidor cuyas ausencias de la Dra. Ana Ejemplo cambian con los `POST` y `DELETE`. */
function servidorConAusencias(sesion: object, iniciales: object[]): { servidor: ServidorFalso; ausencias: object[] } {
  const servidor = servidorCon(sesion);
  const ausencias = [...iniciales];
  servidor.responder('GET /profesionales/{id}/ausencias', () => json(200, ausencias));
  conEspecialidades(servidor);
  return { servidor, ausencias };
}

function entrada(dialogo: HTMLElement, nombre: string): HTMLInputElement {
  const encontrada = dialogo.querySelector<HTMLInputElement>(`input[name="${nombre}"]`);
  if (encontrada === null) {
    throw new Error(`No existe el campo ${nombre}`);
  }
  return encontrada;
}

function completarPeriodo(dialogo: HTMLElement, desde: string, horaDesde: string, hasta: string, horaHasta: string): void {
  escribir(entrada(dialogo, 'inicio'), desde);
  escribir(entrada(dialogo, 'horaInicio'), horaDesde);
  escribir(entrada(dialogo, 'fin'), hasta);
  escribir(entrada(dialogo, 'horaFin'), horaHasta);
}

/** Abre la pestaña "Ausencias" y espera la lista de la Dra. Ana Ejemplo. */
async function abrirAusencias(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Ausencias');
  await vi.waitFor(() => expect(servidor.a('GET', '/profesionales/1/ausencias')).toHaveLength(1));
  await vi.waitFor(() => expect(texto(panel)).not.toContain('Cargando'));
  return panel;
}

function filasAfectadas(panel: HTMLElement): HTMLLIElement[] {
  return [...panel.querySelectorAll<HTMLLIElement>('.afectados li')];
}

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Ausencias y licencias', () => {
  it('El odontólogo solo ve sus ausencias', async () => {
    const { servidor } = servidorConAusencias(SESION_ODONTOLOGA, [LICENCIA]);

    const panel = await abrirAusencias(servidor);

    await vi.waitFor(() => expect(texto(panel)).toContain('Congreso'));
    expect(texto(panel)).toContain('Licencia');
    expect(texto(panel)).toContain('Lunes 14 de diciembre 08:00 al viernes 18 de diciembre 13:00');
    expect(texto(panel)).toContain('Tus ausencias');
    expect(panel.querySelector('select')).toBeNull();
    const botones = [...panel.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones).not.toContain('Registrar ausencia');
    expect(botones).not.toContain('Borrar');
    expect(servidor.pedidos.filter((p) => p.ruta.includes('/ausencias') && p.ruta !== '/profesionales/1/ausencias')).toHaveLength(0);
  });
});

describe('Registro y borrado de ausencias', () => {
  it('Recepción registra una licencia', async () => {
    const { servidor, ausencias } = servidorConAusencias(SESION_RECEPCION_CONFIG, []);
    servidor.responder('POST /profesionales/{id}/ausencias', (pedido) => {
      const periodo = { id: 5, profesionalId: 1, ...(pedido.cuerpo as object) };
      ausencias.push(periodo);
      return json(201, { periodo, turnosAfectados: [] });
    });
    const panel = await abrirAusencias(servidor);

    boton(panel, 'Registrar ausencia').click();
    const dialogo = await dialogoAbierto();
    expect(texto(dialogo)).toContain('Registrar ausencia de Ana Ejemplo');
    escribir(campo<HTMLSelectElement>(dialogo, 'Tipo'), 'LICENCIA');
    completarPeriodo(dialogo, '2026-12-07', '08:00', '2026-12-11', '13:00');
    escribir(campo(dialogo, 'Motivo (opcional)'), 'Congreso');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/profesionales/1/ausencias')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/profesionales/1/ausencias').cuerpo).toEqual({
      tipo: 'LICENCIA',
      inicio: '2026-12-07T08:00:00-03:00',
      fin: '2026-12-11T13:00:00-03:00',
      motivo: 'Congreso',
    });
    await vi.waitFor(() => expect(texto(panel.querySelector('.lista-config') ?? panel)).toContain('Licencia'));
    expect(texto(panel)).toContain('Lunes 7 de diciembre 08:00 al viernes 11 de diciembre 13:00');
    expect(dialogo.open).toBe(false);
  });

  it('Fin anterior al inicio', async () => {
    const { servidor } = servidorConAusencias(SESION_RECEPCION_CONFIG, []);
    servidor.responder('POST /profesionales/{id}/ausencias', () =>
      json(422, { status: 422, codigo: 'PERIODO_INVALIDO', detail: 'periodo invalido' }),
    );
    const panel = await abrirAusencias(servidor);

    boton(panel, 'Registrar ausencia').click();
    const dialogo = await dialogoAbierto();
    completarPeriodo(dialogo, '2026-12-11', '08:00', '2026-12-07', '13:00');
    enviar(dialogo);

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('El fin tiene que ser posterior al inicio.'),
    );
    expect(dialogo.open).toBe(true);
    expect(entrada(dialogo, 'inicio').value).toBe('2026-12-11');
  });

  it('Borrar una ausencia pide confirmación', async () => {
    const { servidor, ausencias } = servidorConAusencias(SESION_ADMIN, [AUSENCIA_4]);
    servidor.responder('DELETE /profesionales/{id}/ausencias/{id}', () => {
      ausencias.splice(0);
      return json(204);
    });
    const panel = await abrirAusencias(servidor);
    await vi.waitFor(() => expect(texto(panel)).toContain('Trámite personal'));

    boton(panel, 'Borrar').click();
    const dialogo = await dialogoAbierto();
    expect(servidor.a('DELETE', '/profesionales/1/ausencias/4')).toHaveLength(0);
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('DELETE', '/profesionales/1/ausencias/4')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(panel)).toContain('No hay ausencias registradas.'));
  });
});

describe('Turnos afectados por una ausencia', () => {
  it('Registrar una ausencia con turnos dados', async () => {
    const { servidor } = servidorConAusencias(SESION_RECEPCION_CONFIG, []);
    servidor.responder('POST /profesionales/{id}/ausencias', () =>
      json(201, {
        periodo: { ...AUSENCIA_4, motivo: null },
        turnosAfectados: [
          { turnoId: 10, inicio: '2026-12-07T09:00:00-03:00', fin: '2026-12-07T09:30:00-03:00', prestacionId: 1 },
        ],
      }),
    );
    const panel = await abrirAusencias(servidor);

    boton(panel, 'Registrar ausencia').click();
    const dialogo = await dialogoAbierto();
    completarPeriodo(dialogo, '2026-12-07', '08:00', '2026-12-07', '13:00');
    enviar(dialogo);

    await vi.waitFor(() => expect(texto(panel)).toContain('Turnos afectados: 1'));
    const filas = filasAfectadas(panel);
    expect(filas).toHaveLength(1);
    const fila = filas[0] as HTMLLIElement;
    expect(texto(fila)).toContain('Lunes 7 de diciembre · 09:00');
    expect(texto(fila)).toContain('Consulta o control');
    expect(boton(fila, 'Reasignar')).toBeDefined();
  });

  it('Ver los turnos afectados de una ausencia ya registrada', async () => {
    const { servidor } = servidorConAusencias(SESION_ADMIN, [AUSENCIA_4]);
    servidor.responder('GET /turnos', () =>
      json(200, [
        turno({ id: 10, inicio: '2026-12-07T09:00:00-03:00', fin: '2026-12-07T09:30:00-03:00', estado: 'RESERVADO' }),
        turno({ id: 11, inicio: '2026-12-07T10:00:00-03:00', fin: '2026-12-07T10:30:00-03:00', estado: 'CONFIRMADO' }),
        turno({ id: 12, inicio: '2026-12-07T11:00:00-03:00', fin: '2026-12-07T11:30:00-03:00', estado: 'CANCELADO' }),
      ]),
    );
    const panel = await abrirAusencias(servidor);
    await vi.waitFor(() => expect(texto(panel)).toContain('Trámite personal'));

    boton(panel, 'Ver turnos afectados').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Turnos afectados: 2'));
    const pedido = servidor.ultimo('GET', '/turnos');
    expect(pedido.query.get('desde')).toBe('2026-12-07T08:00:00-03:00');
    expect(pedido.query.get('hasta')).toBe('2026-12-07T13:00:00-03:00');
    expect(pedido.query.get('profesionalId')).toBe('1');
    const filas = filasAfectadas(panel).map(texto);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('Lunes 7 de diciembre · 09:00');
    expect(filas[1]).toContain('Lunes 7 de diciembre · 10:00');
    // Sin datos del paciente (design D7, RN-NO-01).
    expect(texto(panel.querySelector('.afectados') ?? panel)).not.toContain('Paciente');
  });
});

describe('Reasignación de un turno afectado', () => {
  it('Reasignar un turno afectado', async () => {
    const { servidor } = servidorConAusencias(SESION_RECEPCION_CONFIG, [AUSENCIA_4]);
    servidor.responder('GET /turnos', () =>
      json(200, [turno({ id: 10, inicio: '2026-12-07T09:00:00-03:00', fin: '2026-12-07T09:30:00-03:00' })]),
    );
    servidor.responder('POST /profesionales/{id}/ausencias/{id}/turnos/{id}/reasignar', () =>
      json(200, turno({ id: 20, profesionalId: 2, inicio: '2026-12-07T09:00:00-03:00' })),
    );
    const panel = await abrirAusencias(servidor);
    await vi.waitFor(() => expect(texto(panel)).toContain('Trámite personal'));
    boton(panel, 'Ver turnos afectados').click();
    await vi.waitFor(() => expect(filasAfectadas(panel)).toHaveLength(1));

    boton(panel, 'Reasignar').click();
    const dialogo = await dialogoAbierto();
    const selector = campo<HTMLSelectElement>(dialogo, 'Nuevo profesional');
    await vi.waitFor(() => expect([...selector.options].map((o) => o.textContent)).toEqual(['Bruno Prueba']));
    escribir(selector, '2');
    await vi.waitFor(() => expect(boton(dialogo, 'Reasignar').disabled).toBe(false));
    enviar(dialogo);

    const ruta = '/profesionales/1/ausencias/4/turnos/10/reasignar';
    await vi.waitFor(() => expect(servidor.a('POST', ruta)).toHaveLength(1));
    expect(servidor.ultimo('POST', ruta).cuerpo).toEqual({ profesionalId: 2, inicio: null, sillonId: null });
    await vi.waitFor(() => expect(texto(panel)).toContain('Turno reasignado.'));
    expect(filasAfectadas(panel)).toHaveLength(0);
    expect(texto(panel)).toContain('Turnos afectados: 0');
    expect(dialogo.open).toBe(false);
  });

  it('El otro profesional no hace la prestación', async () => {
    const { servidor } = servidorConAusencias(SESION_RECEPCION_CONFIG, [AUSENCIA_4]);
    // Una tercera profesional ficticia, activa, que no hace "Tratamiento de conducto".
    servidor.responder('GET /profesionales', () =>
      json(200, [
        ...PROFESIONALES,
        { id: 3, nombre: 'Carla', apellido: 'Muestra', matricula: 'MP-0003', activo: true, sillonHabitualId: null },
      ]),
    );
    servidor.responder('GET /turnos', () =>
      json(200, [turno({ id: 10, prestacionId: 3, inicio: '2026-12-07T09:00:00-03:00', fin: '2026-12-07T10:00:00-03:00' })]),
    );
    const panel = await abrirAusencias(servidor);
    await vi.waitFor(() => expect(texto(panel)).toContain('Trámite personal'));
    boton(panel, 'Ver turnos afectados').click();
    await vi.waitFor(() => expect(filasAfectadas(panel)).toHaveLength(1));

    boton(panel, 'Reasignar').click();
    const dialogo = await dialogoAbierto();

    await vi.waitFor(() => expect(texto(dialogo)).toContain('Solo aparecen los profesionales activos que hacen Tratamiento de conducto.'));
    const opciones = [...campo<HTMLSelectElement>(dialogo, 'Nuevo profesional').options].map((o) => o.textContent);
    expect(opciones).toEqual(['Bruno Prueba']);
    expect(opciones).not.toContain('Carla Muestra');
    expect(opciones).not.toContain('Ana Ejemplo');
  });

  it('Un error de negocio deja el turno en la lista', async () => {
    const { servidor } = servidorConAusencias(SESION_RECEPCION_CONFIG, [AUSENCIA_4]);
    servidor.responder('GET /turnos', () =>
      json(200, [turno({ id: 10, inicio: '2026-12-07T09:00:00-03:00', fin: '2026-12-07T09:30:00-03:00' })]),
    );
    servidor.responder('POST /profesionales/{id}/ausencias/{id}/turnos/{id}/reasignar', () =>
      json(409, { status: 409, codigo: 'SOLAPAMIENTO_PROFESIONAL' }),
    );
    const panel = await abrirAusencias(servidor);
    await vi.waitFor(() => expect(texto(panel)).toContain('Trámite personal'));
    boton(panel, 'Ver turnos afectados').click();
    await vi.waitFor(() => expect(filasAfectadas(panel)).toHaveLength(1));

    boton(panel, 'Reasignar').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(boton(dialogo, 'Reasignar').disabled).toBe(false));
    escribir(entrada(dialogo, 'horaInicio'), '11:00');
    enviar(dialogo);

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('El profesional ya tiene un turno en ese horario.'),
    );
    expect(servidor.ultimo('POST', '/profesionales/1/ausencias/4/turnos/10/reasignar').cuerpo).toEqual({
      profesionalId: 2,
      inicio: '2026-12-07T11:00:00-03:00',
      sillonId: null,
    });
    expect(filasAfectadas(panel)).toHaveLength(1);
  });
});
