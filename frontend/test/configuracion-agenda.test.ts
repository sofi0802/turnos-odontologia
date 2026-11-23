import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json, texto, type Pedido, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  elegirPestana,
  ponerHora,
  selectoresDeHora,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_ODONTOLOGO_SIN_PROFESIONAL,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';

/** Horarios ficticios: Ana Ejemplo (1) de lunes a viernes de 08:00 a 13:00; Bruno Prueba (2) de 10:00 a 18:00. */
function lunesAViernes(desde: string, hasta: string): { dia: number; desde: string; hasta: string }[] {
  return [1, 2, 3, 4, 5].map((dia) => ({ dia, desde, hasta }));
}

const FRANJAS_ANA = lunesAViernes('08:00', '13:00');
const FRANJAS_BRUNO = lunesAViernes('10:00', '18:00');

function conHorarios(servidor: ServidorFalso): ServidorFalso {
  servidor.responder('GET /profesionales/{id}/horarios', (pedido: Pedido) => {
    const id = Number(pedido.ruta.split('/')[2]);
    return json(200, { profesionalId: id, franjas: id === 2 ? FRANJAS_BRUNO : FRANJAS_ANA });
  });
  return servidor;
}

/** Fila `<li>` de un día en la lista de franjas. */
function dia(panel: HTMLElement, nombre: string): HTMLLIElement {
  const fila = [...panel.querySelectorAll<HTMLLIElement>('.franjas > li')].find(
    (li) => li.querySelector('.dia')?.textContent === nombre,
  );
  if (fila === undefined) {
    throw new Error(`No existe el día ${nombre}`);
  }
  return fila;
}

function horasDe(panel: HTMLElement, nombre: string): string[] {
  return [...dia(panel, nombre).querySelectorAll('.franja-horas')].map((h) => h.textContent ?? '');
}

async function esperarHorario(panel: HTMLElement): Promise<void> {
  await vi.waitFor(() => expect(panel.querySelector('.franjas')).not.toBeNull());
}

async function abrirHorarios(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Horarios');
  await esperarHorario(panel);
  return panel;
}

function agregarFranja(panel: HTMLElement, nombreDia: string, desde: string, hasta: string): void {
  const selector = campo<HTMLSelectElement>(panel, 'Día');
  const opcion = [...selector.options].find((o) => o.textContent === nombreDia);
  escribir(selector, opcion?.value ?? '');
  ponerHora(panel, 'Desde', desde);
  ponerHora(panel, 'Hasta', hasta);
  boton(panel, 'Agregar franja').click();
}

function pedidosDeHorarios(servidor: ServidorFalso): Pedido[] {
  return servidor.pedidos.filter((p) => /^\/profesionales\/\d+\/horarios$/.test(p.ruta));
}

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

describe('Profesional de las secciones de agenda', () => {
  it('El odontólogo ve su propio horario', async () => {
    const servidor = conHorarios(servidorCon(SESION_ODONTOLOGA));

    const panel = await abrirHorarios(servidor);

    expect(servidor.a('GET', '/profesionales/1/horarios')).toHaveLength(1);
    expect(panel.querySelector('select')).toBeNull();
    expect(panel.querySelector('.profesional-fijo')?.textContent).toContain('Tus horarios');
    expect(horasDe(panel, 'Lunes')).toEqual(['08:00 a 13:00']);
    expect(panel.querySelectorAll('button')).toHaveLength(0);
  });

  // El spec usa la pestaña "Bloqueos"; acá se verifica la misma regla (D4) en "Horarios".
  it('Odontólogo sin profesional vinculado', async () => {
    const servidor = conHorarios(servidorCon(SESION_ODONTOLOGO_SIN_PROFESIONAL));
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Horarios');

    await vi.waitFor(() => expect(texto(panel)).toContain('Tu usuario no está vinculado a un profesional.'));
    expect(panel.querySelector('select')).toBeNull();
    expect(pedidosDeHorarios(servidor)).toHaveLength(0);
  });
});

describe('Horarios de trabajo', () => {
  it('Ver el horario de un profesional', async () => {
    const servidor = conHorarios(servidorCon(SESION_RECEPCION_CONFIG));
    const panel = await abrirHorarios(servidor);
    const selector = campo<HTMLSelectElement>(panel, 'Profesional');
    escribir(selector, '2');
    await vi.waitFor(() => expect(horasDe(panel, 'Lunes')).toEqual(['10:00 a 18:00']));

    escribir(selector, '1');

    await vi.waitFor(() => expect(horasDe(panel, 'Lunes')).toEqual(['08:00 a 13:00']));
    expect(servidor.ultimo('GET', '/profesionales/1/horarios')).toBeDefined();
    for (const nombre of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes']) {
      expect(horasDe(panel, nombre)).toEqual(['08:00 a 13:00']);
    }
    for (const nombre of ['Sábado', 'Domingo']) {
      expect(dia(panel, nombre).querySelector('.no-atiende')?.textContent).toBe('No atiende');
    }
    const botones = [...panel.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones).not.toContain('Agregar franja');
    expect(botones).not.toContain('Quitar');
    expect(botones).not.toContain('Guardar horario');
    expect(panel.querySelector('.agregar-franja')).toBeNull();
  });
});

