import { formatearFecha } from '../coberturas/modelo.ts';
import { NOMBRE_CARA } from '../ficha/odontograma-modelo.ts';
import { crearBoton } from '../shared/ui/campos.ts';
import type { ItemPresupuesto, Presupuesto } from './api.ts';
import { estadoPresupuesto, formatearPesos, type EstadoEscrito, type Totales } from './modelo.ts';

/**
 * Tarjeta de un presupuesto (C-50, design D5): fecha y estado escrito, vencimiento, profesional, cobertura usada o
 * "Particular", totales con su rótulo y el detalle de los ítems. La comparten la ficha, "Coberturas" y "Mis
 * presupuestos". Con `mostrarPieza: false` (recepción) no dibuja pieza ni cara aunque lleguen (RN-CL-01).
 */

const CLASE_ESTADO: Readonly<Record<EstadoEscrito, string>> = {
  Pendiente: 'etiqueta etiqueta-pendiente',
  Vencido: 'etiqueta etiqueta-vencido',
  Aceptado: 'etiqueta etiqueta-aceptado',
  Rechazado: 'etiqueta etiqueta-rechazado',
};

let contador = 0;

function parrafo(clase: string, ...contenido: (string | Node)[]): HTMLParagraphElement {
  const p = document.createElement('p');
  p.className = clase;
  p.append(...contenido);
  return p;
}

function span(clase: string, texto: string): HTMLSpanElement {
  const s = document.createElement('span');
  if (clase !== '') {
    s.className = clase;
  }
  s.textContent = texto;
  return s;
}

/** Tres importes con su rótulo, siempre escritos. */
export function crearTotales(totales: Totales, rotulos: readonly [string, string, string] = ['Precio', 'Cobertura', 'Copago']): HTMLDListElement {
  const lista = document.createElement('dl');
  lista.className = 'pre-totales';
  const valores = [totales.precio, totales.cobertura, totales.copago];
  rotulos.forEach((rotulo, i) => {
    const caja = document.createElement('div');
    if (i === 2) {
      caja.className = 'pre-copago';
    }
    const dt = document.createElement('dt');
    dt.textContent = rotulo;
    const dd = document.createElement('dd');
    dd.textContent = formatearPesos(valores[i] ?? 0);
    caja.append(dt, dd);
    lista.append(caja);
  });
  return lista;
}

function textoPieza(item: ItemPresupuesto): string {
  if (item.pieza === null) {
    return 'Sin pieza';
  }
  return item.cara === null ? `Pieza ${item.pieza}` : `Pieza ${item.pieza}, cara ${NOMBRE_CARA[item.cara]}`;
}

function crearRenglon(item: ItemPresupuesto, mostrarPieza: boolean): HTMLLIElement {
  const renglon = document.createElement('li');
  renglon.className = 'pre-renglon';
  const datos = parrafo('pre-renglon-datos');
  datos.append(item.codigo === null ? 'Sin código' : span('cob-codigo', item.codigo));
  if (mostrarPieza) {
    datos.append(` · ${textoPieza(item)}`);
  }
  datos.append(` · Cantidad ${item.cantidad} × ${formatearPesos(item.precioUnitario)} · ${item.porcentaje} %`);
  const quien = document.createElement('div');
  quien.append(parrafo('pre-renglon-titulo', item.prestacion), datos);
  renglon.append(quien, crearTotales({ precio: item.importe, cobertura: item.cobertura, copago: item.copago }, ['Importe', 'Cobertura', 'Copago']));
  return renglon;
}

export interface OpcionesTarjeta {
  readonly mostrarPieza: boolean;
  /** Botones de acción ("Aceptar", "Rechazar"), después de "Ver detalle". */
  readonly acciones?: readonly HTMLButtonElement[];
  /** Ayuda para un vencido ("ya no se puede aceptar"). */
  readonly avisoVencido?: string;
  /** Abre el detalle al dibujarla (el presupuesto recién emitido). */
  readonly detalleAbierto?: boolean;
}

export function crearTarjetaPresupuesto(presupuesto: Presupuesto, opciones: OpcionesTarjeta): HTMLLIElement {
  contador += 1;
  const estado = estadoPresupuesto(presupuesto);
  const cerrado = estado === 'Aceptado' || estado === 'Rechazado';
  const tarjeta = document.createElement('li');
  tarjeta.className = cerrado ? 'pre-presupuesto pre-presupuesto-cerrado' : 'pre-presupuesto';
  tarjeta.dataset['presupuesto'] = String(presupuesto.id);

  const fecha = formatearFecha(presupuesto.fecha);
  const datos = document.createElement('div');
  datos.append(parrafo('pre-cabecera', span('', `Presupuesto del ${fecha}`), span(CLASE_ESTADO[estado], estado)));
  const vence = estado === 'Vencido' ? `Venció el ${formatearFecha(presupuesto.venceEl)}` : `Vence el ${formatearFecha(presupuesto.venceEl)}`;
  const meta = cerrado ? [presupuesto.profesional] : [vence, presupuesto.profesional];
  datos.append(parrafo('pre-meta', meta.filter((m) => m !== '').join(' · ')));
  if (presupuesto.cobertura === null) {
    datos.append(parrafo('pre-meta', 'Particular'));
  } else {
    datos.append(
      parrafo(
        'pre-meta',
        `${presupuesto.cobertura.obraSocial} · ${presupuesto.cobertura.plan} · N.º `,
        span('cob-numero', presupuesto.cobertura.numeroAfiliado),
      ),
    );
  }

  const idDetalle = `presupuesto-detalle-${contador}`;
  const ver = crearBoton('Ver detalle');
  ver.className = 'btn btn-chico';
  ver.setAttribute('aria-expanded', 'false');
  ver.setAttribute('aria-controls', idDetalle);
  const acciones = document.createElement('div');
  acciones.className = 'pre-acciones';
  acciones.append(ver, ...(opciones.acciones ?? []));

  tarjeta.append(datos, acciones, crearTotales(presupuesto.totales));
  if (estado === 'Vencido' && opciones.avisoVencido !== undefined) {
    tarjeta.append(parrafo('aviso aviso-info', opciones.avisoVencido));
  }

  let detalle: HTMLUListElement | null = null;
  const alternar = (): void => {
    if (detalle === null) {
      detalle = document.createElement('ul');
      detalle.className = 'pre-detalle';
      detalle.id = idDetalle;
      detalle.setAttribute('aria-label', `Ítems del presupuesto del ${fecha}`);
      detalle.append(...presupuesto.items.map((item) => crearRenglon(item, opciones.mostrarPieza)));
      tarjeta.append(detalle);
      ver.textContent = 'Ocultar detalle';
      ver.setAttribute('aria-expanded', 'true');
    } else {
      detalle.remove();
      detalle = null;
      ver.textContent = 'Ver detalle';
      ver.setAttribute('aria-expanded', 'false');
    }
  };
  ver.addEventListener('click', alternar);
  if (opciones.detalleAbierto === true) {
    alternar();
  }
  return tarjeta;
}
