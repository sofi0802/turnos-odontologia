import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import {
  cargarPrestaciones,
  cargarProfesionales,
  cargarSillones,
  nombreCompleto,
  type Prestacion,
  type Profesional,
  type Sillon,
} from './api.ts';
import { AVISO_FALLA_PARCIAL, crearExtrasAlta, type ExtrasAlta } from './profesional-alta.ts';
import { crearDetalleProfesional } from './profesional-detalle.ts';
import {
  abrirDialogo,
  botonChico,
  cargarEn,
  crearCabecera,
  crearFila,
  crearLista,
  guardando,
  mostrarErrorEnFormulario,
  type ContextoSeccion,
} from './ui.ts';

/** Campo de un formulario de catálogo; `nombre` es el `name` del control y la clave del JSON. */
interface CampoCatalogo {
  readonly etiqueta: string;
  readonly nombre: string;
  readonly tipo: 'text' | 'number';
}

interface ElementoCatalogo {
  readonly id: number;
  readonly activo: boolean;
}

/** Lo que distingue a cada catálogo; la lista, los formularios y las acciones son comunes (design D1). */
interface DefinicionCatalogo<T extends ElementoCatalogo> {
  readonly titulo: string;
  /** Sustantivo en singular para los botones y diálogos ("sillón"). */
  readonly singular: string;
  /** Recurso de la API, por ejemplo `/sillones`. */
  readonly recurso: string;
  readonly ayudaLectura: string;
  cargar(api: ClienteApi): Promise<T[]>;
  nombre(item: T): string;
  datos(item: T): string[];
  readonly campos: readonly CampoCatalogo[];
  valorInicial(item: T, campo: string): string;
  /** Cuerpo del `POST` o `PUT` a partir de los valores del formulario. */
  cuerpo(valores: ReadonlyMap<string, string>): object;
  /** Contenido extra de la fila (el detalle del profesional). Devuelve los botones que agrega. */
  /** `items` es la lista vigente de la pestaña (se actualiza al recargar), por si el detalle la necesita. */
  extra?(fila: HTMLLIElement, item: T, contexto: ContextoSeccion, items: T[]): HTMLButtonElement[];
  /** Campos que suma el formulario de alta (el alta completa del profesional, design D14). */
  extrasAlta?(contexto: ContextoSeccion, items: T[]): ExtrasAlta;
}

function texto(valores: ReadonlyMap<string, string>, nombre: string): string {
  return (valores.get(nombre) ?? '').trim();
}

