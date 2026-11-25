import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  enviarFirma,
  prepararEntorno,
  resumen,
  SESION_ANA,
  TITULO_EXTRACCION,
  vistaDe,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 6.1: "Devolvé el equipo al profesional" y el desbloqueo con la contraseña (spec `frontend-consentimientos`). */

const CLAVE_BUENA = 'clave-ficticia-buena';
const CLAVE_MALA = 'clave-ficticia-mala';

let entorno: Entorno;
/** Si el servidor tiene la sesión del odontólogo abierta: el logout la cierra y el ingreso correcto la abre. */
let sesionAbierta: boolean;
/** Qué responde el ingreso con la contraseña buena. */
let sesionDelIngreso: object;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
  sesionAbierta = true;
  sesionDelIngreso = SESION_ANA;
  entorno.servidor.responder('GET /public/sesion', () => json(200, sesionAbierta ? SESION_ANA : { autenticado: false }));
  entorno.servidor.responder('POST /logout', () => {
    sesionAbierta = false;
    return json(204);
  });
  entorno.servidor.responder('POST /public/login', (pedido) => {
    if ((pedido.cuerpo as { password: string }).password === CLAVE_BUENA) {
      sesionAbierta = true;
      return json(200, sesionDelIngreso);
    }
    return json(401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'x' });
  });
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => {
    const nuevo = resumen({ id: 13, firmadoEn: '2026-11-24T10:30:00-03:00' });
    entorno.consentimientos = [nuevo, ...entorno.consentimientos];
    return json(201, vistaDe(nuevo));
  });
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

function bloqueo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.bloqueo-pantalla');
}

function logouts(): number {
  return entorno.servidor.a('POST', '/logout').length;
}

function logins(): number {
  return entorno.servidor.a('POST', '/public/login').length;
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

function escribirClave(pantalla: HTMLElement, clave: string): void {
  escribir(campo(pantalla, 'Contraseña'), clave);
}

/** "Desbloquear" con la contraseña dada, como lo haría el profesional. */
function intentar(pantalla: HTMLElement, clave: string): void {
  escribirClave(pantalla, clave);
  boton(pantalla, 'Desbloquear').click();
}

function errorDelBloqueo(pantalla: HTMLElement): string {
  return pantalla.querySelector('.campo-error')?.textContent ?? '';
}

describe('Devolver el equipo al profesional', () => {
  it('El bloqueo tapa todo y cierra la sesión', async () => {
    const pantalla = await firmarYBloquear();

    const texto = visible(pantalla);
    expect(texto).toContain('Devolvé el equipo al profesional');
    expect(texto).toContain('Usuario: ana.ejemplo');
    expect(texto).toContain('Contraseña');
    expect(texto).toContain('Desbloquear');
    expect(texto).toContain('Para seguir, el profesional escribe su contraseña.');
    await vi.waitFor(() => expect(logouts()).toBe(1));
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.body.textContent).not.toContain('99000001');
    expect(document.body.textContent).not.toContain(TITULO_EXTRACCION);
    expect(document.querySelector('.ficha-columnas, .cons-seccion, .odontograma')).toBeNull();
    // Fuera de la raíz de la aplicación, opaco, y la raíz queda inerte.
    const raiz = entorno.app()?.raiz as HTMLElement;
    expect(raiz.contains(pantalla)).toBe(false);
    expect(pantalla.parentElement).toBe(document.body);
    expect(raiz.hasAttribute('inert')).toBe(true);
    expect(pantalla.hasAttribute('inert')).toBe(false);
    expect(pantalla.tagName).not.toBe('DIALOG');
  });

  it('Escape no quita el bloqueo', async () => {
    const pantalla = await firmarYBloquear();

    for (let i = 0; i < 2; i += 1) {
      const tecla = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
      document.dispatchEvent(tecla);
      pantalla.dispatchEvent(new Event('cancel', { cancelable: true, bubbles: true }));
    }

    expect(bloqueo()).toBe(pantalla);
    expect(pantalla.isConnected).toBe(true);
  });

  it('Atrás no quita el bloqueo', async () => {
    const pantalla = await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));

    window.location.hash = '#/agenda';
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/public/sesion').length).toBeGreaterThan(1));
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(bloqueo()).toBe(pantalla);
    expect(pantalla.isConnected).toBe(true);
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.querySelector('.ficha-columnas, .cons-seccion')).toBeNull();
  });

  it('el foco queda en "Contraseña" y el aviso del caso se anuncia', async () => {
    const pantalla = await firmarYBloquear();

    expect(document.activeElement).toBe(campo(pantalla, 'Contraseña'));
    expect(pantalla.querySelector('.bloqueo-caso')?.getAttribute('role')).toBe('status');
  });
});

