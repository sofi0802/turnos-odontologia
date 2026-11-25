import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, errorDeCampo, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  dialogosAbiertos,
  enviarFirma,
  escribirTexto,
  plantillaVista,
  presionarEscape,
  prepararEntorno,
  resumen,
  SESION_ANA,
  TITULO_EXTRACCION,
  ultimoDialogo,
  vistaDe,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 5.3: firmar, los rechazos del servidor, cancelar y el cierre por el navegador (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

const RUTA_FIRMA = '/pacientes/1/consentimientos';

function firmas(): number {
  return entorno.servidor.a('POST', RUTA_FIRMA).length;
}

function pantallaDeFirma(): HTMLElement | null {
  return document.querySelector<HTMLElement>('dialog.firma-pantalla');
}

function bloqueo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.bloqueo-pantalla');
}

/** La API que firma: guarda el consentimiento nuevo (id 13) y lo suma al listado, como el backend. */
function responderFirma(): void {
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => {
    const nuevo = resumen({ id: 13, firmadoEn: '2026-11-24T11:00:00-03:00' });
    entorno.consentimientos = [nuevo, ...entorno.consentimientos];
    return json(201, vistaDe(nuevo));
  });
}

describe('Firmar el consentimiento', () => {
  it('Firma correcta', async () => {
    responderFirma();
    const pantalla = await abrirVistaDeFirma(entorno);

    completarFirma(pantalla, { nombre: 'Paciente Uno', dni: '99.000.001' });
    enviarFirma(pantalla);

    await vi.waitFor(() => expect(firmas()).toBe(1));
    expect(entorno.servidor.ultimo('POST', RUTA_FIRMA).cuerpo).toEqual({
      plantillaId: 4,
      versionNumero: 3,
      firmanteNombre: 'Paciente Uno',
      firmanteDni: '99.000.001',
      aceptado: true,
    });
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(pantallaDeFirma()).toBeNull();
    expect(visible(bloqueo() as HTMLElement)).toContain('Devolvé el equipo al profesional');
    expect(visible(bloqueo() as HTMLElement)).toContain('Listo: la firma quedó registrada.');
  });

  it('Un solo envío', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => new Promise<Response>((resolver) => (terminar = resolver)));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);

    boton(pantalla, 'Firmar').click();
    boton(pantalla, 'Firmando…').click();
    pantalla.querySelector('form')?.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
    enviarFirma(pantalla);

    await vi.waitFor(() => expect(firmas()).toBe(1));
    const firmar = boton(pantalla, 'Firmando…');
    expect(firmar.disabled).toBe(true);
    expect(boton(pantalla, 'Cancelar').disabled).toBe(true);
    terminar(json(201, vistaDe(resumen({ id: 13 }))));
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(firmas()).toBe(1);
  });

  it('Volver a la ficha después de firmar', async () => {
    responderFirma();
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());

    // El desbloqueo se prueba en la tarea 6.1: acá solo, que la ficha ya no está en la página.
    expect(document.body.textContent).not.toContain('Paciente Uno');
    expect(document.body.textContent).not.toContain('99000001');
    expect(document.body.textContent).not.toContain(TITULO_EXTRACCION);
  });
});

describe('DNI que no coincide', () => {
  it('El DNI no es el del paciente', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(422, { codigo: 'FIRMANTE_NO_COINCIDE', detail: 'no coincide' }));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { dni: '99000002' });

    enviarFirma(pantalla);

    await vi.waitFor(() => expect(errorDeCampo(pantalla, 'DNI')).toBe('El DNI no coincide con el del paciente. Revisalo.'));
    expect(pantalla.isConnected).toBe(true);
    expect(pantalla.open).toBe(true);
    expect(campo<HTMLInputElement>(pantalla, 'DNI').value).toBe('99000002');
    expect(campo<HTMLInputElement>(pantalla, 'Nombre completo').value).toBe('Paciente Uno');
    expect(pantalla.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true);
    // Fuera del campo, nada de la vista repite el DNI escrito ni el del paciente.
    expect(pantalla.textContent).not.toContain('99000002');
    expect(pantalla.textContent).not.toContain('99000001');
    expect(pantalla.querySelector('[role="alert"]')?.textContent ?? '').not.toMatch(/9900000/);
    expect(boton(pantalla, 'Firmar').disabled).toBe(false);
    expect(bloqueo()).toBeNull();
  });

  it('con el DNI corregido, el mensaje se limpia y se puede volver a firmar', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(422, { codigo: 'FIRMANTE_NO_COINCIDE', detail: 'x' }));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { dni: '99000002' });
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(errorDeCampo(pantalla, 'DNI')).not.toBe(''));
    responderFirma();

    escribirTexto(campo(pantalla, 'DNI'), '99000001');
    enviarFirma(pantalla);

    await vi.waitFor(() => expect(bloqueo()).not.toBeNull());
    expect(firmas()).toBe(2);
  });
});

