import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorPermiso } from '../shared/api/errores.ts';
import { crearBoton, crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';

/** Datos mínimos de un paciente que devuelve `GET /api/v1/pacientes`: nada de contacto ni de salud. */
export interface PacienteEncontrado {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
}

/** Lo que el buscador necesita de quien lo usa. */
export interface OpcionesBuscador {
  /** Si el error es de sesión vencida, lleva a Mi cuenta y devuelve `true`. */
  sesionVencida(causa: unknown): boolean;
  /** Espera después de la última tecla, en milisegundos (300 por defecto). */
  esperaMs?: number;
  /** C-44: avisa el paciente elegido (la página Pacientes abre su ficha). */
  alElegir?(paciente: PacienteEncontrado): void;
}

/** Campo "Paciente": cumple `Campo<HTMLInputElement>` y guarda el id del paciente elegido. */
export interface BuscadorPacientes extends Campo<HTMLInputElement> {
  /** Identificador del paciente elegido, o `null` si todavía no se eligió ninguno. */
  pacienteId(): number | null;
}

export const ESPERA_BUSQUEDA_MS = 300;
export const LIMITE_RESULTADOS = 10;
export const MENSAJE_PACIENTE_OBLIGATORIO = 'Elegí un paciente de la lista.';

const AYUDA_INICIAL = 'Buscá por DNI o por nombre y apellido.';
const AYUDA_TEXTO_CORTO = 'Escribí al menos 2 letras o 3 números del DNI.';
const MENSAJE_BUSCANDO = 'Buscando…';
const MENSAJE_SIN_RESULTADOS = 'No encontramos pacientes con ese DNI o nombre.';
const MENSAJE_FALLA = 'No pudimos buscar pacientes. Probá de nuevo.';
const MENSAJE_LIMITE = `Se muestran las primeras ${LIMITE_RESULTADOS} coincidencias. Escribí más para acotar.`;
const MINIMO_LETRAS = 2;
const MINIMO_DIGITOS = 3;

let contadorBuscadores = 0;

function esPaciente(valor: unknown): valor is PacienteEncontrado {
  if (typeof valor !== 'object' || valor === null) {
    return false;
  }
  return (
    typeof Reflect.get(valor, 'id') === 'number' &&
    typeof Reflect.get(valor, 'nombre') === 'string' &&
    typeof Reflect.get(valor, 'apellido') === 'string' &&
    typeof Reflect.get(valor, 'dni') === 'string'
  );
}

function leerPacientes(cuerpo: unknown): PacienteEncontrado[] {
  if (!Array.isArray(cuerpo)) {
    return [];
  }
  return cuerpo.filter(esPaciente).map(({ id, nombre, apellido, dni }) => ({ id, nombre, apellido, dni }));
}

/** El texto es demasiado corto para buscar: menos de 2 caracteres, o solo dígitos y menos de 3. */
function esTextoCorto(texto: string): boolean {
  return /^\d+$/.test(texto) ? texto.length < MINIMO_DIGITOS : texto.length < MINIMO_LETRAS;
}

function etiquetaDe(paciente: PacienteEncontrado): string {
  return `${paciente.apellido}, ${paciente.nombre}`;
}

/** "Apellido, Nombre" y "· DNI n", en dos partes para darles estilo. */
function crearDescripcion(paciente: PacienteEncontrado): Array<HTMLSpanElement | string> {
  const nombre = document.createElement('span');
  nombre.className = 'nombre';
  nombre.textContent = etiquetaDe(paciente);
  const dni = document.createElement('span');
  dni.className = 'dni';
  dni.textContent = `· DNI ${paciente.dni}`;
  return [nombre, ' ', dni];
}

/** Lupa decorativa del campo; los lectores de pantalla la ignoran. */
function crearLupa(): SVGSVGElement {
  const espacio = 'http://www.w3.org/2000/svg';
  const lupa = document.createElementNS(espacio, 'svg');
  lupa.setAttribute('class', 'buscador-lupa');
  lupa.setAttribute('viewBox', '0 0 20 20');
  lupa.setAttribute('fill', 'none');
  lupa.setAttribute('stroke', 'currentColor');
  lupa.setAttribute('stroke-width', '2');
  lupa.setAttribute('aria-hidden', 'true');
  const circulo = document.createElementNS(espacio, 'circle');
  circulo.setAttribute('cx', '8.5');
  circulo.setAttribute('cy', '8.5');
  circulo.setAttribute('r', '5.5');
  const mango = document.createElementNS(espacio, 'path');
  mango.setAttribute('d', 'm13 13 4.5 4.5');
  lupa.append(circulo, mango);
  return lupa;
}

/**
 * Buscador de pacientes del personal (design D10): espera 300 ms después de la última tecla, pide
 * `GET /pacientes?q=`, muestra la lista como combobox de ARIA 1.2 y guarda el id del paciente elegido.
 * El texto buscado vive solo en el `<input>`: no se guarda en el navegador ni en la dirección de la página.
 */
export function crearBuscadorPacientes(api: ClienteApi, opciones: OpcionesBuscador): BuscadorPacientes {
  contadorBuscadores += 1;
  const base = `buscador-${contadorBuscadores}`;
  const espera = opciones.esperaMs ?? ESPERA_BUSQUEDA_MS;

  const entrada = crearEntrada('text', 'paciente');
  entrada.autocomplete = 'off';
  entrada.setAttribute('role', 'combobox');
  entrada.setAttribute('aria-autocomplete', 'list');
  entrada.setAttribute('aria-expanded', 'false');
  entrada.setAttribute('aria-controls', `${base}-lista`);
  const campo = crearCampo('Paciente', entrada);
  campo.contenedor.classList.add('buscador');
  const etiqueta = campo.contenedor.querySelector('label');
  const error = campo.contenedor.querySelector<HTMLElement>('.campo-error');
  if (etiqueta === null || error === null) {
    throw new Error('El campo del buscador no tiene etiqueta o área de error');
  }
  etiqueta.id = `${base}-etiqueta`;
  error.setAttribute('aria-live', 'polite');

  const cajaEntrada = document.createElement('div');
  cajaEntrada.className = 'buscador-entrada';
  entrada.replaceWith(cajaEntrada);
  cajaEntrada.append(crearLupa(), entrada);

  const lista = document.createElement('ul');
  lista.id = `${base}-lista`;
  lista.className = 'buscador-lista';
  lista.setAttribute('role', 'listbox');
  lista.setAttribute('aria-label', 'Pacientes encontrados');
  lista.hidden = true;
  const nota = document.createElement('p');
  nota.className = 'buscador-nota';
  nota.hidden = true;
  nota.textContent = MENSAJE_LIMITE;
  const estado = document.createElement('p');
  estado.className = 'buscador-estado';
  estado.setAttribute('role', 'status');
  estado.textContent = AYUDA_INICIAL;

  const elegido = document.createElement('div');
  elegido.className = 'buscador-elegido';
  elegido.setAttribute('role', 'group');
  elegido.setAttribute('aria-labelledby', etiqueta.id);
  elegido.hidden = true;
  const resumen = document.createElement('span');
  const cambiar = crearBoton('Cambiar paciente');
  cambiar.className = 'boton-secundario';
  elegido.append(resumen, cambiar);

  cajaEntrada.after(lista, nota, estado, elegido);

  let resultados: PacienteEncontrado[] = [];
  let activo = -1;
  let abierta = false;
  let seleccion: PacienteEncontrado | null = null;
  let busqueda = 0;
  let temporizador: ReturnType<typeof setTimeout> | undefined;

  function dibujarLista(): void {
    lista.hidden = !abierta;
    nota.hidden = !abierta || resultados.length < LIMITE_RESULTADOS;
    entrada.setAttribute('aria-expanded', String(abierta));
    if (!abierta || activo < 0) {
      entrada.removeAttribute('aria-activedescendant');
    } else {
      entrada.setAttribute('aria-activedescendant', `${base}-op${activo}`);
    }
    [...lista.children].forEach((opcion, i) => opcion.setAttribute('aria-selected', String(i === activo)));
  }

  function cerrarLista(): void {
    abierta = false;
    activo = -1;
    resultados = [];
    lista.replaceChildren();
    dibujarLista();
  }

  function mostrarEstado(mensaje: string, vacio = false): void {
    estado.replaceChildren();
    estado.classList.toggle('buscador-vacio', vacio);
    estado.append(mensaje);
  }

  function elegir(paciente: PacienteEncontrado): void {
    busqueda += 1;
    clearTimeout(temporizador);
    seleccion = paciente;
    cerrarLista();
    campo.mostrarError('');
    resumen.replaceChildren(...crearDescripcion(paciente));
    cajaEntrada.hidden = true;
    estado.hidden = true;
    elegido.hidden = false;
    cambiar.focus();
    opciones.alElegir?.(paciente);
  }

  function mostrarResultados(encontrados: PacienteEncontrado[]): void {
    resultados = encontrados;
    activo = -1;
    lista.replaceChildren();
    if (encontrados.length === 0) {
      abierta = false;
      mostrarEstado(MENSAJE_SIN_RESULTADOS, true);
      dibujarLista();
      return;
    }
    encontrados.forEach((paciente, i) => {
      const opcion = document.createElement('li');
      opcion.id = `${base}-op${i}`;
      opcion.className = 'buscador-opcion';
      opcion.setAttribute('role', 'option');
      opcion.setAttribute('aria-selected', 'false');
      opcion.append(...crearDescripcion(paciente));
      // El mouse no le saca el foco al campo antes de elegir.
      opcion.addEventListener('mousedown', (evento) => evento.preventDefault());
      opcion.addEventListener('click', () => elegir(paciente));
      lista.append(opcion);
    });
    abierta = true;
    mostrarEstado(encontrados.length === 1 ? '1 paciente encontrado.' : `${encontrados.length} pacientes encontrados.`);
    dibujarLista();
  }

  async function buscar(): Promise<void> {
    const texto = entrada.value.trim();
    cerrarLista();
    if (texto === '') {
      mostrarEstado(AYUDA_INICIAL);
      return;
    }
    if (esTextoCorto(texto)) {
      mostrarEstado(AYUDA_TEXTO_CORTO);
      return;
    }
    busqueda += 1;
    const esta = busqueda;
    estado.classList.remove('buscador-vacio');
    const girador = document.createElement('span');
    girador.className = 'girador';
    girador.setAttribute('aria-hidden', 'true');
    estado.replaceChildren(girador, MENSAJE_BUSCANDO);
    try {
      const cuerpo = await api.obtener<unknown>(`/pacientes?q=${encodeURIComponent(texto)}`);
      if (esta === busqueda) {
        mostrarResultados(leerPacientes(cuerpo));
      }
    } catch (causa) {
      if (esta !== busqueda) {
        return;
      }
      mostrarEstado('');
      if (!opciones.sesionVencida(causa)) {
        campo.mostrarError(causa instanceof ErrorPermiso ? mensajeDeError(causa) : MENSAJE_FALLA);
      }
    }
  }

  function moverActivo(paso: 1 | -1): void {
    if (resultados.length === 0) {
      return;
    }
    abierta = true;
    activo = paso === 1 ? (activo + 1) % resultados.length : activo <= 0 ? resultados.length - 1 : activo - 1;
    dibujarLista();
    lista.children[activo]?.scrollIntoView?.({ block: 'nearest' });
  }

  function volverAEscribir(): void {
    seleccion = null;
    elegido.hidden = true;
    cajaEntrada.hidden = false;
    estado.hidden = false;
  }

  entrada.addEventListener('input', () => {
    // Editar el texto deja sin efecto la elección anterior y cualquier búsqueda en curso.
    volverAEscribir();
    busqueda += 1;
    clearTimeout(temporizador);
    campo.mostrarError('');
    temporizador = setTimeout(() => void buscar(), espera);
  });

  entrada.addEventListener('keydown', (evento) => {
    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        moverActivo(1);
        break;
      case 'ArrowUp':
        evento.preventDefault();
        moverActivo(-1);
        break;
      case 'Enter': {
        const paciente = abierta && activo >= 0 ? resultados[activo] : undefined;
        if (paciente !== undefined) {
          // Elegir no envía el formulario.
          evento.preventDefault();
          elegir(paciente);
        }
        break;
      }
      case 'Escape':
        if (abierta) {
          // Cierra solo la lista: sin esto el navegador cierra también el diálogo.
          evento.preventDefault();
          evento.stopPropagation();
          cerrarLista();
        }
        break;
      default:
        break;
    }
  });

  cambiar.addEventListener('click', () => {
    volverAEscribir();
    entrada.value = '';
    mostrarEstado(AYUDA_INICIAL);
    entrada.focus();
  });

  return {
    contenedor: campo.contenedor,
    control: entrada,
    mostrarError: campo.mostrarError,
    pacienteId: () => seleccion?.id ?? null,
  };
}
