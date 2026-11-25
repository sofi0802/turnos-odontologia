import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, boton, elegirPaciente, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';
import { PRESUPUESTO_40, PRESUPUESTO_41, responderPresupuestos, sinDatosClinicos } from './apoyo-presupuestos.ts';

/**
 * C-50, grupo 4: la sección "Presupuestos" en la ficha, en "Coberturas" y "Mis presupuestos" en "Mi cuenta"
 * (spec `frontend-presupuestos`). Datos ficticios del seed.
 */

const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };
const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
  servidor = new ServidorFalso();
  responderPresupuestos(servidor);
});

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  app = undefined;
  vi.useRealTimers();
});

function conSesion(sesion: object): void {
  servidor.responder('GET /public/sesion', () => json(200, sesion));
}

function main(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz.querySelector('main') ?? app.raiz;
}

function seccionPresupuestos(): HTMLElement | null {
  return main().querySelector<HTMLElement>('.pre-seccion[aria-labelledby]');
}

async function abrirFicha(): Promise<void> {
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/odontograma/historial', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, []));
  dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' }, turno: null });
  app = abrirApp(servidor, '#/pacientes');
  await vi.waitFor(() => expect(main().querySelector('.ficha-columnas')).not.toBeNull());
  await vi.waitFor(() => expect(seccionPresupuestos()?.getAttribute('aria-busy')).toBeNull());
}

