import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, errorDeCampo, escribir, json, texto, type ServidorFalso } from './apoyo.ts';
import {
  abrirConfiguracion,
  dialogoAbierto,
  elegirPestana,
  enviar,
  SESION_ADMIN,
  SESION_ODONTOLOGA,
  SESION_RECEPCION_CONFIG,
  servidorCon,
} from './apoyo-configuracion.ts';
import { OBRAS_SOCIALES } from './apoyo-coberturas.ts';
import { ARANCELES, COBERTURA_PLAN_BASE, NOMENCLADOR_C19 } from './apoyo-presupuestos.ts';

/** C-50, grupo 2: precios en "Prestaciones" y cobertura de cada plan en "Obras sociales" (spec `frontend-aranceles`). */

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

function servidorAdmin(sesion: object = SESION_ADMIN): ServidorFalso {
  const servidor = servidorCon(sesion);
  servidor.responder('GET /aranceles', () => json(200, ARANCELES));
  servidor.responder('GET /obras-sociales', () => json(200, OBRAS_SOCIALES));
  servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR_C19));
  servidor.responder('GET /obras-sociales/1/planes/11/coberturas', () => json(200, COBERTURA_PLAN_BASE));
  return servidor;
}

/** Fila de "Prestaciones" por el nombre de la prestación. */
function fila(panel: HTMLElement, nombre: string): HTMLElement {
  const encontrada = [...panel.querySelectorAll<HTMLElement>('.lista-config > li')].find(
    (li) => li.querySelector('.item-nombre')?.textContent === nombre,
  );
  if (encontrada === undefined) {
    throw new Error(`No existe la fila ${nombre}`);
  }
  return encontrada;
}

async function abrirPrestaciones(servidor: ServidorFalso = servidorAdmin()): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Prestaciones');
  await vi.waitFor(() => expect(panel.querySelectorAll('.lista-config > li').length).toBeGreaterThan(0));
  return panel;
}

async function abrirCoberturaDePlanBase(servidor: ServidorFalso = servidorAdmin()): Promise<{ dialogo: HTMLDialogElement; panel: HTMLElement }> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Obras sociales');
  await vi.waitFor(() => expect(panel.querySelector('.cob-plan')).not.toBeNull());
  boton(panel, 'Cobertura de Plan Base').click();
  const dialogo = await dialogoAbierto();
  await vi.waitFor(() => expect(dialogo.querySelectorAll('.pre-cob-fila').length).toBeGreaterThan(0));
  return { dialogo, panel };
}

describe('Precio particular en la pestaña "Prestaciones"', () => {
  it('El administrador ve los precios', async () => {
    const servidor = servidorAdmin();
    const panel = await abrirPrestaciones(servidor);

    expect(servidor.a('GET', '/aranceles')).toHaveLength(1);
    await vi.waitFor(() => expect(texto(fila(panel, 'Consulta o control'))).toContain('$ 15.000,00'));
    expect(boton(fila(panel, 'Consulta o control'), 'Cambiar precio')).toBeDefined();
  });

  it('Prestación sin precio', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /aranceles', () =>
      json(200, ARANCELES.map((a) => (a.prestacion.id === 3 ? { ...a, precio: null, actualizadoEn: null } : a))),
    );
    const panel = await abrirPrestaciones(servidor);

    await vi.waitFor(() => expect(texto(fila(panel, 'Tratamiento de conducto'))).toContain('Sin precio'));
  });

  it('La recepción no ve precios', async () => {
    const servidor = servidorAdmin(SESION_RECEPCION_CONFIG);
    const panel = await abrirPrestaciones(servidor);

    expect(servidor.a('GET', '/aranceles')).toHaveLength(0);
    expect([...panel.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Cambiar precio');
    expect(texto(panel)).not.toContain('$ 15.000,00');
  });

  it('Falla al pedir los aranceles', async () => {
    const servidor = servidorAdmin();
    let pedidos = 0;
    servidor.responder('GET /aranceles', () => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, ARANCELES);
    });
    const panel = await abrirPrestaciones(servidor);

    await vi.waitFor(() =>
      expect([...panel.querySelectorAll('[role="alert"]')].map((a) => a.textContent)).toContain(
        'No pudimos cargar los precios. No pudimos completar la operación. Probá de nuevo en unos minutos.',
      ),
    );
    expect(fila(panel, 'Consulta o control')).toBeDefined();
    boton(panel, 'Reintentar').click();

    await vi.waitFor(() => expect(texto(fila(panel, 'Consulta o control'))).toContain('$ 15.000,00'));
    expect(servidor.a('GET', '/aranceles')).toHaveLength(2);
  });
});

