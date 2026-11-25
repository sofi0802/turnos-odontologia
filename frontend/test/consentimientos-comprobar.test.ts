import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, escribir, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  dialogosAbiertos,
  enviarFirma,
  prepararEntorno,
  responderDocumento,
  REVOCADO,
  resumen,
  SESION_ANA,
  TITULO_BLANQUEAMIENTO,
  TITULO_EXTRACCION,
  ultimoDialogo,
  vistaDe,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/**
 * C-48, tarea 6.3: sesión vencida, envío dudoso y comprobar si la firma quedó guardada (spec `frontend-consentimientos`).
 * Antes de la firma el paciente tiene los consentimientos 10 y 11 (los dos revocados, de otras plantillas).
 */

const CLAVE_BUENA = 'clave-ficticia-buena';
const DIEZ = resumen({ id: 10, plantillaId: 5, versionNumero: 1, titulo: TITULO_BLANQUEAMIENTO, firmadoEn: '2026-09-01T09:00:00-03:00', estado: 'REVOCADO' });
const ONCE = { ...REVOCADO, id: 11 };
/** El consentimiento que quedó guardado aunque la respuesta no llegó: id 12, plantilla 4, versión 3, Dra. Ana Ejemplo. */
const DOCE = resumen({ id: 12, firmadoEn: '2026-11-24T10:30:00-03:00' });

let entorno: Entorno;
let sesionAbierta: boolean;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
  entorno.consentimientos = [DIEZ, ONCE];
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
  responderDocumento(entorno.servidor, vistaDe(DOCE));
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

function bloqueo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.bloqueo-pantalla');
}

function textoDelBloqueo(): string {
  return visible(bloqueo() as HTMLElement);
}

/** Abre la vista de firma, la completa y la envía. */
async function firmar(): Promise<HTMLDialogElement> {
  const pantalla = await abrirVistaDeFirma(entorno);
  completarFirma(pantalla);
  enviarFirma(pantalla);
  return pantalla;
}

/** Firma, espera el bloqueo y desbloquea con la contraseña buena; devuelve cuando la ficha se reabrió. */
async function firmarYDesbloquear(): Promise<void> {
  await firmar();
  const pantalla = await vi.waitFor(() => {
    expect(bloqueo()).not.toBeNull();
    return bloqueo() as HTMLElement;
  });
  escribir(campo(pantalla, 'Contraseña'), CLAVE_BUENA);
  boton(pantalla, 'Desbloquear').click();
  await vi.waitFor(() => expect(bloqueo()).toBeNull());
  await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());
}

/** La ficha reabierta, con la sección ya sin "Cargando". */
async function fichaLista(): Promise<HTMLElement> {
  await vi.waitFor(() => expect(entorno.seccion().querySelector('.cargando')).toBeNull());
  return entorno.seccion();
}

function resultado(): HTMLElement | null {
  return entorno.seccion().querySelector<HTMLElement>('.cons-resultado');
}

describe('Sesión vencida durante la firma', () => {
  it('La sesión venció al firmar', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(401, { codigo: 'NO_AUTENTICADO' }));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Nombre Escrito Ficticio', dni: '99.000.001' });

    enviarFirma(pantalla);

    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(document.querySelector('dialog.firma-pantalla')).toBeNull();
    expect(document.body.innerHTML).not.toContain('Nombre Escrito Ficticio');
    expect(textoDelBloqueo()).toContain('La firma no se completó.');
    expect(textoDelBloqueo()).not.toContain('quedó registrada');

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());
    const seccion = await fichaLista();
    expect(seccion.querySelector('[role="alert"]')?.textContent).toBe(
      'La firma no se guardó porque la sesión había vencido. Si hace falta, hacela firmar de nuevo.',
    );
    // Sin comprobar: no es un envío dudoso, no hay "Comprobando…" ni resultado.
    expect(resultado()).toBeNull();
    expect(visible(seccion)).not.toContain('Comprobando');
  });

  it('un 403 (por ejemplo, el token CSRF) se trata igual que un 401', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(403, { codigo: 'ACCESO_DENEGADO' }));

    await firmarYDesbloquear();

    const seccion = await fichaLista();
    expect(seccion.querySelector('[role="alert"]')?.textContent).toContain('La firma no se guardó porque la sesión había vencido.');
    expect(resultado()).toBeNull();
  });

  it('el aviso se ve una sola vez: al volver a abrir la ficha ya no está', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(401, { codigo: 'NO_AUTENTICADO' }));
    await firmarYDesbloquear();
    await fichaLista();

    entorno.app()?.router.actualizar();

    // La selección también se consumió: vuelve el buscador, sin la ficha y sin el aviso.
    await vi.waitFor(() => expect(visible(entorno.main())).toContain('Buscar paciente'));
    expect(visible(entorno.main())).not.toContain('La firma no se guardó porque la sesión había vencido.');
    expect(entorno.main().querySelector('.cons-seccion')).toBeNull();
  });
});

