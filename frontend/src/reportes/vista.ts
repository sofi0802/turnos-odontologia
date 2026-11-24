import { crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import type { FilaOcupacion, Reporte } from './consulta.ts';

/** Dibujo de los bloques de la pantalla Reportes (spec frontend-reportes, design D10 de C-32). Sin datos de pacientes. */

export const MENSAJE_SIN_SILLONES = 'La ocupación por sillón se ve sin filtro de profesional.';
export const MENSAJE_SIN_TURNOS = 'No hay turnos en este período.';
export const MENSAJE_SIN_AUSENCIAS_PROFESIONALES = 'Sin ausencias ni licencias en el período.';
export const MENSAJE_SIN_AUSENTISMO = 'sin turnos atendidos ni ausentes';
export const AYUDA_OCUPACION =
  'Minutos de turnos no cancelados sobre los minutos de atención (horario menos ausencias, licencias y bloqueos). ' +
  'Con sobreturnos puede pasar del 100 %.';

const NUMERO = new Intl.NumberFormat('es-AR');
const HORAS = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });
const TIPOS: Readonly<Record<string, string>> = { AUSENCIA: 'Ausencia', LICENCIA: 'Licencia' };

function elemento<K extends keyof HTMLElementTagNameMap>(etiqueta: K, clase?: string, contenido?: string) {
  const nuevo = document.createElement(etiqueta);
  if (clase !== undefined) {
    nuevo.className = clase;
  }
  if (contenido !== undefined) {
    nuevo.textContent = contenido;
  }
  return nuevo;
}

function panel(titulo: string, ...contenido: HTMLElement[]): HTMLElement {
  const seccion = elemento('section', 'panel reporte-panel');
  seccion.append(elemento('h2', undefined, titulo), ...contenido);
  return seccion;
}

/** Fila con el porcentaje como texto y una barra que no depende solo del color (design D10). */
function filaOcupacion(fila: FilaOcupacion): HTMLLIElement {
  const item = elemento('li', 'reporte-fila');
  const cabecera = elemento('div', 'reporte-fila-cabecera');
  cabecera.append(elemento('span', 'reporte-nombre', fila.nombre));
  if (fila.porcentaje === null) {
    cabecera.append(elemento('span', 'reporte-valor sin-horario', 'Sin horario'));
    item.append(cabecera, elemento('span', 'reporte-detalle', `${NUMERO.format(fila.minutosOcupados)} min ocupados`));
    return item;
  }
  cabecera.append(elemento('span', 'reporte-valor', `${fila.porcentaje} %`));
  const medidor = elemento('div', fila.porcentaje > 100 ? 'medidor excedido' : 'medidor');
  medidor.setAttribute('role', 'meter');
  medidor.setAttribute('aria-label', `Ocupación de ${fila.nombre}`);
  medidor.setAttribute('aria-valuemin', '0');
  medidor.setAttribute('aria-valuemax', String(Math.max(100, fila.porcentaje)));
  medidor.setAttribute('aria-valuenow', String(fila.porcentaje));
  const relleno = elemento('span', 'medidor-relleno');
  relleno.style.width = `${Math.min(100, fila.porcentaje)}%`;
  medidor.append(relleno);
  item.append(
    cabecera,
    medidor,
    elemento(
      'span',
      'reporte-detalle',
      `${NUMERO.format(fila.minutosOcupados)} de ${NUMERO.format(fila.minutosAtencion)} min`,
    ),
  );
  return item;
}

function listaOcupacion(filas: readonly FilaOcupacion[]): HTMLUListElement {
  const lista = elemento('ul', 'reporte-lista');
  lista.append(...filas.map(filaOcupacion));
  return lista;
}

function columna(titulo: string, contenido: HTMLElement): HTMLDivElement {
  const div = elemento('div');
  div.append(elemento('h3', undefined, titulo), contenido);
  return div;
}

function bloqueOcupacion(reporte: Reporte): HTMLElement {
  const columnas = elemento('div', 'reporte-columnas');
  const sillones =
    reporte.profesionalId === null
      ? listaOcupacion(reporte.ocupacion.sillones)
      : elemento('p', 'reporte-nota', MENSAJE_SIN_SILLONES);
  columnas.append(
    columna('Por profesional', listaOcupacion(reporte.ocupacion.profesionales)),
    columna('Por sillón', sillones),
  );
  const seccion = panel('Ocupación', columnas, elemento('p', 'reporte-ayuda', AYUDA_OCUPACION));
  seccion.classList.add('reporte-ocupacion');
  return seccion;
}

