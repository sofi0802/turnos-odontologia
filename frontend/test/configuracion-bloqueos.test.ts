import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json, texto, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  enviar,
  ponerHora,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_ODONTOLOGO_SIN_PROFESIONAL,
  servidorCon,
} from './apoyo-configuracion.ts';

/** Servidor cuyos bloqueos por profesional cambian con los `POST` y `DELETE`. */
function servidorConBloqueos(sesion: object, iniciales: Record<number, object[]>): ServidorFalso {
  const servidor = servidorCon(sesion);
  const bloqueos = new Map<number, object[]>(Object.entries(iniciales).map(([id, lista]) => [Number(id), [...lista]]));
  const profesionalDe = (ruta: string): number => Number(ruta.split('/')[2]);
  servidor.responder('GET /profesionales/{id}/bloqueos', (pedido) =>
    json(200, bloqueos.get(profesionalDe(pedido.ruta)) ?? []),
  );
  servidor.responder('DELETE /profesionales/{id}/bloqueos/{id}', (pedido) => {
    const id = Number(pedido.ruta.split('/')[4]);
    const lista = bloqueos.get(profesionalDe(pedido.ruta)) ?? [];
    bloqueos.set(
      profesionalDe(pedido.ruta),
      lista.filter((b) => Reflect.get(b, 'id') !== id),
    );
    return json(204);
  });
  servidor.responder('POST /profesionales/{id}/bloqueos', (pedido) => {
    const profesionalId = profesionalDe(pedido.ruta);
    const periodo = { id: 9, profesionalId, tipo: 'BLOQUEO', ...(pedido.cuerpo as object) };
    bloqueos.set(profesionalId, [...(bloqueos.get(profesionalId) ?? []), periodo]);
    return json(201, { periodo, turnosAfectados: [] });
  });
  return servidor;
}

function entrada(dialogo: HTMLElement, nombre: string): HTMLInputElement {
  const encontrada = dialogo.querySelector<HTMLInputElement>(`input[name="${nombre}"]`);
  if (encontrada === null) {
    throw new Error(`No existe el campo ${nombre}`);
  }
  return encontrada;
}

async function registrarBloqueo(panel: HTMLElement): Promise<HTMLDialogElement> {
  boton(panel, 'Bloquear horario').click();
  const dialogo = await dialogoAbierto();
  escribir(entrada(dialogo, 'inicio'), '2026-12-09');
  ponerHora(dialogo, 'Hora de inicio', '11:00');
  escribir(entrada(dialogo, 'fin'), '2026-12-09');
  ponerHora(dialogo, 'Hora de fin', '12:00');
  escribir(campo(dialogo, 'Motivo (opcional)'), 'Trámite');
  enviar(dialogo);
  return dialogo;
}