describe('Envío dudoso de la firma', () => {
  const MENSAJE = 'No pudimos confirmar si la firma se guardó. El profesional lo va a revisar.';

  it('Falla la red al firmar', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => Promise.reject(new TypeError('sin red')));

    await firmar();

    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(textoDelBloqueo()).toContain(MENSAJE);
    expect(textoDelBloqueo()).not.toContain('quedó guardada');
    expect(textoDelBloqueo()).not.toContain('no se guardó.');
    expect(document.querySelector('dialog.firma-pantalla')).toBeNull();
  });

  it('un error 5xx también es dudoso', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(502));

    await firmar();

    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(textoDelBloqueo()).toContain(MENSAJE);
  });

  it('una respuesta que no se puede leer también es dudosa, aunque sea un 201', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, { cosa: 'rara' }));

    await firmar();

    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(textoDelBloqueo()).toContain(MENSAJE);
    expect(textoDelBloqueo()).not.toContain('quedó registrada');
  });

  it('La firma tarda demasiado', async () => {
    let senal: AbortSignal | null | undefined;
    entorno.servidor.responder(
      'POST /pacientes/{id}/consentimientos',
      (pedido) =>
        new Promise<Response>((_resolver, rechazar) => {
          senal = pedido.senal;
          pedido.senal?.addEventListener('abort', () => rechazar(new DOMException('cancelado', 'AbortError')));
        }),
    );
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    vi.setSystemTime(new Date('2026-11-24T11:00:00-03:00'));

    enviarFirma(pantalla);
    await vi.advanceTimersByTimeAsync(29_999);
    expect(senal?.aborted).toBe(false);
    expect(bloqueo()).toBeNull();
    expect(pantalla.open).toBe(true);
    await vi.advanceTimersByTimeAsync(1);

    expect(senal?.aborted).toBe(true);
    vi.useRealTimers();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(textoDelBloqueo()).toContain(MENSAJE);
  });

  it('el navegador cierra la vista con el envío en curso: también es dudoso', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => new Promise<Response>(() => undefined));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(boton(pantalla, 'Firmando…').disabled).toBe(true));

    pantalla.close();

    expect(textoDelBloqueo()).toContain(MENSAJE);
    expect(document.body.innerHTML).not.toContain('99.000.001');
  });
});

