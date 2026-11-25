import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, json } from './apoyo.ts';
import {
  dialogosAbiertos,
  plantillaVista,
  prepararEntorno,
  REVOCADO,
  resumen,
  SESION_ANA,
  TEXTO_V3,
  TITULO_CONDUCTO,
  TITULO_EXTRACCION,
  ultimoDialogo,
  visible,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 5.1: elegir la plantilla para firmar y el aviso de doble firma (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
  // Hoy es el 2026-11-24 y el listado del paciente empieza sin firmas de la plantilla 4.
  entorno.consentimientos = [REVOCADO];
});

afterEach(() => {
  entorno.cerrar();
});

async function abrirEleccion(): Promise<HTMLDialogElement> {
  await entorno.abrirFicha();
  await entorno.seccionLista();
  boton(entorno.seccion(), 'Firmar consentimiento').click();
  return ultimoDialogo();
}

function opcion(dialogo: HTMLElement, titulo: string): HTMLInputElement {
  const etiqueta = [...dialogo.querySelectorAll<HTMLLabelElement>('label.cons-opcion')].find((l) => l.textContent?.includes(titulo));
  const radio = etiqueta?.querySelector('input');
  if (radio === null || radio === undefined) {
    throw new Error(`No existe la opción ${titulo}`);
  }
  return radio;
}

function pantallaDeFirma(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.firma-pantalla');
}

/** Elige la plantilla y "Continuar"; espera a que la vista de firma o la pregunta se abran. */
async function continuar(dialogo: HTMLElement, titulo = TITULO_EXTRACCION): Promise<void> {
  const radio = opcion(dialogo, titulo);
  radio.checked = true;
  radio.dispatchEvent(new Event('change', { bubbles: true }));
  boton(dialogo, 'Continuar').click();
}

describe('Elegir la plantilla para firmar', () => {
  it('Elegir una plantilla', async () => {
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(1);
    expect(entorno.servidor.ultimo('GET', '/consentimientos/plantillas').query.has('incluirInactivas')).toBe(false);
    expect(dialogo.querySelector('h2')?.textContent).toBe('Elegí el consentimiento');
    expect(visible(opcion(dialogo, TITULO_EXTRACCION).closest('label') as HTMLElement)).toContain('Versión 3');
    expect(visible(opcion(dialogo, TITULO_CONDUCTO).closest('label') as HTMLElement)).toContain('Versión 1');

    await continuar(dialogo);

    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/4')).toHaveLength(1);
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(2);
    expect(pantallaDeFirma()?.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
    expect(visible(pantallaDeFirma() as HTMLElement)).toContain('Versión 3');
  });

  it('elige la segunda plantilla y pide esa', async () => {
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, plantillaVista(6, true, 1, { titulo: TITULO_CONDUCTO })));
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));

    await continuar(dialogo, TITULO_CONDUCTO);

    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/6')).toHaveLength(1);
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/4')).toHaveLength(0);
    expect(pantallaDeFirma()?.querySelector('h2')?.textContent).toBe(TITULO_CONDUCTO);
  });

  it('la primera plantilla viene elegida y las opciones forman un grupo con nombre', async () => {
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));

    expect(opcion(dialogo, TITULO_EXTRACCION).checked).toBe(true);
    expect(opcion(dialogo, TITULO_CONDUCTO).checked).toBe(false);
    expect(dialogo.querySelector('fieldset legend')?.textContent).toBe('Plantillas activas');
  });

  it('Sin plantillas activas', async () => {
    entorno.servidor.responder('GET /consentimientos/plantillas', () => json(200, []));
    const dialogo = await abrirEleccion();

    await vi.waitFor(() =>
      expect(visible(dialogo)).toContain(
        'No hay plantillas activas. Pedile al administrador que cargue una en Configuración → Consentimientos.',
      ),
    );
    expect([...dialogo.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Cerrar']);
    boton(dialogo, 'Cerrar').click();
    expect(dialogosAbiertos()).toHaveLength(0);
  });

  it('La plantilla se desactivó antes de abrirla', async () => {
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () => json(404, { codigo: 'RECURSO_NO_ENCONTRADO' }));
    entorno.servidor.responder('GET /consentimientos/plantillas', () => json(200, [ACTIVA_UNICA]));

    await continuar(dialogo);

    await vi.waitFor(() => expect(visible(dialogo)).toContain('Esa plantilla ya no está disponible. Elegí otra.'));
    await vi.waitFor(() => expect(entorno.servidor.a('GET', '/consentimientos/plantillas')).toHaveLength(2));
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(1));
    expect(pantallaDeFirma()).toBeNull();
    expect(dialogo.open).toBe(true);
  });

  it('si no se puede pedir la plantilla o el listado, avisa y no abre la firma', async () => {
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(500));

    await continuar(dialogo);

    await vi.waitFor(() => expect(visible(dialogo)).toContain(MENSAJE_ERROR_GENERICO));
    expect(pantallaDeFirma()).toBeNull();
    expect(dialogo.open).toBe(true);
  });

  it('"Cancelar" cierra sin pedir nada más y el foco vuelve a "Firmar consentimiento"', async () => {
    const dialogo = await abrirEleccion();
    await vi.waitFor(() => expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(2));

    boton(dialogo, 'Cancelar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(pantallaDeFirma()).toBeNull();
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/4')).toHaveLength(0);
    expect(document.activeElement).toBe(boton(entorno.seccion(), 'Firmar consentimiento'));
  });

  it('si fallan las plantillas, avisa dentro del diálogo', async () => {
    entorno.servidor.responder('GET /consentimientos/plantillas', () => json(500));
    const dialogo = await abrirEleccion();

    await vi.waitFor(() => expect(visible(dialogo)).toContain(MENSAJE_ERROR_GENERICO));
    expect(dialogo.querySelectorAll('label.cons-opcion')).toHaveLength(0);
  });
});

