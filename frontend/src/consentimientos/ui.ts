import { abrirDialogo, type DialogoAbierto, type OpcionesDialogo } from '../shared/ui/dialogo.ts';
import { crearIcono, type NombreIcono } from '../shared/ui/iconos.ts';
import { formatoContador, normalizar } from './modelo.ts';

/** Piezas compartidas por la pestaña de plantillas y la sección de la ficha (C-48). Todo texto va con `textContent`. */

let contador = 0;

export function nuevoId(base: string): string {
  contador += 1;
  return `${base}-${contador}`;
}

/** Texto plano que respeta los saltos de línea: nunca se interpreta como HTML (design D4). */
export function crearTextoPlano(texto: string, clase = 'cons-texto'): HTMLDivElement {
  const bloque = document.createElement('div');
  bloque.className = clase;
  bloque.textContent = texto;
  return bloque;
}

export function crearEtiqueta(texto: string, clase: string): HTMLSpanElement {
  const etiqueta = document.createElement('span');
  etiqueta.className = `etiqueta ${clase}`;
  etiqueta.textContent = texto;
  return etiqueta;
}

export function conIcono(boton: HTMLButtonElement, icono: NombreIcono): void {
  boton.prepend(crearIcono(icono));
}

export interface CampoConContador {
  readonly contenedor: HTMLElement;
  readonly control: HTMLInputElement | HTMLTextAreaElement;
  mostrarError(mensaje: string): void;
}

export interface OpcionesCampoConContador {
  readonly etiqueta: string;
  readonly limite: number;
  readonly valor?: string;
  readonly multilinea?: boolean;
  readonly filas?: number;
  readonly placeholder?: string;
  /** Ayuda debajo del campo, ligada con `aria-describedby`. */
  readonly ayuda?: string;
  /** Aviso que se anuncia una sola vez al pasar el 90 % y al pasar el límite (design D4). */
  readonly anunciar?: (cantidad: number, limite: number, excedido: boolean) => string;
}

/**
 * Campo de texto con su contador "N/límite", ligado con `aria-describedby`. No tiene `maxlength`: un texto pegado que
 * supera el límite no se recorta, se marca como error (design D4). El contador no se anuncia en cada tecla.
 */
export function crearCampoConContador(opciones: OpcionesCampoConContador): CampoConContador {
  const id = nuevoId('cons-campo');
  const control = opciones.multilinea === true ? document.createElement('textarea') : document.createElement('input');
  control.id = id;
  if (control instanceof HTMLInputElement) {
    control.type = 'text';
  } else {
    control.rows = opciones.filas ?? 16;
  }
  control.autocomplete = 'off';
  control.spellcheck = true;
  if (opciones.placeholder !== undefined) {
    control.placeholder = opciones.placeholder;
  }
  control.value = opciones.valor ?? '';

  const etiqueta = document.createElement('label');
  etiqueta.htmlFor = id;
  etiqueta.textContent = opciones.etiqueta;
  const cuenta = document.createElement('span');
  cuenta.className = 'contador';
  cuenta.id = `${id}-contador`;
  const cabecera = document.createElement('div');
  cabecera.className = 'campo-suave-cabecera';
  cabecera.append(etiqueta, cuenta);

  const error = document.createElement('p');
  error.className = 'campo-error';
  error.id = `${id}-error`;
  const aviso = document.createElement('p');
  aviso.className = 'solo-lectores';
  aviso.setAttribute('role', 'status');

  const descripciones = [cuenta.id];
  const contenedor = document.createElement('div');
  contenedor.className = opciones.multilinea === true ? 'campo-suave cons-texto-editor' : 'campo-suave';
  contenedor.append(cabecera, control);
  if (opciones.ayuda !== undefined) {
    const ayuda = document.createElement('p');
    ayuda.className = 'cons-ayuda';
    ayuda.id = `${id}-ayuda`;
    ayuda.textContent = opciones.ayuda;
    descripciones.push(ayuda.id);
    contenedor.append(ayuda);
  }
  descripciones.push(error.id);
  control.setAttribute('aria-describedby', descripciones.join(' '));
  contenedor.append(error, aviso);

  let zona: 'normal' | 'cerca' | 'excedido' = 'normal';
  const actualizar = (): void => {
    const cantidad = control.value.length;
    cuenta.textContent = formatoContador(cantidad, opciones.limite);
    const excedido = normalizar(control.value).length > opciones.limite;
    cuenta.classList.toggle('excedido', excedido);
    const nueva = excedido ? 'excedido' : cantidad >= opciones.limite * 0.9 ? 'cerca' : 'normal';
    if (nueva !== zona) {
      zona = nueva;
      aviso.textContent = nueva === 'normal' ? '' : (opciones.anunciar?.(cantidad, opciones.limite, nueva === 'excedido') ?? '');
    }
  };
  actualizar();
  control.addEventListener('input', actualizar);

  return {
    contenedor,
    control,
    mostrarError(mensaje) {
      error.textContent = mensaje;
      if (mensaje === '') {
        control.removeAttribute('aria-invalid');
      } else {
        control.setAttribute('aria-invalid', 'true');
      }
    },
  };
}

