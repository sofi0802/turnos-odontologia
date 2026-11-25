import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';
import { dialogoAbierto, elegirPestana, enviar, pestanas, PRESTACIONES_SEED, SESION_ADMIN } from './apoyo-configuracion.ts';
import { OBRAS_SOCIALES } from './apoyo-coberturas.ts';
import { PRESUPUESTO_40, PRESUPUESTO_41, responderPresupuestos } from './apoyo-presupuestos.ts';

/**
 * C-50, tarea 5.1: "Presupuestos solo en la pantalla" y la privacidad de `frontend-aranceles` (RN-PR-07, reglas duras 1
 * y 6). Piezas, caras, importes, coberturas y precios no quedan en la dirección, el almacenamiento ni la consola.
 */

const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const METODOS = ['log', 'info', 'warn', 'error', 'debug'] as const;
const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;
let consola: MockInstance[] = [];

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
  localStorage.clear();
  sessionStorage.clear();
  consola = METODOS.map((metodo) => vi.spyOn(console, metodo).mockImplementation(() => undefined));
  servidor = new ServidorFalso();
  responderPresupuestos(servidor);
});

afterEach(() => {
  for (const espia of consola) {
    espia.mockRestore();
  }
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  app = undefined;
  vi.useRealTimers();
});

function main(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz.querySelector('main') ?? app.raiz;
}

function guardadoEnElNavegador(): string {
  return [localStorage, sessionStorage]
    .flatMap((almacen) => Array.from({ length: almacen.length }, (_, i) => `${almacen.key(i) ?? ''}=${almacen.getItem(almacen.key(i) ?? '') ?? ''}`))
    .join('\n');
}

function textoDeLaConsola(): string {
  return consola.flatMap((espia) => espia.mock.calls.flat().map((valor) => String(valor))).join('\n');
}

function nadaFueraDeLaPantalla(hash: string, sensibles: readonly string[]): void {
  expect(window.location.hash).toBe(hash);
  const navegador = guardadoEnElNavegador();
  const deConsola = textoDeLaConsola();
  for (const dato of sensibles) {
    expect(decodeURIComponent(window.location.href)).not.toContain(dato);
    expect(navegador).not.toContain(dato);
    expect(deConsola).not.toContain(dato);
  }
}

async function abrirFichaComoOdontologa(): Promise<HTMLElement> {
  servidor.responder('GET /public/sesion', () => json(200, SESION_ANA));
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/odontograma/historial', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, []));
  dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' }, turno: null });
  app = abrirApp(servidor, '#/pacientes');
  return vi.waitFor(() => {
    const seccion = main().querySelector<HTMLElement>('.pre-seccion[aria-labelledby]');
    expect(seccion?.getAttribute('aria-busy')).toBeNull();
    return seccion as HTMLElement;
  });
}