const ACTIVA_UNICA = { id: 6, activa: true, versionActual: 1, titulo: TITULO_CONDUCTO, actualizadaEn: '2026-11-20T09:00:00-03:00' };

describe('Aviso antes de firmar dos veces', () => {
  const PREGUNTA = '“Consentimiento para extracción dentaria”, versión 3, quedó firmado hoy a las 10:30 y está vigente. ¿Hacerlo firmar otra vez?';

  async function elegirExtraccion(): Promise<HTMLDialogElement> {
    const eleccion = await abrirEleccion();
    await vi.waitFor(() => expect(eleccion.querySelectorAll('label.cons-opcion')).toHaveLength(2));
    await continuar(eleccion);
    return eleccion;
  }

  it('Ya firmó hoy la misma versión', async () => {
    entorno.consentimientos = [VIGENTE, REVOCADO];
    await elegirExtraccion();

    const pregunta = await vi.waitFor(() => {
      const abierto = dialogosAbiertos().find((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy');
      expect(abierto).toBeDefined();
      return abierto as HTMLDialogElement;
    });
    expect(visible(pregunta)).toContain(PREGUNTA);
    expect([...pregunta.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['No firmar', 'Firmar otra vez']);
    expect(pantallaDeFirma()).toBeNull();
    expect(document.activeElement).toBe(boton(pregunta, 'No firmar'));

    boton(pregunta, 'Firmar otra vez').click();
    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(pantallaDeFirma()?.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
  });

  it('No firmar otra vez', async () => {
    entorno.consentimientos = [VIGENTE];
    await elegirExtraccion();
    const pregunta = await vi.waitFor(() => {
      const abierto = dialogosAbiertos().find((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy');
      expect(abierto).toBeDefined();
      return abierto as HTMLDialogElement;
    });
    const antes = entorno.servidor.pedidos.length;

    boton(pregunta, 'No firmar').click();

    expect(pregunta.open).toBe(false);
    expect(pantallaDeFirma()).toBeNull();
    expect(entorno.servidor.pedidos.filter((p) => p.metodo === 'POST')).toHaveLength(0);
    expect(entorno.servidor.pedidos).toHaveLength(antes);
  });

  it('Una firma revocada no avisa', async () => {
    entorno.consentimientos = [resumen({ id: 12, estado: 'REVOCADO' })];
    await elegirExtraccion();

    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(dialogosAbiertos().some((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy')).toBe(false);
  });

  it('Una firma de otro día no avisa', async () => {
    entorno.consentimientos = [resumen({ id: 12, firmadoEn: '2026-11-20T10:30:00-03:00' })];
    await elegirExtraccion();

    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(dialogosAbiertos().some((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy')).toBe(false);
  });

  it('una firma de otra versión no avisa', async () => {
    entorno.consentimientos = [resumen({ id: 12, versionNumero: 2 })];
    await elegirExtraccion();

    await vi.waitFor(() => expect(pantallaDeFirma()).not.toBeNull());
    expect(dialogosAbiertos().some((d) => d.querySelector('h2')?.textContent === 'Ya firmó este consentimiento hoy')).toBe(false);
  });
});
