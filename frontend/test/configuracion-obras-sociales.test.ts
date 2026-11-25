import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, errorDeCampo, escribir, json, texto, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  enviar,
  opcionesDe,
  pestanas,
  SESION_ADMIN,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';
import { NOMENCLADOR, OBRAS_SOCIALES } from './apoyo-coberturas.ts';

/** C-49, grupo 2: Configuración → "Obras sociales" (spec `frontend-coberturas`). Datos ficticios. */

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

function servidorAdmin(): ServidorFalso {
  const servidor = servidorCon(SESION_ADMIN);
  servidor.responder('GET /obras-sociales', () => json(200, OBRAS_SOCIALES));
  servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR));
  return servidor;
}

/** Abre la pestaña "Obras sociales" y espera a que se dibujen las dos listas. */
async function abrirPestana(servidor: ServidorFalso = servidorAdmin()): Promise<{ panel: HTMLElement; servidor: ServidorFalso }> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Obras sociales');
  await vi.waitFor(() => expect(panel.querySelector('[aria-busy="true"]')).toBeNull());
  return { panel, servidor };
}

/** Bloque de la pestaña por el título de su `h2`. */
function bloque(panel: HTMLElement, titulo: string): HTMLElement {
  const encontrado = [...panel.querySelectorAll<HTMLElement>('.cob-bloque')].find(
    (b) => b.querySelector('h2')?.textContent === titulo,
  );
  if (encontrado === undefined) {
    throw new Error(`No existe el bloque ${titulo}`);
  }
  return encontrado;
}

describe('Pestaña "Obras sociales" solo para el administrador', () => {
  it('El administrador abre la pestaña', async () => {
    const servidor = servidorAdmin();
    const { raiz } = await abrirConfiguracion(servidor);
    expect(servidor.a('GET', '/obras-sociales')).toHaveLength(0);

    elegirPestana(raiz, 'Obras sociales');

    await vi.waitFor(() => expect(servidor.a('GET', '/obras-sociales')).toHaveLength(1));
    expect(servidor.a('GET', '/nomenclador')).toHaveLength(1);
    const elegida = pestanas(raiz).find((p) => p.textContent === 'Obras sociales');
    expect(elegida?.getAttribute('aria-selected')).toBe('true');
  });

  it('La recepción no ve la pestaña', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = await abrirConfiguracion(servidor);

    expect(pestanas(raiz).map((p) => p.textContent)).not.toContain('Obras sociales');
    expect(servidor.a('GET', '/obras-sociales')).toHaveLength(0);
    expect(servidor.a('GET', '/nomenclador')).toHaveLength(0);
  });
});

describe('Lista de obras sociales y planes', () => {
  it('Obras sociales con sus planes', async () => {
    const { panel } = await abrirPestana();
    const obras = [...bloque(panel, 'Obras sociales y prepagas').querySelectorAll<HTMLElement>('.cob-obra')];

    expect(obras).toHaveLength(2);
    const [ose, prepaga] = obras as [HTMLElement, HTMLElement];
    expect(ose.querySelector('h3')?.textContent).toBe('Obra Social Ejemplo');
    expect(texto(ose.querySelector('.cob-obra-cabecera') as HTMLElement)).toContain('OSE');
    expect(texto(ose.querySelector('.cob-obra-cabecera') as HTMLElement)).toContain('Obra social');
    expect(texto(ose.querySelector('.cob-obra-cabecera') as HTMLElement)).toContain('Activa');
    const planes = [...ose.querySelectorAll<HTMLElement>('.cob-plan')].map((p) => texto(p));
    expect(planes[0]).toContain('Plan Base');
    expect(planes[0]).toContain('Activo');
    expect(planes[1]).toContain('Plan Integral');
    expect(planes[1]).toContain('Inactivo');
    expect(prepaga.querySelector('h3')?.textContent).toBe('Prepaga Prueba');
    expect(texto(prepaga)).toContain('Prepaga');
    expect(texto(prepaga)).toContain('Plan Oro');
  });

  it('Catálogo vacío', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /obras-sociales', () => json(200, []));
    const { panel } = await abrirPestana(servidor);
    const obras = bloque(panel, 'Obras sociales y prepagas');

    await vi.waitFor(() => expect(texto(obras)).toContain('Todavía no hay obras sociales ni prepagas.'));
    expect(boton(obras, 'Agregar obra social')).toBeDefined();
  });

  it('una obra social sin planes lo dice', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /obras-sociales', () => json(200, [{ ...OBRAS_SOCIALES[1], planes: [] }]));
    const { panel } = await abrirPestana(servidor);

    await vi.waitFor(() => expect(texto(panel)).toContain('Sin planes todavía.'));
  });
});