describe('Sección "Presupuestos" en la ficha', () => {
  it('La ficha muestra los presupuestos', async () => {
    conSesion(SESION_ANA);
    await abrirFicha();

    const presupuestos = seccionPresupuestos() as HTMLElement;
    const cobertura = main().querySelector('.cob-seccion[aria-labelledby]') as HTMLElement;
    const odontograma = [...main().querySelectorAll('h2')].find((h) => h.textContent === 'Odontograma')?.closest('section') as HTMLElement;
    expect(cobertura.compareDocumentPosition(presupuestos) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(presupuestos.compareDocumentPosition(odontograma) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(presupuestos.querySelectorAll('.pre-presupuesto')).toHaveLength(2);
    expect(boton(presupuestos, 'Nuevo presupuesto').hidden).toBe(false);
  });

  it('Falla de los presupuestos en la ficha', async () => {
    conSesion(SESION_ANA);
    servidor.responder('GET /pacientes/1/presupuestos', () => json(500));
    await abrirFicha();

    const presupuestos = seccionPresupuestos() as HTMLElement;
    expect(presupuestos.querySelector('[role="alert"]:not(:empty)')).not.toBeNull();
    expect(boton(presupuestos, 'Reintentar')).toBeDefined();
    expect(main().querySelector('.cob-tarjeta')).not.toBeNull();
    expect(texto(main())).toContain('Odontograma');
    expect(main().querySelector('.ficha-columnas')).not.toBeNull();
  });

  it('"Cambiar de paciente" deja de mostrar los presupuestos', async () => {
    conSesion(SESION_ANA);
    await abrirFicha();

    boton(main(), 'Cambiar de paciente').click();

    expect(seccionPresupuestos()).toBeNull();
  });
});

describe('Solo lectura para el administrador en la ficha', () => {
  it('El administrador ve los presupuestos', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha();

    const presupuestos = seccionPresupuestos() as HTMLElement;
    expect(texto(presupuestos)).toContain('$ 40.000,00');
    expect(texto(presupuestos)).toContain('Solo lectura: los presupuestos los arma el odontólogo.');
    const botones = [...presupuestos.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones).not.toContain('Nuevo presupuesto');
    expect(botones).not.toContain('Aceptar');
    expect(botones).not.toContain('Rechazar');
    expect(servidor.a('GET', '/pacientes/1/presupuestos/sugerencias')).toHaveLength(0);
  });
});

describe('Presupuestos en la página "Coberturas"', () => {
  async function abrirCoberturas(sesion: object, busqueda: string, opcion: string): Promise<HTMLElement> {
    conSesion(sesion);
    app = abrirApp(servidor, '#/coberturas');
    await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Coberturas'));
    await elegirPaciente(main(), busqueda, opcion);
    await vi.waitFor(() => expect(seccionPresupuestos()?.getAttribute('aria-busy')).toBeNull());
    return seccionPresupuestos() as HTMLElement;
  }

  it('La recepción ve los presupuestos sin pieza ni cara', async () => {
    servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [sinDatosClinicos(PRESUPUESTO_40)]));
    const presupuestos = await abrirCoberturas(SESION_RECEPCION, '99000001', 'Uno, Paciente · DNI 99000001');

    boton(presupuestos, 'Ver detalle').click();

    expect(texto(presupuestos)).toContain('Extracción simple');
    expect(texto(presupuestos)).toContain('$ 30.000,00');
    expect(texto(presupuestos)).not.toContain('Pieza');
    expect(texto(presupuestos)).not.toContain('36');
    expect([...presupuestos.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Nuevo presupuesto');
  });

  it('Ningún pedido clínico en Coberturas', async () => {
    await abrirCoberturas(SESION_RECEPCION, '99000001', 'Uno, Paciente · DNI 99000001');

    for (const pedido of servidor.pedidos) {
      expect(['/ficha', '/odontograma', '/periodontograma', '/imagenes', '/historial', '/sugerencias'].some((r) => pedido.ruta.includes(r))).toBe(false);
    }
    expect(servidor.a('GET', '/pacientes/1/presupuestos')).toHaveLength(1);
  });

  it('el administrador también los ve en Coberturas, en solo lectura', async () => {
    const presupuestos = await abrirCoberturas(SESION_ADMIN, '99000001', 'Uno, Paciente · DNI 99000001');

    expect(presupuestos.querySelectorAll('.pre-presupuesto')).toHaveLength(2);
    expect([...presupuestos.querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Aceptar');
  });
});

describe('Mis presupuestos del paciente', () => {
  async function abrirMiCuenta(): Promise<HTMLElement> {
    conSesion(SESION_PACIENTE);
    servidor.responder('GET /paciente/cobertura', () => json(200, { particular: true, afiliacion: null }));
    app = abrirApp(servidor, '#/mi-cuenta');
    const seccion = await vi.waitFor(() => {
      const encontrada = main().querySelector<HTMLElement>('.mis-presupuestos');
      expect(encontrada?.getAttribute('aria-busy')).toBeNull();
      return encontrada as HTMLElement;
    });
    return seccion;
  }

  it('El paciente ve sus presupuestos', async () => {
    servidor.responder('GET /paciente/presupuestos', () => json(200, [PRESUPUESTO_41, PRESUPUESTO_40]));
    const seccion = await abrirMiCuenta();

    expect([...servidor.ultimo('GET', '/paciente/presupuestos').query.keys()]).toEqual([]);
    expect(seccion.querySelector('h2')?.textContent).toBe('Mis presupuestos');
    const tarjetas = [...seccion.querySelectorAll<HTMLElement>('.pre-presupuesto')];
    expect(tarjetas[0]?.querySelector('.etiqueta')?.textContent).toBe('Pendiente');
    expect(texto(tarjetas[0] as HTMLElement)).toContain('$ 12.500,00');
    expect(tarjetas[1]?.querySelector('.etiqueta')?.textContent).toBe('Vencido');
    const botones = [...seccion.querySelectorAll('button')].map((b) => b.textContent);
    expect(botones.every((b) => b === 'Ver detalle')).toBe(true);
    // Va debajo de "Mi cobertura".
    const cobertura = main().querySelector('.mi-cobertura') as HTMLElement;
    expect(cobertura.compareDocumentPosition(seccion) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('el paciente ve la pieza de sus propios ítems', async () => {
    servidor.responder('GET /paciente/presupuestos', () => json(200, [PRESUPUESTO_40]));
    const seccion = await abrirMiCuenta();

    boton(seccion, 'Ver detalle').click();

    expect(texto(seccion)).toContain('Pieza 36');
  });
});

describe('Mis presupuestos vacíos o con error', () => {
  async function abrirMiCuentaCon(respuesta: () => Response): Promise<HTMLElement> {
    conSesion(SESION_PACIENTE);
    servidor.responder('GET /paciente/cobertura', () => json(200, { particular: true, afiliacion: null }));
    servidor.responder('GET /paciente/presupuestos', respuesta);
    app = abrirApp(servidor, '#/mi-cuenta');
    return vi.waitFor(() => {
      const encontrada = main().querySelector<HTMLElement>('.mis-presupuestos');
      expect(encontrada?.getAttribute('aria-busy')).toBeNull();
      return encontrada as HTMLElement;
    });
  }

  it('Paciente sin presupuestos', async () => {
    const seccion = await abrirMiCuentaCon(() => json(200, []));

    expect(texto(seccion)).toContain('Todavía no tenés presupuestos.');
  });

  it('Falla de Mis presupuestos', async () => {
    let pedidos = 0;
    const seccion = await abrirMiCuentaCon(() => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, []);
    });

    expect(seccion.querySelector('[role="alert"]:not(:empty)')).not.toBeNull();
    expect(texto(main().querySelector('.mi-cobertura') as HTMLElement)).toContain('Te atendés como particular.');
    boton(seccion, 'Reintentar').click();

    await vi.waitFor(() => expect(texto(main().querySelector('.mis-presupuestos') as HTMLElement)).toContain('Todavía no tenés presupuestos.'));
  });
});
