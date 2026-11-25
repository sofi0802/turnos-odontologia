import type { Prestacion } from '../configuracion/api.ts';
import { botonChico, guardando, type ContextoSeccion } from '../configuracion/ui.ts';
import { abrirDialogoDesde, agregarAyuda, marcarErrores, mostrarRechazo } from '../coberturas/ui.ts';
import { formatearPesos, leerPrecio, type Centavos } from '../presupuestos/modelo.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { cambiarPrecio, listarAranceles, type Arancel } from './api.ts';

/**
 * Precio particular de cada prestación en la pestaña "Prestaciones" (C-50, design D2). Es el gancho `extra` de
 * `montarCatalogo`: suma a cada fila el precio y "Cambiar precio". Los aranceles se piden una vez por pestaña y aparte de
 * la lista: si fallan, la lista se sigue viendo. Solo `ADMIN`.
 */

export const MENSAJE_PRECIO_GUARDADO = 'Precio guardado.';
const PREFIJO_FALLA = 'No pudimos cargar los precios.';

interface FilaPrecio {
  readonly prestacion: Prestacion;
  readonly fila: HTMLLIElement;
  readonly valor: HTMLSpanElement;
  readonly boton: HTMLButtonElement;
}

interface EstadoPrecios {
  aranceles: Map<number, Arancel> | null;
  pedido: number;
  readonly filas: Set<FilaPrecio>;
  readonly barra: HTMLDivElement;
  readonly exito: HTMLParagraphElement;
  readonly error: HTMLParagraphElement;
  readonly reintentar: HTMLButtonElement;
}

const estados = new WeakMap<ContextoSeccion, EstadoPrecios>();

function estadoDe(contexto: ContextoSeccion): EstadoPrecios {
  let estado = estados.get(contexto);
  if (estado === undefined) {
    const barra = document.createElement('div');
    barra.className = 'pre-precios-avisos';
    const exito = crearAviso('status');
    const error = crearAviso('alert');
    const reintentar = crearBoton('Reintentar');
    reintentar.className = 'btn';
    reintentar.hidden = true;
    barra.append(exito, error, reintentar);
    const nuevo: EstadoPrecios = { aranceles: null, pedido: 0, filas: new Set(), barra, exito, error, reintentar };
    reintentar.addEventListener('click', () => cargar(nuevo, contexto));
    estados.set(contexto, nuevo);
    cargar(nuevo, contexto);
    estado = nuevo;
  }
  return estado;
}

/** Pone los avisos arriba de la lista, la primera vez que hay una fila en la página. */
function ubicarBarra(estado: EstadoPrecios): void {
  if (estado.barra.isConnected) {
    return;
  }
  const lista = [...estado.filas].map((f) => f.fila.parentElement).find((ul) => ul?.isConnected === true);
  lista?.before(estado.barra);
}

function dibujarPrecio(estado: EstadoPrecios, fila: FilaPrecio): void {
  const arancel = estado.aranceles?.get(fila.prestacion.id);
  if (estado.aranceles === null) {
    fila.valor.className = 'pre-sin-precio';
    fila.valor.textContent = 'Precio: cargando…';
    fila.boton.disabled = true;
    return;
  }
  fila.boton.disabled = false;
  if (arancel === undefined || arancel.precio === null) {
    fila.valor.className = 'pre-sin-precio';
    fila.valor.textContent = 'Sin precio: no se puede presupuestar';
    return;
  }
  const monto = document.createElement('span');
  monto.className = 'pre-precio';
  monto.textContent = formatearPesos(arancel.precio);
  fila.valor.className = '';
  fila.valor.replaceChildren('Precio particular: ', monto);
}

function redibujar(estado: EstadoPrecios): void {
  for (const fila of [...estado.filas]) {
    if (!fila.fila.isConnected && estado.aranceles !== null) {
      // La lista se volvió a dibujar: la fila vieja ya no está.
      estado.filas.delete(fila);
      continue;
    }
    dibujarPrecio(estado, fila);
  }
}