describe('Nomenclador en la pestaña', () => {
  it('Códigos del nomenclador', async () => {
    const { panel } = await abrirPestana();
    const filas = [...bloque(panel, 'Nomenclador').querySelectorAll<HTMLElement>('.lista-config > li')].map((f) => texto(f));

    expect(filas).toHaveLength(3);
    expect(filas[0]).toContain('N-0101');
    expect(filas[0]).toContain('Consulta');
    expect(filas[0]).toContain('Consulta o control');
    expect(filas[1]).toContain('N-0201');
    expect(filas[1]).toContain('Limpieza');
    expect(filas[2]).toContain('N-0301');
    expect(filas[2]).toContain('Extracción simple');
    expect(filas[2]).toContain('Sin prestación');
    expect(filas[2]).toContain('Inactivo');
  });

  it('Nomenclador vacío', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /nomenclador', () => json(200, []));
    const { panel } = await abrirPestana(servidor);
    const nomenclador = bloque(panel, 'Nomenclador');

    await vi.waitFor(() => expect(texto(nomenclador)).toContain('Todavía no hay códigos en el nomenclador.'));
    expect(boton(nomenclador, 'Agregar código')).toBeDefined();
  });
});

describe('Alta y edición de obras sociales', () => {
  it('Alta de una prepaga sin sigla', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales', () => json(201, { id: 2 }));

    boton(panel, 'Agregar obra social').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Prepaga Prueba');
    escribir(campo<HTMLSelectElement>(dialogo, 'Tipo'), 'PREPAGA');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/obras-sociales')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/obras-sociales').cuerpo).toEqual({ nombre: 'Prepaga Prueba', sigla: null, tipo: 'PREPAGA' });
    await vi.waitFor(() => expect(servidor.a('GET', '/obras-sociales')).toHaveLength(2));
    expect(dialogo.open).toBe(false);
  });

  it('Editar la sigla', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /obras-sociales/{id}', () => json(200, {}));

    boton(panel, 'Editar Prepaga Prueba').click();
    const dialogo = await dialogoAbierto();
    expect(campo(dialogo, 'Nombre').value).toBe('Prepaga Prueba');
    escribir(campo(dialogo, 'Sigla (opcional)'), 'PP');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('PUT', '/obras-sociales/2')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/obras-sociales/2').cuerpo).toEqual({ nombre: 'Prepaga Prueba', sigla: 'PP', tipo: 'PREPAGA' });
  });

  it('Nombre vacío', async () => {
    const { panel, servidor } = await abrirPestana();

    boton(panel, 'Agregar obra social').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Nombre')).toBe('Completá el nombre.');
    expect(campo(dialogo, 'Nombre').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campo(dialogo, 'Nombre'));
    expect(servidor.a('POST', '/obras-sociales')).toHaveLength(0);
  });

  it('una sigla de 21 caracteres se marca sin enviar', async () => {
    const { panel, servidor } = await abrirPestana();

    boton(panel, 'Agregar obra social').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Obra Nueva');
    escribir(campo(dialogo, 'Sigla (opcional)'), 'S'.repeat(21));
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Sigla (opcional)')).toBe('La sigla puede tener hasta 20 caracteres.');
    expect(servidor.a('POST', '/obras-sociales')).toHaveLength(0);
  });
});

describe('Alta y edición de planes', () => {
  it('Agregar un plan', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales/{id}/planes', () => json(201, {}));

    boton(panel, 'Agregar plan a Prepaga Prueba').click();
    const dialogo = await dialogoAbierto();
    expect(dialogo.querySelector('.dialogo-resumen')?.textContent).toContain('Prepaga Prueba');
    escribir(campo(dialogo, 'Nombre'), 'Plan Plata');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/obras-sociales/2/planes')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/obras-sociales/2/planes').cuerpo).toEqual({ nombre: 'Plan Plata' });
  });

  it('Renombrar un plan', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}', () => json(200, {}));

    boton(panel, 'Editar Plan Base').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Plan Base 2027');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('PUT', '/obras-sociales/1/planes/11')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/obras-sociales/1/planes/11').cuerpo).toEqual({ nombre: 'Plan Base 2027' });
  });

  it('un nombre de plan de 81 caracteres se marca sin enviar', async () => {
    const { panel, servidor } = await abrirPestana();

    boton(panel, 'Agregar plan a Obra Social Ejemplo').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'P'.repeat(81));
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Nombre')).toBe('El nombre puede tener hasta 80 caracteres.');
    expect(servidor.a('POST', '/obras-sociales/1/planes')).toHaveLength(0);
  });
});

