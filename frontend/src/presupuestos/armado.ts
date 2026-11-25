import { listarAranceles, listarCoberturaPlan, type Arancel } from '../aranceles/api.ts';
import { listarNomenclador, obtenerCobertura, type CodigoNomenclador, type Cobertura } from '../coberturas/api.ts';
import { abrirDialogoDesde, marcarErrores, mostrarRechazo, type CampoFormulario, type DestinoDeCampo } from '../coberturas/ui.ts';
import { CARAS } from '../ficha/api.ts';
import { MENSAJE_PIEZA_INVALIDA } from '../ficha/imagenes-modelo.ts';
import { NOMBRE_CARA } from '../ficha/odontograma-modelo.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector, type Campo, type Opcion } from '../shared/ui/campos.ts';
import type { DialogoAbierto } from '../shared/ui/dialogo.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { emitirPresupuesto, listarSugerencias, type Presupuesto } from './api.ts';
import {
  cuerpoDeEmision,
  estimarItem,
  formatearPesos,
  MENSAJE_CANTIDAD,
  MENSAJE_CARA_INVALIDA,
  MENSAJE_CODIGO,
  sumarTotales,
  validarCantidad,
  validarCara,
  validarCodigo,
  validarPieza,
  validarPrestacion,
  type Importes,
} from './modelo.ts';
import { crearTotales } from './vista.ts';

/**
 * "Nuevo presupuesto" (C-50, design D4 y D6): el odontólogo arma los ítems, suma las piezas sugeridas del odontograma y
 * ve un estimado con los precios y la cobertura de hoy. El borrador vive solo en este diálogo: se pierde al cerrarlo.
 */

export const MAXIMO_ITEMS = 50;
export const AYUDA_SIN_PRECIO = 'Las prestaciones sin precio no se pueden presupuestar.';
export const AYUDA_ESTIMADO = 'Estimado: el importe final lo calcula el sistema al emitir.';
export const MENSAJE_SIN_SUGERENCIAS = 'No hay caries ni piezas a tratar en el odontograma.';

export interface OpcionesArmado {
  readonly origen: HTMLButtonElement;
  readonly pacienteId: number;
  readonly nombrePaciente: string;
  readonly api: ClienteApi;
  mensajeDeError(causa: unknown): string;
  vigente(): boolean;
  /** Se llama con el presupuesto que devolvió la API, ya cerrado el diálogo. */
  alEmitir(emitido: Presupuesto | null): void;
}

interface Datos {
  readonly prestaciones: readonly Arancel[];
  readonly codigosPorPrestacion: ReadonlyMap<number, readonly CodigoNomenclador[]>;
  readonly cobertura: Cobertura;
  /** Porcentaje del plan vigente por id de código; vacío si es particular. */
  readonly porcentajes: ReadonlyMap<number, number>;
}

interface Item {
  prestacionId: number | null;
  nomencladorId: number | null;
  pieza: string;
  cara: string;
  cantidad: string;
  origen: string | null;
}

interface ControlesItem {
  readonly prestacion: Campo<HTMLSelectElement>;
  readonly codigo: Campo<HTMLSelectElement> | null;
  readonly pieza: Campo<HTMLInputElement>;
  readonly cara: Campo<HTMLSelectElement>;
  readonly cantidad: Campo<HTMLInputElement>;
}

const ORIGEN: Readonly<Record<string, { texto: string; clase: string }>> = {
  CARIES: { texto: 'Caries', clase: 'etiqueta etiqueta-vencido pre-origen' },
  A_TRATAR: { texto: 'A tratar', clase: 'etiqueta etiqueta-pendiente pre-origen' },
};

function itemVacio(): Item {
  return { prestacionId: null, nomencladorId: null, pieza: '', cara: '', cantidad: '1', origen: null };
}

function esVacio(item: Item): boolean {
  return item.prestacionId === null && item.pieza.trim() === '' && item.cara === '' && item.cantidad.trim() === '1' && item.origen === null;
}

