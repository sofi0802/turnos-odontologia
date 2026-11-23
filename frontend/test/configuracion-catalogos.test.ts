import { afterEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, campo, escribir, json, PROFESIONALES, SILLONES, texto, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  enviar,
  opcionesDe,
  PRESTACIONES_SEED,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

/** Fila de `.lista-config` cuyo nombre es `nombre`. */
function fila(panel: ParentNode, nombre: string): HTMLLIElement {
  const encontrada = [...panel.querySelectorAll<HTMLLIElement>('.lista-config > li')].find(
    (li) => li.querySelector('.item-nombre')?.textContent === nombre,
  );
  if (encontrada === undefined) {
    throw new Error(`No existe la fila ${nombre}`);
  }
  return encontrada;
}

async function filaCargada(panel: ParentNode, nombre: string): Promise<HTMLLIElement> {
  return vi.waitFor(() => fila(panel, nombre));
}

function botones(raiz: ParentNode): string[] {
  return [...raiz.querySelectorAll('button')].map((b) => b.textContent?.trim() ?? '');
}

/** Hace que `GET {ruta}` devuelva siempre el contenido actual de `lista`. */
function listaMutable<T>(servidor: ServidorFalso, ruta: string, lista: T[]): T[] {
  servidor.responder(`GET ${ruta}`, () => json(200, lista));
  return lista;
}

describe('Catálogos del consultorio', () => {
  it('Lista de prestaciones', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Prestaciones');

    const consulta = await filaCargada(panel, 'Consulta o control');
    expect(texto(consulta)).toContain('30 min');
    expect(consulta.querySelector('.estado-activo')?.textContent).toBe('Activo');
    expect(servidor.a('GET', '/prestaciones').length).toBeGreaterThan(0);
  });

  it('Recepción y odontólogo solo leen los catálogos', async () => {
    for (const sesion of [SESION_RECEPCION_CONFIG, SESION_ODONTOLOGA]) {
      const { raiz } = await abrirConfiguracion(servidorCon(sesion));
      for (const [pestana, nombre] of [
        ['Profesionales', 'Ana Ejemplo'],
        ['Sillones', 'Sillón 1'],
        ['Prestaciones', 'Consulta o control'],
      ] as const) {
        const panel = elegirPestana(raiz, pestana);
        await filaCargada(panel, nombre);
        const visibles = botones(panel);
        for (const prohibido of ['Editar', 'Activar', 'Desactivar']) {
          expect(visibles).not.toContain(prohibido);
        }
        expect(visibles.some((b) => b.startsWith('Agregar'))).toBe(false);
        expect(texto(panel)).toContain('Solo el administrador puede agregar o cambiar');
      }
    }
  });

  it('Cada elemento muestra si está inactivo', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(200, [...SILLONES, { id: 3, nombre: 'Sillón 3', activo: false }]));
    const { raiz } = await abrirConfiguracion(servidor);

    const panel = elegirPestana(raiz, 'Sillones');

    const inactivo = await filaCargada(panel, 'Sillón 3');
    expect(inactivo.querySelector('.estado-inactivo')?.textContent).toBe('Inactivo');
    expect(botones(inactivo)).toContain('Activar');
    expect(botones(fila(panel, 'Sillón 1'))).toContain('Desactivar');
  });
});

