import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, campo, errorDeCampo, json } from './apoyo.ts';
import {
  abrirVistaDeFirma,
  completarFirma,
  enviarFirma,
  escribirTexto,
  plantillaVista,
  prepararEntorno,
  SESION_ANA,
  TEXTO_V3,
  TITULO_EXTRACCION,
  visible,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, tarea 5.2: la vista de firma, los campos del paciente y su validación (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
  document.querySelectorAll('dialog.firma-pantalla').forEach((d) => d.remove());
});

function firmas(): number {
  return entorno.servidor.a('POST', '/pacientes/1/consentimientos').length;
}

/** Todo lo que el teclado puede alcanzar dentro de la vista. */
function controles(pantalla: HTMLElement): HTMLElement[] {
  return [...pantalla.querySelectorAll<HTMLElement>('button, input, select, textarea, a[href], [tabindex]:not([tabindex="-1"])')];
}

describe('Vista de firma a pantalla completa', () => {
  it('Lo que ve el paciente', async () => {
    const showModal = vi.spyOn(HTMLDialogElement.prototype, 'showModal');
    const pantalla = await abrirVistaDeFirma(entorno);

    // Un diálogo modal: el navegador deja el resto de la página inerte y fuera del teclado.
    expect(showModal).toHaveBeenCalled();
    expect(showModal.mock.contexts.some((c) => c === pantalla)).toBe(true);
    expect(pantalla.open).toBe(true);
    expect(pantalla.parentElement).toBe(document.body);

    expect(pantalla.querySelector('h2')?.textContent).toBe(TITULO_EXTRACCION);
    expect(pantalla.querySelector('.cons-texto')?.textContent).toBe(TEXTO_V3);
    expect(visible(pantalla)).toContain('Para: Paciente Uno');
    expect(visible(pantalla)).toContain('Si no sos vos, avisale al profesional.');
    expect(visible(pantalla)).toContain('Versión 3');
    const nombres = controles(pantalla).map((c) => (c instanceof HTMLInputElement ? `input:${c.type}` : `${c.tagName.toLowerCase()}:${c.textContent}`));
    expect(nombres).toEqual(['input:text', 'input:text', 'input:checkbox', 'button:Cancelar', 'button:Firmar']);
    expect(campo(pantalla, 'Nombre completo')).toBeDefined();
    expect(campo(pantalla, 'DNI')).toBeDefined();
    expect(visible(pantalla)).toContain('Leí y acepto este consentimiento');
    // Nada clínico: ni el odontograma ni las imágenes ni el DNI del paciente, en el texto o en un atributo.
    expect(pantalla.querySelector('.odontograma, .img-seccion, .ficha-columnas, .cons-lista')).toBeNull();
    expect(pantalla.textContent).not.toContain('99000001');
    for (const elemento of [pantalla, ...pantalla.querySelectorAll('*')]) {
      for (const atributo of elemento.getAttributeNames()) {
        expect(elemento.getAttribute(atributo), `${elemento.tagName}[${atributo}]`).not.toContain('99000001');
      }
    }
    expect(pantalla.outerHTML).not.toContain('99000001');
  });

  it('El texto se muestra como texto plano', async () => {
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () =>
      json(200, plantillaVista(4, true, 3, { texto: 'Primer párrafo.\n\nSegundo párrafo con <b>riesgos</b> y más.' })),
    );
    const pantalla = await abrirVistaDeFirma(entorno);

    const texto = pantalla.querySelector<HTMLElement>('.cons-texto');
    expect(texto?.textContent).toBe('Primer párrafo.\n\nSegundo párrafo con <b>riesgos</b> y más.');
    expect(texto?.querySelector('b')).toBeNull();
    expect(texto?.children).toHaveLength(0);
  });

  it('el texto mostrado es, carácter por carácter, el de la versión cargada', async () => {
    const texto = 'Línea con espacios al final   \n\n\n  Sangría y ñ, ü, “comillas”, € y emoji 🦷.\ná́ combinado\tcon tabulación\n<script>alert(1)</script>';
    entorno.servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, plantillaVista(4, true, 3, { texto })));
    const pantalla = await abrirVistaDeFirma(entorno);

    expect(pantalla.querySelector('.cons-texto')?.textContent).toBe(texto);
    expect(pantalla.querySelector('script')).toBeNull();
  });

  it('el DNI de la ficha no aparece en ningún texto ni atributo de la vista ni en el almacenamiento', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const todo = [pantalla.outerHTML, JSON.stringify({ ...localStorage }), JSON.stringify({ ...sessionStorage }), window.location.href].join('|');
    expect(todo).not.toContain('99000001');
    expect(todo).not.toContain('99.000.001');
    expect(pantalla.querySelector('datalist')).toBeNull();
  });
});