function conEtiqueta<T extends HTMLInputElement | HTMLSelectElement>(rotulo: string, numero: number, control: T): Campo<T> {
  control.classList.add('control');
  const campo = crearCampo(rotulo, control);
  const oculto = document.createElement('span');
  oculto.className = 'solo-lectores';
  oculto.textContent = ` del ítem ${numero}`;
  campo.contenedor.querySelector('label')?.append(oculto);
  return campo;
}

async function cargarDatos(api: ClienteApi, pacienteId: number): Promise<Datos> {
  const [aranceles, nomenclador, cobertura] = await Promise.all([
    listarAranceles(api),
    listarNomenclador(api),
    obtenerCobertura(api, pacienteId),
  ]);
  const afiliacion = cobertura.afiliacion;
  const cargados =
    afiliacion === null || afiliacion.obraSocial.id === null
      ? []
      : await listarCoberturaPlan(api, afiliacion.obraSocial.id, afiliacion.plan.id);
  const codigosPorPrestacion = new Map<number, CodigoNomenclador[]>();
  for (const codigo of nomenclador) {
    if (codigo.activo && codigo.prestacion !== null) {
      codigosPorPrestacion.set(codigo.prestacion.id, [...(codigosPorPrestacion.get(codigo.prestacion.id) ?? []), codigo]);
    }
  }
  return {
    prestaciones: aranceles.filter((a) => a.activa && a.precio !== null),
    codigosPorPrestacion,
    cobertura,
    porcentajes: new Map(cargados.map((c) => [c.nomencladorId, c.porcentaje])),
  };
}