describe('Cambiar el precio particular', () => {
  async function dialogoDe(nombre: string, servidor: ServidorFalso): Promise<HTMLDialogElement> {
    const panel = await abrirPrestaciones(servidor);
    await vi.waitFor(() => expect(texto(fila(panel, nombre))).toContain('$'));
    boton(fila(panel, nombre), 'Cambiar precio').click();
    return dialogoAbierto();
  }

  it('Cambiar un precio', async () => {
    const servidor = servidorAdmin();
    servidor.responder('PUT /aranceles/{id}', () => json(200, {}));
    const dialogo = await dialogoDe('Limpieza (profilaxis)', servidor);

    expect(campo(dialogo, 'Precio').value).toBe('25000,00');
    escribir(campo(dialogo, 'Precio'), '27000,50');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('PUT', '/aranceles/2')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/aranceles/2').cuerpo).toEqual({ precio: 27000.5 });
    await vi.waitFor(() => expect(servidor.a('GET', '/aranceles')).toHaveLength(2));
    expect(dialogo.open).toBe(false);
  });

  it('Precio cero', async () => {
    const servidor = servidorAdmin();
    const dialogo = await dialogoDe('Consulta o control', servidor);

    escribir(campo(dialogo, 'Precio'), '0');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Precio')).toBe('Ingresá un precio mayor que cero.');
    expect(campo(dialogo, 'Precio').getAttribute('aria-invalid')).toBe('true');
    expect(servidor.a('PUT', '/aranceles/1')).toHaveLength(0);
  });

  it('Precio con tres decimales', async () => {
    const servidor = servidorAdmin();
    const dialogo = await dialogoDe('Consulta o control', servidor);

    escribir(campo(dialogo, 'Precio'), '15000,005');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Precio')).toBe('Usá como máximo dos decimales.');
    expect(servidor.a('PUT', '/aranceles/1')).toHaveLength(0);
  });

  it('La API rechaza el precio', async () => {
    const servidor = servidorAdmin();
    servidor.responder('PUT /aranceles/{id}', () =>
      json(422, { codigo: 'DATOS_INVALIDOS', detail: 'Datos inválidos', errores: [{ campo: 'precio', mensaje: 'valor inválido' }] }),
    );
    const dialogo = await dialogoDe('Consulta o control', servidor);

    escribir(campo(dialogo, 'Precio'), '15000');
    enviar(dialogo);

    await vi.waitFor(() => expect(campo(dialogo, 'Precio').getAttribute('aria-invalid')).toBe('true'));
    expect(dialogo.open).toBe(true);
  });

  it('al cancelar, el foco vuelve a "Cambiar precio"', async () => {
    const servidor = servidorAdmin();
    const panel = await abrirPrestaciones(servidor);
    await vi.waitFor(() => expect(texto(fila(panel, 'Consulta o control'))).toContain('$'));
    const origen = boton(fila(panel, 'Consulta o control'), 'Cambiar precio');

    origen.click();
    boton(await dialogoAbierto(), 'Cancelar').click();

    expect(document.activeElement).toBe(origen);
  });
});

describe('Cobertura de un plan por código', () => {
  it('Ver la cobertura de "Plan Base"', async () => {
    const servidor = servidorAdmin();
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    expect(servidor.a('GET', '/obras-sociales/1/planes/11/coberturas')).toHaveLength(1);
    expect(campo(dialogo, 'Porcentaje de N-0101').value).toBe('100');
    expect(campo(dialogo, 'Porcentaje de N-0201').value).toBe('50');
    expect(campo(dialogo, 'Porcentaje de N-0301').value).toBe('');
    const n0301 = campo(dialogo, 'Porcentaje de N-0301').closest('.pre-cob-fila') as HTMLElement;
    expect(texto(n0301)).toContain('No cubre (0 %)');
  });

  it('Código inactivo', async () => {
    const servidor = servidorAdmin();
    servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR_C19.map((c) => (c.codigo === 'N-0301' ? { ...c, activo: false } : c))));
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    expect(texto(dialogo)).not.toContain('N-0301');
  });
});

