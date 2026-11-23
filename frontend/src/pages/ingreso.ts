import { ErrorSesion } from '../shared/api/errores.ts';
import { iniciarSesion } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type Pagina } from './pagina.ts';

export const MENSAJE_CREDENCIALES = 'Usuario o contraseña incorrectos.';

/** Página de ingreso del personal (`#/ingreso`), fuera del menú. */
export const paginaIngreso: Pagina = {
  ruta: 'ingreso',
  titulo: 'Ingresar',
  render(contenedor, contexto) {
    const usuario = crearCampo('Usuario', crearEntrada('text', 'username'));
    usuario.control.autocomplete = 'username';
    const clave = crearCampo('Contraseña', crearEntrada('password', 'password'));
    clave.control.autocomplete = 'current-password';
    const general = crearAviso('alert');
    const enviar = crearBoton('Ingresar', 'submit');

    const formulario = document.createElement('form');
    formulario.className = 'formulario formulario-angosto';
    formulario.noValidate = true;
    formulario.append(usuario.contenedor, clave.contenedor, general, enviar);

    const avisoPrevio = crearAviso('status');
    avisoPrevio.textContent = contexto.aviso ?? '';

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const nombre = usuario.control.value.trim();
      const contrasena = clave.control.value;
      usuario.mostrarError(nombre === '' ? 'Completá el usuario.' : '');
      clave.mostrarError(contrasena === '' ? 'Completá la contraseña.' : '');
      general.textContent = '';
      if (nombre === '' || contrasena === '') {
        return;
      }
      enviar.disabled = true;
      iniciarSesion(contexto.api, nombre, contrasena)
        .then(() => contexto.irA('agenda'))
        .catch((error: unknown) => {
          clave.control.value = '';
          general.textContent = error instanceof ErrorSesion ? MENSAJE_CREDENCIALES : mensajeDeError(error);
        })
        .finally(() => {
          enviar.disabled = false;
        });
    });

    contenedor.replaceChildren(crearTitulo('Ingresar'), avisoPrevio, formulario);
  },
};