export function abrirArmado(opciones: OpcionesArmado): DialogoAbierto {
  const estado = crearAviso('status');
  const cobertura = document.createElement('p');
  cobertura.className = 'pre-cobertura-usada';
  cobertura.append(crearCargando('Cargando precios y cobertura…'));
  const sugeridas = crearBoton('Agregar piezas sugeridas');
  sugeridas.className = 'btn btn-chico';
  const agregar = crearBoton('Agregar ítem');
  agregar.className = 'btn btn-chico';
  const botones = document.createElement('div');
  botones.className = 'pre-acciones';
  botones.append(sugeridas, agregar);
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = AYUDA_SIN_PRECIO;
  const lista = document.createElement('ol');
  lista.className = 'pre-items';
  lista.setAttribute('aria-label', 'Ítems del presupuesto');
  const pie = document.createElement('div');
  pie.className = 'pre-pie';
  const notaEstimado = document.createElement('p');
  notaEstimado.className = 'ayuda';
  notaEstimado.textContent = AYUDA_ESTIMADO;

  const dialogo = abrirDialogoDesde(opciones.origen, {
    titulo: 'Nuevo presupuesto',
    resumen: `${opciones.nombrePaciente}. Vence a los 30 días. No se puede editar después de emitirlo.`,
    contenido: [estado, cobertura, botones, ayuda, lista, pie],
    textoDescartar: 'Cancelar',
    textoPrincipal: 'Emitir presupuesto',
  });
  dialogo.dialogo.classList.add('pre-dialogo');
  dialogo.formulario.classList.add('pre-armado');
  dialogo.enviar.disabled = true;
  sugeridas.disabled = true;
  agregar.disabled = true;

  let datos: Datos | null = null;
  let items: Item[] = [itemVacio()];
  let controles: ControlesItem[] = [];
  let estimados: (Importes | null)[] = [];

  const mensaje = (causa: unknown): string | null => {
    const texto = opciones.mensajeDeError(causa);
    return texto === '' ? null : texto;
  };

  function codigosDe(prestacionId: number | null): readonly CodigoNomenclador[] {
    return prestacionId === null || datos === null ? [] : (datos.codigosPorPrestacion.get(prestacionId) ?? []);
  }

  /** El código del ítem: el elegido si hay varios, el único si hay uno, o `null`. */
  function codigoDe(item: Item): CodigoNomenclador | null {
    const codigos = codigosDe(item.prestacionId);
    if (codigos.length === 1) {
      return codigos[0] ?? null;
    }
    return codigos.find((c) => c.id === item.nomencladorId) ?? null;
  }

  function lineaEstimado(item: Item, linea: HTMLParagraphElement): Importes | null {
    linea.replaceChildren();
    if (datos === null || item.prestacionId === null) {
      return null;
    }
    const prestacion = datos.prestaciones.find((p) => p.prestacionId === item.prestacionId);
    const codigos = codigosDe(item.prestacionId);
    const codigo = codigoDe(item);
    const partes: (string | Node)[] = [];
    if (codigos.length === 0) {
      partes.push('Sin código: no tiene cobertura');
    } else if (codigo !== null) {
      const span = document.createElement('span');
      span.className = 'cob-codigo';
      span.textContent = codigo.codigo;
      partes.push(span);
    }
    if (prestacion === undefined || prestacion.precio === null || validarCantidad(item.cantidad) !== '' || (codigos.length > 1 && codigo === null)) {
      linea.append(...partes);
      return null;
    }
    const particular = datos.cobertura.afiliacion === null;
    const porcentaje = codigo === null || particular ? 0 : (datos.porcentajes.get(codigo.id) ?? 0);
    const importes = estimarItem({ precio: prestacion.precio, cantidad: Number(item.cantidad.trim()), porcentaje });
    let detalle: string;
    if (particular) {
      detalle = 'Particular (0 %)';
    } else if (codigo !== null && !datos.porcentajes.has(codigo.id)) {
      detalle = 'el plan no lo cubre (0 %)';
    } else {
      detalle = `${porcentaje} %`;
    }
    const copago = document.createElement('strong');
    copago.textContent = `Copago ${formatearPesos(importes.copago)}`;
    if (partes.length > 0) {
      partes.push(' · ');
    }
    partes.push(`${detalle} · Importe ${formatearPesos(importes.importe)} · Cobertura ${formatearPesos(importes.cobertura)} · `, copago);
    linea.append(...partes);
    return importes;
  }

  function actualizarTotales(): void {
    const completos = estimados.filter((e): e is Importes => e !== null);
    pie.replaceChildren(crearTotales(sumarTotales(completos)), notaEstimado);
  }

  function dibujarItems(enfocar?: number): void {
    controles = [];
    estimados = [];
    lista.replaceChildren(...items.map((item, i) => dibujarItem(item, i + 1)));
    agregar.disabled = datos === null || items.length >= MAXIMO_ITEMS;
    actualizarTotales();
    if (enfocar !== undefined) {
      controles[enfocar]?.prestacion.control.focus();
    }
  }

  function dibujarItem(item: Item, numero: number): HTMLLIElement {
    const tarjeta = document.createElement('li');
    tarjeta.className = 'pre-item';
    const cabecera = document.createElement('div');
    cabecera.className = 'pre-item-cabecera';
    const titulo = document.createElement('p');
    titulo.className = 'pre-item-numero';
    titulo.append(`Ítem ${numero}`);
    const origen = item.origen === null ? undefined : ORIGEN[item.origen];
    if (origen !== undefined) {
      const etiqueta = document.createElement('span');
      etiqueta.className = origen.clase;
      etiqueta.textContent = origen.texto;
      titulo.append(' ', etiqueta);
    }
    const quitar = crearBoton('Quitar');
    quitar.className = 'btn btn-chico';
    quitar.setAttribute('aria-label', `Quitar el ítem ${numero}`);
    quitar.disabled = items.length === 1;
    quitar.addEventListener('click', () => {
      items = items.filter((x) => x !== item);
      dibujarItems(Math.min(numero - 1, items.length - 1));
    });
    cabecera.append(titulo, quitar);

    const opcionesPrestacion: Opcion[] = [
      { valor: '', texto: 'Elegí la prestación' },
      ...(datos?.prestaciones ?? []).map((p) => ({ valor: String(p.prestacionId), texto: p.nombre })),
    ];
    const prestacion = conEtiqueta('Prestación', numero, crearSelector(`items[${numero - 1}].prestacionId`, opcionesPrestacion));
    prestacion.control.value = item.prestacionId === null ? '' : String(item.prestacionId);
    const pieza = conEtiqueta('Pieza', numero, crearEntrada('text', `items[${numero - 1}].pieza`));
    pieza.control.inputMode = 'numeric';
    pieza.control.autocomplete = 'off';
    pieza.control.placeholder = 'Opcional';
    pieza.control.value = item.pieza;
    const cara = conEtiqueta(
      'Cara',
      numero,
      crearSelector(`items[${numero - 1}].cara`, [
        { valor: '', texto: 'Sin cara' },
        ...CARAS.map((c) => ({ valor: c, texto: `${NOMBRE_CARA[c].charAt(0).toUpperCase()}${NOMBRE_CARA[c].slice(1)} (${c})` })),
      ]),
    );
    cara.control.value = item.cara;
    const cantidad = conEtiqueta('Cantidad', numero, crearEntrada('text', `items[${numero - 1}].cantidad`));
    cantidad.control.inputMode = 'numeric';
    cantidad.control.value = item.cantidad;

    const campos = document.createElement('div');
    campos.className = 'pre-item-campos';
    campos.append(prestacion.contenedor, pieza.contenedor, cara.contenedor, cantidad.contenedor);

    const codigos = codigosDe(item.prestacionId);
    let codigo: Campo<HTMLSelectElement> | null = null;
    if (codigos.length > 1) {
      codigo = conEtiqueta(
        'Código',
        numero,
        crearSelector(`items[${numero - 1}].nomencladorId`, [
          { valor: '', texto: 'Elegí el código' },
          ...codigos.map((c) => ({ valor: String(c.id), texto: `${c.codigo} · ${c.descripcion}` })),
        ]),
      );
      codigo.control.value = item.nomencladorId === null ? '' : String(item.nomencladorId);
      codigo.contenedor.classList.add('pre-campo-ancho');
      campos.append(codigo.contenedor);
    }

    const linea = document.createElement('p');
    linea.className = 'pre-estimado';
    const indice = numero - 1;
    estimados[indice] = lineaEstimado(item, linea);
    const recalcular = (): void => {
      estimados[indice] = lineaEstimado(item, linea);
      actualizarTotales();
    };

    prestacion.control.addEventListener('change', () => {
      item.prestacionId = prestacion.control.value === '' ? null : Number(prestacion.control.value);
      item.nomencladorId = null;
      // Cambia si hace falta el selector "Código": se vuelve a dibujar el ítem.
      const nuevo = dibujarItem(item, numero);
      tarjeta.replaceWith(nuevo);
      actualizarTotales();
      controles[indice]?.prestacion.control.focus();
    });
    codigo?.control.addEventListener('change', () => {
      item.nomencladorId = codigo.control.value === '' ? null : Number(codigo.control.value);
      recalcular();
    });
    pieza.control.addEventListener('input', () => {
      item.pieza = pieza.control.value;
    });
    cara.control.addEventListener('change', () => {
      item.cara = cara.control.value;
    });
    cantidad.control.addEventListener('input', () => {
      item.cantidad = cantidad.control.value;
      recalcular();
    });

    controles[indice] = { prestacion, codigo, pieza, cara, cantidad };
    tarjeta.append(cabecera, campos, linea);
    return tarjeta;
  }

  function mostrarCobertura(): void {
    if (datos === null) {
      return;
    }
    const afiliacion = datos.cobertura.afiliacion;
    const fuerte = document.createElement('strong');
    if (afiliacion === null) {
      fuerte.textContent = 'Particular';
      cobertura.replaceChildren('Cobertura de hoy: ', fuerte, ' · el paciente paga el importe completo.');
      return;
    }
    fuerte.textContent = `${afiliacion.obraSocial.nombre} · ${afiliacion.plan.nombre}`;
    const numero = document.createElement('span');
    numero.className = 'cob-numero';
    numero.textContent = afiliacion.numeroAfiliado;
    cobertura.replaceChildren('Cobertura de hoy: ', fuerte, ' · N.º ', numero);
  }

  cargarDatos(opciones.api, opciones.pacienteId).then(
    (cargados) => {
      if (!opciones.vigente() || !dialogo.dialogo.isConnected) {
        return;
      }
      datos = cargados;
      mostrarCobertura();
      sugeridas.disabled = false;
      dialogo.enviar.disabled = false;
      dibujarItems(0);
    },
    (causa: unknown) => {
      const texto = mensaje(causa);
      cobertura.replaceChildren();
      if (texto === null) {
        dialogo.cerrar();
        return;
      }
      dialogo.general.textContent = texto;
    },
  );
  dibujarItems();

  sugeridas.addEventListener('click', () => {
    estado.textContent = '';
    sugeridas.disabled = true;
    listarSugerencias(opciones.api, opciones.pacienteId).then(
      (lista) => {
        sugeridas.disabled = false;
        if (lista.length === 0) {
          estado.textContent = MENSAJE_SIN_SUGERENCIAS;
          return;
        }
        const nuevos = lista.map<Item>((s) => ({ ...itemVacio(), pieza: String(s.pieza), cara: s.cara ?? '', origen: s.estado }));
        items = [...items.filter((i) => !esVacio(i)), ...nuevos].slice(0, MAXIMO_ITEMS);
        dibujarItems(Math.max(0, items.length - nuevos.length));
      },
      (causa: unknown) => {
        sugeridas.disabled = false;
        const texto = mensaje(causa);
        if (texto !== null) {
          dialogo.general.textContent = texto;
        }
      },
    );
  });

  agregar.addEventListener('click', () => {
    if (items.length >= MAXIMO_ITEMS) {
      return;
    }
    items = [...items, itemVacio()];
    dibujarItems(items.length - 1);
  });

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    dialogo.general.textContent = '';
    estado.textContent = '';
    if (datos === null || dialogo.enviar.disabled) {
      return;
    }
    const errores: [CampoFormulario, string][] = [];
    items.forEach((item, i) => {
      const c = controles[i];
      if (c === undefined) {
        return;
      }
      errores.push([c.prestacion, validarPrestacion(item.prestacionId)]);
      if (c.codigo !== null) {
        errores.push([c.codigo, validarCodigo(true, item.nomencladorId)]);
      }
      errores.push([c.pieza, validarPieza(item.pieza)], [c.cara, validarCara(item.pieza, item.cara)], [c.cantidad, validarCantidad(item.cantidad)]);
    });
    if (!marcarErrores(errores)) {
      return;
    }
    const cuerpo = cuerpoDeEmision(items.map((item) => ({ ...item, nomencladorId: codigoDe(item)?.id ?? null })));
    const campos: Record<string, DestinoDeCampo> = {};
    controles.forEach((c, i) => {
      campos[`items[${i}].prestacionId`] = { campo: c.prestacion, mensaje: 'Elegí otra prestación.' };
      campos[`items[${i}].pieza`] = { campo: c.pieza, mensaje: MENSAJE_PIEZA_INVALIDA };
      campos[`items[${i}].cara`] = { campo: c.cara, mensaje: MENSAJE_CARA_INVALIDA };
      campos[`items[${i}].cantidad`] = { campo: c.cantidad, mensaje: MENSAJE_CANTIDAD };
      if (c.codigo !== null) {
        campos[`items[${i}].nomencladorId`] = { campo: c.codigo, mensaje: MENSAJE_CODIGO };
      }
    });
    dialogo.enviar.disabled = true;
    emitirPresupuesto(opciones.api, opciones.pacienteId, cuerpo).then(
      (emitido) => {
        dialogo.enviar.disabled = false;
        dialogo.cerrar();
        if (opciones.vigente()) {
          opciones.alEmitir(emitido);
        }
      },
      (causa: unknown) => {
        dialogo.enviar.disabled = false;
        mostrarRechazo(causa, { general: dialogo.general, campos, sesionVencida: (c) => mensaje(c) === null });
      },
    );
  });

  return dialogo;
}