describe('Edición de catálogos por el administrador', () => {
  it('El administrador agrega un sillón', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    const sillones = listaMutable(servidor, '/sillones', [...SILLONES]);
    servidor.responder('POST /sillones', () => {
      const nuevo = { id: 3, nombre: 'Sillón 3', activo: true };
      sillones.push(nuevo);
      return json(201, nuevo);
    });
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Sillones');
    await filaCargada(panel, 'Sillón 1');

    boton(panel, 'Agregar sillón').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Sillón 3');
    enviar(dialogo);

    const nueva = await filaCargada(panel, 'Sillón 3');
    expect(servidor.a('POST', '/sillones')).toHaveLength(1);
    expect(servidor.ultimo('POST', '/sillones').cuerpo).toEqual({ nombre: 'Sillón 3' });
    expect(nueva.querySelector('.estado-activo')?.textContent).toBe('Activo');
    expect(dialogo.open).toBe(false);
  });

  it('El administrador edita un profesional', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    const profesionales = listaMutable(servidor, '/profesionales', [...PROFESIONALES]);
    servidor.responder('PUT /profesionales/{id}', (pedido) => {
      const editado = { ...PROFESIONALES[0]!, apellido: 'Ejemplar' };
      profesionales[0] = editado;
      expect(pedido.ruta).toBe('/profesionales/1');
      return json(200, editado);
    });
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');

    boton(await filaCargada(panel, 'Ana Ejemplo'), 'Editar').click();
    const dialogo = await dialogoAbierto();
    expect(campo(dialogo, 'Nombre').value).toBe('Ana');
    expect(campo(dialogo, 'Matrícula').value).toBe('MP-0001');
    escribir(campo(dialogo, 'Apellido'), 'Ejemplar');
    enviar(dialogo);

    await filaCargada(panel, 'Ana Ejemplar');
    expect(servidor.ultimo('PUT', '/profesionales/1').cuerpo).toEqual({
      nombre: 'Ana',
      apellido: 'Ejemplar',
      matricula: 'MP-0001',
    });
  });

  it('El administrador desactiva una prestación', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    const prestaciones = listaMutable(servidor, '/prestaciones', PRESTACIONES_SEED.map((p) => ({ ...p })));
    servidor.responder('POST /prestaciones/{id}/desactivar', () => {
      prestaciones[0] = { ...PRESTACIONES_SEED[0]!, activo: false };
      return json(204);
    });
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Prestaciones');

    boton(await filaCargada(panel, 'Consulta o control'), 'Desactivar').click();

    await vi.waitFor(() =>
      expect(fila(panel, 'Consulta o control').querySelector('.estado-inactivo')?.textContent).toBe('Inactivo'),
    );
    expect(servidor.a('POST', '/prestaciones/1/desactivar')).toHaveLength(1);
    expect(botones(fila(panel, 'Consulta o control'))).toContain('Activar');
    expect(botones(fila(panel, 'Consulta o control'))).not.toContain('Desactivar');
  });

  it('Matrícula repetida', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('POST /profesionales', () =>
      json(409, { status: 409, codigo: 'MATRICULA_DUPLICADA', detail: 'Matrícula duplicada' }),
    );
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');
    await filaCargada(panel, 'Ana Ejemplo');

    boton(panel, 'Agregar profesional').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Carla');
    escribir(campo(dialogo, 'Apellido'), 'Muestra');
    escribir(campo(dialogo, 'Matrícula'), 'MP-0002');
    enviar(dialogo);

    await vi.waitFor(() => expect(texto(dialogo)).toContain('Ya existe un profesional con esa matrícula.'));
    expect(servidor.ultimo('POST', '/profesionales').cuerpo).toEqual({
      nombre: 'Carla',
      apellido: 'Muestra',
      matricula: 'MP-0002',
    });
    expect(dialogo.open).toBe(true);
    expect(campo(dialogo, 'Matrícula').value).toBe('MP-0002');
    expect(campo(dialogo, 'Nombre').value).toBe('Carla');
  });

  it('Duración que no es múltiplo de 5', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('POST /prestaciones', () =>
      json(422, { status: 422, codigo: 'DURACION_INVALIDA', detail: 'Duración inválida' }),
    );
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Prestaciones');
    await filaCargada(panel, 'Consulta o control');

    boton(panel, 'Agregar prestación').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Control rápido');
    escribir(campo(dialogo, 'Duración (minutos)'), '32');
    enviar(dialogo);

    await vi.waitFor(() =>
      expect(texto(dialogo)).toContain('La duración tiene que ser mayor que 0 y múltiplo de 5.'),
    );
    expect(servidor.ultimo('POST', '/prestaciones').cuerpo).toEqual({ nombre: 'Control rápido', duracionMinutos: 32 });
    expect(dialogo.open).toBe(true);
  });
});

