import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearSeccionPresupuestos, type ModoPresupuestos, type SeccionPresupuestos } from '../src/presupuestos/seccion.ts';
import { mensajeDeError } from '../src/shared/ui/mensajes.ts';
import { boton, json, ServidorFalso, texto } from './apoyo.ts';
import { dialogoAbierto, enviar } from './apoyo-configuracion.ts';
import { PRESUPUESTO_40, PRESUPUESTO_41, responderPresupuestos, sinDatosClinicos } from './apoyo-presupuestos.ts';

/** C-50, grupo 3: lista, detalle, aceptar y rechazar de la sección "Presupuestos" (spec `frontend-presupuestos`). */

let seccion: SeccionPresupuestos | null = null;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
});

afterEach(() => {
  seccion?.destruir();
  seccion = null;
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  vi.useRealTimers();
});

function servidorCon(): ServidorFalso {
  const servidor = new ServidorFalso();
  responderPresupuestos(servidor);
  return servidor;
}

async function montar(servidor: ServidorFalso, modo: ModoPresupuestos = 'odontologo', pacienteId = 1): Promise<HTMLElement> {
  seccion = crearSeccionPresupuestos({
    pacienteId,
    nombrePaciente: pacienteId === 1 ? 'Paciente Uno' : 'Paciente Dos',
    modo,
    api: servidor.api,
    mensajeDeError,
    vigente: () => true,
  });
  document.body.replaceChildren(seccion.contenedor);
  await vi.waitFor(() => expect(seccion?.contenedor.getAttribute('aria-busy')).toBeNull());
  return seccion.contenedor;
}

function tarjetas(raiz: HTMLElement): HTMLElement[] {
  return [...raiz.querySelectorAll<HTMLElement>('.pre-presupuesto')];
}

function tarjeta(raiz: HTMLElement, fecha: string): HTMLElement {
  const encontrada = tarjetas(raiz).find((t) => texto(t).includes(`Presupuesto del ${fecha}`));
  if (encontrada === undefined) {
    throw new Error(`No existe el presupuesto del ${fecha}`);
  }
  return encontrada;
}

describe('Lista de presupuestos del paciente', () => {
  it('Presupuesto pendiente con obra social', async () => {
    const raiz = await montar(servidorCon());
    const t = tarjeta(raiz, '24/11/2026');

    expect(raiz.querySelector('h2')?.textContent).toBe('Presupuestos');
    expect(t.querySelector('.etiqueta')?.textContent).toBe('Pendiente');
    for (const dato of ['Vence el 24/12/2026', 'Dra. Ana Ejemplo', 'Obra Social Ejemplo · Plan Base · N.º OSE-000001', '$ 40.000,00', '$ 27.500,00', '$ 12.500,00']) {
      expect(texto(t)).toContain(dato);
    }
  });

  it('Presupuesto vencido', async () => {
    const raiz = await montar(servidorCon());

    expect(tarjeta(raiz, '01/10/2026').querySelector('.etiqueta')?.textContent).toBe('Vencido');
    expect(texto(tarjeta(raiz, '01/10/2026'))).not.toContain('Pendiente');
  });

  it('Presupuesto particular', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/2/presupuestos', () => json(200, [{ ...PRESUPUESTO_41, particular: true, cobertura: null }]));
    const raiz = await montar(servidor, 'odontologo', 2);

    expect(texto(tarjetas(raiz)[0] as HTMLElement)).toContain('Particular');
  });

  it('Sin presupuestos', async () => {
    const raiz = await montar(servidorCon(), 'odontologo', 2);

    expect(texto(raiz)).toContain('Este paciente no tiene presupuestos.');
  });

  it('en el orden de la API', async () => {
    const raiz = await montar(servidorCon());

    expect(tarjetas(raiz).map((t) => t.querySelector('.pre-cabecera span')?.textContent)).toEqual([
      'Presupuesto del 24/11/2026',
      'Presupuesto del 01/10/2026',
    ]);
  });

  it('si falla muestra el error y "Reintentar" en la sección', async () => {
    const servidor = servidorCon();
    let pedidos = 0;
    servidor.responder('GET /pacientes/1/presupuestos', () => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, [PRESUPUESTO_41]);
    });
    const raiz = await montar(servidor);

    expect(raiz.querySelector('[role="alert"]:not(:empty)')).not.toBeNull();
    boton(raiz, 'Reintentar').click();

    await vi.waitFor(() => expect(tarjetas(raiz)).toHaveLength(1));
  });
});