/** Abre un diálogo con el molde de la aplicación y, al cerrarse, devuelve el foco al botón que lo abrió. */
export function abrirDialogoDesde(origen: HTMLElement, opciones: OpcionesDialogo, clase = ''): DialogoAbierto {
  const abierto = abrirDialogo({
    ...opciones,
    alCerrar: () => {
      opciones.alCerrar?.();
      if (origen.isConnected) {
        origen.focus();
      }
    },
  });
  abierto.formulario.classList.add('cons-form');
  if (clase !== '') {
    abierto.dialogo.classList.add(clase);
  }
  return abierto;
}

export interface OpcionesDialogoDeLectura {
  readonly titulo: string;
  readonly resumen?: string;
  readonly contenido: readonly HTMLElement[];
  readonly clase: string;
  /** El botón que lo abrió: el foco vuelve ahí al cerrar. */
  readonly origen: HTMLElement;
  readonly alCerrar?: () => void;
}

export interface DialogoDeLectura {
  readonly dialogo: HTMLDialogElement;
  readonly cuerpo: HTMLElement;
  readonly cerrar: () => void;
}

/** Diálogo de solo lectura con un único botón "Cerrar" (versiones y documento firmado). */
export function abrirDialogoDeLectura(opciones: OpcionesDialogoDeLectura): DialogoDeLectura {
  const cuerpo = document.createElement('div');
  cuerpo.className = 'cons-dialogo-cuerpo';
  cuerpo.append(...opciones.contenido);
  // Con el molde de los diálogos (C-39, D8): "Cerrar" descarta y no hay acción principal, ni un aviso que no se usa.
  const abierto = abrirDialogo({
    titulo: opciones.titulo,
    ...(opciones.resumen === undefined ? {} : { resumen: opciones.resumen }),
    contenido: [cuerpo],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Cerrar',
    alCerrar: () => {
      opciones.alCerrar?.();
      if (opciones.origen.isConnected) {
        opciones.origen.focus();
      }
    },
  });
  abierto.principal.remove();
  abierto.general.remove();
  abierto.dialogo.classList.add(opciones.clase);
  abierto.descartar.focus();
  return { dialogo: abierto.dialogo, cuerpo, cerrar: abierto.cerrar };
}

export interface OpcionesDescarte {
  readonly abierto: DialogoAbierto;
  /** Hay datos escritos que se perderían al cerrar. */
  readonly hayCambios: () => boolean;
  readonly titulo: string;
  readonly mensaje: string;
}

/**
 * Con datos sin guardar, "Cancelar" y Escape preguntan antes de cerrar; sin datos, cierran sin preguntar. Devuelve una
 * función que cierra la pregunta si sigue abierta (para cuando el diálogo se cierra por otro camino).
 */
export function pedirDescarteAlCancelar(opciones: OpcionesDescarte): () => void {
  const { abierto } = opciones;
  let pregunta: DialogoAbierto | null = null;

  function preguntar(): void {
    pregunta?.cerrar();
    const confirmar = abrirDialogo({
      titulo: opciones.titulo,
      resumen: opciones.mensaje,
      textoDescartar: 'Seguir editando',
      textoPrincipal: 'Descartar',
      peligro: true,
      alCerrar: () => {
        if (pregunta === confirmar) {
          pregunta = null;
        }
        if (abierto.dialogo.open) {
          abierto.descartar.focus();
        }
      },
    });
    pregunta = confirmar;
    confirmar.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      confirmar.cerrar();
      abierto.cerrar();
    });
  }

  // En la fase de captura, antes de que el molde del diálogo cierre con "Cancelar".
  abierto.formulario.addEventListener(
    'click',
    (evento) => {
      if (evento.target instanceof Node && abierto.descartar.contains(evento.target) && opciones.hayCambios()) {
        evento.stopPropagation();
        preguntar();
      }
    },
    true,
  );
  abierto.dialogo.addEventListener('cancel', (evento) => {
    if (opciones.hayCambios()) {
      evento.preventDefault();
      preguntar();
    }
  });
  return () => pregunta?.cerrar();
}