describe('Especialidades y sillón habitual', () => {
  it('Ver las especialidades', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    servidor.responder('GET /profesionales/{id}/prestaciones', () =>
      json(200, [PRESTACIONES_SEED[0], PRESTACIONES_SEED[2]]),
    );
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');

    const bruno = await filaCargada(panel, 'Bruno Prueba');
    boton(bruno, 'Ver detalle').click();

    const detalle = await vi.waitFor(() => {
      const encontrado = bruno.querySelector<HTMLElement>('.detalle-profesional');
      expect(encontrado?.querySelector('.etiquetas')).not.toBeNull();
      return encontrado as HTMLElement;
    });
    expect(servidor.a('GET', '/profesionales/2/prestaciones')).toHaveLength(1);
    expect(texto(detalle)).toContain('Tratamiento de conducto');
    expect(texto(detalle)).toContain('Sillón habitual: Sillón 2');
    expect(detalle.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);
    expect(detalle.querySelectorAll('select')).toHaveLength(0);
    expect(detalle.querySelectorAll('button')).toHaveLength(0);
    expect(boton(bruno, 'Ocultar detalle').getAttribute('aria-expanded')).toBe('true');
  });
});

/** Abre el detalle de la Dra. Ana Ejemplo como admin; sus prestaciones son 1, 2 y 4. */
async function detalleDeAna(servidor: ServidorFalso): Promise<HTMLElement> {
  servidor.responder('GET /profesionales/{id}/prestaciones', () =>
    json(200, [PRESTACIONES_SEED[0], PRESTACIONES_SEED[1], PRESTACIONES_SEED[3]]),
  );
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Profesionales');
  const ana = await filaCargada(panel, 'Ana Ejemplo');
  boton(ana, 'Ver detalle').click();
  return vi.waitFor(() => {
    const detalle = ana.querySelector<HTMLElement>('.detalle-profesional');
    expect(detalle?.querySelector('input[type="radio"]')).not.toBeNull();
    return detalle as HTMLElement;
  });
}

const SILLON_3 = { id: 3, nombre: 'Sillón 3', activo: true };

/** Radio de una opción de sillón habitual por su nombre ("Sin sillón habitual" incluido). */
function opcionSillon(detalle: HTMLElement, nombre: string): HTMLInputElement {
  const etiqueta = [...detalle.querySelectorAll('label.opcion')].find(
    (l) => l.querySelector('.opcion-nombre')?.textContent === nombre,
  );
  const radio = etiqueta?.querySelector('input');
  if (radio === null || radio === undefined) {
    throw new Error(`No existe la opción de sillón ${nombre}`);
  }
  return radio;
}

/** Estado que dice la tarjeta: "Elegido", "Libre" u "Ocupado · <profesional>". */
function estadoDeSillon(detalle: HTMLElement, nombre: string): string {
  return opcionSillon(detalle, nombre).closest('label')?.querySelector('small')?.textContent ?? '';
}

function casilla(detalle: HTMLElement, nombre: string): HTMLInputElement {
  const etiqueta = [...detalle.querySelectorAll('label.casilla')].find((l) => l.textContent?.trim() === nombre);
  const control = etiqueta?.querySelector('input');
  if (control === null || control === undefined) {
    throw new Error(`No existe la casilla ${nombre}`);
  }
  return control;
}

