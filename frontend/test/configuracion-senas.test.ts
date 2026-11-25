import { afterEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, errorDeCampo, escribir, json, texto, type ServidorFalso } from './apoyo.ts';
import { abrirConfiguracion, elegirPestana, pestanas, SESION_ADMIN, SESION_RECEPCION_CONFIG, servidorCon } from './apoyo-configuracion.ts';

// C-23: la sección "Señas" de Configuración (spec frontend-senas).

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
});

function conSenas(activas = false, porcentaje = 30, plazoMinutos = 30): ServidorFalso {
  const servidor = servidorCon(SESION_ADMIN);
  servidor.responder('GET /senas/configuracion', () => json(200, { activas, porcentaje, plazoMinutos }));
  return servidor;
}

async function abrirSenas(servidor: ServidorFalso): Promise<HTMLElement> {
  const { raiz } = await abrirConfiguracion(servidor);
  const panel = elegirPestana(raiz, 'Señas');
  await vi.waitFor(() => expect(panel.querySelector('form')).not.toBeNull());
  return panel;
}

function casilla(panel: HTMLElement): HTMLInputElement {
  return campo<HTMLInputElement>(panel, 'Pedir seña en la reserva online');
}

describe('Sección "Señas" en Configuración', () => {
  it('Ver la configuración de las señas', async () => {
    const panel = await abrirSenas(conSenas());

    expect(casilla(panel).checked).toBe(false);
    expect(campo(panel, 'Porcentaje del copago').value).toBe('30');
    expect(campo(panel, 'Plazo para pagar (minutos)').value).toBe('30');
  });

  it('Guardar las señas', async () => {
    const servidor = conSenas();
    servidor.responder('PUT /senas/configuracion', () => json(200, { activas: true, porcentaje: 25, plazoMinutos: 45 }));
    const panel = await abrirSenas(servidor);

    casilla(panel).click();
    escribir(campo(panel, 'Porcentaje del copago'), '25');
    escribir(campo(panel, 'Plazo para pagar (minutos)'), '45');
    boton(panel, 'Guardar').click();

    await vi.waitFor(() => expect(texto(panel)).toContain('Configuración de señas guardada.'));
    expect(servidor.ultimo('PUT', '/senas/configuracion').cuerpo).toEqual({
      activas: true,
      porcentaje: 25,
      plazoMinutos: 45,
    });
  });

  it('Porcentaje inválido en la pantalla', async () => {
    const servidor = conSenas();
    servidor.responder('PUT /senas/configuracion', () =>
      json(422, {
        status: 422,
        codigo: 'DATOS_INVALIDOS',
        detail: 'Hay datos inválidos en el pedido',
        errores: [{ campo: 'porcentaje', mensaje: 'Escribí un número entero de 1 a 100.' }],
      }),
    );
    const panel = await abrirSenas(servidor);

    escribir(campo(panel, 'Porcentaje del copago'), '0');
    boton(panel, 'Guardar').click();

    await vi.waitFor(() =>
      expect(errorDeCampo(panel, 'Porcentaje del copago')).toBe('Escribí un número entero de 1 a 100.'),
    );
    expect(texto(panel)).not.toContain('Configuración de señas guardada.');
  });

  it('La recepción no ve la sección', async () => {
    const servidor = servidorCon(SESION_RECEPCION_CONFIG);
    const { raiz } = await abrirConfiguracion(servidor);

    expect(pestanas(raiz).map((p) => p.textContent)).not.toContain('Señas');
    expect(servidor.a('GET', '/senas/configuracion')).toHaveLength(0);
  });
});
