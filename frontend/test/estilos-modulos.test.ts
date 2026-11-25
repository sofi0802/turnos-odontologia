import { describe, expect, it } from 'vitest';
import { estilos, MODULOS_EN_DISCO, NOMBRES_DE_MODULOS, textoDeModulo, textoDeTokens, textoPrincipal } from './estilos.ts';

// C-39, etapa A: los estilos se parten en módulos sin cambiar ninguna regla.

/** Una regla característica de cada módulo: si se pierde o cambia de módulo, el test avisa. */
const REGLAS_POR_MODULO: Readonly<Record<string, string>> = {
  '01-base.css': '.contenido {',
  '02-formularios.css': '.campo-error {',
  '03-agenda.css': '.agenda-lista',
  '04-dialogos.css': '.dialogo-acciones {',
  '05-reprogramar.css': '.chips {',
  '06-componentes.css': '.btn-principal {',
  '07-selector-prestaciones.css': '.selector-lista {',
  '08-paciente.css': '.pagina-paciente',
  '09-buscador-pacientes.css': '@keyframes buscador-girar',
  '10-portada.css': '.hero {',
  '11-nuevo-turno.css': '/* Fin del nuevo turno guiado (C-40) */',
  '12-configuracion.css': '.lista-config > li {',
  '13-layout.css': '.cabecera {',
  '14-estados.css': '.btn-principal:hover:not(:disabled)',
  '15-estados-vacios.css': '.estado-vacio {',
  // C-41: cuentas del personal.
  '16-cuentas.css': '.contrasena-caja {',
  // C-44: ficha clínica y odontograma.
  '17-ficha.css': '.odontograma .cara {',
  // C-45: sobreturno y extensión.
  '18-sobreturno.css': '.aviso-choque {',
  // C-46: periodontograma.
  '19-periodontograma.css': '.perio-linea-margen,',
  // C-32: pantalla Reportes.
  '20-reportes.css': '.medidor-relleno {',
  // C-47: imágenes y estudios de la ficha.
  '21-imagenes.css': '.img-fila-anulada {',
  // C-22: caja y cobros.
  '22-caja.css': '.caja-grilla {',
  // C-15: lista de espera.
  '23-lista-espera.css': '.le-fila {',
};

function importaciones(): string[] {
  return [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1] ?? '');
}

describe('Estilos en módulos', () => {
  it('Módulos importados en orden', () => {
    const sinComentarios = textoPrincipal.replace(/\/\*[\s\S]*?\*\//g, '').trim();
    const reglasSueltas = sinComentarios.replace(/@import\s+'[^']+';/g, '').trim();
    expect(reglasSueltas).toBe('');

    const modulos = importaciones().filter((ruta) => ruta.startsWith('./styles/'));
    expect(modulos).toEqual(MODULOS_EN_DISCO.map((nombre) => `./styles/${nombre}`));
    expect(NOMBRES_DE_MODULOS).toEqual(MODULOS_EN_DISCO);
    expect(importaciones()[0]).toBe('../../design-system/tokens.css');
    for (const nombre of NOMBRES_DE_MODULOS) {
      expect(textoDeModulo(nombre).trim().length).toBeGreaterThan(0);
    }
  });

  it('Sin cambios de comportamiento', () => {
    expect(NOMBRES_DE_MODULOS).toEqual(Object.keys(REGLAS_POR_MODULO));
    for (const [nombre, regla] of Object.entries(REGLAS_POR_MODULO)) {
      expect(textoDeModulo(nombre), `${nombre} debe contener ${regla}`).toContain(regla);
    }
    // El orden de las reglas es el del archivo único: la última sección va al final.
    expect(estilos.lastIndexOf('.lista-config > li')).toBeGreaterThan(estilos.indexOf('.selector-lista {'));
  });

  it('ningún módulo importa a otro ni repite la importación de los tokens', () => {
    for (const nombre of NOMBRES_DE_MODULOS) {
      expect(textoDeModulo(nombre)).not.toMatch(/@import/);
    }
  });
});

describe('Módulo de imágenes (C-47)', () => {
  it('Sin las reglas que son solo de los mockups', () => {
    const modulo = textoDeModulo('21-imagenes.css');
    for (const regla of ['.nota-mockup', '.rotulo-estado', '.dialogo-estatico', '.img-ficticia', '.nota-contexto']) {
      expect(modulo, regla).not.toContain(regla);
    }
  });

  it('Sin quitar el foco y con controles de 44 px', () => {
    const modulo = textoDeModulo('21-imagenes.css');
    expect(modulo).not.toMatch(/outline:\s*none/);
    expect(modulo).toMatch(/\.img-acciones \.btn \{[^}]*min-height: var\(--ds-alto-control\)/);
    expect(modulo).toMatch(/\.img-check \{[^}]*min-height: var\(--ds-alto-control\)/);
  });

  it('Se importa justo después del módulo de reportes', () => {
    // C-22 agregó 22-caja.css después de este módulo: ya no es el último, pero sigue inmediatamente después de reportes.
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    const reportes = importaciones.indexOf('./styles/20-reportes.css');
    expect(reportes).toBeGreaterThan(-1);
    expect(importaciones[reportes + 1]).toBe('./styles/21-imagenes.css');
  });
});

describe('Módulo de la lista de espera (C-15)', () => {
  it('Se importa al final de styles.css, después del de caja', () => {
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    expect(importaciones.at(-1)).toBe('./styles/23-lista-espera.css');
    expect(importaciones.at(-2)).toBe('./styles/22-caja.css');
  });

  it('La fila pasa a tres columnas desde 768 px y las acciones de la oferta se apilan en el teléfono', () => {
    const modulo = textoDeModulo('23-lista-espera.css');
    expect(modulo).toMatch(/@media \(min-width: 768px\) \{\s*\.le-fila \{[^}]*grid-template-columns: auto 1fr auto/);
    expect(modulo).toMatch(/\.oferta-acciones \{[^}]*flex-direction: column-reverse/);
  });
});

describe('Solo variables del design system', () => {
  it('Sin alias ni colores sueltos', () => {
    const alias = /--(color|espacio|radio|ancho-maximo)[a-z0-9-]*/;
    expect(textoDeTokens).not.toMatch(alias);
    expect(textoPrincipal).not.toMatch(alias);
    for (const nombre of NOMBRES_DE_MODULOS) {
      expect(textoDeModulo(nombre), nombre).not.toMatch(alias);
    }
    expect(estilos).not.toMatch(/#[0-9a-fA-F]{3,8}/);
    expect(estilos).not.toMatch(/(rgba?|hsla?)\(/);
    expect(textoDeTokens).toMatch(/--ds-capa-modal:/);
  });

  it('toda variable que usan los estilos está definida en tokens.css', () => {
    const definidas = new Set([...textoDeTokens.matchAll(/(--ds-[a-z0-9-]+)\s*:/g)].map((m) => m[1]));
    const usadas = new Set([...estilos.matchAll(/var\((--[a-z0-9-]+)/g)].map((m) => m[1]));
    const propias = new Set([...estilos.matchAll(/(--[a-z0-9-]+)\s*:/g)].map((m) => m[1]));
    const sinDefinir = [...usadas].filter((v) => v !== undefined && !definidas.has(v) && !propias.has(v));
    expect(sinDefinir).toEqual([]);
  });
});