describe('Edición de especialidades y sillón habitual', () => {
  it('El administrador habilita una prestación', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('PUT /profesionales/{id}/prestaciones', () => json(204));
    const detalle = await detalleDeAna(servidor);
    expect(casilla(detalle, 'Consulta o control').checked).toBe(true);
    expect(casilla(detalle, 'Tratamiento de conducto').checked).toBe(false);

    casilla(detalle, 'Tratamiento de conducto').click();
    boton(detalle, 'Guardar prestaciones').click();

    await vi.waitFor(() => expect(texto(detalle)).toContain('Prestaciones guardadas.'));
    const cuerpo = servidor.ultimo('PUT', '/profesionales/1/prestaciones').cuerpo as { prestacionIds: number[] };
    expect([...cuerpo.prestacionIds].sort()).toEqual([1, 2, 3, 4]);
  });

  it('El administrador quita el sillón habitual', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('PUT /profesionales/{id}/sillon-habitual', () => json(204));
    const detalle = await detalleDeAna(servidor);
    expect(opcionSillon(detalle, 'Sillón 1').checked).toBe(true);

    opcionSillon(detalle, 'Sin sillón habitual').click();
    expect(opcionSillon(detalle, 'Sin sillón habitual').checked).toBe(true);
    boton(detalle, 'Guardar sillón habitual').click();

    await vi.waitFor(() => expect(servidor.a('PUT', '/profesionales/1/sillon-habitual')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/profesionales/1/sillon-habitual').cuerpo).toEqual({ sillonId: null });
    await vi.waitFor(() => expect(texto(detalle)).toContain('Sillón habitual guardado: Sin sillón habitual.'));
  });

  it('El administrador elige otro sillón libre', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(200, [...SILLONES, SILLON_3]));
    servidor.responder('PUT /profesionales/{id}/sillon-habitual', () => json(204));
    const detalle = await detalleDeAna(servidor);

    opcionSillon(detalle, 'Sillón 3').click();
    expect(estadoDeSillon(detalle, 'Sillón 3')).toBe('Elegido');
    expect(estadoDeSillon(detalle, 'Sillón 1')).toBe('Libre');
    boton(detalle, 'Guardar sillón habitual').click();

    await vi.waitFor(() => expect(servidor.a('PUT', '/profesionales/1/sillon-habitual')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/profesionales/1/sillon-habitual').cuerpo).toEqual({ sillonId: 3 });
  });

  it('El detalle separa el bloque de prestaciones del de sillón habitual', async () => {
    const detalle = await detalleDeAna(servidorCon(SESION_ADMIN));

    const grupos = [...detalle.querySelectorAll<HTMLElement>('.grupo-config')];

    expect(grupos).toHaveLength(2);
    expect(boton(grupos[0] as HTMLElement, 'Guardar prestaciones')).toBeDefined();
    expect(boton(grupos[1] as HTMLElement, 'Guardar sillón habitual')).toBeDefined();
    expect(grupos[1]?.querySelector('.opciones-sillon')).not.toBeNull();
  });
});

describe('Sillones habituales ocupados', () => {
  it('El sillón de otro profesional no se puede elegir', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(200, [...SILLONES, SILLON_3]));
    const detalle = await detalleDeAna(servidor);

    const ocupado = opcionSillon(detalle, 'Sillón 2');

    expect(ocupado.disabled).toBe(true);
    expect(estadoDeSillon(detalle, 'Sillón 2')).toBe('Ocupado · Bruno Prueba');
    expect(ocupado.closest('label')?.classList.contains('opcion-ocupada')).toBe(true);
    expect(opcionSillon(detalle, 'Sillón 1').checked).toBe(true);
    expect(estadoDeSillon(detalle, 'Sillón 1')).toBe('Elegido');
    expect(opcionSillon(detalle, 'Sillón 3').disabled).toBe(false);
    expect(estadoDeSillon(detalle, 'Sillón 3')).toBe('Libre');
    // "Sin sillón habitual" es una opción más y va al final.
    const nombres = [...detalle.querySelectorAll('.opciones-sillon .opcion-nombre')].map((n) => n.textContent);
    expect(nombres).toEqual(['Sillón 1', 'Sillón 2', 'Sillón 3', 'Sin sillón habitual']);
    expect(opcionSillon(detalle, 'Sin sillón habitual').disabled).toBe(false);
  });

  it('El sillón lo tomó otro profesional mientras tanto', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(200, [...SILLONES, SILLON_3]));
    servidor.responder('PUT /profesionales/{id}/sillon-habitual', () =>
      json(409, { status: 409, codigo: 'SILLON_HABITUAL_OCUPADO', detail: 'Sillón ocupado' }),
    );
    const detalle = await detalleDeAna(servidor);
    opcionSillon(detalle, 'Sillón 3').click();

    boton(detalle, 'Guardar sillón habitual').click();

    await vi.waitFor(() => expect(texto(detalle)).toContain('Ese sillón ya es el habitual de otro profesional.'));
    expect(servidor.ultimo('PUT', '/profesionales/1/sillon-habitual').cuerpo).toEqual({ sillonId: 3 });
    // El sillón anterior sigue elegido.
    expect(opcionSillon(detalle, 'Sillón 1').checked).toBe(true);
    expect(opcionSillon(detalle, 'Sillón 3').checked).toBe(false);
    expect(estadoDeSillon(detalle, 'Sillón 1')).toBe('Elegido');
  });

  it('Un profesional inactivo también ocupa su sillón, y el sillón actual se ve aunque esté inactivo', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /profesionales', () =>
      json(200, [PROFESIONALES[0], { ...PROFESIONALES[1], activo: false }]),
    );
    servidor.responder('GET /sillones', () => json(200, [{ ...SILLONES[0], activo: false }, SILLONES[1], SILLON_3]));
    const detalle = await detalleDeAna(servidor);

    expect(estadoDeSillon(detalle, 'Sillón 2')).toBe('Ocupado · Bruno Prueba');
    expect(opcionSillon(detalle, 'Sillón 1').checked).toBe(true);
  });

  it('Dos detalles ven el sillón que se acaba de asignar', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(200, [...SILLONES, SILLON_3]));
    servidor.responder('PUT /profesionales/{id}/sillon-habitual', () => json(204));
    servidor.responder('GET /profesionales/{id}/prestaciones', () => json(200, []));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');
    const ana = await filaCargada(panel, 'Ana Ejemplo');
    boton(ana, 'Ver detalle').click();
    const detalleAna = await vi.waitFor(() => {
      const d = ana.querySelector<HTMLElement>('.detalle-profesional');
      expect(d?.querySelector('input[type="radio"]')).not.toBeNull();
      return d as HTMLElement;
    });
    opcionSillon(detalleAna, 'Sillón 3').click();
    boton(detalleAna, 'Guardar sillón habitual').click();
    await vi.waitFor(() => expect(servidor.a('PUT', '/profesionales/1/sillon-habitual')).toHaveLength(1));
    await vi.waitFor(() => expect(texto(detalleAna)).toContain('Sillón habitual guardado: Sillón 3.'));

    const bruno = fila(panel, 'Bruno Prueba');
    boton(bruno, 'Ver detalle').click();
    const detalleBruno = await vi.waitFor(() => {
      const d = bruno.querySelector<HTMLElement>('.detalle-profesional');
      expect(d?.querySelector('input[type="radio"]')).not.toBeNull();
      return d as HTMLElement;
    });

    expect(estadoDeSillon(detalleBruno, 'Sillón 3')).toBe('Ocupado · Ana Ejemplo');
    expect(estadoDeSillon(detalleBruno, 'Sillón 1')).toBe('Libre');
  });
});

