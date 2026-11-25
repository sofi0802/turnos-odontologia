import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorGenerico, ErrorSesion } from '../shared/api/errores.ts';
import { debeCambiarContrasena, iniciarSesion } from '../shared/sesion/sesion.ts';
import { crearBoton, crearCampo } from '../shared/ui/campos.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import type { FinDeFirma } from './firma.ts';
import { borrarMarca } from './marca.ts';

/**
 * "Devolvé el equipo al profesional" (C-48, design D12): la pantalla opaca que sigue a cualquier final de la firma. No
 * es un `<dialog>`: vive en `document.body`, fuera de la raíz de la aplicación, que queda inerte, y Escape no tiene
 * nada que cerrar. No muestra nada del paciente ni del consentimiento. Al mostrarse pide `POST /logout`: la sesión del
 * odontólogo se termina en el servidor y solo vuelve con su contraseña, que se comprueba con `POST /public/login`.
 */

export const TITULO_DEL_BLOQUEO = 'Devolvé el equipo al profesional';
export const MENSAJE_SESION_CERRADA = 'Por seguridad, cerramos la sesión. Ingresá de nuevo.';
export const MENSAJE_SIN_COMPROBAR = 'No pudimos comprobar la contraseña. Probá de nuevo.';
export const INTENTOS_DE_CONTRASENA = 3;

const CASOS: Readonly<Record<FinDeFirma['tipo'], string>> = {
  firmada: 'Listo: la firma quedó registrada.',
  cancelada: 'La firma se canceló. No se guardó nada.',
  incompleta: 'La firma no se completó.',
  'sesion-vencida': 'La firma no se completó.',
  dudosa: 'No pudimos confirmar si la firma se guardó. El profesional lo va a revisar.',
};

/** La línea del bloqueo según cómo terminó la firma. */
export function textoDelCaso(tipo: FinDeFirma['tipo']): string {
  return CASOS[tipo];
}

function textoDeIntentos(quedan: number): string {
  return quedan === 1 ? 'Contraseña incorrecta. Te queda 1 intento.' : `Contraseña incorrecta. Te quedan ${quedan} intentos.`;
}

export interface OpcionesBloqueo {
  readonly api: ClienteApi;
  /** Usuario de la sesión que abrió la ficha: se muestra como texto fijo, nunca como campo. */
  readonly usuario: string;
  readonly fin: FinDeFirma['tipo'];
  /** La contraseña es la del mismo usuario y con rol `ODONTOLOGO`: el bloqueo ya se quitó y hay que reabrir la ficha. */
  alDesbloquear(): void;
  /** Tres intentos fallidos o una respuesta inesperada: el bloqueo ya se quitó y hay que ir al ingreso con el aviso. */
  alIngreso(aviso: string): void;
  /** La cuenta tiene una contraseña temporal (C-41): el bloqueo ya se quitó y sigue el cambio de contraseña. */
  alCambioDeContrasena(): void;
}

export interface Bloqueo {
  readonly elemento: HTMLElement;
}

let contador = 0;