describe('Desbloquear con la contraseña del profesional', () => {
  it('Desbloquear con la contraseña correcta', async () => {
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    expect(entorno.servidor.ultimo('POST', '/public/login').cuerpo).toEqual({ username: 'ana.ejemplo', password: CLAVE_BUENA });
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());
    expect(visible(entorno.main())).toContain('Paciente Uno');
    expect((entorno.app()?.raiz as HTMLElement).hasAttribute('inert')).toBe(false);
    expect(document.body.textContent).not.toContain(CLAVE_BUENA);
  });

  it('Volver a la ficha después de firmar', async () => {
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion .cons-lista')).not.toBeNull());
    const seccion = entorno.seccion();
    expect(seccion.querySelector('[role="status"]')?.textContent).toBe(
      `Consentimiento firmado: ${TITULO_EXTRACCION}, versión 3, a las 10:30.`,
    );
    // El listado se vuelve a pedir y muestra el consentimiento nuevo como "Vigente".
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos').length).toBeGreaterThanOrEqual(3);
    const primera = seccion.querySelector('.cons-lista > li') as HTMLElement;
    expect(primera.querySelector('.etiqueta')?.textContent).toBe('Vigente');
    expect(visible(primera)).toContain('Firmado el 24/11/2026 a las 10:30');
  });

  it('El usuario no se puede cambiar', async () => {
    const pantalla = await firmarYBloquear();

    const entradas = [...pantalla.querySelectorAll('input, select, textarea')];

    expect(entradas).toHaveLength(1);
    expect((entradas[0] as HTMLInputElement).type).toBe('password');
    expect((entradas[0] as HTMLInputElement).getAttribute('autocomplete')).toBe('off');
    expect(pantalla.querySelector('.bloqueo-usuario strong')?.textContent).toBe('ana.ejemplo');
  });

  it('La sesión nueva es de otro usuario', async () => {
    sesionDelIngreso = { autenticado: true, username: 'bruno.prueba', rol: 'ODONTOLOGO', profesionalId: 2 };
    const pantalla = await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(logouts()).toBe(2));
    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.querySelector('.ficha-columnas, .cons-seccion')).toBeNull();
  });

  it('una sesión nueva con otro rol tampoco vuelve a la ficha', async () => {
    sesionDelIngreso = { autenticado: true, username: 'ana.ejemplo', rol: 'ADMIN' };
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(logouts()).toBe(2));
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    expect(document.querySelector('.cons-seccion')).toBeNull();
  });

  it('una respuesta del ingreso que no es una sesión tampoco vuelve a la ficha', async () => {
    sesionDelIngreso = { cosa: 'rara' };
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(logouts()).toBe(2));
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    expect(bloqueo()).toBeNull();
  });

  it('si la cuenta tiene una contraseña temporal, sigue el cambio de contraseña y no reabre la ficha', async () => {
    sesionDelIngreso = { ...SESION_ANA, debeCambiarContrasena: true };
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    await vi.waitFor(() => expect(window.location.hash).toBe('#/cambiar-contrasena'));
    expect(document.querySelector('.cons-seccion')).toBeNull();
  });

  it('no envía nada con la contraseña vacía', async () => {
    const pantalla = await firmarYBloquear();

    boton(pantalla, 'Desbloquear').click();

    expect(logins()).toBe(0);
    expect(errorDelBloqueo(pantalla)).toBe('Escribí tu contraseña.');
    expect(bloqueo()).toBe(pantalla);
  });

  it('un solo intento a la vez: mientras comprueba, "Desbloqueando…" está deshabilitado', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('POST /public/login', () => new Promise<Response>((resolver) => (terminar = resolver)));
    const pantalla = await firmarYBloquear();
    escribirClave(pantalla, CLAVE_BUENA);

    boton(pantalla, 'Desbloquear').click();
    await vi.waitFor(() => expect(boton(pantalla, 'Desbloqueando…').disabled).toBe(true));
    await vi.waitFor(() => expect(logins()).toBe(1));
    pantalla.querySelector('form')?.requestSubmit();
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(logins()).toBe(1);
    sesionAbierta = true;
    terminar(json(200, SESION_ANA));
    await vi.waitFor(() => expect(bloqueo()).toBeNull());
  });
});

