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
  // C-49: obras sociales, cobertura del paciente y "Mi cobertura".
  '24-coberturas.css': '.cob-afiliacion {',
  // C-50: aranceles, presupuestos y "Mis presupuestos".
  '25-presupuestos.css': '.pre-presupuesto {',
  // C-23: señas.
  '26-senas.css': '.sena-linea {',
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
    // C-49 suma 24-coberturas.css después: la lista de espera sigue inmediatamente después de caja.
    const caja = importaciones.indexOf('./styles/22-caja.css');
    expect(caja).toBeGreaterThan(-1);
    expect(importaciones[caja + 1]).toBe('./styles/23-lista-espera.css');
  });

  it('La fila pasa a tres columnas desde 768 px y las acciones de la oferta se apilan en el teléfono', () => {
    const modulo = textoDeModulo('23-lista-espera.css');
    expect(modulo).toMatch(/@media \(min-width: 768px\) \{\s*\.le-fila \{[^}]*grid-template-columns: auto 1fr auto/);
    expect(modulo).toMatch(/\.oferta-acciones \{[^}]*flex-direction: column-reverse/);
  });
});

describe('Módulo de coberturas (C-49, design D10)', () => {
  it('Sin las reglas que son solo de los mockups', () => {
    const modulo = textoDeModulo('24-coberturas.css');
    for (const regla of ['.nota-mockup', '.rotulo-estado', '.dialogo-estatico', '.nota-contexto', 'Solo mockups']) {
      expect(modulo, regla).not.toContain(regla);
    }
  });

  it('Sin quitar el foco y con las piezas de los mockups', () => {
    const modulo = textoDeModulo('24-coberturas.css');
    expect(modulo).not.toMatch(/outline:\s*none/);
    for (const regla of ['.cob-obra {', '.cob-planes {', '.cob-tarjeta {', '.cob-tarjeta-particular {', '.etiqueta-proxima {', '.mi-cobertura {']) {
      expect(modulo, regla).toContain(regla);
    }
  });

  it('Se importa al final de styles.css, después del módulo de la lista de espera', () => {
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    // C-50 suma 25-presupuestos.css después: coberturas sigue inmediatamente después de la lista de espera.
    const coberturas = importaciones.indexOf('./styles/24-coberturas.css');
    expect(importaciones[coberturas - 1]).toBe('./styles/23-lista-espera.css');
  });
});

describe('Módulo de presupuestos (C-50, design D9)', () => {
  it('Sin las reglas que son solo de los mockups', () => {
    const modulo = textoDeModulo('25-presupuestos.css');
    for (const regla of ['.nota-mockup', '.rotulo-estado', '.dialogo-estatico', '.nota-contexto', 'Solo mockups']) {
      expect(modulo, regla).not.toContain(regla);
    }
  });

  it('Sin quitar el foco, con importes tabulares y las piezas de los mockups', () => {
    const modulo = textoDeModulo('25-presupuestos.css');
    expect(modulo).not.toMatch(/outline:\s*none/);
    expect(modulo).toMatch(/\.pre-totales dd \{[^}]*font-variant-numeric: tabular-nums/);
    for (const regla of ['.pre-presupuesto {', '.pre-item {', '.pre-cob-fila {', '.etiqueta-vencido {', '.dialogo.pre-dialogo {', '.mis-presupuestos {']) {
      expect(modulo, regla).toContain(regla);
    }
  });

  it('Se importa al final de styles.css, después del módulo de coberturas', () => {
    // C-23 agregó 26-senas.css después de este módulo: ya no es el último, pero sigue justo después de coberturas.
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    const presupuestos = importaciones.indexOf('./styles/25-presupuestos.css');
    expect(presupuestos).toBeGreaterThan(-1);
    expect(importaciones[presupuestos - 1]).toBe('./styles/24-coberturas.css');
    expect(importaciones[presupuestos + 1]).toBe('./styles/26-senas.css');
    expect(importaciones.at(-1)).toBe('./styles/26-senas.css');
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
