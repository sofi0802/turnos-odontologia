import { formatearHora } from '../shared/fecha/zona.ts';
import { crearBoton } from '../shared/ui/campos.ts';
import { MEDIOS, type Caja, type Cobro, type CuentaCorriente, type Pendientes } from './api.ts';
import { formatearPesos } from './dinero.ts';
import { diaCorto, diaYMes, fechaYHora, NOMBRE_MEDIO } from './texto.ts';

/** Dibujo de las secciones de la pantalla Caja (spec frontend-caja, design D10 de C-22). */

export const MENSAJE_SIN_CAJA = 'No hay una caja abierta.';
export const MENSAJE_SIN_PENDIENTES = 'No hay cobros pendientes.';
export const MENSAJE_SIN_PRECIO = 'Sin precio: el administrador tiene que cargar el arancel.';
export const MENSAJE_SIN_CARGOS = 'El paciente no tiene cargos.';
export const MENSAJE_SIN_CAJAS = 'No hay cajas en los últimos 30 días.';

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

/** Etiqueta visible más ": " solo para lectores, así el texto completo es "Etiqueta: valor". */
function conEtiqueta(contenedor: HTMLElement, etiqueta: string, valor: string, claseValor: string): void {
  const nombre = elemento('dt', undefined, etiqueta);
  nombre.append(elemento('span', 'solo-lectores', ': '));
  const definicion = elemento('dd');
  definicion.append(elemento('span', claseValor, valor));
  contenedor.append(nombre, definicion);
}

function cifra(etiqueta: string, valor: number, destacada = false): HTMLDivElement {
  const div = elemento('div', destacada ? 'reporte-cifra destacada' : 'reporte-cifra');
  conEtiqueta(div, etiqueta, formatearPesos(valor), 'reporte-numero');
  return div;
}

export interface AccionesEstado {
  readonly puedeOperar: boolean;
  abrirCaja(): void;
  cerrarCaja(caja: Caja): void;
}

/** Estado de la caja: sin abrir (con "Abrir caja" para la recepción) o abierta con sus totales. */
export function dibujarEstado(seccion: HTMLElement, caja: Caja | null, formularioApertura: HTMLElement | null,
                              acciones: AccionesEstado): void {
  const titulo = elemento('h2', undefined, 'Caja del día');
  if (caja === null) {
    const contenido: HTMLElement[] = [titulo, elemento('p', 'caja-vacia', MENSAJE_SIN_CAJA)];
    if (acciones.puedeOperar && formularioApertura !== null) {
      contenido.push(formularioApertura);
    } else {
      contenido.push(elemento('p', 'reporte-nota', 'La abre la recepción al empezar el día.'));
    }
    seccion.replaceChildren(...contenido);
    return;
  }
  const encabezado = elemento('div', 'caja-encabezado');
  const datos = elemento('div');
  datos.append(titulo, elemento('p', 'reporte-detalle',
    `Abierta por ${caja.abiertaPor} · ${diaCorto(caja.fecha)} a las ${formatearHora(caja.abiertaEn)}`));
  encabezado.append(datos);
  if (acciones.puedeOperar) {
    const cerrar = crearBoton('Cerrar caja');
    cerrar.className = 'btn';
    cerrar.addEventListener('click', () => acciones.cerrarCaja(caja));
    encabezado.append(cerrar);
  }
  const cifras = elemento('dl', 'reporte-cifras');
  cifras.append(
    cifra('Monto inicial', caja.montoInicial),
    ...MEDIOS.map((m) => cifra(m.texto, caja.totales[m.valor])),
    cifra('Efectivo esperado', caja.efectivoEsperado, true),
  );
  seccion.replaceChildren(encabezado, cifras);
}

/** Pendientes de cobro y turnos sin precio; "Cobrar" solo si se puede cobrar (recepción con caja abierta). */
export function dibujarPendientes(seccion: HTMLElement, pendientes: Pendientes, puedeCobrar: boolean,
                                  cobrar: (indice: number) => void): void {
  const contenido: HTMLElement[] = [elemento('h2', undefined, 'Pendientes de cobro')];
  if (pendientes.cargos.length === 0) {
    contenido.push(elemento('p', 'reporte-nota', MENSAJE_SIN_PENDIENTES));
  } else {
    const lista = elemento('ul', 'caja-lista');
    pendientes.cargos.forEach((cargo, indice) => {
      const fila = elemento('li', 'caja-fila');
      const datos = elemento('div');
      datos.append(elemento('p', 'caja-fila-titulo', cargo.paciente), elemento('p', 'reporte-detalle',
        `${fechaYHora(cargo.inicio)} · ${cargo.prestacion} · Copago ${formatearPesos(cargo.copago)}`));
      const monto = elemento('div', 'caja-fila-monto');
      monto.append(elemento('span', 'caja-saldo', `Saldo: ${formatearPesos(cargo.saldo)}`));
      if (puedeCobrar) {
        const boton = crearBoton('Cobrar');
        boton.className = 'btn btn-principal';
        boton.setAttribute('aria-label', `Cobrar a ${cargo.paciente}, ${cargo.prestacion}`);
        boton.addEventListener('click', () => cobrar(indice));
        monto.append(boton);
      }
      fila.append(datos, monto);
      lista.append(fila);
    });
    contenido.push(lista);
  }
  if (pendientes.sinPrecio.length > 0) {
    const lista = elemento('ul', 'caja-lista');
    for (const turno of pendientes.sinPrecio) {
      const fila = elemento('li', 'caja-fila sin-precio');
      const datos = elemento('div');
      datos.append(elemento('p', 'caja-fila-titulo', turno.paciente),
        elemento('p', 'reporte-detalle', `${fechaYHora(turno.inicio)} · ${turno.prestacion}`));
      fila.append(datos, elemento('span', 'caja-aviso', MENSAJE_SIN_PRECIO));
      lista.append(fila);
    }
    contenido.push(elemento('h3', undefined, 'Sin precio'), lista);
  }
  seccion.replaceChildren(...contenido);
}