function numeroONulo(valor: string): number | null {
  if (valor.trim() === '') {
    return null;
  }
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

function montarCatalogo<T extends ElementoCatalogo>(
  panel: HTMLElement,
  contexto: ContextoSeccion,
  definicion: DefinicionCatalogo<T>,
): void {
  const edita = contexto.permisos.editaCatalogos;
  const contenido = document.createElement('div');
  const errorAccion = crearAviso('alert');

  let agregar: HTMLButtonElement | undefined;
  if (edita) {
    agregar = crearBoton(`Agregar ${definicion.singular}`);
    agregar.addEventListener('click', () => abrirFormulario(null));
  }
  panel.append(crearCabecera(definicion.titulo, agregar), errorAccion, contenido);
  if (!edita) {
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = definicion.ayudaLectura;
    panel.append(ayuda);
  }

  const vigentes: T[] = [];
  const recargar = cargarEn(
    contenido,
    contexto,
    () => definicion.cargar(contexto.api),
    (items, contenedor) => {
      vigentes.splice(0, vigentes.length, ...items);
      const lista = crearLista();
      lista.append(...items.map(dibujarFila));
      contenedor.append(lista);
    },
  );

  function dibujarFila(item: T): HTMLLIElement {
    const acciones: HTMLButtonElement[] = [];
    if (edita) {
      const editar = botonChico('Editar');
      editar.addEventListener('click', () => abrirFormulario(item));
      const cambiarEstado = item.activo ? botonChico('Desactivar', 'peligro') : botonChico('Activar');
      cambiarEstado.addEventListener('click', () => {
        errorAccion.textContent = '';
        const accion = item.activo ? 'desactivar' : 'activar';
        void guardando(cambiarEstado, () => contexto.api.crear(`${definicion.recurso}/${item.id}/${accion}`)).then(
          (ok) => {
            if (ok) {
              recargar();
            }
          },
          (causa: unknown) => {
            if (!contexto.sesionVencida(causa)) {
              errorAccion.textContent = mensajeDeError(causa);
            }
          },
        );
      });
      acciones.push(editar, cambiarEstado);
    }
    const fila = crearFila(definicion.nombre(item), definicion.datos(item), { activo: item.activo, acciones });
    if (definicion.extra !== undefined) {
      const botones = definicion.extra(fila, item, contexto, vigentes);
      let caja = fila.querySelector('.item-acciones');
      if (caja === null && botones.length > 0) {
        caja = document.createElement('div');
        caja.className = 'item-acciones';
        fila.firstElementChild?.after(caja);
      }
      caja?.prepend(...botones);
    }
    return fila;
  }

  function abrirFormulario(item: T | null): void {
    errorAccion.textContent = '';
    const campos: Campo<HTMLInputElement>[] = definicion.campos.map((c) => {
      const entrada = crearEntrada(c.tipo, c.nombre);
      entrada.classList.add('control');
      if (c.tipo === 'number') {
        entrada.min = '5';
        entrada.step = '5';
      }
      if (item !== null) {
        entrada.value = definicion.valorInicial(item, c.nombre);
      }
      return crearCampo(c.etiqueta, entrada);
    });
    const titulo = item === null ? `Agregar ${definicion.singular}` : `Editar ${definicion.singular}`;
    const extras = item === null ? definicion.extrasAlta?.(contexto, vigentes) : undefined;
    const dialogo = abrirDialogo(
      titulo,
      [...campos.map((c) => c.contenedor), ...(extras?.nodos ?? [])],
      'Guardar',
      item === null ? {} : { resumen: definicion.nombre(item) },
    );
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const valores = new Map(campos.map((c) => [c.control.name, c.control.value]));
      const cuerpo = definicion.cuerpo(valores);
      // Falla de un pedido posterior al alta: el elemento ya existe (design D14).
      let fallaParcial: unknown = null;
      void guardando(dialogo.enviar, async () => {
        if (item !== null) {
          await contexto.api.actualizar(`${definicion.recurso}/${item.id}`, cuerpo);
          return;
        }
        const creado = await contexto.api.crear<unknown>(definicion.recurso, cuerpo);
        if (extras === undefined) {
          return;
        }
        try {
          const id = creado !== null && typeof creado === 'object' ? Reflect.get(creado, 'id') : undefined;
          if (typeof id !== 'number') {
            throw new Error(`Respuesta inesperada de ${definicion.recurso}`);
          }
          await extras.completar(id);
        } catch (causa) {
          fallaParcial = causa;
        }
      }).then(
        (ok) => {
          if (ok) {
            dialogo.cerrar();
            recargar();
            if (fallaParcial !== null && !contexto.sesionVencida(fallaParcial)) {
              errorAccion.textContent = `${AVISO_FALLA_PARCIAL} ${mensajeDeError(fallaParcial)}`;
            }
          }
        },
        (causa: unknown) => {
          // El formulario sigue abierto con sus datos y el error a la vista.
          mostrarErrorEnFormulario(causa, dialogo.general, campos, contexto);
        },
      );
    });
  }
}