describe('Alta y edición de códigos del nomenclador', () => {
  it('Agregar un código vinculado', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /nomenclador', () => json(201, {}));

    boton(panel, 'Agregar código').click();
    const dialogo = await dialogoAbierto();
    const prestacion = campo<HTMLSelectElement>(dialogo, 'Prestación');
    await vi.waitFor(() => expect(opcionesDe(prestacion)).toContain('Tratamiento de conducto'));
    expect(opcionesDe(prestacion)[0]).toBe('Sin prestación');
    escribir(campo(dialogo, 'Código'), 'N-0401');
    escribir(campo(dialogo, 'Descripción'), 'Tratamiento de conducto');
    escribir(prestacion, '3');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/nomenclador')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/nomenclador').cuerpo).toEqual({
      codigo: 'N-0401',
      descripcion: 'Tratamiento de conducto',
      prestacionId: 3,
    });
  });

  it('Quitar la prestación de un código', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('PUT /nomenclador/{id}', () => json(200, {}));

    boton(panel, 'Editar N-0101').click();
    const dialogo = await dialogoAbierto();
    const prestacion = campo<HTMLSelectElement>(dialogo, 'Prestación');
    await vi.waitFor(() => expect(prestacion.value).toBe('1'));
    escribir(prestacion, '');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('PUT', '/nomenclador/101')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/nomenclador/101').cuerpo).toEqual({ codigo: 'N-0101', descripcion: 'Consulta', prestacionId: null });
  });

  it('Descripción de 201 caracteres', async () => {
    const { panel, servidor } = await abrirPestana();

    boton(panel, 'Agregar código').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(boton(dialogo, 'Guardar').disabled).toBe(false));
    escribir(campo(dialogo, 'Código'), 'N-0501');
    escribir(campo(dialogo, 'Descripción'), 'D'.repeat(201));
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Descripción')).toBe('La descripción puede tener hasta 200 caracteres.');
    expect(servidor.a('POST', '/nomenclador')).toHaveLength(0);
  });
});

describe('Activar y desactivar sin borrar', () => {
  it('Desactivar una obra social', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales/{id}/desactivar', () => {
      servidor.responder('GET /obras-sociales', () => json(200, [{ ...OBRAS_SOCIALES[0], activa: false }, OBRAS_SOCIALES[1]]));
      return json(200, {});
    });

    boton(panel, 'Desactivar Obra Social Ejemplo').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/obras-sociales/1/desactivar')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/obras-sociales')).toHaveLength(2));
    const ose = await vi.waitFor(() => {
      const obra = panel.querySelector<HTMLElement>('.cob-obra-inactiva');
      expect(obra).not.toBeNull();
      return obra as HTMLElement;
    });
    expect(texto(ose.querySelector('.cob-obra-cabecera') as HTMLElement)).toContain('Inactiva');
    expect(texto(ose)).toContain('Inactiva: no se puede afiliar a sus planes. Las afiliaciones que ya existen no cambian.');
    expect(texto(ose.querySelector('.cob-plan') as HTMLElement)).toContain('Activo');
  });

  it('Activar un plan', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales/{id}/planes/{id}/activar', () => json(200, {}));

    boton(panel, 'Activar Plan Integral').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/obras-sociales/1/planes/12/activar')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/obras-sociales')).toHaveLength(2));
  });

  it('activar un código vuelve a pedir el nomenclador', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /nomenclador/{id}/activar', () => json(200, {}));

    boton(panel, 'Activar N-0301').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/nomenclador/103/activar')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/nomenclador')).toHaveLength(2));
  });

  it('No hay borrado', async () => {
    const { panel } = await abrirPestana();

    const textos = [...panel.querySelectorAll('button')].map((b) => b.textContent ?? '');
    expect(textos.length).toBeGreaterThan(0);
    expect(textos.some((t) => /borrar|eliminar/i.test(t))).toBe(false);
  });

  it('una falla al desactivar se muestra arriba de la lista', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales/{id}/desactivar', () => json(403, { codigo: 'ACCESO_DENEGADO' }));

    boton(panel, 'Desactivar Obra Social Ejemplo').click();

    await vi.waitFor(() =>
      expect(bloque(panel, 'Obras sociales y prepagas').querySelector('[role="alert"]')?.textContent).toBe(
        'No tenés permiso para esta acción.',
      ),
    );
  });
});