export function mostrarBloqueo(opciones: OpcionesBloqueo): Bloqueo {
  contador += 1;
  const idTitulo = `bloqueo-titulo-${contador}`;
  const pantalla = document.createElement('div');
  pantalla.className = 'bloqueo-pantalla';
  const tarjeta = document.createElement('section');
  tarjeta.className = 'bloqueo-tarjeta';
  tarjeta.setAttribute('aria-labelledby', idTitulo);

  const icono = document.createElement('span');
  icono.className = 'bloqueo-icono';
  icono.append(crearIcono('candado'));
  const titulo = document.createElement('h1');
  titulo.id = idTitulo;
  titulo.textContent = TITULO_DEL_BLOQUEO;
  const caso = document.createElement('p');
  caso.className = 'bloqueo-caso';
  caso.setAttribute('role', 'status');
  caso.textContent = textoDelCaso(opciones.fin);

  const formulario = document.createElement('form');
  formulario.className = 'bloqueo-form';
  formulario.noValidate = true;
  const indicacion = document.createElement('p');
  indicacion.textContent = 'Para seguir, el profesional escribe su contraseña.';
  const usuario = document.createElement('p');
  usuario.className = 'bloqueo-usuario';
  const nombre = document.createElement('strong');
  nombre.textContent = opciones.usuario;
  usuario.append('Usuario: ', nombre);
  const entrada = document.createElement('input');
  entrada.type = 'password';
  entrada.name = 'contrasena';
  entrada.autocomplete = 'off';
  const contrasena = crearCampo('Contraseña', entrada);
  contrasena.contenedor.querySelector('.campo-error')?.setAttribute('role', 'alert');
  const desbloquear = crearBoton('', 'submit');
  desbloquear.className = 'btn btn-principal';
  const textoDesbloquear = document.createElement('span');
  textoDesbloquear.textContent = 'Desbloquear';
  desbloquear.append(crearIcono('candado'), textoDesbloquear);
  formulario.append(indicacion, usuario, contrasena.contenedor, desbloquear);

  tarjeta.append(icono, titulo, caso, formulario);
  pantalla.append(tarjeta);

  // La raíz de la aplicación (y todo lo que no sea el bloqueo) queda fuera del alcance del teclado y de los lectores.
  const inertes: Element[] = [];
  for (const hijo of [...document.body.children]) {
    if (hijo !== pantalla && !hijo.hasAttribute('inert')) {
      hijo.setAttribute('inert', '');
      inertes.push(hijo);
    }
  }
  document.body.append(pantalla);
  entrada.focus();

  // --- Cierre de la sesión del odontólogo en el servidor ---
  let logoutPendiente = true;
  async function pedirLogout(): Promise<void> {
    try {
      await opciones.api.crear<unknown>('/logout');
      logoutPendiente = false;
    } catch {
      logoutPendiente = true;
    }
  }
  void pedirLogout();
  // Si el logout no salió, se vuelve a intentar al salir de la página (mejor esfuerzo).
  const alSalirDeLaPagina = (): void => {
    if (logoutPendiente) {
      void opciones.api.crear<unknown>('/logout').catch(() => undefined);
    }
  };
  window.addEventListener('pagehide', alSalirDeLaPagina);

  function quitar(): void {
    window.removeEventListener('pagehide', alSalirDeLaPagina);
    pantalla.remove();
    for (const hijo of inertes) {
      hijo.removeAttribute('inert');
    }
    borrarMarca();
  }

  // --- Desbloqueo ---
  let intentosFallidos = 0;
  let comprobando = false;

  function mostrarError(mensaje: string): void {
    contrasena.mostrarError(mensaje);
    entrada.value = '';
    entrada.focus();
  }

  function irAlIngreso(): void {
    quitar();
    opciones.alIngreso(MENSAJE_SESION_CERRADA);
  }

  async function comprobar(clave: string): Promise<void> {
    if (logoutPendiente) {
      await pedirLogout();
    }
    try {
      const sesion = await iniciarSesion(opciones.api, opciones.usuario, clave);
      if (sesion.autenticado && sesion.username === opciones.usuario && sesion.rol === 'ODONTOLOGO') {
        quitar();
        if (debeCambiarContrasena(sesion)) {
          opciones.alCambioDeContrasena();
        } else {
          opciones.alDesbloquear();
        }
        return;
      }
      // La sesión nueva no es la del odontólogo: se cierra y se va al ingreso, sin ficha.
      await pedirLogout();
      irAlIngreso();
    } catch (causa) {
      if (causa instanceof ErrorSesion && causa.codigo === 'CREDENCIALES_INVALIDAS') {
        intentosFallidos += 1;
        if (intentosFallidos >= INTENTOS_DE_CONTRASENA) {
          irAlIngreso();
          return;
        }
        mostrarError(textoDeIntentos(INTENTOS_DE_CONTRASENA - intentosFallidos));
        return;
      }
      if (causa instanceof ErrorGenerico && (causa.estado === null || causa.estado >= 500)) {
        // Sin conexión o con el servidor caído: no se sabe si la contraseña es buena, y no cuenta como intento.
        mostrarError(MENSAJE_SIN_COMPROBAR);
        return;
      }
      irAlIngreso();
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    if (comprobando) {
      return;
    }
    const clave = entrada.value;
    if (clave === '') {
      contrasena.mostrarError('Escribí tu contraseña.');
      entrada.focus();
      return;
    }
    contrasena.mostrarError('');
    comprobando = true;
    desbloquear.disabled = true;
    textoDesbloquear.textContent = 'Desbloqueando…';
    void comprobar(clave).finally(() => {
      comprobando = false;
      desbloquear.disabled = false;
      textoDesbloquear.textContent = 'Desbloquear';
    });
  });

  return { elemento: pantalla };
}
