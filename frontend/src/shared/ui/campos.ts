let contador = 0;

function nuevoId(base: string): string {
  contador += 1;
  return `${base}-${contador}`;
}

/** Control de formulario con su etiqueta y un mensaje de error enlazado por `aria-describedby`. */
export interface Campo<T extends HTMLInputElement | HTMLSelectElement> {
  readonly contenedor: HTMLElement;
  readonly control: T;
  mostrarError(mensaje: string): void;
}

export function crearCampo<T extends HTMLInputElement | HTMLSelectElement>(
  etiqueta: string,
  control: T,
): Campo<T> {
  const id = nuevoId('campo');
  control.id = id;
  const label = document.createElement('label');
  label.htmlFor = id;
  label.textContent = etiqueta;

  const error = document.createElement('p');
  error.id = `${id}-error`;
  error.className = 'campo-error';
  control.setAttribute('aria-describedby', error.id);

  const contenedor = document.createElement('div');
  contenedor.className = 'campo';
  contenedor.append(label, control, error);

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

export function crearEntrada(tipo: string, nombre: string): HTMLInputElement {
  const entrada = document.createElement('input');
  entrada.type = tipo;
  entrada.name = nombre;
  return entrada;
}

export interface Opcion {
  readonly valor: string;
  readonly texto: string;
}

export function crearSelector(nombre: string, opciones: readonly Opcion[]): HTMLSelectElement {
  const selector = document.createElement('select');
  selector.name = nombre;
  cambiarOpciones(selector, opciones);
  return selector;
}

export function cambiarOpciones(selector: HTMLSelectElement, opciones: readonly Opcion[]): void {
  const elegido = selector.value;
  selector.replaceChildren(
    ...opciones.map(({ valor, texto }) => {
      const opcion = document.createElement('option');
      opcion.value = valor;
      opcion.textContent = texto;
      return opcion;
    }),
  );
  if (opciones.some((o) => o.valor === elegido)) {
    selector.value = elegido;
  }
}

/** Región para avisos que las tecnologías de asistencia leen al cambiar. */
export function crearAviso(rol: 'alert' | 'status'): HTMLParagraphElement {
  const aviso = document.createElement('p');
  aviso.setAttribute('role', rol);
  aviso.className = rol === 'alert' ? 'aviso aviso-error' : 'aviso aviso-ok';
  return aviso;
}

export function crearBoton(texto: string, tipo: 'button' | 'submit' = 'button'): HTMLButtonElement {
  const boton = document.createElement('button');
  boton.type = tipo;
  boton.textContent = texto;
  return boton;
}
