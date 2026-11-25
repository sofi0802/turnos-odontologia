import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CLAVE_DE_LA_MARCA } from '../src/consentimientos/marca.ts';
import { boton, campo, escribir, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  enviarFirma,
  escribirTexto,
  prepararEntorno,
  resumen,
  SESION_ANA,
  TEXTO_V3,
  TITULO_EXTRACCION,
  ultimoDialogo,
  VERIFICACION_INTEGRA,
  vistaDe,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 7.3 y 7.4: los consentimientos solo viven en la pantalla (spec `frontend-consentimientos`). */

const CLAVE_BUENA = 'clave-ficticia-buena';
const MOTIVO = 'El paciente decidió no hacer el tratamiento';
const IP = '192.0.2.10';

let entorno: Entorno;
let sesionAbierta: boolean;
let consola: Array<ReturnType<typeof vi.spyOn>>;

beforeEach(() => {
  consola = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) => vi.spyOn(console, metodo).mockImplementation(() => undefined));
  entorno = prepararEntorno(SESION_ANA);
  sesionAbierta = true;
  entorno.servidor.responder('GET /public/sesion', () => json(200, sesionAbierta ? SESION_ANA : { autenticado: false }));
  entorno.servidor.responder('POST /logout', () => {
    sesionAbierta = false;
    return json(204);
  });
  entorno.servidor.responder('POST /public/login', () => {
    sesionAbierta = true;
    return json(200, SESION_ANA);
  });
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos', () => {
    const nuevo = resumen({ id: 13, firmadoEn: '2026-11-24T10:30:00-03:00' });
    entorno.consentimientos = [nuevo, ...entorno.consentimientos.filter((c) => c.estado === 'REVOCADO')];
    return json(201, vistaDe(nuevo));
  });
  entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(200, vistaDe(resumen({ id: 13 }), { evidencia: { ip: IP, dispositivo: 'Navegador de prueba' } })));
  entorno.servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, VERIFICACION_INTEGRA));
  entorno.servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', () => {
    entorno.consentimientos = entorno.consentimientos.map((c) => (c.id === 13 ? { ...c, estado: 'REVOCADO' as const } : c));
    return json(200, vistaDe(resumen({ id: 13, estado: 'REVOCADO' })));
  });
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

/** Todo lo que el navegador guarda fuera de la pantalla: la dirección, los dos almacenamientos y la consola. */
function fueraDeLaPantalla(): string {
  const almacenados = (almacen: Storage): string => JSON.stringify(Object.fromEntries(Object.keys(almacen).map((k) => [k, almacen.getItem(k)])));
  const mensajes = JSON.stringify(consola.map((espia) => espia.mock.calls));
  return [window.location.href, almacenados(localStorage), almacenados(sessionStorage), mensajes, document.title].join('|');
}

