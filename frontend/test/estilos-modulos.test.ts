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
  // C-48: plantillas y consentimientos informados, vista de firma y bloqueo.
  '26-consentimientos.css': '.cons-fila {',
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

  it('Se importa después del módulo de la lista de espera', () => {
    // C-50 y C-48 suman 25-presupuestos.css y 26-consentimientos.css después: coberturas sigue inmediatamente después de la lista de espera.
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
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

  it('Se importa justo después del módulo de coberturas', () => {
    // C-48 suma 26-consentimientos.css después: presupuestos sigue inmediatamente después de coberturas.
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    const coberturas = importaciones.indexOf('./styles/24-coberturas.css');
    expect(coberturas).toBeGreaterThan(-1);
    expect(importaciones[coberturas + 1]).toBe('./styles/25-presupuestos.css');
  });
});

describe('Módulo de consentimientos (C-48, design D10)', () => {
  it('Sin las reglas que son solo de los mockups', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    for (const regla of ['.nota-mockup', '.rotulo-estado', '.dialogo-estatico', '.nota-contexto', '.mockup-pagina', '.pagina-estados', '.ingreso-mockup', 'Solo mockups']) {
      expect(modulo, regla).not.toContain(regla);
    }
  });

  it('Sin quitar el foco y con las piezas de los mockups', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    expect(modulo).not.toMatch(/outline:\s*none/);
    for (const regla of [
      '.cons-plantillas > li {',
      '.cons-texto {',
      '.cons-fila-revocada {',
      '.firma-pantalla {',
      '.cons-verificacion.alterado {',
      '.bloqueo-pantalla {',
      '.cons-resultado-ok {',
    ]) {
      expect(modulo, regla).toContain(regla);
    }
  });

  it('El texto plano conserva los saltos de línea y no rompe el ancho', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    expect(modulo).toMatch(/\.cons-texto \{[^}]*white-space: pre-wrap/);
    expect(modulo).toMatch(/\.cons-texto \{[^}]*overflow-wrap: anywhere/);
  });

  it('La vista de firma y el bloqueo tapan la ficha con un fondo opaco', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    expect(modulo).toMatch(/\.firma-pantalla \{[^}]*position: fixed[^}]*inset: 0/);
    expect(modulo).toMatch(/\.firma-pantalla \{[^}]*background: var\(--ds-fondo\)/);
    expect(modulo).toMatch(/\.firma-pantalla::backdrop \{[^}]*background: var\(--ds-fondo\)/);
    expect(modulo).toMatch(/\.bloqueo-pantalla \{[^}]*position: fixed[^}]*inset: 0/);
    expect(modulo).toMatch(/\.bloqueo-pantalla \{[^}]*background: var\(--ds-fondo\)/);
  });

  it('El bloqueo usa una capa numérica: --ds-capa-modal es un color y no sirve como z-index', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    expect(modulo).toMatch(/\.bloqueo-pantalla \{[^}]*z-index: \d+/);
    expect(modulo).not.toMatch(/z-index:\s*var\(--ds-capa-modal\)/);
  });

  it('Solo usa tokens que existen en design-system/tokens.css', () => {
    const usados = new Set([...textoDeModulo('26-consentimientos.css').matchAll(/var\((--ds-[a-z0-9-]+)/g)].map((m) => m[1] ?? ''));
    for (const token of usados) {
      expect(textoDeTokens, token).toContain(`${token}:`);
    }
    expect(usados.size).toBeGreaterThan(10);
  });

  it('Los controles de la firma, el bloqueo y las filas miden al menos 44 px', () => {
    const modulo = textoDeModulo('26-consentimientos.css');
    for (const regla of ['.cons-acciones .btn {', '.firma-acciones .btn {', '.bloqueo-form .btn {', '.cons-opcion {', '.cons-acepto {']) {
      const inicio = modulo.indexOf(regla);
      expect(inicio, regla).toBeGreaterThan(-1);
      expect(modulo.slice(inicio, modulo.indexOf('}', inicio)), regla).toContain('min-height: var(--ds-alto-control)');
    }
  });

  it('Se importa al final de styles.css, después del módulo de presupuestos', () => {
    const importaciones = [...textoPrincipal.matchAll(/@import\s+'([^']+)';/g)].map((m) => m[1]);
    expect(importaciones.at(-1)).toBe('./styles/26-consentimientos.css');
    expect(importaciones.at(-2)).toBe('./styles/25-presupuestos.css');
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
