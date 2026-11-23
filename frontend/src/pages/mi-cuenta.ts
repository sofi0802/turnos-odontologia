import { leerReservaPendiente } from '../paciente/reserva.ts';
import { ErrorNegocio, ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import { esPaciente, esPersonal, iniciarSesion, registrarPaciente, type Sesion } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_CREDENCIALES_PACIENTE = 'Email o contraseña incorrectos.';
export const MENSAJE_CUENTA_CREADA = 'Tu cuenta se creó. Ingresá con tu email y tu contraseña.';

type Seccion = 'ingresar' | 'registro';

function campoDeTexto(etiqueta: string, nombre: string, tipo = 'text'): Campo<HTMLInputElement> {
  const entrada = crearEntrada(tipo, nombre);
  entrada.classList.add('control');
  return crearCampo(etiqueta, entrada);
}

/** Lleva a la página que corresponde a la sesión recién iniciada (design D6). */
function seguirConSesion(contexto: ContextoPagina, sesion: Sesion): void {
  if (esPaciente(sesion.rol)) {
    contexto.irA(leerReservaPendiente() === null ? 'mis-turnos' : 'reserva');
  } else if (esPersonal(sesion.rol)) {
    contexto.irA('agenda');
  }
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  // --- Ingresar ---
  const email = campoDeTexto('Email', 'username', 'email');
  email.control.autocomplete = 'username';
  const clave = campoDeTexto('Contraseña', 'password', 'password');
  clave.control.autocomplete = 'current-password';
  const avisoIngreso = crearAviso('status');
  avisoIngreso.className = 'aviso aviso-info';
  const errorIngreso = crearAviso('alert');
  const enviarIngreso = crearBoton('Ingresar', 'submit');
  enviarIngreso.className = 'btn btn-principal';
  const formIngreso = document.createElement('form');
  formIngreso.className = 'formulario';
  formIngreso.noValidate = true;
  formIngreso.setAttribute('aria-label', 'Ingresar');
  formIngreso.append(avisoIngreso, email.contenedor, clave.contenedor, errorIngreso, enviarIngreso);

  // --- Crear cuenta ---
  const nombre = campoDeTexto('Nombre', 'nombre');
  nombre.control.autocomplete = 'given-name';
  const apellido = campoDeTexto('Apellido', 'apellido');
  apellido.control.autocomplete = 'family-name';
  const dni = campoDeTexto('DNI', 'dni');
  dni.control.inputMode = 'numeric';
  const emailRegistro = campoDeTexto('Email', 'email', 'email');
  emailRegistro.control.autocomplete = 'email';
  const telefono = campoDeTexto('Teléfono (opcional)', 'telefono', 'tel');
  telefono.control.autocomplete = 'tel';
  const claveRegistro = campoDeTexto('Contraseña', 'password', 'password');
  claveRegistro.control.autocomplete = 'new-password';
  const repeticion = campoDeTexto('Repetí la contraseña', 'repeticion', 'password');
  repeticion.control.autocomplete = 'new-password';
  const errorRegistro = crearAviso('alert');
  const enviarRegistro = crearBoton('Crear cuenta', 'submit');
  enviarRegistro.className = 'btn btn-principal';
  const formRegistro = document.createElement('form');
  formRegistro.className = 'formulario';
  formRegistro.noValidate = true;
  formRegistro.setAttribute('aria-label', 'Crear cuenta');
  formRegistro.append(
    nombre.contenedor,
    apellido.contenedor,
    dni.contenedor,
    emailRegistro.contenedor,
    telefono.contenedor,
    claveRegistro.contenedor,
    repeticion.contenedor,
    errorRegistro,
    enviarRegistro,
  );

  const camposPorNombre: Readonly<Record<string, Campo<HTMLInputElement>>> = {
    nombre,
    apellido,
    dni,
    email: emailRegistro,
    telefono,
    password: claveRegistro,
  };

  // --- Pestañas: solo la sección activa queda en el documento ---
  const lugar = document.createElement('div');
  const pestanas = document.createElement('div');
  pestanas.className = 'pestanas';
  pestanas.setAttribute('role', 'tablist');
  pestanas.setAttribute('aria-label', 'Cuenta');
  const pestanaIngresar = crearBoton('Ingresar');
  const pestanaRegistro = crearBoton('Crear cuenta');
  for (const pestana of [pestanaIngresar, pestanaRegistro]) {
    pestana.setAttribute('role', 'tab');
    pestanas.append(pestana);
  }

  function mostrarSeccion(seccion: Seccion): void {
    pestanaIngresar.setAttribute('aria-selected', String(seccion === 'ingresar'));
    pestanaRegistro.setAttribute('aria-selected', String(seccion === 'registro'));
    lugar.replaceChildren(seccion === 'ingresar' ? formIngreso : formRegistro);
  }
  pestanaIngresar.addEventListener('click', () => mostrarSeccion('ingresar'));
  pestanaRegistro.addEventListener('click', () => mostrarSeccion('registro'));

  formIngreso.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const usuario = email.control.value.trim();
    const contrasena = clave.control.value;
    email.mostrarError(usuario === '' ? 'Completá el email.' : '');
    clave.mostrarError(contrasena === '' ? 'Completá la contraseña.' : '');
    errorIngreso.textContent = '';
    if (usuario === '' || contrasena === '') {
      return;
    }
    enviarIngreso.disabled = true;
    iniciarSesion(contexto.api, usuario, contrasena)
      .then((sesion) => seguirConSesion(contexto, sesion))
      .catch((error: unknown) => {
        clave.control.value = '';
        errorIngreso.textContent = error instanceof ErrorSesion ? MENSAJE_CREDENCIALES_PACIENTE : mensajeDeError(error);
      })
      .finally(() => {
        enviarIngreso.disabled = false;
      });
  });

  function validarRegistro(): boolean {
    const vacio = (campo: Campo<HTMLInputElement>): boolean => campo.control.value.trim() === '';
    const errores: [Campo<HTMLInputElement>, string][] = [
      [nombre, vacio(nombre) ? 'Completá el nombre.' : ''],
      [apellido, vacio(apellido) ? 'Completá el apellido.' : ''],
      [
        dni,
        vacio(dni) ? 'Completá el DNI.' : /^\d{7,8}$/.test(dni.control.value.trim()) ? '' : 'El DNI debe tener 7 u 8 dígitos.',
      ],
      [emailRegistro, vacio(emailRegistro) ? 'Completá el email.' : ''],
      [telefono, ''],
      [
        claveRegistro,
        claveRegistro.control.value === ''
          ? 'Completá la contraseña.'
          : claveRegistro.control.value.length < 8
            ? 'La contraseña debe tener al menos 8 caracteres.'
            : '',
      ],
      [repeticion, repeticion.control.value === claveRegistro.control.value ? '' : 'Las contraseñas no coinciden.'],
    ];
    for (const [campo, mensaje] of errores) {
      campo.mostrarError(mensaje);
    }
    return errores.every(([, mensaje]) => mensaje === '');
  }

  function vaciarContrasenas(): void {
    claveRegistro.control.value = '';
    repeticion.control.value = '';
  }

  function mostrarRechazo(error: unknown): void {
    vaciarContrasenas();
    if (error instanceof ErrorNegocio && error.codigo === 'DNI_YA_REGISTRADO') {
      dni.mostrarError(mensajeDeError(error));
    } else if (error instanceof ErrorNegocio && error.codigo === 'EMAIL_YA_REGISTRADO') {
      emailRegistro.mostrarError(mensajeDeError(error));
    } else if (error instanceof ErrorValidacion && error.errores.length > 0) {
      const sinCampo: string[] = [];
      for (const { campo, mensaje } of error.errores) {
        const destino = camposPorNombre[campo];
        if (destino === undefined) {
          sinCampo.push(mensaje);
        } else {
          destino.mostrarError(mensaje);
        }
      }
      errorRegistro.textContent = sinCampo.join(' ');
    } else {
      errorRegistro.textContent = mensajeDeError(error);
    }
  }

  formRegistro.addEventListener('submit', (evento) => {
    evento.preventDefault();
    errorRegistro.textContent = '';
    if (!validarRegistro()) {
      return;
    }
    const correo = emailRegistro.control.value.trim();
    const contrasena = claveRegistro.control.value;
    const tel = telefono.control.value.trim();
    enviarRegistro.disabled = true;
    registrarPaciente(contexto.api, {
      nombre: nombre.control.value.trim(),
      apellido: apellido.control.value.trim(),
      dni: dni.control.value.trim(),
      email: correo,
      telefono: tel === '' ? null : tel,
      password: contrasena,
    })
      .then(async () => {
        // El registro no inicia la sesión: se ingresa con lo recién escrito (design D6).
        try {
          const sesion = await iniciarSesion(contexto.api, correo, contrasena);
          vaciarContrasenas();
          seguirConSesion(contexto, sesion);
        } catch {
          vaciarContrasenas();
          email.control.value = correo;
          clave.control.value = '';
          avisoIngreso.textContent = MENSAJE_CUENTA_CREADA;
          mostrarSeccion('ingresar');
        }
      }, mostrarRechazo)
      .finally(() => {
        enviarRegistro.disabled = false;
      });
  });

  const avisoPrevio = crearAviso('status');
  avisoPrevio.className = 'aviso aviso-info';
  avisoPrevio.textContent = contexto.aviso ?? '';

  const panel = document.createElement('section');
  panel.className = 'panel';
  panel.setAttribute('aria-label', 'Mi cuenta');
  panel.append(avisoPrevio, pestanas, lugar);
  const pagina = document.createElement('div');
  pagina.className = 'pagina-paciente';
  pagina.append(crearTitulo('Mi cuenta'), panel);
  mostrarSeccion('ingresar');
  contenedor.replaceChildren(pagina);

  // Con una sesión de paciente, la cuenta lleva directo a sus turnos.
  void contexto.sesionLista.then((sesion) => {
    if (contenedor.contains(pagina) && sesion.autenticado && esPaciente(sesion.rol)) {
      contexto.irA('mis-turnos');
    }
  });
}

/** Cuenta del paciente (`#/mi-cuenta`): ingresar o crear la cuenta. Fuera del menú. */
export const paginaMiCuenta: Pagina = {
  ruta: 'mi-cuenta',
  titulo: 'Mi cuenta',
  render: dibujar,
};