describe('El texto cambió mientras se leía', () => {
  it('Versión desactualizada', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(409, { codigo: 'VERSION_DESACTUALIZADA', detail: 'cambió' }));
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () =>
      json(200, plantillaVista(4, true, 4, { texto: 'Texto nuevo de la versión cuatro.' })),
    );
    completarFirma(pantalla);

    enviarFirma(pantalla);

    await vi.waitFor(() => expect(pantalla.querySelector('.cons-texto')?.textContent).toBe('Texto nuevo de la versión cuatro.'));
    expect(entorno.servidor.a('GET', '/consentimientos/plantillas/4')).toHaveLength(2);
    expect(visible(pantalla)).toContain('Versión 4');
    expect(pantalla.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false);
    expect(visible(pantalla)).toContain('El texto del consentimiento cambió mientras se leía. Leé la versión nueva antes de firmar.');
    expect(campo<HTMLInputElement>(pantalla, 'Nombre completo').value).toBe('Paciente Uno');
    expect(campo<HTMLInputElement>(pantalla, 'DNI').value).toBe('99.000.001');
    expect(pantalla.open).toBe(true);

    // La próxima firma envía la versión 4, y solo si el paciente vuelve a aceptar.
    responderFirma();
    enviarFirma(pantalla);
    expect(firmas()).toBe(1);
    expect(errorDeCampo(pantalla, 'DNI')).toBe('');
    expect(pantalla.querySelector('.campo-error:not(:empty)')?.textContent).toBe('Para firmar, marcá que leíste y aceptás el consentimiento.');
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(firmas()).toBe(2));
    expect(entorno.servidor.ultimo('POST', RUTA_FIRMA).cuerpo).toMatchObject({ plantillaId: 4, versionNumero: 4 });
  });

  it('el aviso se anuncia y recibe el foco', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(409, { codigo: 'VERSION_DESACTUALIZADA', detail: 'x' }));
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, plantillaVista(4, true, 4, { texto: 'Otro' })));
    completarFirma(pantalla);

    enviarFirma(pantalla);

    await vi.waitFor(() => expect(pantalla.querySelector('.cons-texto')?.textContent).toBe('Otro'));
    const aviso = pantalla.querySelector<HTMLElement>('[role="alert"]');
    expect(aviso?.textContent).toContain('cambió mientras se leía');
    expect(document.activeElement).toBe(aviso);
  });
});

describe('Plantilla desactivada durante la firma', () => {
  async function conPlantillaInactiva(): Promise<HTMLDialogElement> {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => json(409, { codigo: 'PLANTILLA_INACTIVA', detail: 'x' }));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(visible(pantalla)).toContain('Esta plantilla se desactivó y ya no se puede firmar.'));
    return pantalla;
  }

  it('Plantilla inactiva', async () => {
    const pantalla = await conPlantillaInactiva();

    expect([...pantalla.querySelectorAll('button')].map((b) => b.textContent)).toEqual(['Terminar']);
    expect(pantalla.querySelector('input')).toBeNull();
    expect(pantalla.querySelector('[role="alert"]')?.textContent).toBe('Esta plantilla se desactivó y ya no se puede firmar.');
    expect(firmas()).toBe(1);
  });

  it('Terminar después de una plantilla inactiva', async () => {
    const pantalla = await conPlantillaInactiva();

    boton(pantalla, 'Terminar').click();

    expect(pantallaDeFirma()).toBeNull();
    expect(visible(bloqueo() as HTMLElement)).toContain('La firma no se completó.');
    expect(firmas()).toBe(1);
  });
});