describe('Detalle de un presupuesto', () => {
  it('Detalle de los ítems', async () => {
    const raiz = await montar(servidorCon());
    const t = tarjeta(raiz, '24/11/2026');
    const ver = boton(t, 'Ver detalle');
    expect(ver.getAttribute('aria-expanded')).toBe('false');

    ver.click();

    expect(ver.getAttribute('aria-expanded')).toBe('true');
    const limpieza = [...t.querySelectorAll<HTMLElement>('.pre-renglon')].find((r) => texto(r).includes('Limpieza (profilaxis)')) as HTMLElement;
    for (const dato of ['N-0201', 'Cantidad 1', '50 %', '$ 25.000,00', '$ 12.500,00']) {
      expect(texto(limpieza)).toContain(dato);
    }
    expect(texto(limpieza).match(/\$ 12\.500,00/g)).toHaveLength(2);
  });

  it('muestra la pieza al odontólogo', async () => {
    const raiz = await montar(servidorCon());
    const t = tarjeta(raiz, '01/10/2026');

    boton(t, 'Ver detalle').click();

    expect(texto(t)).toContain('Pieza 36');
  });

  it('"Ocultar detalle" lo esconde', async () => {
    const raiz = await montar(servidorCon());
    const t = tarjeta(raiz, '24/11/2026');
    boton(t, 'Ver detalle').click();

    boton(t, 'Ocultar detalle').click();

    expect(t.querySelector('.pre-detalle')).toBeNull();
  });
});

describe('Aceptar o rechazar un presupuesto', () => {
  it('Aceptar un presupuesto pendiente', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/aceptar', () => json(200, { ...PRESUPUESTO_41, estado: 'ACEPTADO' }));
    const raiz = await montar(servidor);

    boton(tarjeta(raiz, '24/11/2026'), 'Aceptar').click();
    const dialogo = await dialogoAbierto();
    expect(dialogo.querySelector('h2')?.textContent).toBe('¿Aceptar el presupuesto?');
    expect(texto(dialogo)).toContain('No se puede deshacer.');
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos/41/aceptar')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/presupuestos')).toHaveLength(2));
    await vi.waitFor(() => expect(raiz.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('Presupuesto aceptado.'));
  });

  it('rechazar pide confirmación de peligro', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/rechazar', () => json(200, { ...PRESUPUESTO_40, estado: 'RECHAZADO' }));
    const raiz = await montar(servidor);

    boton(tarjeta(raiz, '01/10/2026'), 'Rechazar').click();
    const dialogo = await dialogoAbierto();
    expect(boton(dialogo, 'Rechazar presupuesto').classList.contains('btn-peligro')).toBe(true);
    enviar(dialogo);

    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos/40/rechazar')).toHaveLength(1));
    await vi.waitFor(() => expect(raiz.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('Presupuesto rechazado.'));
  });

  it('Cancelar la confirmación', async () => {
    const servidor = servidorCon();
    const raiz = await montar(servidor);
    const aceptar = boton(tarjeta(raiz, '24/11/2026'), 'Aceptar');

    aceptar.click();
    boton(await dialogoAbierto(), 'Cancelar').click();

    expect(servidor.pedidos.filter((p) => p.metodo === 'POST')).toHaveLength(0);
    expect(document.activeElement).toBe(aceptar);
  });

  it('Un vencido solo se rechaza', async () => {
    const raiz = await montar(servidorCon());
    const botones = [...tarjeta(raiz, '01/10/2026').querySelectorAll('button')].map((b) => b.textContent);

    expect(botones).toContain('Rechazar');
    expect(botones).not.toContain('Aceptar');
  });

  it('un aceptado no tiene acciones', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [{ ...PRESUPUESTO_41, estado: 'ACEPTADO' }]));
    const raiz = await montar(servidor);
    const botones = [...(tarjetas(raiz)[0] as HTMLElement).querySelectorAll('button')].map((b) => b.textContent);

    expect(botones).toEqual(['Ver detalle']);
  });
});