describe('Nombre y DNI escritos por el paciente', () => {
  it('Campos vacíos al abrir', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const nombre = campo<HTMLInputElement>(pantalla, 'Nombre completo');
    const dni = campo<HTMLInputElement>(pantalla, 'DNI');
    expect(nombre.value).toBe('');
    expect(dni.value).toBe('');
    expect(nombre.getAttribute('value')).toBeNull();
    expect(dni.getAttribute('value')).toBeNull();
    // Sin sugerencias: ni el nombre ni el DNI de la ficha en un placeholder.
    expect(nombre.placeholder).not.toContain('Paciente');
    expect(dni.placeholder).not.toContain('9900');
    expect(pantalla.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false);
  });

  it('los campos desactivan el autocompletado y el teclado numérico del DNI', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const nombre = campo<HTMLInputElement>(pantalla, 'Nombre completo');
    const dni = campo<HTMLInputElement>(pantalla, 'DNI');
    expect(nombre.getAttribute('autocomplete')).toBe('off');
    expect(dni.getAttribute('autocomplete')).toBe('off');
    expect(nombre.getAttribute('autocapitalize')).toBe('words');
    expect(dni.getAttribute('inputmode')).toBe('numeric');
    expect(dni.getAttribute('spellcheck')).toBe('false');
    expect(pantalla.querySelector('input[type="password"]')).toBeNull();
  });
});

describe('Firma electrónica, no firma digital', () => {
  it('Texto de la firma', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    const texto = visible(pantalla);
    expect(texto).toContain('Firma electrónica');
    expect(texto).toContain('firma electrónica');
    for (const guardado of ['el nombre', 'el DNI', 'la fecha y hora', 'el profesional', 'la dirección IP', 'el dispositivo']) {
      expect(texto).toContain(guardado);
    }
    expect(texto.toLowerCase()).not.toContain('firma digital');
    expect(document.body.textContent?.toLowerCase()).not.toContain('firma digital');
  });
});

describe('Validar la firma antes de enviarla', () => {
  it('Sin aceptar', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { acepto: false });

    enviarFirma(pantalla);

    expect(firmas()).toBe(0);
    const aviso = pantalla.querySelector('.campo-error:not(:empty)');
    expect(aviso?.textContent).toBe('Para firmar, marcá que leíste y aceptás el consentimiento.');
    expect(pantalla.querySelector('input[type="checkbox"]')?.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(pantalla.querySelector('input[type="checkbox"]'));
  });

  it('DNI con letras', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { dni: '99A00001' });

    enviarFirma(pantalla);

    expect(firmas()).toBe(0);
    expect(errorDeCampo(pantalla, 'DNI')).toBe('Escribí el DNI sin letras: 7 u 8 números.');
    expect(document.activeElement).toBe(campo(pantalla, 'DNI'));
  });

  it('Nombre demasiado corto', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Pa' });

    enviarFirma(pantalla);

    expect(firmas()).toBe(0);
    expect(errorDeCampo(pantalla, 'Nombre completo')).toBe('Escribí tu nombre completo.');
    expect(document.activeElement).toBe(campo(pantalla, 'Nombre completo'));
  });

  it('con varios errores marca todos y lleva el foco al primero', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);

    enviarFirma(pantalla);

    expect(firmas()).toBe(0);
    expect(errorDeCampo(pantalla, 'Nombre completo')).toBe('Escribí tu nombre completo.');
    expect(errorDeCampo(pantalla, 'DNI')).toBe('Escribí el DNI sin letras: 7 u 8 números.');
    expect(pantalla.querySelector('.campo-error:not(:empty)')).not.toBeNull();
    expect(document.activeElement).toBe(campo(pantalla, 'Nombre completo'));
  });

  it('un DNI con 6 o 9 dígitos no se envía y con 7 u 8 (con puntos o espacios) pasa la validación', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    for (const dni of ['990000', '990000012']) {
      completarFirma(pantalla, { dni });
      enviarFirma(pantalla);
      expect(errorDeCampo(pantalla, 'DNI'), dni).toBe('Escribí el DNI sin letras: 7 u 8 números.');
    }
    for (const dni of ['9900001', '99 000 001', '99.000.001']) {
      completarFirma(pantalla, { dni });
      enviarFirma(pantalla);
      expect(errorDeCampo(pantalla, 'DNI'), dni).toBe('');
    }
  });

  it('un error de un campo se limpia al corregirlo y volver a firmar', async () => {
    const pantalla = await abrirVistaDeFirma(entorno);
    completarFirma(pantalla, { nombre: 'Pa' });
    enviarFirma(pantalla);
    expect(errorDeCampo(pantalla, 'Nombre completo')).not.toBe('');

    escribirTexto(campo(pantalla, 'Nombre completo'), 'Paciente Uno');
    enviarFirma(pantalla);

    expect(errorDeCampo(pantalla, 'Nombre completo')).toBe('');
    expect(boton(pantalla, 'Cancelar')).toBeDefined();
  });
});