describe('Consentimientos solo en la pantalla', () => {
  it('Nada queda guardado en el navegador', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Paciente Uno', dni: '99.000.001' });
    // Mientras el paciente firma, lo único guardado es la marca.
    expect(Object.keys(localStorage)).toEqual([CLAVE_DE_LA_MARCA]);
    enviarFirma(pantalla);
    const bloqueo = await vi.waitFor(() => {
      const actual = document.querySelector<HTMLElement>('.bloqueo-pantalla');
      expect(actual).not.toBeNull();
      return actual as HTMLElement;
    });
    escribir(campo(bloqueo, 'Contraseña'), CLAVE_BUENA);
    boton(bloqueo, 'Desbloquear').click();
    await vi.waitFor(() => expect(entorno.main().querySelector('.cons-lista')).not.toBeNull());

    boton(entorno.seccion(), `Ver ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const documento = await ultimoDialogo();
    await vi.waitFor(() => expect(visible(documento)).toContain('Íntegro'));
    boton(documento, 'Cerrar').click();
    boton(entorno.seccion(), `Revocar ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const revocacion = await ultimoDialogo();
    escribirTexto(campo<HTMLTextAreaElement>(revocacion, 'Motivo'), MOTIVO);
    revocacion.querySelector('form')?.requestSubmit();
    await vi.waitFor(() => expect(visible(entorno.seccion())).toContain('Consentimiento revocado.'));

    const guardado = fueraDeLaPantalla();
    for (const dato of ['Primer párrafo', 'Paciente Uno', '99000001', '99.000.001', IP, MOTIVO, 'riesgos', CLAVE_BUENA, TITULO_EXTRACCION]) {
      expect(guardado, dato).not.toContain(dato);
    }
    expect(window.location.hash).toBe('#/pacientes');
    expect(Object.keys(localStorage)).toEqual([]);
    expect(Object.keys(sessionStorage)).toEqual([]);
    for (const espia of consola) {
      expect(espia).not.toHaveBeenCalled();
    }
  });

  it('la dirección sigue siendo #/pacientes durante la firma, el bloqueo y el documento', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    expect(window.location.hash).toBe('#/pacientes');
    completarFirma(pantalla);
    enviarFirma(pantalla);
    await vi.waitFor(() => expect(document.querySelector('.bloqueo-pantalla')).not.toBeNull());

    expect(window.location.hash).toBe('#/pacientes');
    expect(fueraDeLaPantalla()).not.toContain('Paciente Uno');
    expect(fueraDeLaPantalla()).not.toContain('99000001');
  });

  it('Sin autocompletado en la firma', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    expect(campo(pantalla, 'Nombre completo').getAttribute('autocomplete')).toBe('off');
    expect(campo(pantalla, 'DNI').getAttribute('autocomplete')).toBe('off');
    expect(pantalla.querySelector('form')?.getAttribute('autocomplete') ?? 'off').toBe('off');
  });

  it('el documento se quita de la página al cerrarse', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), `Ver ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const documento = await ultimoDialogo();
    await vi.waitFor(() => expect(documento.querySelector('.cons-texto')).not.toBeNull());
    expect(document.body.textContent).toContain(TEXTO_V3.slice(0, 20));

    boton(documento, 'Cerrar').click();

    expect(document.querySelector('.cons-documento')).toBeNull();
    expect(document.body.textContent).not.toContain('Primer párrafo del consentimiento.');
  });

  it('el documento y la sección se quitan al cambiar de paciente', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();
    boton(entorno.seccion(), `Ver ${TITULO_EXTRACCION} del 24/11/2026`).click();
    const documento = await ultimoDialogo();
    await vi.waitFor(() => expect(documento.querySelector('.cons-texto')).not.toBeNull());

    boton(entorno.main(), 'Cambiar de paciente').click();

    expect(document.querySelector('.cons-documento')).toBeNull();
    expect(document.querySelector('.cons-seccion')).toBeNull();
    expect(document.body.textContent).not.toContain('Primer párrafo del consentimiento.');
    expect(visible(entorno.main())).toContain('Buscar paciente');
  });

  it('la vista de firma y el bloqueo no quedan en la página después de desbloquear', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Nombre Escrito Ficticio', dni: '99.000.001' });
    enviarFirma(pantalla);
    const bloqueo = await vi.waitFor(() => {
      const actual = document.querySelector<HTMLElement>('.bloqueo-pantalla');
      expect(actual).not.toBeNull();
      return actual as HTMLElement;
    });
    escribir(campo(bloqueo, 'Contraseña'), CLAVE_BUENA);

    boton(bloqueo, 'Desbloquear').click();

    await vi.waitFor(() => expect(document.querySelector('.bloqueo-pantalla')).toBeNull());
    expect(document.querySelector('dialog.firma-pantalla')).toBeNull();
    expect(document.body.innerHTML).not.toContain('Nombre Escrito Ficticio');
    expect(document.body.innerHTML).not.toContain(CLAVE_BUENA);
  });
});

/** Los módulos de la pantalla, leídos como texto, sin ejecutarlos. */
const MODULOS_DE_CONSENTIMIENTOS = import.meta.glob('../src/consentimientos/*.ts', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
const TODO_EL_CODIGO = import.meta.glob('../src/**/*.ts', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

describe('Reglas de los módulos de consentimientos', () => {
  it('hay módulos que revisar', () => {
    const nombres = Object.keys(MODULOS_DE_CONSENTIMIENTOS).map((ruta) => ruta.split('/').pop());
    for (const modulo of ['api.ts', 'bloqueo.ts', 'documento.ts', 'firma.ts', 'marca.ts', 'modelo.ts', 'pendiente.ts', 'plantillas.ts', 'seccion.ts', 'ui.ts']) {
      expect(nombres).toContain(modulo);
    }
  });

  it('ningún módulo usa la consola ni asigna HTML', () => {
    for (const [ruta, codigo] of Object.entries(MODULOS_DE_CONSENTIMIENTOS)) {
      expect(codigo, ruta).not.toMatch(/\bconsole\s*\./);
      expect(codigo, ruta).not.toMatch(/innerHTML|outerHTML|insertAdjacentHTML|document\.write/);
    }
  });

  it('solo la marca toca el almacenamiento y ningún módulo cambia la dirección con datos', () => {
    for (const [ruta, codigo] of Object.entries(MODULOS_DE_CONSENTIMIENTOS)) {
      expect(codigo, ruta).not.toMatch(/sessionStorage|indexedDB|document\.cookie/);
      expect(codigo, ruta).not.toMatch(/history\.(push|replace)State|location\.(hash|href|search)\s*=/);
      if (!ruta.endsWith('/marca.ts')) {
        expect(codigo, ruta).not.toMatch(/localStorage/);
      }
    }
  });

  it('no usan "any" ni se salta el tipado', () => {
    for (const [ruta, codigo] of Object.entries(MODULOS_DE_CONSENTIMIENTOS)) {
      expect(codigo, ruta).not.toMatch(/:\s*any\b|<any>|as any\b|@ts-ignore|@ts-expect-error/);
    }
  });

  it('ningún archivo de frontend/src dice "firma digital": es firma electrónica', () => {
    const encontrados = Object.entries(TODO_EL_CODIGO)
      .filter(([, codigo]) => /firma\s+digital/i.test(codigo))
      .map(([ruta]) => ruta);

    expect(Object.keys(TODO_EL_CODIGO).length).toBeGreaterThan(50);
    expect(encontrados).toEqual([]);
  });
});