describe('Rechazos al aceptar o rechazar', () => {
  it('Venció mientras estaba abierto', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/aceptar', () => json(409, { codigo: 'PRESUPUESTO_VENCIDO' }));
    const raiz = await montar(servidor);

    boton(tarjeta(raiz, '24/11/2026'), 'Aceptar').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/presupuestos')).toHaveLength(2));
    expect([...raiz.querySelectorAll('[role="alert"]')].map((a) => a.textContent)).toContain(
      'El presupuesto venció: ya no se puede aceptar. Emití uno nuevo.',
    );
  });

  it('Otra persona ya lo cerró', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/rechazar', () => json(409, { codigo: 'PRESUPUESTO_CERRADO' }));
    const raiz = await montar(servidor);

    boton(tarjeta(raiz, '01/10/2026'), 'Rechazar').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.open).toBe(false));
    expect([...raiz.querySelectorAll('[role="alert"]')].map((a) => a.textContent)).toContain('Ese presupuesto ya estaba aceptado o rechazado.');
  });

  it('otro error deja la confirmación abierta con el mensaje', async () => {
    const servidor = servidorCon();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/aceptar', () => json(500));
    const raiz = await montar(servidor);

    boton(tarjeta(raiz, '24/11/2026'), 'Aceptar').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);

    await vi.waitFor(() => expect(dialogo.querySelector('[role="alert"]')?.textContent).not.toBe(''));
    expect(dialogo.open).toBe(true);
  });
});

describe('Modos de solo lectura', () => {
  it('en modo lectura no hay acciones ni "Nuevo presupuesto" y sí el aviso', async () => {
    const servidor = servidorCon();
    const raiz = await montar(servidor, 'lectura');

    expect(texto(raiz)).toContain('Solo lectura: los presupuestos los arma el odontólogo.');
    const botones = [...raiz.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones).not.toContain('Nuevo presupuesto');
    expect(botones).not.toContain('Aceptar');
    expect(botones).not.toContain('Rechazar');
    expect(servidor.a('GET', '/pacientes/1/presupuestos/sugerencias')).toHaveLength(0);
  });

  it('en modo recepción no dibuja pieza ni cara aunque la respuesta las trajera', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [PRESUPUESTO_40]));
    const raiz = await montar(servidor, 'recepcion');

    boton(tarjetas(raiz)[0] as HTMLElement, 'Ver detalle').click();

    expect(texto(raiz)).toContain('Extracción simple');
    expect(texto(raiz)).not.toContain('Pieza');
    expect(texto(raiz)).not.toContain('36');
  });

  it('en modo recepción con la respuesta sin pieza ni cara', async () => {
    const servidor = servidorCon();
    servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [sinDatosClinicos(PRESUPUESTO_40)]));
    const raiz = await montar(servidor, 'recepcion');

    boton(tarjetas(raiz)[0] as HTMLElement, 'Ver detalle').click();

    expect(texto(raiz)).toContain('$ 30.000,00');
    expect(texto(raiz)).not.toContain('Pieza');
  });
});

describe('Presupuestos accesibles', () => {
  it('Importes con rótulo', async () => {
    const raiz = await montar(servidorCon());
    const totales = tarjeta(raiz, '24/11/2026').querySelector('.pre-totales') as HTMLElement;

    expect([...totales.querySelectorAll('dt')].map((dt) => dt.textContent)).toEqual(['Precio', 'Cobertura', 'Copago']);
    expect(raiz.getAttribute('aria-labelledby')).toBe(raiz.querySelector('h2')?.id);
  });

  it('destruir() descarta las respuestas que llegan después', async () => {
    const servidor = servidorCon();
    let responder: (r: Response) => void = () => undefined;
    servidor.responder('GET /pacientes/1/presupuestos', () => new Promise<Response>((r) => (responder = r)));
    seccion = crearSeccionPresupuestos({ pacienteId: 1, nombrePaciente: 'Paciente Uno', modo: 'odontologo', api: servidor.api, mensajeDeError, vigente: () => true });
    const contenedor = seccion.contenedor;
    document.body.replaceChildren(contenedor);

    seccion.destruir();
    responder(json(200, [PRESUPUESTO_41]));
    await new Promise((r) => setTimeout(r, 0));

    expect(contenedor.querySelector('.pre-presupuesto')).toBeNull();
  });
});
