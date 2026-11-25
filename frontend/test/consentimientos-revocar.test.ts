import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, campo, errorDeCampo, json } from './apoyo.ts';
import { enviar } from './apoyo-configuracion.ts';
import {
  dialogosAbiertos,
  escribirTexto,
  prepararEntorno,
  REVOCADO,
  resumen,
  SESION_ANA,
  TITULO_BLANQUEAMIENTO,
  TITULO_EXTRACCION,
  ultimoDialogo,
  vistaDe,
  visible,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 4.3: revocar un consentimiento (spec `frontend-consentimientos`). Datos ficticios. */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
});

const REVOCAR_EXTRACCION = `Revocar ${TITULO_EXTRACCION} del 24/11/2026`;
const MOTIVO = 'El paciente decidió no hacer el tratamiento';

async function abrirRevocacion(): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), REVOCAR_EXTRACCION).click();
  return ultimoDialogo();
}

/** La API que revoca: cambia el estado en el listado, como lo haría el backend. */
function responderRevocacion(): void {
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', (pedido) => {
    entorno.consentimientos = entorno.consentimientos.map((c) => (c.id === 12 ? { ...c, estado: 'REVOCADO' as const } : c));
    return json(200, vistaDe({ ...VIGENTE, estado: 'REVOCADO' }, { motivo: (pedido.cuerpo as { motivo: string }).motivo }));
  });
}

describe('Revocar un consentimiento', () => {
  it('Revocar con motivo', async () => {
    responderRevocacion();
    const dialogo = await abrirRevocacion();

    expect(dialogo.querySelector('h2')?.textContent).toBe('¿Revocar el consentimiento?');
    expect(visible(dialogo)).toContain(
      `${TITULO_EXTRACCION}, firmado el 24/11/2026. El documento no se borra ni cambia: queda como revocado, con tu nombre, la fecha y el motivo.`,
    );
    expect(campo<HTMLTextAreaElement>(dialogo, 'Motivo').value).toBe('');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), MOTIVO);
    enviar(dialogo);

    await vi.waitFor(() => expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(1));
    expect(entorno.servidor.ultimo('POST', '/pacientes/1/consentimientos/12/revocacion').cuerpo).toEqual({ motivo: MOTIVO });
    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('Consentimiento revocado.'));
    expect(dialogosAbiertos()).toHaveLength(0);
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(2));
    await vi.waitFor(() => {
      const fila = entorno.seccion().querySelector('.cons-lista > li') as HTMLElement;
      expect(fila.querySelector('.etiqueta')?.textContent).toBe('Revocado');
    });
    const primera = entorno.seccion().querySelector('.cons-lista > li') as HTMLElement;
    expect(primera.textContent).not.toContain('Revocar');
    expect(entorno.seccion().querySelector('[role="status"]')?.textContent).toBe('Consentimiento revocado.');
  });

  it('Revocar sin motivo', async () => {
    responderRevocacion();
    const dialogo = await abrirRevocacion();

    enviar(dialogo);

    expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(0);
    expect(errorDeCampo(dialogo, 'Motivo')).toBe('Escribí el motivo de la revocación.');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), '    ');
    enviar(dialogo);
    expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(0);
    expect(dialogo.open).toBe(true);
  });

  it('un motivo de más de 200 caracteres no se envía', async () => {
    responderRevocacion();
    const dialogo = await abrirRevocacion();
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), 'm'.repeat(201));

    enviar(dialogo);

    expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(0);
    expect(errorDeCampo(dialogo, 'Motivo')).toBe('El motivo supera los 200 caracteres.');
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), 'm'.repeat(200));
    enviar(dialogo);
    await vi.waitFor(() => expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(1));
  });

  it('el motivo tiene su contador "N/200" ligado al campo', async () => {
    const dialogo = await abrirRevocacion();
    const motivo = campo<HTMLTextAreaElement>(dialogo, 'Motivo');

    escribirTexto(motivo, 'abc');

    expect(document.getElementById(`${motivo.id}-contador`)?.textContent).toBe('3/200');
    expect(motivo.getAttribute('aria-describedby')).toContain(`${motivo.id}-contador`);
  });

  it('Ya estaba revocado', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', () =>
      json(409, { codigo: 'CONSENTIMIENTO_YA_REVOCADO', detail: 'ya' }),
    );
    const dialogo = await abrirRevocacion();
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), MOTIVO);

    enviar(dialogo);

    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('El consentimiento ya estaba revocado.'));
    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('El consentimiento ya estaba revocado.');
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(2));
    expect(dialogosAbiertos()).toHaveLength(0);
  });

  it('Un revocado no se revoca', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const segunda = entorno.seccion().querySelectorAll<HTMLElement>('.cons-lista > li')[1] as HTMLElement;
    const botones = [...segunda.querySelectorAll('button')].map((b) => b.textContent);

    expect(botones).toEqual(['Ver']);
    expect(segunda.textContent).toContain(TITULO_BLANQUEAMIENTO);
    expect(segunda.textContent).not.toContain('Revocar');
    expect(REVOCADO.estado).toBe('REVOCADO');
  });

  it('un error al revocar queda dentro del diálogo, con lo escrito', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', () => json(500));
    const dialogo = await abrirRevocacion();
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), MOTIVO);

    enviar(dialogo);

    await vi.waitFor(() => expect(visible(dialogo)).toContain(MENSAJE_ERROR_GENERICO));
    expect(dialogo.open).toBe(true);
    expect(campo<HTMLTextAreaElement>(dialogo, 'Motivo').value).toBe(MOTIVO);
  });

  it('un solo envío por vez: el botón queda deshabilitado mientras se revoca', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', () => new Promise<Response>((resolver) => (terminar = resolver)));
    const dialogo = await abrirRevocacion();
    escribirTexto(campo<HTMLTextAreaElement>(dialogo, 'Motivo'), MOTIVO);

    enviar(dialogo);
    enviar(dialogo);

    expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(1);
    terminar(json(200, vistaDe(resumen({ id: 12, estado: 'REVOCADO' }))));
    await vi.waitFor(() => expect(dialogosAbiertos()).toHaveLength(0));
  });

  it('"Cancelar" cierra sin enviar y el foco vuelve a "Revocar"', async () => {
    const dialogo = await abrirRevocacion();

    boton(dialogo, 'Cancelar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(entorno.servidor.a('POST', '/pacientes/1/consentimientos/12/revocacion')).toHaveLength(0);
    expect(document.activeElement).toBe(boton(entorno.seccion(), REVOCAR_EXTRACCION));
  });
});