describe('Cancelar la firma', () => {
  it('Cancelar con datos escritos', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Nombre Escrito Ficticio', dni: '12345678', acepto: false });

    boton(pantalla, 'Cancelar').click();
    const pregunta = await ultimoDialogo();
    expect(pregunta).not.toBe(pantalla);
    expect(pregunta.querySelector('h2')?.textContent).toBe('¿Cancelar la firma?');
    expect(pregunta.querySelector('.dialogo-resumen')?.textContent).toContain('Lo escrito se descarta.');
    boton(pregunta, 'Cancelar la firma').click();

    expect(firmas()).toBe(0);
    expect(pantallaDeFirma()).toBeNull();
    expect(dialogosAbiertos().filter((d) => d !== pantalla)).toHaveLength(0);
    expect(document.body.innerHTML).not.toContain('Nombre Escrito Ficticio');
    expect(document.body.innerHTML).not.toContain('12345678');
    expect(visible(bloqueo() as HTMLElement)).toContain('La firma se canceló. No se guardó nada.');
  });

  it('Cancelar sin datos', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const evento = presionarEscape(pantalla);

    expect(evento.defaultPrevented).toBe(true);
    expect(dialogosAbiertos()).toHaveLength(0);
    expect(pantallaDeFirma()).toBeNull();
    expect(visible(bloqueo() as HTMLElement)).toContain('La firma se canceló. No se guardó nada.');
    expect(firmas()).toBe(0);
  });

  it('el botón "Cancelar" sin datos también cierra sin preguntar', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    boton(pantalla, 'Cancelar').click();

    expect(dialogosAbiertos()).toHaveLength(0);
    expect(visible(bloqueo() as HTMLElement)).toContain('La firma se canceló.');
  });

  it('Seguir firmando', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Paciente Uno', dni: '', acepto: false });
    boton(pantalla, 'Cancelar').click();
    const pregunta = await ultimoDialogo();

    boton(pregunta, 'Seguir firmando').click();

    expect(pregunta.open).toBe(false);
    expect(pantalla.open).toBe(true);
    expect(campo<HTMLInputElement>(pantalla, 'Nombre completo').value).toBe('Paciente Uno');
    expect(bloqueo()).toBeNull();
    expect(document.activeElement).toBe(boton(pantalla, 'Cancelar'));
  });

  it('marcar solo la casilla ya cuenta como datos escritos', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: '', dni: '', acepto: true });

    boton(pantalla, 'Cancelar').click();

    expect(visible(await ultimoDialogo())).toContain('¿Cancelar la firma?');
  });

  it('Escape con datos pregunta y no cierra la vista', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Paciente Uno', dni: '', acepto: false });

    const evento = presionarEscape(pantalla);

    expect(evento.defaultPrevented).toBe(true);
    expect(pantalla.open).toBe(true);
    expect(visible(await ultimoDialogo())).toContain('¿Cancelar la firma?');
  });
});

describe('Cierre de la vista por el navegador', () => {
  it('El navegador cierra la vista', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Nombre Escrito Ficticio', dni: '12345678', acepto: true });

    pantalla.close();

    expect(firmas()).toBe(0);
    expect(document.body.innerHTML).not.toContain('Nombre Escrito Ficticio');
    expect(document.body.innerHTML).not.toContain('12345678');
    expect(visible(bloqueo() as HTMLElement)).toContain('Devolvé el equipo al profesional');
    expect(visible(bloqueo() as HTMLElement)).toContain('La firma se canceló. No se guardó nada.');
  });

  it('Escape durante el envío', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => new Promise<Response>(() => undefined));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(boton(pantalla, 'Firmando…').disabled).toBe(true));

    const evento = presionarEscape(pantalla);

    expect(evento.defaultPrevented).toBe(true);
    expect(pantalla.open).toBe(true);
    expect(boton(pantalla, 'Cancelar').disabled).toBe(true);
    expect(bloqueo()).toBeNull();
    expect(dialogosAbiertos()).toEqual([pantalla]);
  });

  it('"Cancelar" no hace nada durante el envío', async () => {
    entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => new Promise<Response>(() => undefined));
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(boton(pantalla, 'Firmando…').disabled).toBe(true));

    boton(pantalla, 'Cancelar').click();

    expect(pantalla.open).toBe(true);
    expect(dialogosAbiertos()).toEqual([pantalla]);
  });
});