/** "Ver detalle" / "Ocultar detalle" de un profesional, con sus especialidades y su sillón habitual. */
function detalleDeProfesional(
  fila: HTMLLIElement,
  item: Profesional,
  contexto: ContextoSeccion,
  todos: Profesional[],
): HTMLButtonElement[] {
  let actual = item;
  let detalle: HTMLDivElement | null = null;
  const boton = botonChico('Ver detalle');
  boton.setAttribute('aria-expanded', 'false');
  boton.addEventListener('click', () => {
    if (detalle === null) {
      detalle = crearDetalleProfesional(actual, contexto, todos, (sillonId) => {
        actual = { ...actual, sillonHabitualId: sillonId };
        // La lista compartida se actualiza para que los demás detalles vean el sillón ya asignado.
        const posicion = todos.findIndex((p) => p.id === actual.id);
        if (posicion >= 0) {
          todos[posicion] = actual;
        }
      });
      fila.append(detalle);
      fila.classList.add('item-elegido');
      boton.textContent = 'Ocultar detalle';
      boton.setAttribute('aria-expanded', 'true');
    } else {
      detalle.remove();
      detalle = null;
      fila.classList.remove('item-elegido');
      boton.textContent = 'Ver detalle';
      boton.setAttribute('aria-expanded', 'false');
    }
  });
  return [boton];
}

const PROFESIONALES: DefinicionCatalogo<Profesional> = {
  titulo: 'Profesionales',
  singular: 'profesional',
  recurso: '/profesionales',
  ayudaLectura: 'Solo el administrador puede agregar o cambiar profesionales.',
  cargar: cargarProfesionales,
  nombre: nombreCompleto,
  datos: (p) => (p.matricula === '' ? [] : [`Matrícula ${p.matricula}`]),
  campos: [
    { etiqueta: 'Nombre', nombre: 'nombre', tipo: 'text' },
    { etiqueta: 'Apellido', nombre: 'apellido', tipo: 'text' },
    { etiqueta: 'Matrícula', nombre: 'matricula', tipo: 'text' },
  ],
  valorInicial: (p, campo) => (campo === 'nombre' ? p.nombre : campo === 'apellido' ? p.apellido : p.matricula),
  cuerpo: (v) => ({ nombre: texto(v, 'nombre'), apellido: texto(v, 'apellido'), matricula: texto(v, 'matricula') }),
  extra: detalleDeProfesional,
  extrasAlta: crearExtrasAlta,
};

const SILLONES: DefinicionCatalogo<Sillon> = {
  titulo: 'Sillones',
  singular: 'sillón',
  recurso: '/sillones',
  ayudaLectura: 'Solo el administrador puede agregar o cambiar sillones.',
  cargar: cargarSillones,
  nombre: (s) => s.nombre,
  datos: () => [],
  campos: [{ etiqueta: 'Nombre', nombre: 'nombre', tipo: 'text' }],
  valorInicial: (s) => s.nombre,
  cuerpo: (v) => ({ nombre: texto(v, 'nombre') }),
};

const PRESTACIONES: DefinicionCatalogo<Prestacion> = {
  titulo: 'Prestaciones',
  singular: 'prestación',
  recurso: '/prestaciones',
  ayudaLectura: 'Solo el administrador puede agregar o cambiar prestaciones.',
  cargar: cargarPrestaciones,
  nombre: (p) => p.nombre,
  datos: (p) => [`${p.duracionMinutos} min`],
  campos: [
    { etiqueta: 'Nombre', nombre: 'nombre', tipo: 'text' },
    { etiqueta: 'Duración (minutos)', nombre: 'duracionMinutos', tipo: 'number' },
  ],
  valorInicial: (p, campo) => (campo === 'nombre' ? p.nombre : String(p.duracionMinutos)),
  // La duración se valida en el dominio (`DURACION_INVALIDA`): se envía tal cual.
  cuerpo: (v) => ({ nombre: texto(v, 'nombre'), duracionMinutos: numeroONulo(v.get('duracionMinutos') ?? '') }),
};

export function montarProfesionales(panel: HTMLElement, contexto: ContextoSeccion): void {
  montarCatalogo(panel, contexto, PROFESIONALES);
}

export function montarSillones(panel: HTMLElement, contexto: ContextoSeccion): void {
  montarCatalogo(panel, contexto, SILLONES);
}

export function montarPrestaciones(panel: HTMLElement, contexto: ContextoSeccion): void {
  montarCatalogo(panel, contexto, PRESTACIONES);
}