/** Servidor del alta: el `POST /profesionales` devuelve a Carla Muestra con el id 3 y la agrega a la lista. */
function servidorDeAlta(): { servidor: ServidorFalso; profesionales: object[] } {
  const servidor = servidorCon(SESION_ADMIN);
  const profesionales: object[] = [...PROFESIONALES];
  servidor.responder('GET /profesionales', () => json(200, profesionales));
  servidor.responder('GET /sillones', () => json(200, [...SILLONES, SILLON_3]));
  servidor.responder('POST /profesionales', () => {
    const nuevo = { id: 3, nombre: 'Carla', apellido: 'Muestra', matricula: 'MP-0003', activo: true, sillonHabitualId: null };
    profesionales.push(nuevo);
    return json(201, nuevo);
  });
  servidor.responder('PUT /profesionales/{id}/prestaciones', () => json(204));
  servidor.responder('PUT /profesionales/{id}/sillon-habitual', () => json(204));
  return { servidor, profesionales };
}

/** Abre "Agregar profesional" y completa a Carla Muestra, esperando a que carguen las prestaciones y los sillones. */
async function abrirAlta(servidor: ServidorFalso): Promise<{ panel: HTMLElement; dialogo: HTMLDialogElement }> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Profesionales');
  await filaCargada(panel, 'Ana Ejemplo');
  boton(panel, 'Agregar profesional').click();
  const dialogo = await dialogoAbierto();
  await vi.waitFor(() => expect(dialogo.querySelectorAll('label.casilla').length).toBeGreaterThan(0));
  escribir(campo(dialogo, 'Nombre'), 'Carla');
  escribir(campo(dialogo, 'Apellido'), 'Muestra');
  escribir(campo(dialogo, 'Matrícula'), 'MP-0003');
  return { panel, dialogo };
}

const AVISO_FALLA_PARCIAL = 'El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón.';

