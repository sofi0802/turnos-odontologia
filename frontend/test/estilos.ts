/**
 * Los estilos están partidos en módulos (`src/styles/NN-nombre.css`) que `src/styles.css` importa en orden
 * (C-39, etapa A). Los tests que comprueban reglas de CSS leen el texto de todos los módulos juntos, en
 * el orden de importación, igual que el archivo único de antes.
 *
 * Vitest devuelve vacío al leer un `.css` con `import.meta.glob`, aunque se pida `?raw`; por eso cada módulo
 * se importa por su nombre. Un módulo nuevo se agrega acá, y el test "Módulos importados en orden" avisa si falta.
 */
import tokens from '../../design-system/tokens.css?raw';
import principal from '../src/styles.css?raw';
import base from '../src/styles/01-base.css?raw';
import formularios from '../src/styles/02-formularios.css?raw';
import agenda from '../src/styles/03-agenda.css?raw';
import dialogos from '../src/styles/04-dialogos.css?raw';
import reprogramar from '../src/styles/05-reprogramar.css?raw';
import componentes from '../src/styles/06-componentes.css?raw';
import selectorPrestaciones from '../src/styles/07-selector-prestaciones.css?raw';
import paciente from '../src/styles/08-paciente.css?raw';
import buscadorPacientes from '../src/styles/09-buscador-pacientes.css?raw';
import portada from '../src/styles/10-portada.css?raw';
import nuevoTurno from '../src/styles/11-nuevo-turno.css?raw';
import configuracion from '../src/styles/12-configuracion.css?raw';
import layout from '../src/styles/13-layout.css?raw';
import estados from '../src/styles/14-estados.css?raw';
import estadosVacios from '../src/styles/15-estados-vacios.css?raw';
import cuentas from '../src/styles/16-cuentas.css?raw';
import ficha from '../src/styles/17-ficha.css?raw';
import sobreturno from '../src/styles/18-sobreturno.css?raw';

const MODULOS: Readonly<Record<string, string>> = {
  '01-base.css': base,
  '02-formularios.css': formularios,
  '03-agenda.css': agenda,
  '04-dialogos.css': dialogos,
  '05-reprogramar.css': reprogramar,
  '06-componentes.css': componentes,
  '07-selector-prestaciones.css': selectorPrestaciones,
  '08-paciente.css': paciente,
  '09-buscador-pacientes.css': buscadorPacientes,
  '10-portada.css': portada,
  '11-nuevo-turno.css': nuevoTurno,
  '12-configuracion.css': configuracion,
  '13-layout.css': layout,
  '14-estados.css': estados,
  '15-estados-vacios.css': estadosVacios,
  '16-cuentas.css': cuentas,
  '17-ficha.css': ficha,
  '18-sobreturno.css': sobreturno,
};

/** Archivos que hay en `src/styles/`, ordenados (el prefijo numérico fija el orden). Solo los nombres, sin leerlos. */
export const MODULOS_EN_DISCO: readonly string[] = Object.keys(import.meta.glob('../src/styles/*.css'))
  .map((ruta) => ruta.replace('../src/styles/', ''))
  .sort();

/** Módulos que conoce este ayudante, en el orden de `styles.css`. */
export const NOMBRES_DE_MODULOS: readonly string[] = Object.keys(MODULOS);

/** Texto de `design-system/tokens.css`, la única fuente de valores visuales. */
export const textoDeTokens: string = tokens;

/** Texto de `src/styles.css` (solo las importaciones). */
export const textoPrincipal: string = principal;

/** Texto de un módulo por su nombre de archivo. */
export function textoDeModulo(nombre: string): string {
  const texto = MODULOS[nombre];
  if (texto === undefined) {
    throw new Error(`El módulo ${nombre} no está en test/estilos.ts`);
  }
  return texto;
}

/** Todos los estilos, en el orden en que los importa `styles.css`. */
export const estilos: string = NOMBRES_DE_MODULOS.map(textoDeModulo).join('');