function cargar(estado: EstadoPrecios, contexto: ContextoSeccion): void {
  estado.pedido += 1;
  const este = estado.pedido;
  estado.error.textContent = '';
  estado.reintentar.hidden = true;
  listarAranceles(contexto.api).then(
    (lista) => {
      if (este !== estado.pedido) {
        return;
      }
      estado.aranceles = new Map(lista.map((a) => [a.prestacionId, a]));
      redibujar(estado);
      ubicarBarra(estado);
    },
    (causa: unknown) => {
      if (este !== estado.pedido || contexto.sesionVencida(causa)) {
        return;
      }
      ubicarBarra(estado);
      estado.error.textContent = `${PREFIJO_FALLA} ${mensajeDeError(causa)}`;
      estado.reintentar.hidden = false;
      for (const fila of estado.filas) {
        fila.valor.textContent = 'Precio: sin datos';
      }
    },
  );
}

function valorInicial(precio: Centavos | null): string {
  return precio === null ? '' : (precio / 100).toFixed(2).replace('.', ',');
}

function dialogoPrecio(estado: EstadoPrecios, contexto: ContextoSeccion, fila: FilaPrecio): void {
  estado.exito.textContent = '';
  const actual = estado.aranceles?.get(fila.prestacion.id)?.precio ?? null;
  const entrada = crearEntrada('text', 'precio');
  entrada.classList.add('control');
  entrada.inputMode = 'decimal';
  entrada.autocomplete = 'off';
  entrada.value = valorInicial(actual);
  const precio = crearCampo('Precio', entrada);
  agregarAyuda(precio, 'En pesos, con hasta dos decimales. Ej.: 15000 o 15000,50.');
  const dialogo = abrirDialogoDesde(fila.boton, {
    titulo: 'Cambiar precio',
    resumen: `${fila.prestacion.nombre}. Los presupuestos ya emitidos no cambian.`,
    contenido: [precio.contenedor],
    textoDescartar: 'Cancelar',
    textoPrincipal: 'Guardar',
  });
  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    dialogo.general.textContent = '';
    const leido = leerPrecio(entrada.value);
    if (!marcarErrores([[precio, 'error' in leido ? leido.error : '']]) || !('centavos' in leido)) {
      return;
    }
    void guardando(dialogo.enviar, () => cambiarPrecio(contexto.api, fila.prestacion.id, leido.centavos)).then(
      (ok) => {
        if (ok) {
          dialogo.cerrar();
          estado.exito.textContent = MENSAJE_PRECIO_GUARDADO;
          cargar(estado, contexto);
        }
      },
      (causa: unknown) =>
        mostrarRechazo(causa, {
          general: dialogo.general,
          campos: { precio: { campo: precio, mensaje: 'Revisá el precio: mayor que cero y con hasta dos decimales.' } },
          sesionVencida: (c) => contexto.sesionVencida(c),
        }),
    );
  });
}

/** Gancho `extra` de "Prestaciones": el precio de la fila y "Cambiar precio", solo para `ADMIN`. */
export function precioDePrestacion(fila: HTMLLIElement, prestacion: Prestacion, contexto: ContextoSeccion): HTMLButtonElement[] {
  if (!contexto.permisos.editaCatalogos) {
    return [];
  }
  const estado = estadoDe(contexto);
  const valor = document.createElement('span');
  const datos = fila.querySelector('.item-datos');
  if (datos === null) {
    const linea = document.createElement('div');
    linea.className = 'item-datos';
    linea.append(valor);
    fila.querySelector('.item-nombre')?.after(linea);
  } else {
    datos.append(valor);
  }
  const boton = botonChico('Cambiar precio');
  boton.setAttribute('aria-label', `Cambiar el precio de ${prestacion.nombre}`);
  const registro: FilaPrecio = { prestacion, fila, valor, boton };
  boton.addEventListener('click', () => dialogoPrecio(estado, contexto, registro));
  estado.filas.add(registro);
  dibujarPrecio(estado, registro);
  return [boton];
}