/** Pedidos que cambian datos, en orden. */
function escrituras(servidor: ServidorFalso): string[] {
  return servidor.pedidos.filter((p) => p.metodo !== 'GET').map((p) => `${p.metodo} ${p.ruta}`);
}

describe('Alta completa de un profesional', () => {
  it('Alta con prestaciones y sillón', async () => {
    const { servidor } = servidorDeAlta();
    const { panel, dialogo } = await abrirAlta(servidor);
    // Los sillones de otros profesionales no se pueden elegir.
    const selector = campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)');
    expect(opcionesDe(selector)).toEqual([
      'Sin sillón habitual',
      'Sillón 1 (de Ana Ejemplo)',
      'Sillón 2 (de Bruno Prueba)',
      'Sillón 3',
    ]);
    expect([...selector.options].map((o) => o.disabled)).toEqual([false, true, true, false]);
    expect(opcionesDe(campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)'))).not.toContain('Sillón 4');

    casilla(dialogo, 'Consulta o control').click();
    escribir(selector, '3');
    enviar(dialogo);

    await filaCargada(panel, 'Carla Muestra');
    expect(escrituras(servidor)).toEqual([
      'POST /profesionales',
      'PUT /profesionales/3/prestaciones',
      'PUT /profesionales/3/sillon-habitual',
    ]);
    expect(servidor.ultimo('POST', '/profesionales').cuerpo).toEqual({
      nombre: 'Carla',
      apellido: 'Muestra',
      matricula: 'MP-0003',
    });
    expect(servidor.ultimo('PUT', '/profesionales/3/prestaciones').cuerpo).toEqual({ prestacionIds: [1] });
    expect(servidor.ultimo('PUT', '/profesionales/3/sillon-habitual').cuerpo).toEqual({ sillonId: 3 });
    expect(dialogo.open).toBe(false);
    expect(texto(panel)).not.toContain(AVISO_FALLA_PARCIAL);
  });

  it('Alta sin prestaciones ni sillón', async () => {
    const { servidor } = servidorDeAlta();
    const { panel, dialogo } = await abrirAlta(servidor);
    expect(campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)').value).toBe('');

    enviar(dialogo);

    await filaCargada(panel, 'Carla Muestra');
    expect(escrituras(servidor)).toEqual(['POST /profesionales']);
  });

  it('El alta con varias prestaciones y sin sillón solo envía las prestaciones', async () => {
    const { servidor } = servidorDeAlta();
    const { panel, dialogo } = await abrirAlta(servidor);

    casilla(dialogo, 'Consulta o control').click();
    casilla(dialogo, 'Tratamiento de conducto').click();
    enviar(dialogo);

    await filaCargada(panel, 'Carla Muestra');
    expect(escrituras(servidor)).toEqual(['POST /profesionales', 'PUT /profesionales/3/prestaciones']);
    expect(servidor.ultimo('PUT', '/profesionales/3/prestaciones').cuerpo).toEqual({ prestacionIds: [1, 3] });
  });

  it('Si falla el alta, el formulario sigue abierto y no se envían prestaciones ni sillón', async () => {
    const { servidor } = servidorDeAlta();
    servidor.responder('POST /profesionales', () =>
      json(409, { status: 409, codigo: 'MATRICULA_DUPLICADA', detail: 'Matrícula duplicada' }),
    );
    const { dialogo } = await abrirAlta(servidor);
    casilla(dialogo, 'Consulta o control').click();
    escribir(campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)'), '3');

    enviar(dialogo);

    await vi.waitFor(() => expect(texto(dialogo)).toContain('Ya existe un profesional con esa matrícula.'));
    expect(dialogo.open).toBe(true);
    expect(escrituras(servidor)).toEqual(['POST /profesionales']);
    expect(casilla(dialogo, 'Consulta o control').checked).toBe(true);
  });

  it('El formulario de edición no pide prestaciones ni sillón', async () => {
    const { servidor } = servidorDeAlta();
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Profesionales');

    boton(await filaCargada(panel, 'Ana Ejemplo'), 'Editar').click();
    const dialogo = await dialogoAbierto();

    expect(dialogo.querySelectorAll('label.casilla')).toHaveLength(0);
    expect(dialogo.querySelectorAll('select')).toHaveLength(0);
  });
});

