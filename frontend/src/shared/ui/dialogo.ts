import { crearAviso, crearBoton } from './campos.ts';

/**
 * Molde de los diálogos de la aplicación (C-39, D8): título, resumen opcional, aviso, campos y botones, siempre
 * en el mismo orden. El aviso va debajo del título y antes del primer campo. Los botones van "descartar" y después
 * "principal"; el CSS los pone a la izquierda y a la derecha en la computadora, y apilados con el principal arriba
 * en el celular.
 */

export interface OpcionesDialogo {
  readonly titulo: string;
  readonly resumen?: string;
  /** Campos y otros elementos del cuerpo, después del aviso. */
  readonly contenido?: readonly HTMLElement[];
  /** Texto del botón que descarta o cierra ("Cancelar", "Cerrar", "Volver"). */
  readonly textoDescartar: string;
  /** Texto del botón de la acción principal ("Confirmar", "Guardar", "Crear turno"). */
  readonly textoPrincipal: string;
  /** La acción principal es destructiva: el botón es de peligro y el foco empieza en "descartar". */
  readonly peligro?: boolean;
  /** Dónde se agrega el diálogo (por defecto, el documento). */
  readonly contenedor?: HTMLElement;
  /** Se llama cuando el diálogo ya se cerró y se quitó. */
  readonly alCerrar?: () => void;
}

export interface DialogoAbierto {
  readonly dialogo: HTMLDialogElement;
  readonly formulario: HTMLFormElement;
  /** Aviso `role="alert"` para los errores dentro del diálogo. */
  readonly general: HTMLParagraphElement;
  readonly descartar: HTMLButtonElement;
  /** Botón de envío del formulario: la acción principal. */
  readonly principal: HTMLButtonElement;
  /** El mismo botón que `principal`, con el nombre que usan las secciones que escuchan el envío. */
  readonly enviar: HTMLButtonElement;
  cerrar(): void;
}

let contador = 0;

/** Arma un `<dialog>` modal con el molde, lo agrega a la página y lo abre. */
export function abrirDialogo(opciones: OpcionesDialogo): DialogoAbierto {
  contador += 1;
  const dialogo = document.createElement('dialog');
  dialogo.className = 'dialogo';
  const idTitulo = `dialogo-titulo-${contador}`;
  dialogo.setAttribute('aria-labelledby', idTitulo);

  const titulo = document.createElement('h2');
  titulo.id = idTitulo;
  titulo.textContent = opciones.titulo;
  dialogo.append(titulo);
  if (opciones.resumen !== undefined) {
    const resumen = document.createElement('p');
    resumen.className = 'dialogo-resumen';
    resumen.textContent = opciones.resumen;
    dialogo.append(resumen);
  }

  const formulario = document.createElement('form');
  formulario.className = 'formulario';
  formulario.noValidate = true;
  const general = crearAviso('alert');
  const descartar = crearBoton(opciones.textoDescartar);
  descartar.className = 'btn';
  const principal = crearBoton(opciones.textoPrincipal, 'submit');
  principal.className = opciones.peligro === true ? 'btn btn-peligro' : 'btn btn-principal';
  const botones = document.createElement('div');
  botones.className = 'dialogo-acciones';
  botones.append(descartar, principal);
  formulario.append(general, ...(opciones.contenido ?? []), botones);
  dialogo.append(formulario);

  const cerrar = (): void => {
    if (dialogo.open) {
      dialogo.close();
    }
  };
  descartar.addEventListener('click', cerrar);
  dialogo.addEventListener('close', () => {
    dialogo.remove();
    opciones.alCerrar?.();
  });
  (opciones.contenedor ?? document.body).append(dialogo);
  dialogo.showModal();

  // El foco va al primer campo; si no hay, a "descartar" en una acción destructiva y a la acción principal en las demás.
  const primero = formulario.querySelector<HTMLElement>('input, select, textarea');
  (primero ?? (opciones.peligro === true ? descartar : principal)).focus();

  return { dialogo, formulario, general, descartar, principal, enviar: principal, cerrar };
}