describe('Guardar la cobertura de un plan', () => {
  it('Cambiar un porcentaje', async () => {
    const servidor = servidorAdmin();
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}/coberturas/{id}', () => json(200, {}));
    const { dialogo, panel } = await abrirCoberturaDePlanBase(servidor);

    escribir(campo(dialogo, 'Porcentaje de N-0201'), '60');
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    expect(servidor.pedidos.filter((p) => p.metodo === 'PUT').map((p) => p.ruta)).toEqual(['/obras-sociales/1/planes/11/coberturas/102']);
    expect(servidor.ultimo('PUT', '/obras-sociales/1/planes/11/coberturas/102').cuerpo).toEqual({ porcentaje: 60 });
    expect([...panel.querySelectorAll('[role="status"]')].map((s) => s.textContent)).toContain('Cobertura guardada.');
  });

  it('Falla a mitad del guardado', async () => {
    const servidor = servidorAdmin();
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}/coberturas/{id}', (pedido) =>
      pedido.ruta.endsWith('/101') ? json(200, {}) : json(500),
    );
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    escribir(campo(dialogo, 'Porcentaje de N-0101'), '90');
    escribir(campo(dialogo, 'Porcentaje de N-0201'), '60');
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.querySelector('[role="alert"]')?.textContent).toContain('Se guardó N-0101; falta N-0201.'));
    expect(dialogo.open).toBe(true);
    // Al reintentar solo se manda el que falta.
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}/coberturas/{id}', () => json(200, {}));
    enviar(dialogo);
    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    expect(servidor.a('PUT', '/obras-sociales/1/planes/11/coberturas/101')).toHaveLength(1);
    expect(servidor.a('PUT', '/obras-sociales/1/planes/11/coberturas/102')).toHaveLength(2);
  });
});

describe('Porcentajes válidos', () => {
  it('Porcentaje fuera de rango', async () => {
    const servidor = servidorAdmin();
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    escribir(campo(dialogo, 'Porcentaje de N-0101'), '101');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Porcentaje de N-0101')).toBe('Usá un número entero de 0 a 100.');
    expect(campo(dialogo, 'Porcentaje de N-0101').getAttribute('aria-invalid')).toBe('true');
    expect(servidor.pedidos.filter((p) => p.metodo === 'PUT')).toHaveLength(0);
  });

  it('Porcentaje con decimales', async () => {
    const servidor = servidorAdmin();
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    escribir(campo(dialogo, 'Porcentaje de N-0201'), '50,5');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Porcentaje de N-0201')).toBe('Usá un número entero de 0 a 100.');
    expect(servidor.pedidos.filter((p) => p.metodo === 'PUT')).toHaveLength(0);
  });

  it('Vaciar un porcentaje cargado', async () => {
    const servidor = servidorAdmin();
    const { dialogo } = await abrirCoberturaDePlanBase(servidor);

    escribir(campo(dialogo, 'Porcentaje de N-0201'), '');
    enviar(dialogo);

    expect(errorDeCampo(dialogo, 'Porcentaje de N-0201')).toBe('Para dejar de cubrirlo, cargá 0.');
    expect(servidor.pedidos.filter((p) => p.metodo === 'PUT')).toHaveLength(0);
  });

  it('no hay botón para borrar una cobertura', async () => {
    const { dialogo } = await abrirCoberturaDePlanBase();

    expect([...dialogo.querySelectorAll('button')].some((b) => /borrar|eliminar|quitar/i.test(b.textContent ?? ''))).toBe(false);
  });
});

describe('Aranceles solo para el administrador', () => {
  it('El odontólogo no ve los aranceles', async () => {
    const servidor = servidorAdmin(SESION_ODONTOLOGA);
    const panel = await abrirPrestaciones(servidor);

    expect([...panel.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Cambiar precio');
    expect(servidor.a('GET', '/aranceles')).toHaveLength(0);
  });
});
