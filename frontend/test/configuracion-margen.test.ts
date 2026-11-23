import { afterEach, describe, expect, it, vi } from 'vitest';
import { leerMargen } from '../src/configuracion/margen.ts';
import { boton, campo, escribir, json, texto, type ServidorFalso } from './apoyo.ts';
import { abrirConfiguracion, elegirPestana, SESION_ADMIN, SESION_RECEPCION_CONFIG, servidorCon } from './apoyo-configuracion.ts';

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

function conMargen(sesion: object, minutos = 10): ServidorFalso {
  const servidor = servidorCon(sesion);
  servidor.responder('GET /configuracion/margen', () => json(200, { minutos }));
  return servidor;
}

async function abrirMargen(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Margen');
  await vi.waitFor(() => expect(panel.querySelector('.margen-valor')).not.toBeNull());
  return panel;
}

function vigente(panel: HTMLElement): string {
  return panel.querySelector('.margen-valor')?.textContent ?? '';
}

describe('Margen entre turnos', () => {
  it('El administrador cambia el margen', async () => {
    const servidor = conMargen(SESION_ADMIN);
    servidor.responder('PUT /configuracion/margen', () => json(200, { minutos: 15 }));
    const panel = await abrirMargen(servidor);
    expect(vigente(panel)).toBe('10');

    escribir(campo(panel, 'Nuevo margen'), '15');
    boton(panel, 'Guardar margen').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Margen guardado: 15 minutos.'));
    expect(servidor.a('PUT', '/configuracion/margen')).toHaveLength(1);
    expect(servidor.ultimo('PUT', '/configuracion/margen').cuerpo).toEqual({ minutos: 15 });
    expect(vigente(panel)).toBe('15');
  });

  it('Margen menor que el mínimo', async () => {
    const servidor = conMargen(SESION_ADMIN);
    servidor.responder('PUT /configuracion/margen', () =>
      json(422, { status: 422, codigo: 'MARGEN_INVALIDO', detail: 'Margen inválido' }),
    );
    const panel = await abrirMargen(servidor);

    escribir(campo(panel, 'Nuevo margen'), '5');
    boton(panel, 'Guardar margen').click();

    await vi.waitFor(() =>
      expect(panel.querySelector('[role="alert"]')?.textContent).toBe('El margen tiene que ser de 10 minutos o más.'),
    );
    expect(servidor.ultimo('PUT', '/configuracion/margen').cuerpo).toEqual({ minutos: 5 });
    expect(vigente(panel)).toBe('10');
    expect(texto(panel)).toContain('Margen vigente: 10 minutos');
    expect(texto(panel)).not.toContain('Margen guardado');
  });

  it('Recepción solo ve el margen', async () => {
    const servidor = conMargen(SESION_RECEPCION_CONFIG);
    const panel = await abrirMargen(servidor);

    expect(texto(panel)).toContain('10 minutos');
    expect(panel.querySelectorAll('input, select, textarea')).toHaveLength(0);
    expect(panel.querySelectorAll('button')).toHaveLength(0);
  });

  it('Lee el margen de forma defensiva', () => {
    expect(leerMargen({ minutos: 10 })).toBe(10);
    expect(leerMargen({ minutos: '10' })).toBeNull();
    expect(leerMargen(null)).toBeNull();
  });
});
