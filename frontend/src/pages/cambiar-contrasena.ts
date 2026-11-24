import { ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import { cambiarContrasena, debeCambiarContrasena } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_CONTRASENA_CAMBIADA = 'Tu contraseña se cambió.';
export const AVISO_CAMBIO_OBLIGATORIO = 'Antes de seguir, elegí una contraseña nueva.';
const MINIMO = 8;
const MAXIMO = 72;

/**
 * Cambio de la propia contraseña del personal (spec frontend-cuentas-personal, C-41). Es obligatorio después de
 * ingresar con una contraseña temporal (sin menú, solo el formulario y "Salir") y voluntario desde "Mi contraseña".
 */
function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const obligatoria = debeCambiarContrasena(contexto.sesion);
  const pagina = document.createElement('div');
  pagina.className = 'pagina-angosta';

  if (!obligatoria) {
    const volver = document.createElement('a');
    volver.className = 'enlace volver';
    volver.href = '#/agenda';
    volver.append(crearIcono('volver'), 'Volver a la agenda');
    pagina.append(volver);
  }
  pagina.append(crearTitulo('Cambiar mi contraseña'));

  if (obligatoria) {
    const aviso = document.createElement('p');
    aviso.className = 'aviso aviso-info';
    aviso.setAttribute('role', 'status');
    const fuerte = document.createElement('strong');
    fuerte.textContent = AVISO_CAMBIO_OBLIGATORIO;
    aviso.append(fuerte, ' Ingresaste con una contraseña temporal que te dio el administrador.');
    pagina.append(aviso);
  } else {
    const subtitulo = document.createElement('p');
    subtitulo.className = 'subtitulo';
    subtitulo.textContent = 'Para cambiarla necesitás la contraseña actual.';
    pagina.append(subtitulo);
  }

  const general = crearAviso('alert');
  const actual = crearCampo(obligatoria ? 'Contraseña temporal' : 'Contraseña actual', crearEntrada('password', 'actual'));
  actual.control.autocomplete = 'current-password';
  const nueva = crearCampo('Contraseña nueva', crearEntrada('password', 'nueva'));
  nueva.control.autocomplete = 'new-password';
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Al menos 8 caracteres y distinta de la actual.';
  nueva.contenedor.insertBefore(ayuda, nueva.contenedor.lastChild);
  const repetir = crearCampo('Repetí la contraseña nueva', crearEntrada('password', 'repetir'));
  repetir.control.autocomplete = 'new-password';
  const guardar = crearBoton('Guardar contraseña', 'submit');
  guardar.className = 'btn btn-principal btn-ancho';
  const acciones = document.createElement('div');
  acciones.className = 'acciones';
  acciones.append(guardar);

  const formulario = document.createElement('form');
  formulario.className = 'formulario';
  formulario.noValidate = true;
  formulario.append(general, actual.contenedor, nueva.contenedor, repetir.contenedor, acciones);
  pagina.append(formulario);
  contenedor.replaceChildren(pagina);

  function vaciar(): void {
    actual.control.value = '';
    nueva.control.value = '';
    repetir.control.value = '';
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    for (const campo of [actual, nueva, repetir]) {
      campo.mostrarError('');
    }
    general.textContent = '';
    let valido = true;
    if (actual.control.value === '') {
      actual.mostrarError(obligatoria ? 'Escribí la contraseña temporal.' : 'Escribí tu contraseña actual.');
      valido = false;
    }
    if (nueva.control.value.length < MINIMO) {
      nueva.mostrarError('La contraseña nueva tiene que tener al menos 8 caracteres.');
      valido = false;
    } else if (nueva.control.value.length > MAXIMO) {
      nueva.mostrarError('La contraseña nueva puede tener hasta 72 caracteres.');
      valido = false;
    } else if (nueva.control.value !== repetir.control.value) {
      repetir.mostrarError('Las contraseñas nuevas no coinciden.');
      valido = false;
    }
    if (!valido || guardar.disabled) {
      return;
    }
    guardar.disabled = true;
    cambiarContrasena(contexto.api, actual.control.value, nueva.control.value).then(
      () => {
        vaciar();
        contexto.irA('agenda', MENSAJE_CONTRASENA_CAMBIADA);
      },
      (causa: unknown) => {
        guardar.disabled = false;
        if (causa instanceof ErrorSesion) {
          contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
          return;
        }
        if (causa instanceof ErrorValidacion) {
          for (const error of causa.errores) {
            if (error.campo === 'nueva') {
              nueva.mostrarError(error.mensaje);
            }
          }
        }
        general.textContent = mensajeDeError(causa);
        vaciar();
        actual.control.focus();
      },
    );
  });
}

export const paginaCambiarContrasena: Pagina = {
  ruta: 'cambiar-contrasena',
  titulo: 'Mi contraseña',
  acceso: 'personal',
  menu: 'personal',
  icono: 'llave',
  seccionMenu: 'cuenta',
  render: dibujar,
};