describe('Edición de horarios por el administrador', () => {
  it('El administrador agrega una franja', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    const sabado = { dia: 6, desde: '09:00', hasta: '12:00' };
    servidor.responder('PUT /profesionales/{id}/horarios', () =>
      json(200, { profesionalId: 1, franjas: [...FRANJAS_ANA, sabado] }),
    );
    const panel = await abrirHorarios(servidor);

    agregarFranja(panel, 'Sábado', '09:00', '12:00');
    expect(dia(panel, 'Sábado').querySelector('.franja-nueva')?.textContent).toContain('09:00 a 12:00');
    boton(panel, 'Guardar horario').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Horario guardado.'));
    const pedido = servidor.ultimo('PUT', '/profesionales/1/horarios');
    expect(pedido.cuerpo).toEqual({ franjas: [...FRANJAS_ANA, sabado] });
    expect(servidor.a('PUT', '/profesionales/1/horarios')).toHaveLength(1);
    expect(horasDe(panel, 'Sábado')).toEqual(['09:00 a 12:00']);
    expect(panel.querySelector('.franja-nueva')).toBeNull();
  });

  it('Franjas superpuestas', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    servidor.responder('PUT /profesionales/{id}/horarios', () =>
      json(422, { detail: 'Franjas superpuestas', codigo: 'FRANJAS_SUPERPUESTAS' }),
    );
    const panel = await abrirHorarios(servidor);
    agregarFranja(panel, 'Lunes', '12:00', '15:00');

    boton(panel, 'Guardar horario').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Hay franjas del mismo día que se superponen.'));
    expect(panel.querySelector('[role="alert"]')?.textContent).toContain('No se guardó el horario.');
    expect(servidor.ultimo('PUT', '/profesionales/1/horarios').cuerpo).toEqual({
      franjas: [
        { dia: 1, desde: '08:00', hasta: '13:00' },
        { dia: 1, desde: '12:00', hasta: '15:00' },
        ...FRANJAS_ANA.slice(1),
      ],
    });
    expect(horasDe(panel, 'Lunes')).toEqual(['08:00 a 13:00', '12:00 a 15:00']);
    expect(dia(panel, 'Lunes').querySelector('.franja-nueva')?.textContent).toContain('12:00 a 15:00');
    expect(texto(panel)).not.toContain('Horario guardado.');
  });

  it('Quitar y descartar cambios solo tocan la copia local', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    const panel = await abrirHorarios(servidor);

    boton(dia(panel, 'Viernes'), 'Quitar Viernes 08:00 a 13:00').click();
    agregarFranja(panel, 'Domingo', '09:00', '11:00');
    expect(dia(panel, 'Viernes').querySelector('.no-atiende')?.textContent).toBe('No atiende');
    expect(horasDe(panel, 'Domingo')).toEqual(['09:00 a 11:00']);

    boton(panel, 'Descartar cambios').click();

    expect(horasDe(panel, 'Viernes')).toEqual(['08:00 a 13:00']);
    expect(dia(panel, 'Domingo').querySelector('.no-atiende')).not.toBeNull();
    expect(servidor.pedidos.filter((p) => p.metodo === 'PUT')).toHaveLength(0);
  });

  it('No se agrega una franja con "Desde" posterior a "Hasta"', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    const panel = await abrirHorarios(servidor);

    agregarFranja(panel, 'Sábado', '12:00', '09:00');

    expect(dia(panel, 'Sábado').querySelector('.no-atiende')).not.toBeNull();
    const hasta = selectoresDeHora(panel, 'Hasta').hora;
    expect(hasta.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(hasta.getAttribute('aria-describedby') ?? '')?.textContent).not.toBe('');
  });

  it('Cambiar de profesional vuelve a pedir su horario y descarta lo editado', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    const panel = await abrirHorarios(servidor);
    agregarFranja(panel, 'Sábado', '09:00', '12:00');

    escribir(campo<HTMLSelectElement>(panel, 'Profesional'), '2');

    await vi.waitFor(() => expect(horasDe(panel, 'Lunes')).toEqual(['10:00 a 18:00']));
    expect(servidor.a('GET', '/profesionales/2/horarios')).toHaveLength(1);
    expect(dia(panel, 'Sábado').querySelector('.no-atiende')).not.toBeNull();
  });

  it('Doble clic en "Guardar horario" envía un solo pedido', async () => {
    const servidor = conHorarios(servidorCon(SESION_ADMIN));
    servidor.responder('PUT /profesionales/{id}/horarios', () => json(200, { profesionalId: 1, franjas: FRANJAS_ANA }));
    const panel = await abrirHorarios(servidor);

    boton(panel, 'Guardar horario').click();
    boton(panel, 'Guardar horario').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Horario guardado.'));
    expect(servidor.a('PUT', '/profesionales/1/horarios')).toHaveLength(1);
  });
});
