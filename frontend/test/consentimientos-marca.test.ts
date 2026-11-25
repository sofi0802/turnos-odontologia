import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CLAVE_DE_LA_MARCA, MENSAJE_SESION_CERRADA_AL_ARRANCAR } from '../src/consentimientos/marca.ts';
import { abrirApp, boton, campo, escribir, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  enviarFirma,
  prepararEntorno,
  resumen,
  SESION_ANA,
  vistaDe,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 6.2: la marca de firma en curso y el arranque seguro (spec `frontend-consentimientos`). */

const CLAVE_BUENA = 'clave-ficticia-buena';

let entorno: Entorno;
let sesionAbierta: boolean;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
  sesionAbierta = true;
  entorno.servidor.responder('GET /public/sesion', () => json(200, sesionAbierta ? SESION_ANA : { autenticado: false }));
  entorno.servidor.responder('POST /logout', () => {
    sesionAbierta = false;
    return json(204);
  });
  entorno.servidor.responder('POST /public/login', (pedido) => {
    if ((pedido.cuerpo as { password: string }).password === CLAVE_BUENA) {
      sesionAbierta = true;
      return json(200, SESION_ANA);
    }
    return json(401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'x' });
  });
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, vistaDe(resumen({ id: 13 }))));
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

function bloqueo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.bloqueo-pantalla');
}

function guardado(): Record<string, string> {
  return Object.fromEntries(Object.keys(localStorage).map((clave) => [clave, localStorage.getItem(clave) ?? '']));
}

/** Firma con datos válidos y espera el bloqueo. */
async function firmarYBloquear(): Promise<HTMLElement> {
  const pantalla = await abrirVistaDeFirma(entorno);
  completarFirma(pantalla);
  enviarFirma(pantalla);
  return vi.waitFor(() => {
    const actual = bloqueo();
    expect(actual).not.toBeNull();
    return actual as HTMLElement;
  });
}

/** Simula recargar la página: la memoria se pierde (la aplicación vieja y el bloqueo) y el almacenamiento queda. */
function recargar(): ReturnType<typeof abrirApp> {
  entorno.app()?.router.detener();
  document.querySelectorAll('.bloqueo-pantalla, dialog').forEach((e) => e.remove());
  document.body.replaceChildren();
  entorno.servidor.pedidos.length = 0;
  return abrirApp(entorno.servidor, '#/pacientes');
}

describe('Marca de firma en curso', () => {
  it('La marca no tiene datos', async () => {
    await abrirVistaDeFirma(entorno);

    expect(guardado()).toEqual({ [CLAVE_DE_LA_MARCA]: '1' });
    expect(CLAVE_DE_LA_MARCA).toBe('turnos.firmaEnCurso');
    expect(JSON.stringify(guardado())).not.toMatch(/Paciente|9900|extracción|ana\.ejemplo/);
  });

  it('sin firma en curso no hay marca', async () => {
    entorno.consentimientos = [];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(guardado()).toEqual({});
  });

  it('la marca sigue después de cancelar, durante el bloqueo', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    boton(pantalla, 'Cancelar').click();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());

    expect(guardado()).toEqual({ [CLAVE_DE_LA_MARCA]: '1' });
  });

  it('se borra al desbloquear con éxito', async () => {
    const pantalla = await firmarYBloquear();
    expect(guardado()).toEqual({ [CLAVE_DE_LA_MARCA]: '1' });

    escribir(campo(pantalla, 'Contraseña'), CLAVE_BUENA);
    boton(pantalla, 'Desbloquear').click();

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    expect(guardado()).toEqual({});
  });

  it('una contraseña incorrecta no la borra, pero llevar al ingreso sí', async () => {
    const pantalla = await firmarYBloquear();
    for (let intento = 1; intento <= 2; intento += 1) {
      escribir(campo(pantalla, 'Contraseña'), 'clave-ficticia-mala');
      boton(pantalla, 'Desbloquear').click();
      await vi.waitFor(() => expect(pantalla.querySelector('.campo-error')?.textContent).toContain('Contraseña incorrecta'));
      expect(guardado()).toEqual({ [CLAVE_DE_LA_MARCA]: '1' });
    }

    escribir(campo(pantalla, 'Contraseña'), 'clave-ficticia-mala');
    boton(pantalla, 'Desbloquear').click();

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    expect(guardado()).toEqual({});
  });

  it('si el almacenamiento falla, la firma sigue sin la marca', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('lleno', 'QuotaExceededError');
    });

    const pantalla = await abrirVistaDeFirma(entorno);

    expect(pantalla.open).toBe(true);
    expect(guardado()).toEqual({});
  });
});