/** Cobros de la caja abierta; los anulados tachados y con "Anulado"; "Anular" solo para el admin. */
export function dibujarCobros(seccion: HTMLElement, caja: Caja | null, puedeAnular: boolean,
                              anular: (cobro: Cobro) => void): void {
  seccion.hidden = caja === null;
  if (caja === null) {
    seccion.replaceChildren();
    return;
  }
  const contenido: HTMLElement[] = [elemento('h2', undefined, 'Cobros de la caja')];
  if (caja.cobros.length === 0) {
    contenido.push(elemento('p', 'reporte-nota', 'Todavía no hay cobros en esta caja.'));
  } else {
    const lista = elemento('ul', 'caja-lista');
    for (const cobro of caja.cobros) {
      const fila = elemento('li', cobro.anulado ? 'caja-cobro anulado' : 'caja-cobro');
      fila.append(elemento('span', 'caja-cobro-hora', formatearHora(cobro.registradoEn)),
        elemento('span', 'caja-cobro-paciente', `${cobro.paciente} · ${NOMBRE_MEDIO[cobro.medio]}`),
        elemento('span', 'caja-cobro-importe', formatearPesos(cobro.importe)));
      if (cobro.anulado) {
        fila.append(elemento('span', 'etiqueta etiqueta-anulado', 'Anulado'));
      } else if (puedeAnular) {
        const boton = crearBoton('Anular');
        boton.className = 'btn btn-peligro';
        boton.setAttribute('aria-label', `Anular el cobro de ${cobro.paciente} de ${formatearPesos(cobro.importe)}`);
        boton.addEventListener('click', () => anular(cobro));
        fila.append(boton);
      }
      lista.append(fila);
    }
    contenido.push(lista);
  }
  seccion.replaceChildren(...contenido);
}

/** Celda con la etiqueta de su columna solo para lectores, así se lee "Pagado: $ 4.000,00". */
function celda(etiqueta: string | null, valor: string): HTMLTableCellElement {
  const td = elemento('td');
  if (etiqueta !== null) {
    td.append(elemento('span', 'solo-lectores', `${etiqueta}: `));
  }
  td.append(valor);
  return td;
}

export function dibujarCuenta(destino: HTMLElement, cuenta: CuentaCorriente): void {
  if (cuenta.cargos.length === 0) {
    destino.replaceChildren(elemento('p', 'reporte-nota', MENSAJE_SIN_CARGOS));
    return;
  }
  const tabla = elemento('table', 'caja-tabla');
  const cabecera = elemento('thead');
  const filaCabecera = elemento('tr');
  for (const titulo of ['Turno', 'Prestación', 'Copago', 'Pagado', 'Saldo']) {
    filaCabecera.append(elemento('th', undefined, titulo));
  }
  cabecera.append(filaCabecera);
  const cuerpo = elemento('tbody');
  for (const cargo of cuenta.cargos) {
    const fila = elemento('tr');
    fila.append(celda(null, diaYMes(cargo.inicio)), celda(null, cargo.prestacion),
      celda('Copago', formatearPesos(cargo.copago)), celda('Pagado', formatearPesos(cargo.pagado)),
      celda('Saldo', formatearPesos(cargo.saldo)));
    cuerpo.append(fila);
  }
  tabla.append(cabecera, cuerpo);
  const desplazable = elemento('div', 'caja-tabla-scroll');
  desplazable.append(tabla);
  destino.replaceChildren(desplazable,
    elemento('p', 'caja-saldo-total', `Saldo total: ${formatearPesos(cuenta.saldoTotal)}`));
}

/** Cajas anteriores; una diferencia distinta de cero lleva su texto y su motivo, no solo el color. */
export function dibujarHistorial(seccion: HTMLElement, cajas: readonly Caja[]): void {
  const contenido: HTMLElement[] = [elemento('h2', undefined, 'Cajas anteriores')];
  const cerradas = cajas.filter((c) => c.cerradaPor !== null);
  if (cerradas.length === 0) {
    contenido.push(elemento('p', 'reporte-nota', MENSAJE_SIN_CAJAS));
  } else {
    const lista = elemento('ul', 'caja-lista');
    for (const caja of cerradas) {
      const fila = elemento('li', 'caja-fila');
      const datos = elemento('div');
      datos.append(elemento('p', 'caja-fila-titulo', diaCorto(caja.fecha)), elemento('p', 'reporte-detalle',
        `${caja.abiertaPor} abrió · ${caja.cerradaPor ?? ''} cerró · esperado ${formatearPesos(caja.efectivoEsperado)}` +
        ` · contado ${formatearPesos(caja.efectivoContado ?? 0)}`));
      const diferencia = caja.diferencia ?? 0;
      const motivo = caja.motivoDiferencia === null ? '' : ` (${caja.motivoDiferencia})`;
      fila.append(datos, elemento('span', diferencia === 0 ? 'caja-diferencia' : 'caja-diferencia con-diferencia',
        `Diferencia: ${formatearPesos(diferencia)}${motivo}`));
      lista.append(fila);
    }
    contenido.push(lista);
  }
  seccion.replaceChildren(...contenido);
}