async function abrirBloqueos(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Bloqueos');
  await vi.waitFor(() => expect(servidor.a('GET', '/profesionales/1/bloqueos')).toHaveLength(1));
  await vi.waitFor(() => expect(texto(panel)).not.toContain('Cargando'));
  return panel;
}

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Bloqueos de agenda', () => {
  it('El odontólogo bloquea su agenda', async () => {
    const servidor = servidorConBloqueos(SESION_ODONTOLOGA, {});
    const panel = await abrirBloqueos(servidor);
    expect(texto(panel)).toContain('Tus bloqueos');
    expect(panel.querySelector('select')).toBeNull();

    const dialogo = await registrarBloqueo(panel);

    await vi.waitFor(() => expect(servidor.a('POST', '/profesionales/1/bloqueos')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/profesionales/1/bloqueos').cuerpo).toEqual({
      inicio: '2026-12-09T11:00:00-03:00',
      fin: '2026-12-09T12:00:00-03:00',
      motivo: 'Trámite',
    });
    await vi.waitFor(() => expect(texto(panel)).toContain('Miércoles 9 de diciembre, 11:00 a 12:00'));
    expect(texto(panel.querySelector('.lista-config') ?? panel)).toContain('Trámite');
    expect(texto(panel)).toContain('Bloqueo registrado.');
    expect(dialogo.open).toBe(false);
  });

  it('Bloqueo que se cruza con turnos', async () => {
    const servidor = servidorConBloqueos(SESION_ODONTOLOGA, {});
    servidor.responder('POST /profesionales/{id}/bloqueos', () =>
      json(201, {
        periodo: {
          id: 9,
          profesionalId: 1,
          tipo: 'BLOQUEO',
          inicio: '2026-12-09T11:00:00-03:00',
          fin: '2026-12-09T12:00:00-03:00',
          motivo: 'Trámite',
        },
        turnosAfectados: [
          { turnoId: 21, inicio: '2026-12-09T11:00:00-03:00', fin: '2026-12-09T11:30:00-03:00', prestacionId: 1 },
          { turnoId: 22, inicio: '2026-12-09T11:30:00-03:00', fin: '2026-12-09T12:00:00-03:00', prestacionId: 1 },
        ],
      }),
    );
    const panel = await abrirBloqueos(servidor);

    await registrarBloqueo(panel);

    await vi.waitFor(() => expect(texto(panel)).toContain('El bloqueo se cruza con 2 turnos dados.'));
    expect(panel.querySelector('.aviso-alerta')).not.toBeNull();
    // Solo se informa: no se ofrece reasignar.
    expect([...panel.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Reasignar');
  });

  it('Borrar un bloqueo', async () => {
    const servidor = servidorConBloqueos(SESION_ADMIN, {
      2: [
        {
          id: 7,
          profesionalId: 2,
          tipo: 'BLOQUEO',
          inicio: '2026-12-10T15:00:00-03:00',
          fin: '2026-12-10T16:00:00-03:00',
          motivo: 'Reunión',
        },
      ],
    });
    const panel = await abrirBloqueos(servidor);
    escribir(campo<HTMLSelectElement>(panel, 'Profesional'), '2');
    await vi.waitFor(() => expect(texto(panel)).toContain('Reunión'));

    boton(panel, 'Borrar').click();
    const dialogo = await dialogoAbierto();
    expect(servidor.a('DELETE', '/profesionales/2/bloqueos/7')).toHaveLength(0);
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('DELETE', '/profesionales/2/bloqueos/7')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(panel)).toContain('No hay bloqueos registrados.'));
    expect(texto(panel)).not.toContain('Reunión');
  });

  it('Odontólogo sin profesional vinculado', async () => {
    const servidor = servidorCon(SESION_ODONTOLOGO_SIN_PROFESIONAL);
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Bloqueos');

    await vi.waitFor(() => expect(texto(panel)).toContain('Tu usuario no está vinculado a un profesional.'));
    expect(servidor.pedidos.some((p) => p.ruta.includes('/bloqueos'))).toBe(false);
    expect(panel.querySelector('button')).toBeNull();
  });
});

describe('Todos los profesionales en bloqueos', () => {
  it('Bloqueos de todos los profesionales', async () => {
    const servidor = servidorConBloqueos(SESION_ADMIN, {
      2: [
        {
          id: 7,
          profesionalId: 2,
          tipo: 'BLOQUEO',
          inicio: '2026-12-10T15:00:00-03:00',
          fin: '2026-12-10T16:00:00-03:00',
          motivo: 'Reunión',
        },
      ],
      1: [
        {
          id: 3,
          profesionalId: 1,
          tipo: 'BLOQUEO',
          inicio: '2026-12-09T11:00:00-03:00',
          fin: '2026-12-09T12:00:00-03:00',
          motivo: 'Trámite',
        },
      ],
    });
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Bloqueos');

    await vi.waitFor(() => expect(texto(panel)).toContain('Reunión'));
    expect(campo<HTMLSelectElement>(panel, 'Profesional').selectedOptions[0]?.textContent).toBe('Todos los profesionales');
    const filas = [...panel.querySelectorAll<HTMLLIElement>('.lista-config.con-columna > li:not(.encabezado-columnas)')];
    expect(filas.map((f) => texto(f))).toEqual([
      expect.stringContaining('Ana Ejemplo'),
      expect.stringContaining('Bruno Prueba'),
    ]);
    expect(texto(filas[0] as HTMLLIElement)).toContain('Miércoles 9 de diciembre, 11:00 a 12:00');
    expect(texto(filas[1] as HTMLLIElement)).toContain('Bruno Prueba');
    expect(servidor.a('GET', '/profesionales/1/bloqueos')).toHaveLength(1);
    expect(servidor.a('GET', '/profesionales/2/bloqueos')).toHaveLength(1);
  });

  it('Falla la carga de un profesional en la vista de todos', async () => {
    const servidor = servidorConBloqueos(SESION_ADMIN, {});
    servidor.responder('GET /profesionales/{id}/bloqueos', (pedido) =>
      pedido.ruta.split('/')[2] === '2' ? json(500) : json(200, []),
    );
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Bloqueos');

    await vi.waitFor(() => expect(boton(panel, 'Reintentar')).toBeDefined());
    expect(panel.querySelector('.lista-config')).toBeNull();
  });
});
