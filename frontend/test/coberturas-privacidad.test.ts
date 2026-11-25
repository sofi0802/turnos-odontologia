import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import { abrirApp, boton, campo, elegirPaciente, escribir, json, ServidorFalso, type AppDePrueba } from './apoyo.ts';
import { dialogoAbierto, enviar } from './apoyo-configuracion.ts';
import { COBERTURA_UNO, responderCoberturas } from './apoyo-coberturas.ts';

/**
 * C-49, tarea 6.1: "Cobertura solo en la pantalla" (RN-OS-07, reglas duras 1 y 6). La obra social, el plan, el número
 * de afiliado y el paciente elegido no quedan en la dirección, en el almacenamiento del navegador ni en la consola.
 */

const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const METODOS = ['log', 'info', 'warn', 'error', 'debug'] as const;
const DIRECCIONES = ['#/coberturas', '#/pacientes', '#/configuracion', '#/mi-cuenta'];

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
  responderCoberturas(servidor);
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

function contenidoDelNavegador(): string {
  const guardado = [localStorage, sessionStorage].flatMap((almacen) =>
    Array.from({ length: almacen.length }, (_, i) => {
      const clave = almacen.key(i) ?? '';
      return `${clave}=${almacen.getItem(clave) ?? ''}`;
    }),
  );
  return guardado.join('\n');
}

function textoDeLaConsola(): string {
  return consola.flatMap((espia) => espia.mock.calls.flat().map((valor) => String(valor))).join('\n');
}

function nadaFueraDeLaPantalla(sensibles: readonly string[]): void {
  expect(DIRECCIONES).toContain(window.location.hash);
  const navegador = contenidoDelNavegador();
  const deConsola = textoDeLaConsola();
  for (const dato of sensibles) {
    expect(decodeURIComponent(window.location.href)).not.toContain(dato);
    expect(navegador).not.toContain(dato);
    expect(deConsola).not.toContain(dato);
  }
}

async function elegirEnCoberturas(busqueda: string, opcion: string): Promise<HTMLElement> {
  servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
  app = abrirApp(servidor, '#/coberturas');
  await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Coberturas'));
  await elegirPaciente(main(), busqueda, opcion);
  const seccion = await vi.waitFor(() => {
    const encontrada = main().querySelector<HTMLElement>('.cob-seccion[aria-labelledby]');
    expect(encontrada?.getAttribute('aria-busy')).toBeNull();
    return encontrada as HTMLElement;
  });
  return seccion;
}

describe('Cobertura solo en la pantalla', () => {
  it('Nada fuera de la pantalla al afiliar', async () => {
    servidor.responder('POST /pacientes/{id}/afiliaciones', () => json(201, {}));
    const seccion = await elegirEnCoberturas('99000002', 'Dos, Paciente · DNI 99000002');

    boton(seccion, 'Afiliar').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo, 'Obra social').disabled).toBe(false));
    escribir(campo<HTMLSelectElement>(dialogo, 'Obra social'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Plan'), '11');
    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000002');
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/2/afiliaciones')).toHaveLength(1));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/2/afiliaciones')).toHaveLength(2));

    expect(window.location.hash).toBe('#/coberturas');
    nadaFueraDeLaPantalla(['OSE-000002', 'Plan Base', 'Paciente Dos', '99000002']);
  });

  it('nada fuera de la pantalla al corregir y al rechazar el número', async () => {
    servidor.responder('PUT /pacientes/{id}/afiliaciones/{id}', () => json(409, { codigo: 'AFILIADO_DUPLICADO' }));
    const seccion = await elegirEnCoberturas('99000001', 'Uno, Paciente · DNI 99000001');

    boton(seccion, 'Corregir la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo, 'Plan').value).toBe('11'));
    escribir(campo(dialogo, 'N.º de afiliado'), 'OSE-000099');
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('PUT', '/pacientes/1/afiliaciones/31')).toHaveLength(1));
    await vi.waitFor(() => expect(dialogo.querySelector('[aria-invalid="true"]')).not.toBeNull());

    expect(document.body.textContent).not.toContain('OSE-000099');
    nadaFueraDeLaPantalla(['OSE-000099', 'OSE-000001', 'Plan Base', 'Paciente Uno']);
  });

  it('nada fuera de la pantalla al cerrar', async () => {
    servidor.responder('POST /pacientes/{id}/afiliaciones/{id}/cerrar', () => json(200, {}));
    const seccion = await elegirEnCoberturas('99000001', 'Uno, Paciente · DNI 99000001');

    boton(seccion, 'Cerrar la afiliación a Plan Base').click();
    const dialogo = await dialogoAbierto();
    enviar(dialogo);
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/afiliaciones/31/cerrar')).toHaveLength(1));

    expect(window.location.hash).toBe('#/coberturas');
    nadaFueraDeLaPantalla(['OSE-000001', 'Plan Base', 'Paciente Uno']);
  });

  it('nada fuera de la pantalla en "Mi cobertura"', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/cobertura', () => json(200, COBERTURA_UNO));
    app = abrirApp(servidor, '#/mi-cuenta');

    await vi.waitFor(() => expect(main().textContent).toContain('OSE-000001'));

    expect(window.location.hash).toBe('#/mi-cuenta');
    nadaFueraDeLaPantalla(['OSE-000001', 'Plan Base', 'Obra Social Ejemplo']);
  });

  it('un error de carga no escribe nada en la consola', async () => {
    servidor.responder('GET /pacientes/2/cobertura', () => json(500));
    await elegirEnCoberturas('99000002', 'Dos, Paciente · DNI 99000002');

    expect(textoDeLaConsola()).toBe('');
  });
});
