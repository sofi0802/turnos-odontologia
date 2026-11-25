import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, json } from './apoyo.ts';
import {
  dialogosAbiertos,
  prepararEntorno,
  responderDocumento,
  REVOCADO,
  SESION_ADMIN,
  SESION_ANA,
  TEXTO_V3,
  TITULO_BLANQUEAMIENTO,
  TITULO_EXTRACCION,
  ultimoDialogo,
  VERIFICACION_ALTERADA,
  VERIFICACION_INTEGRA,
  vistaDe,
  visible,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 4.2: ver un consentimiento firmado y verificarlo al abrir (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
});

const VER_EXTRACCION = `Ver ${TITULO_EXTRACCION} del 24/11/2026`;
const VER_BLANQUEAMIENTO = `Ver ${TITULO_BLANQUEAMIENTO} del 01/10/2026`;

async function abrirDocumento(nombre = VER_EXTRACCION): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), nombre).click();
  return ultimoDialogo();
}

/** El documento dibujado y la verificación resuelta. */
async function documentoListo(nombre?: string): Promise<HTMLDialogElement> {
  const dialogo = await abrirDocumento(nombre);
  await vi.waitFor(() => expect(dialogo.querySelector('.cons-texto')).not.toBeNull());
  return dialogo;
}

function verificacion(dialogo: HTMLElement): HTMLElement {
  const aside = dialogo.querySelector<HTMLElement>('.cons-verificacion');
  if (aside === null) {
    throw new Error('El documento no tiene la franja de verificación');
  }
  return aside;
}

describe('Ver un consentimiento firmado', () => {
  it('Abrir un consentimiento', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12')).toHaveLength(1);
    expect(dialogo.querySelector('h2')?.textContent).toBe(TITULO_EXTRACCION);
    expect(dialogo.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
    const texto = visible(dialogo);
    for (const dato of ['Paciente Uno', '99000001', '24/11/2026 10:30', 'Ana Ejemplo', 'Firma electrónica', 'Vigente', 'Versión 3']) {
      expect(texto).toContain(dato);
    }
    const evidencia = dialogo.querySelector<HTMLDetailsElement>('details.cons-evidencia');
    expect(evidencia?.querySelector('summary')?.textContent).toBe('Evidencia de la firma');
    expect(evidencia?.open).toBe(false);
    for (const dato of ['192.0.2.10', 'Navegador de prueba 1.0', 'SHA-256', 'a'.repeat(64)]) {
      expect(evidencia?.textContent).toContain(dato);
    }
    const fuera = dialogo.cloneNode(true) as HTMLElement;
    fuera.querySelector('details')?.remove();
    expect(visible(fuera)).not.toContain('192.0.2.10');
    expect(visible(fuera)).not.toContain('a'.repeat(64));
  });

  it('el texto firmado se muestra como texto plano, con sus saltos de línea', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    const texto = dialogo.querySelector<HTMLElement>('.cons-texto');
    expect(texto?.textContent).toContain('Segundo párrafo con <b>riesgos</b> en la misma línea.');
    expect(texto?.textContent).toContain('\n\n');
    expect(dialogo.querySelector('.cons-texto b')).toBeNull();
  });

  it('Ver un consentimiento revocado', async () => {
    responderDocumento(entorno.servidor, vistaDe(REVOCADO));
    const dialogo = await documentoListo(VER_BLANQUEAMIENTO);

    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/11')).toHaveLength(1);
    expect(visible(dialogo)).toContain('Revocado');
    expect(visible(dialogo)).toContain('Revocado el 20/11/2026 por Ana Ejemplo: El paciente decidió no hacer el tratamiento');
    expect(dialogo.querySelector('.etiqueta-revocado')?.textContent).toBe('Revocado');
    expect(dialogo.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
  });

  it('un consentimiento vigente no muestra datos de revocación', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    expect(dialogo.querySelector('.cons-revocacion')).toBeNull();
    expect(visible(dialogo)).not.toContain('Revocado');
  });

  it('no muestra los ids internos', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    expect(dialogo.innerHTML).not.toMatch(/profesionalId|plantillaId|consentimientoId/);
  });

  it('un motivo o un dispositivo con marcas de HTML se ven literales', async () => {
    responderDocumento(
      entorno.servidor,
      vistaDe(REVOCADO, {
        revocacion: { profesionalId: 1, profesional: 'Ana Ejemplo', revocadoEn: '2026-11-20T11:00:00-03:00', motivo: '<i>cambió</i>' },
        evidencia: { ip: '192.0.2.10', dispositivo: '<script>x</script>' },
      }),
    );
    const dialogo = await documentoListo(VER_BLANQUEAMIENTO);

    expect(dialogo.querySelector('i, script')).toBeNull();
    expect(visible(dialogo)).toContain('<i>cambió</i>');
    expect(visible(dialogo)).toContain('<script>x</script>');
  });

  it('solo tiene "Cerrar" y devuelve el foco al botón "Ver"', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();
    const abrir = boton(entorno.seccion(), VER_EXTRACCION);

    expect([...dialogo.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Cerrar']);
    boton(dialogo, 'Cerrar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(document.activeElement).toBe(abrir);
  });

  it('si el documento no se puede pedir, avisa dentro del diálogo y deja "Reintentar"', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(500));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, VERIFICACION_INTEGRA));
    const dialogo = await abrirDocumento();

    await vi.waitFor(() => expect(dialogo.querySelector('[role="alert"]')?.textContent).toBe(MENSAJE_ERROR_GENERICO));
    expect(dialogo.querySelector('.cons-texto')).toBeNull();
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(200, vistaDe(VIGENTE)));
    boton(dialogo, 'Reintentar').click();
    await vi.waitFor(() => expect(dialogo.querySelector('.cons-texto')).not.toBeNull());
  });

  it('el administrador ve lo mismo que el odontólogo', async () => {
    entorno.cerrar();
    entorno = prepararEntorno(SESION_ADMIN);
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    expect(visible(dialogo)).toContain('99000001');
    expect(visible(dialogo)).toContain('Íntegro');
  });
});