describe('Contraseña incorrecta en el bloqueo', () => {
  it('Contraseña incorrecta', async () => {
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_MALA);

    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toBe('Contraseña incorrecta. Te quedan 2 intentos.'));
    expect(bloqueo()).toBe(pantalla);
    expect(campo<HTMLInputElement>(pantalla, 'Contraseña').value).toBe('');
    expect(document.activeElement).toBe(campo(pantalla, 'Contraseña'));
    expect(pantalla.querySelector('.campo-error')?.getAttribute('role')).toBe('alert');
    expect(document.body.textContent).not.toContain(CLAVE_MALA);
  });

  it('el segundo fallo dice "Te queda 1 intento." en singular', async () => {
    const pantalla = await firmarYBloquear();

    intentar(pantalla, CLAVE_MALA);
    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toContain('Te quedan 2 intentos.'));
    intentar(pantalla, CLAVE_MALA);

    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toBe('Contraseña incorrecta. Te queda 1 intento.'));
  });

  it('Demasiados intentos', async () => {
    const pantalla = await firmarYBloquear();
    intentar(pantalla, CLAVE_MALA);
    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toContain('Te quedan 2 intentos.'));
    intentar(pantalla, CLAVE_MALA);
    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toContain('Te queda 1 intento.'));

    intentar(pantalla, CLAVE_MALA);

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    await vi.waitFor(() => expect(visible(entorno.app()?.raiz as HTMLElement)).toContain('Por seguridad, cerramos la sesión. Ingresá de nuevo.'));
    expect(logins()).toBe(3);
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.querySelector('.ficha-columnas, .cons-seccion')).toBeNull();
    expect(logouts()).toBe(1);
  });

  it('Sin conexión al desbloquear', async () => {
    const pantalla = await firmarYBloquear();
    intentar(pantalla, CLAVE_MALA);
    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toContain('Te quedan 2 intentos.'));
    entorno.servidor.responder('POST /public/login', () => Promise.reject(new TypeError('sin red')));

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toBe('No pudimos comprobar la contraseña. Probá de nuevo.'));
    expect(bloqueo()).toBe(pantalla);
    // No cuenta como intento: después de otro error de contraseña quedan 1, no 0.
    entorno.servidor.responder('POST /public/login', () => json(401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'x' }));
    intentar(pantalla, CLAVE_MALA);
    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toBe('Contraseña incorrecta. Te queda 1 intento.'));
  });

  it('un error 5xx tampoco cuenta como intento', async () => {
    const pantalla = await firmarYBloquear();
    entorno.servidor.responder('POST /public/login', () => json(503));

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(errorDelBloqueo(pantalla)).toBe('No pudimos comprobar la contraseña. Probá de nuevo.'));
    expect(bloqueo()).toBe(pantalla);
    expect(campo<HTMLInputElement>(pantalla, 'Contraseña').value).toBe('');
  });

  it.each([
    ['otro 401', () => json(401, { codigo: 'NO_AUTENTICADO' })],
    ['un 403', () => json(403, { codigo: 'ACCESO_DENEGADO' })],
    ['un 404', () => json(404)],
    ['un 409', () => json(409)],
  ])('%s lleva al ingreso, igual que tres intentos', async (_nombre, respuesta) => {
    const pantalla = await firmarYBloquear();
    entorno.servidor.responder('POST /public/login', respuesta);

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    expect(document.querySelector('.cons-seccion')).toBeNull();
    expect(document.body.textContent).not.toContain('Paciente Uno');
  });
});

describe('El logout del bloqueo', () => {
  it('si el logout falla, se reintenta antes de comprobar la contraseña', async () => {
    entorno.servidor.responder('POST /logout', () => Promise.reject(new TypeError('sin red')));
    const pantalla = await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));
    entorno.servidor.responder('POST /logout', () => {
      sesionAbierta = false;
      return json(204);
    });

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(logins()).toBe(1));
    expect(logouts()).toBe(2);
    const orden = entorno.servidor.pedidos.filter((p) => p.ruta === '/logout' || p.ruta === '/public/login').map((p) => p.ruta);
    expect(orden).toEqual(['/logout', '/logout', '/public/login']);
  });

  it('si el logout salió bien, no se repite al desbloquear', async () => {
    const pantalla = await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));

    intentar(pantalla, CLAVE_BUENA);

    await vi.waitFor(() => expect(bloqueo()).toBeNull());
    expect(logouts()).toBe(1);
  });

  it('al salir de la página con el logout pendiente, lo vuelve a pedir', async () => {
    entorno.servidor.responder('POST /logout', () => Promise.reject(new TypeError('sin red')));
    await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));

    window.dispatchEvent(new Event('pagehide'));

    await vi.waitFor(() => expect(logouts()).toBe(2));
  });

  it('al salir de la página con el logout hecho, no pide nada más', async () => {
    await firmarYBloquear();
    await vi.waitFor(() => expect(logouts()).toBe(1));

    window.dispatchEvent(new Event('pagehide'));
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(logouts()).toBe(1);
  });
});