describe('Recargar durante la firma o el bloqueo', () => {
  it('Recargar durante la firma', async () => {
    await abrirVistaDeFirma(entorno);
    expect(sesionAbierta).toBe(true);

    const nueva = recargar();

    await vi.waitFor(() => expect(entorno.servidor.a('POST', '/logout')).toHaveLength(1));
    // El logout es lo primero que se pide: antes de cualquier dato de otra página.
    expect(entorno.servidor.pedidos[0]?.metodo).toBe('POST');
    expect(entorno.servidor.pedidos[0]?.ruta).toBe('/logout');
    await vi.waitFor(() => expect(visible(nueva.raiz)).toContain(MENSAJE_SESION_CERRADA_AL_ARRANCAR));
    expect(MENSAJE_SESION_CERRADA_AL_ARRANCAR).toBe(
      'Por seguridad, la sesión se cerró. Si estabas haciendo firmar un consentimiento, revisá la ficha del paciente para ver si quedó guardado.',
    );
    expect(window.location.hash).toBe('#/mi-cuenta');
    expect(nueva.raiz.querySelector('.ficha-columnas, .cons-seccion')).toBeNull();
    expect(visible(nueva.raiz)).not.toContain('Buscar paciente');
    expect(entorno.servidor.pedidos.some((p) => p.ruta.startsWith('/pacientes'))).toBe(false);
    expect(guardado()).toEqual({});
  });

  it('Recargar durante el bloqueo', async () => {
    await firmarYBloquear();

    const nueva = recargar();

    await vi.waitFor(() => expect(entorno.servidor.a('POST', '/logout')).toHaveLength(1));
    await vi.waitFor(() => expect(visible(nueva.raiz)).toContain(MENSAJE_SESION_CERRADA_AL_ARRANCAR));
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.querySelector('.ficha-columnas, .cons-seccion, .bloqueo-pantalla')).toBeNull();
    expect(guardado()).toEqual({});
    expect(localStorage.getItem(CLAVE_DE_LA_MARCA)).toBeNull();
  });

  it('el aviso se ve una sola vez: una navegación posterior no lo repite', async () => {
    await abrirVistaDeFirma(entorno);
    const nueva = recargar();
    await vi.waitFor(() => expect(visible(nueva.raiz)).toContain(MENSAJE_SESION_CERRADA_AL_ARRANCAR));

    nueva.router.actualizar();
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(visible(nueva.raiz)).not.toContain(MENSAJE_SESION_CERRADA_AL_ARRANCAR);
  });

  it('sin la marca, la aplicación arranca como siempre y no cierra la sesión', async () => {
    entorno.consentimientos = [];
    const nueva = abrirApp(entorno.servidor, '#/agenda');

    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/public/sesion').length).toBeGreaterThan(0));
    expect(entorno.servidor.a('POST', '/logout')).toHaveLength(0);
    expect(visible(nueva.raiz)).not.toContain(MENSAJE_SESION_CERRADA_AL_ARRANCAR);
    nueva.router.detener();
  });

  it('si el logout falla al arrancar, igual se borra la marca y no se dibuja la ficha', async () => {
    await abrirVistaDeFirma(entorno);
    entorno.servidor.responder('POST /logout', () => json(500));

    const nueva = recargar();

    await vi.waitFor(() => expect(localStorage.getItem(CLAVE_DE_LA_MARCA)).toBeNull());
    // Con la sesión todavía abierta, Mi cuenta lleva a la agenda: nunca vuelve a la ficha de la firma.
    await vi.waitFor(() => expect(window.location.hash).not.toBe('#/pacientes'));
    expect(entorno.servidor.a('POST', '/logout')).toHaveLength(1);
    expect(nueva.raiz.querySelector('.ficha-columnas, .cons-seccion')).toBeNull();
    expect(entorno.servidor.pedidos.some((p) => p.ruta.startsWith('/pacientes'))).toBe(false);
  });
});