describe('Verificar al abrir', () => {
  it('Consentimiento íntegro', async () => {
    responderDocumento(entorno.servidor);
    const dialogo = await documentoListo();

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Íntegro'));
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(1);
    expect(visible(verificacion(dialogo))).toContain('El contenido coincide con su hash SHA-256.');
    expect(visible(verificacion(dialogo))).toContain('Verificado el 24/11/2026 a las 11:05.');
    expect(verificacion(dialogo).classList.contains('integro')).toBe(true);
    expect(dialogo.querySelector('[role="alert"]')).toBeNull();
  });

  it('Consentimiento alterado', async () => {
    entorno.cerrar();
    entorno = prepararEntorno(SESION_ADMIN);
    responderDocumento(entorno.servidor, vistaDe(VIGENTE), VERIFICACION_ALTERADA);
    const dialogo = await documentoListo();

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Alterado'));
    const aside = verificacion(dialogo);
    expect(aside.getAttribute('role')).toBe('alert');
    expect(aside.classList.contains('alterado')).toBe(true);
    expect(visible(aside)).toContain('Alterado: el contenido guardado no coincide con su hash. Avisá al administrador y no lo uses como prueba hasta revisarlo.');
    expect(aside.textContent).toContain('3f9a'.repeat(16));
    expect(aside.textContent).toContain('b7e1'.repeat(16));
    expect(visible(aside)).not.toContain('Íntegro');
  });

  it('Falla la verificación', async () => {
    responderDocumento(entorno.servidor, vistaDe(VIGENTE), json(500));
    const dialogo = await documentoListo();

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Sin verificar'));
    expect(boton(verificacion(dialogo), 'Verificar de nuevo')).toBeDefined();
    expect(visible(dialogo)).not.toContain('Íntegro');
    expect(dialogo.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
  });

  it('"Verificar de nuevo" repite el pedido y muestra el resultado nuevo', async () => {
    responderDocumento(entorno.servidor, vistaDe(VIGENTE), json(500));
    const dialogo = await documentoListo();
    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Sin verificar'));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, VERIFICACION_INTEGRA));

    boton(verificacion(dialogo), 'Verificar de nuevo').click();

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Íntegro'));
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(2);
  });

  it('una respuesta que no se puede leer no cuenta como íntegro', async () => {
    responderDocumento(entorno.servidor, vistaDe(VIGENTE), { integro: 'true' });
    const dialogo = await documentoListo();

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Sin verificar'));
    expect(visible(dialogo)).not.toContain('Íntegro');
  });

  it('con la verificación lenta, mientras tanto dice "Verificando…" y nunca "Íntegro"', async () => {
    let terminar: (r: Response) => void = () => undefined;
    responderDocumento(
      entorno.servidor,
      vistaDe(VIGENTE),
    );
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => new Promise<Response>((resolver) => (terminar = resolver)));
    const dialogo = await documentoListo();

    expect(dialogo.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
    expect(visible(verificacion(dialogo))).toContain('Verificando…');
    expect(visible(dialogo)).not.toContain('Íntegro');
    expect(visible(dialogo)).not.toContain('Alterado');
    expect(verificacion(dialogo).classList.contains('integro')).toBe(false);

    terminar(json(200, VERIFICACION_INTEGRA));
    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Íntegro'));
    expect(visible(verificacion(dialogo))).not.toContain('Verificando…');
  });

  it('si la verificación llega antes que el documento, igual se muestra al dibujarlo', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => new Promise<Response>((resolver) => (terminar = resolver)));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, VERIFICACION_INTEGRA));
    const dialogo = await abrirDocumento();
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(1));
    expect(dialogo.querySelector('.cons-verificacion')).toBeNull();
    expect(visible(dialogo)).toContain('Cargando');

    terminar(json(200, vistaDe(VIGENTE)));

    await vi.waitFor(() => expect(visible(verificacion(dialogo))).toContain('Íntegro'));
  });

  it('el pedido del documento y el de la verificación salen a la vez, antes de que llegue cualquiera', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => new Promise<Response>(() => undefined));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => new Promise<Response>(() => undefined));
    await abrirDocumento();

    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12')).toHaveLength(1));
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(1);
  });

  it('una respuesta que llega con el diálogo cerrado no dibuja nada', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => new Promise<Response>((resolver) => (terminar = resolver)));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, VERIFICACION_INTEGRA));
    const dialogo = await abrirDocumento();
    boton(dialogo, 'Cerrar').click();

    terminar(json(200, vistaDe(VIGENTE)));
    await new Promise((r) => setTimeout(r, 0));

    expect(document.body.textContent).not.toContain('Segundo párrafo');
    expect(document.querySelector('.cons-documento')).toBeNull();
    expect(dialogosAbiertos()).toHaveLength(0);
  });
});