describe('Presupuestos solo en la pantalla', () => {
  it('Nada fuera de la pantalla al emitir', async () => {
    servidor.responder('POST /pacientes/{id}/presupuestos', () => json(201, PRESUPUESTO_41));
    const seccion = await abrirFichaComoOdontologa();

    boton(seccion, 'Nuevo presupuesto').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(boton(dialogo, 'Emitir presupuesto').disabled).toBe(false));
    escribir(campo<HTMLSelectElement>(dialogo, 'Prestación del ítem 1'), '2');
    escribir(campo(dialogo, 'Pieza del ítem 1'), '16');
    escribir(campo<HTMLSelectElement>(dialogo, 'Cara del ítem 1'), 'O');
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos')).toHaveLength(1));
    await vi.waitFor(() => expect(dialogo.open).toBe(false));

    nadaFueraDeLaPantalla('#/pacientes', ['16', '12500', '12.500', 'Paciente Uno', 'OSE-000001']);
  });

  it('nada fuera de la pantalla al aceptar y rechazar', async () => {
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/aceptar', () => json(200, { ...PRESUPUESTO_41, estado: 'ACEPTADO' }));
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/rechazar', () => json(409, { codigo: 'PRESUPUESTO_CERRADO' }));
    const seccion = await abrirFichaComoOdontologa();
    const tarjetas = [...seccion.querySelectorAll<HTMLElement>('.pre-presupuesto')];

    boton(tarjetas[0] as HTMLElement, 'Aceptar').click();
    enviar(await dialogoAbierto());
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos/41/aceptar')).toHaveLength(1));
    // Se espera a que la lista se vuelva a dibujar después de aceptar.
    await vi.waitFor(() => expect(seccion.querySelector('[role="status"]:not(.cargando)')?.textContent).toBe('Presupuesto aceptado.'));
    await vi.waitFor(() => expect(seccion.getAttribute('aria-busy')).toBeNull());
    const vencido = [...seccion.querySelectorAll<HTMLElement>('.pre-presupuesto')].find((t) => texto(t).includes('01/10/2026')) as HTMLElement;
    boton(vencido, 'Rechazar').click();
    enviar(await dialogoAbierto());
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/presupuestos/40/rechazar')).toHaveLength(1));

    nadaFueraDeLaPantalla('#/pacientes', ['36', '30000', '30.000', '12500', 'Paciente Uno']);
  });

  it('Nada fuera de la pantalla en Mis presupuestos', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/cobertura', () => json(200, { particular: true, afiliacion: null }));
    servidor.responder('GET /paciente/presupuestos', () => json(200, [PRESUPUESTO_40]));
    app = abrirApp(servidor, '#/mi-cuenta');
    const seccion = await vi.waitFor(() => {
      const encontrada = main().querySelector<HTMLElement>('.mis-presupuestos .pre-presupuesto');
      expect(encontrada).not.toBeNull();
      return encontrada as HTMLElement;
    });

    boton(seccion, 'Ver detalle').click();
    expect(texto(seccion)).toContain('Pieza 36');

    nadaFueraDeLaPantalla('#/mi-cuenta', ['36', '30000', '30.000']);
  });
});

describe('Aranceles solo para el administrador', () => {
  it('Nada fuera de la pantalla al guardar', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ADMIN));
    servidor.responder('GET /prestaciones', () => json(200, PRESTACIONES_SEED));
    servidor.responder('GET /obras-sociales', () => json(200, OBRAS_SOCIALES));
    servidor.responder('PUT /aranceles/{id}', () => json(200, {}));
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}/coberturas/{id}', () => json(200, {}));
    app = abrirApp(servidor, '#/configuracion');
    await vi.waitFor(() => expect(pestanas(main()).length).toBeGreaterThan(0));

    const prestaciones = elegirPestana(main(), 'Prestaciones');
    await vi.waitFor(() => expect(texto(prestaciones)).toContain('$ 15.000,00'));
    boton(prestaciones, 'Cambiar el precio de Consulta o control').click();
    const precio = await dialogoAbierto();
    escribir(campo(precio, 'Precio'), '16000');
    enviar(precio);
    await vi.waitFor(() => expect(servidor.a('PUT', '/aranceles/1')).toHaveLength(1));

    const obras = elegirPestana(main(), 'Obras sociales');
    await vi.waitFor(() => expect(obras.querySelector('.cob-plan')).not.toBeNull());
    boton(obras, 'Cobertura de Plan Base').click();
    const cobertura = await dialogoAbierto();
    await vi.waitFor(() => expect(cobertura.querySelectorAll('.pre-cob-fila').length).toBeGreaterThan(0));
    escribir(campo(cobertura, 'Porcentaje de N-0201'), '60');
    enviar(cobertura);
    await vi.waitFor(() => expect(servidor.a('PUT', '/obras-sociales/1/planes/11/coberturas/102')).toHaveLength(1));

    nadaFueraDeLaPantalla('#/configuracion', ['16000', '16.000', '60']);
  });
});