/** Una cifra como término y definición: "Online" y "3 (50 %)". */
function cifra(etiqueta: string, valor: string, detalle?: string): HTMLDivElement {
  const div = elemento('div', 'reporte-cifra');
  const definicion = elemento('dd');
  definicion.append(elemento('span', 'reporte-numero', valor));
  if (detalle !== undefined) {
    definicion.append(elemento('span', 'reporte-detalle', ` (${detalle})`));
  }
  div.append(elemento('dt', undefined, etiqueta), definicion);
  return div;
}

function cifras(...items: HTMLDivElement[]): HTMLDListElement {
  const lista = elemento('dl', 'reporte-cifras');
  lista.append(...items);
  return lista;
}

function porcentajeDe(parte: number, total: number): string | undefined {
  return total === 0 ? undefined : `${Math.round((parte * 100) / total)} %`;
}

function bloqueOrigen(reporte: Reporte): HTMLElement {
  const { online, consultorio, sinDato } = reporte.origen;
  const total = online + consultorio + sinDato;
  const items = [
    cifra('Online', NUMERO.format(online), porcentajeDe(online, total)),
    cifra('Del consultorio', NUMERO.format(consultorio), porcentajeDe(consultorio, total)),
  ];
  if (sinDato > 0) {
    items.push(cifra('Sin dato', NUMERO.format(sinDato), porcentajeDe(sinDato, total)));
  }
  return panel('Origen de los turnos', cifras(...items));
}

function horas(minutos: number): string {
  return `${HORAS.format(minutos / 60)} h`;
}

function bloqueAusencias(reporte: Reporte): HTMLElement {
  const { ausentes, tasa, sinRegistrar } = reporte.ausencias.pacientes;
  const pacientes = cifras(
    cifra('Ausentes', NUMERO.format(ausentes)),
    cifra('Ausentismo', tasa === null ? MENSAJE_SIN_AUSENTISMO : `${tasa} %`),
    cifra('Sin registrar', NUMERO.format(sinRegistrar)),
  );
  const ausenciasProfesionales = reporte.ausencias.profesionales;
  let profesionales: HTMLElement;
  if (ausenciasProfesionales.length === 0) {
    profesionales = elemento('p', 'reporte-nota', MENSAJE_SIN_AUSENCIAS_PROFESIONALES);
  } else {
    profesionales = elemento('ul', 'reporte-ausencias');
    profesionales.append(
      ...ausenciasProfesionales.map((a) =>
        elemento('li', undefined, `${a.nombre} · ${TIPOS[a.tipo] ?? a.tipo} · ${horas(a.minutos)}`),
      ),
    );
  }
  return panel(
    'Ausencias',
    elemento('h3', undefined, 'Pacientes'),
    pacientes,
    elemento('h3', undefined, 'Profesionales'),
    profesionales,
  );
}

function bloqueCancelaciones(reporte: Reporte): HTMLElement {
  const { porPaciente, porConsultorio, porReprogramacion, sinDato } = reporte.cancelaciones;
  const items = [
    cifra('Por el paciente', NUMERO.format(porPaciente)),
    cifra('Por el consultorio', NUMERO.format(porConsultorio)),
    cifra('Por reprogramación', NUMERO.format(porReprogramacion)),
  ];
  if (sinDato > 0) {
    items.push(cifra('Sin dato', NUMERO.format(sinDato)));
  }
  return panel('Cancelaciones', cifras(...items));
}

/** Sin turnos ni ausencias de profesionales en el período: se muestra el estado vacío (spec "Período vacío"). */
export function estaVacio(reporte: Reporte): boolean {
  const { online, consultorio, sinDato } = reporte.origen;
  const c = reporte.cancelaciones;
  return (
    online + consultorio + sinDato + c.porPaciente + c.porConsultorio + c.porReprogramacion + c.sinDato === 0 &&
    reporte.ausencias.profesionales.length === 0
  );
}

export function dibujarReporte(contenedor: HTMLElement, reporte: Reporte): void {
  if (estaVacio(reporte)) {
    contenedor.replaceChildren(crearEstadoVacio({ titulo: 'Sin datos', texto: MENSAJE_SIN_TURNOS }));
    return;
  }
  const bloques = elemento('div', 'reportes-bloques');
  bloques.append(bloqueOcupacion(reporte), bloqueOrigen(reporte), bloqueAusencias(reporte), bloqueCancelaciones(reporte));
  contenedor.replaceChildren(bloques);
}