describe('Comprobar si la firma quedó guardada', () => {
  /** El envío falla por la red; el listado cambia según el escenario. */
  function enviarConFalla(): void {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => Promise.reject(new TypeError('sin red')));
  }

  it('La firma quedó guardada', async () => {
    enviarConFalla();
    await firmar();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    // La firma sí se guardó en el servidor, aunque la respuesta no llegó.
    entorno.consentimientos = [DOCE, DIEZ, ONCE];

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();
    await vi.waitFor(() => expect(resultado()?.classList.contains('cons-resultado-ok')).toBe(true));

    const caja = resultado() as HTMLElement;
    expect(visible(caja)).toContain('La firma quedó guardada.');
    expect(visible(caja)).toContain(TITULO_EXTRACCION);
    expect(visible(caja)).toContain('versión 3');
    expect(visible(caja)).toContain('10:30');
    expect(caja.getAttribute('role')).toBe('status');
    expect(boton(caja, `Ver ${TITULO_EXTRACCION} del 24/11/2026`)).toBeDefined();
    expect(visible(caja)).not.toContain('no se guardó');

    boton(caja, `Ver ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const documento = await ultimoDialogo();
    expect(documento.querySelector('h2')?.textContent).toBe(TITULO_EXTRACCION);
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12')).toHaveLength(1);
  });

  it('La firma no se guardó', async () => {
    enviarConFalla();
    await firmarYDesbloquear();

    await vi.waitFor(() => expect(resultado()?.classList.contains('cons-resultado-error')).toBe(true));
    const caja = resultado() as HTMLElement;
    expect(visible(caja)).toContain('La firma no se guardó.');
    expect(visible(caja)).toContain(TITULO_EXTRACCION);
    expect(visible(caja)).toContain('versión 3');
    expect(caja.getAttribute('role')).toBe('alert');
    expect(boton(caja, 'Hacer firmar de nuevo')).toBeDefined();
    expect(visible(caja)).not.toContain('quedó guardada');
  });

  it('"Hacer firmar de nuevo" abre la vista de firma con la misma plantilla', async () => {
    enviarConFalla();
    await firmarYDesbloquear();
    await vi.waitFor(() => expect(resultado()?.classList.contains('cons-resultado-error')).toBe(true));
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, vistaDe(DOCE)));

    boton(resultado() as HTMLElement, 'Hacer firmar de nuevo').click();

    await vi.waitFor(() => expect(document.querySelector('dialog.firma-pantalla')).not.toBeNull());
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/4').length).toBeGreaterThanOrEqual(2);
  });

  it('"Hacer firmar de nuevo" pasa por el aviso de doble firma si ya hay una firma de hoy', async () => {
    enviarConFalla();
    await firmarYDesbloquear();
    await vi.waitFor(() => expect(resultado()?.classList.contains('cons-resultado-error')).toBe(true));
    // Mientras tanto, otra firma de la misma plantilla y versión quedó guardada hoy.
    entorno.consentimientos = [resumen({ id: 15, firmadoEn: '2026-11-24T10:50:00-03:00' }), DIEZ, ONCE];

    boton(resultado() as HTMLElement, 'Hacer firmar de nuevo').click();

    const pregunta = await vi.waitFor(() => {
      const abierto = dialogosAbiertos().find((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy');
      expect(abierto).toBeDefined();
      return abierto as HTMLDialogElement;
    });
    expect(document.querySelector('dialog.firma-pantalla')).toBeNull();
    boton(pregunta, 'No firmar').click();
    expect(document.querySelector('dialog.firma-pantalla')).toBeNull();
  });

  it('No se puede comprobar', async () => {
    enviarConFalla();
    await firmar();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(500));

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());
    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('No pudimos comprobar si la firma quedó guardada.'));

    const caja = resultado() as HTMLElement;
    expect(boton(caja, 'Comprobar de nuevo')).toBeDefined();
    expect(visible(entorno.seccion())).not.toContain('La firma no se guardó.');
    expect(visible(entorno.seccion())).not.toContain('La firma quedó guardada.');
    expect(caja.getAttribute('role')).toBe('alert');

    // "Comprobar de nuevo" vuelve a pedir el listado y, con la respuesta, da el resultado.
    entorno.consentimientos = [DOCE, DIEZ, ONCE];
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, entorno.consentimientos));
    boton(caja, 'Comprobar de nuevo').click();
    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('La firma quedó guardada.'));
  });

  it('Comprobando', async () => {
    enviarConFalla();
    await firmar();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => new Promise<Response>((resolver) => (terminar = resolver)));

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());

    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('Comprobando si la firma quedó guardada…'));
    const caja = resultado() as HTMLElement;
    expect(caja.getAttribute('role')).toBe('status');
    expect(caja.classList.contains('cons-resultado-pendiente')).toBe(true);
    expect(visible(caja)).toContain(TITULO_EXTRACCION);
    expect(visible(entorno.seccion())).not.toContain('La firma no se guardó.');
    expect(visible(entorno.seccion())).not.toContain('La firma quedó guardada.');

    terminar(json(200, [DIEZ, ONCE]));
    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('La firma no se guardó.'));
    expect(visible(entorno.seccion())).not.toContain('Comprobando si la firma quedó guardada…');
  });

  it('una firma de otra plantilla, versión o profesional no cuenta como la firma buscada', async () => {
    enviarConFalla();
    await firmar();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    entorno.consentimientos = [
      resumen({ id: 20, versionNumero: 2 }),
      resumen({ id: 21, plantillaId: 6 }),
      resumen({ id: 22, profesionalId: 2, profesional: 'Bruno Prueba' }),
      DIEZ,
      ONCE,
    ];

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();

    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('La firma no se guardó.'));
    expect(visible(entorno.seccion())).not.toContain('La firma quedó guardada.');
  });

  it('una firma que ya estaba en el listado de antes no cuenta', async () => {
    enviarConFalla();
    await firmar();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    // El id 10 ya estaba antes: aunque ahora sea de la plantilla 4, versión 3 y de Ana, no es la firma nueva.
    entorno.consentimientos = [{ ...DIEZ, plantillaId: 4, versionNumero: 3, titulo: TITULO_EXTRACCION }, ONCE];

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();

    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('La firma no se guardó.'));
  });

  it('una firma que se canceló no se comprueba', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    boton(pantalla, 'Cancelar').click();
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    const antes = entorno.servidor.a('GET', '/pacientes/1/consentimientos').length;

    escribir(campo(bloqueo() as HTMLElement, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo() as HTMLElement, 'Desbloquear').click();
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-seccion')).not.toBeNull());
    await fichaLista();

    expect(resultado()).toBeNull();
    expect(visible(entorno.seccion())).not.toContain('Comprobando');
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos').length).toBe(antes + 1);
  });
});