describe('Errores del catálogo', () => {
  it('Falla al cargar el catálogo', async () => {
    const servidor = servidorAdmin();
    let pedidos = 0;
    servidor.responder('GET /obras-sociales', () => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, OBRAS_SOCIALES);
    });
    const { panel } = await abrirPestana(servidor);
    const obras = bloque(panel, 'Obras sociales y prepagas');

    await vi.waitFor(() =>
      expect([...obras.querySelectorAll('[role="alert"]')].map((a) => a.textContent)).toContain(
        'No pudimos completar la operación. Probá de nuevo en unos minutos.',
      ),
    );
    boton(obras, 'Reintentar').click();

    await vi.waitFor(() => expect(servidor.a('GET', '/obras-sociales')).toHaveLength(2));
    await vi.waitFor(() => expect(obras.querySelectorAll('.cob-obra')).toHaveLength(2));
    // El nomenclador no se vuelve a pedir: cada bloque carga aparte.
    expect(servidor.a('GET', '/nomenclador')).toHaveLength(1);
  });

  it('mientras carga dice "Cargando…"', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /obras-sociales', () => new Promise<Response>(() => undefined));
    const { raiz } = await abrirConfiguracion(servidor);
    const panel = elegirPestana(raiz, 'Obras sociales');

    expect(texto(bloque(panel, 'Obras sociales y prepagas'))).toContain('Cargando…');
  });

  it('Nombre repetido', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales', () => json(409, { codigo: 'OBRA_SOCIAL_EXISTENTE', detail: 'x' }));

    boton(panel, 'Agregar obra social').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'obra social ejemplo');
    enviar(dialogo);

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('Ya existe una obra social o prepaga con ese nombre.'),
    );
    expect(dialogo.open).toBe(true);
    expect(campo(dialogo, 'Nombre').value).toBe('obra social ejemplo');
  });

  it('Código repetido', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /nomenclador', () => json(409, { codigo: 'CODIGO_NOMENCLADOR_EXISTENTE' }));

    boton(panel, 'Agregar código').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(boton(dialogo, 'Guardar').disabled).toBe(false));
    escribir(campo(dialogo, 'Código'), 'n-0101');
    escribir(campo(dialogo, 'Descripción'), 'Consulta');
    enviar(dialogo);

    await vi.waitFor(() =>
      expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe('Ya existe ese código en el nomenclador.'),
    );
    expect(dialogo.open).toBe(true);
  });

  it('Error de un campo', async () => {
    const { panel, servidor } = await abrirPestana();
    servidor.responder('POST /obras-sociales/{id}/planes', () =>
      json(422, { codigo: 'DATOS_INVALIDOS', detail: 'Datos inválidos', errores: [{ campo: 'nombre', mensaje: 'valor inválido' }] }),
    );

    boton(panel, 'Agregar plan a Obra Social Ejemplo').click();
    const dialogo = await dialogoAbierto();
    escribir(campo(dialogo, 'Nombre'), 'Plan Raro');
    enviar(dialogo);

    await vi.waitFor(() => expect(campo(dialogo, 'Nombre').getAttribute('aria-invalid')).toBe('true'));
    expect(errorDeCampo(dialogo, 'Nombre')).toBe('Revisá el nombre.');
    expect(dialogo.open).toBe(true);
  });

  it('al cancelar un diálogo el foco vuelve al botón que lo abrió', async () => {
    const { panel } = await abrirPestana();
    const agregar = boton(panel, 'Agregar obra social');

    agregar.click();
    const dialogo = await dialogoAbierto();
    boton(dialogo, 'Cancelar').click();

    expect(dialogo.isConnected).toBe(false);
    expect(document.activeElement).toBe(agregar);
  });
});