describe('Falla parcial del alta de un profesional', () => {
  it('Falla el sillón después del alta', async () => {
    const { servidor } = servidorDeAlta();
    servidor.responder('PUT /profesionales/{id}/sillon-habitual', () =>
      json(409, { status: 409, codigo: 'SILLON_HABITUAL_OCUPADO', detail: 'Sillón ocupado' }),
    );
    const { panel, dialogo } = await abrirAlta(servidor);
    casilla(dialogo, 'Consulta o control').click();
    escribir(campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)'), '3');

    enviar(dialogo);

    await filaCargada(panel, 'Carla Muestra');
    await vi.waitFor(() => expect(texto(panel)).toContain(AVISO_FALLA_PARCIAL));
    expect(texto(panel)).toContain('Ese sillón ya es el habitual de otro profesional.');
    expect(panel.querySelector('[role="alert"]')?.textContent).toContain(AVISO_FALLA_PARCIAL);
    expect(dialogo.open).toBe(false);
    // Las prestaciones ya se habían guardado.
    expect(escrituras(servidor)).toEqual([
      'POST /profesionales',
      'PUT /profesionales/3/prestaciones',
      'PUT /profesionales/3/sillon-habitual',
    ]);
  });

  it('Si fallan las prestaciones, no se intenta el sillón y se avisa igual', async () => {
    const { servidor } = servidorDeAlta();
    servidor.responder('PUT /profesionales/{id}/prestaciones', () =>
      json(422, { status: 422, codigo: 'PRESTACION_INACTIVA', detail: 'Prestación inactiva' }),
    );
    const { panel, dialogo } = await abrirAlta(servidor);
    casilla(dialogo, 'Consulta o control').click();
    escribir(campo<HTMLSelectElement>(dialogo, 'Sillón habitual (opcional)'), '3');

    enviar(dialogo);

    await filaCargada(panel, 'Carla Muestra');
    await vi.waitFor(() => expect(texto(panel)).toContain(AVISO_FALLA_PARCIAL));
    expect(texto(panel)).toContain('Hay una prestación inactiva entre las elegidas.');
    expect(escrituras(servidor)).toEqual(['POST /profesionales', 'PUT /profesionales/3/prestaciones']);
  });
});

describe('Errores al cargar o guardar', () => {
  it('Falla la carga de sillones', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('GET /sillones', () => json(500));
    const { raiz } = await abrirConfiguracion(servidor);

    const sillones = elegirPestana(raiz, 'Sillones');

    const reintentar = await vi.waitFor(() => boton(sillones, 'Reintentar'));
    const avisos = [...sillones.querySelectorAll('[role="alert"]')].map((a) => a.textContent);
    expect(avisos).toContain(MENSAJE_ERROR_GENERICO);
    expect(sillones.querySelector('.lista-config')).toBeNull();

    const prestaciones = elegirPestana(raiz, 'Prestaciones');
    await filaCargada(prestaciones, 'Consulta o control');

    servidor.responder('GET /sillones', () => json(200, SILLONES));
    elegirPestana(raiz, 'Sillones');
    reintentar.click();
    await filaCargada(sillones, 'Sillón 1');
  });

  it('Acción rechazada por permisos', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('PUT /sillones/{id}', () => json(403, { status: 403 }));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Sillones');

    boton(await filaCargada(panel, 'Sillón 1'), 'Editar').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Sillón principal');
    enviar(dialogo);

    await vi.waitFor(() => expect(texto(dialogo)).toContain('No tenés permiso para esta acción.'));
    expect(servidor.ultimo('PUT', '/sillones/1').cuerpo).toEqual({ nombre: 'Sillón principal' });
    expect(dialogo.open).toBe(true);
  });

  it('Doble envío', async () => {
    const servidor = servidorCon(SESION_ADMIN);
    servidor.responder('POST /sillones', () => json(201, { id: 3, nombre: 'Sillón 3', activo: true }));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Sillones');
    await filaCargada(panel, 'Sillón 1');

    boton(panel, 'Agregar sillón').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Sillón 3');
    const guardar = boton(dialogo, 'Guardar');
    guardar.click();
    expect(guardar.disabled).toBe(true);
    guardar.click();

    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    expect(servidor.a('POST', '/sillones')).toHaveLength(1);
  });
});
