import { crearCampo, crearEntrada, crearSelector, type Opcion } from '../shared/ui/campos.ts';
import { nombreCompleto, type Profesional } from './api.ts';
import { mostrarContrasenaTemporal } from './contrasena-temporal.ts';
import { crearCuenta, NOMBRE_DEL_ROL, type CuentaPersonal, type RolPersonal } from './cuentas-api.ts';
import { abrirDialogo, guardando, mostrarErrorEnFormulario, type ContextoSeccion } from './ui.ts';

export const ERROR_SIN_PROFESIONAL = 'Elegí el profesional de esta cuenta.';
const ETIQUETA_PROFESIONAL = 'Profesional';
const ETIQUETA_PROFESIONAL_OPCIONAL = 'Profesional (opcional, si también atiende)';

const DESCRIPCION_DEL_ROL: Readonly<Record<RolPersonal, string>> = {
  ADMIN: 'Configura el consultorio y las cuentas.',
  RECEPCIONISTA: 'Da y cambia turnos de todos.',
  ODONTOLOGO: 'Ve su agenda. Se vincula a un profesional.',
};

export interface OpcionesAlta {
  /** Todos los profesionales: el selector ofrece los activos que no tienen cuenta. */
  readonly profesionales: readonly Profesional[];
  /** Cuentas vigentes, para saber qué profesionales ya tienen una. */
  readonly cuentas: readonly CuentaPersonal[];
  /** "Dar acceso" desde el detalle: el rol queda Odontólogo y el profesional, fijo (spec "Dar acceso…"). */
  readonly profesionalFijo?: Profesional;
}

let contadorRol = 0;

/**
 * Diálogo "Agregar usuario" o "Dar acceso" (spec "Alta de una cuenta del personal", C-41). Al crearla, cierra,
 * avisa con `listo` y muestra la contraseña temporal una sola vez.
 */
export function abrirAltaUsuario(
  contexto: ContextoSeccion,
  opciones: OpcionesAlta,
  listo: (cuenta: CuentaPersonal) => void,
): void {
  const fijo = opciones.profesionalFijo;
  const conCuenta = new Set(opciones.cuentas.flatMap((c) => (c.profesionalId === null ? [] : [c.profesionalId])));
  const disponibles = fijo !== undefined ? [fijo] : opciones.profesionales.filter((p) => p.activo && !conCuenta.has(p.id));

  const usuario = crearCampo('Usuario', crearEntrada('text', 'username'));
  usuario.control.autocomplete = 'off';
  usuario.control.maxLength = 150;
  const ayudaUsuario = document.createElement('p');
  ayudaUsuario.className = 'ayuda';
  ayudaUsuario.textContent = 'De 3 a 150 caracteres. Con este nombre va a ingresar.';
  usuario.contenedor.insertBefore(ayudaUsuario, usuario.contenedor.lastChild);

  // --- Rol: tarjetas de opción (radio), salvo en "Dar acceso" ---
  contadorRol += 1;
  const grupoRol = `rol-${contadorRol}`;
  const radios = new Map<RolPersonal, HTMLInputElement>();
  const errorRol = document.createElement('p');
  errorRol.className = 'campo-error';
  errorRol.id = `${grupoRol}-error`;
  const fieldset = document.createElement('fieldset');
  fieldset.className = 'campo alta-rol';
  const leyenda = document.createElement('legend');
  leyenda.textContent = 'Rol';
  const tarjetas = document.createElement('div');
  tarjetas.className = 'opciones';
  for (const rol of ['ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO'] as const) {
    const tarjeta = document.createElement('div');
    tarjeta.className = 'opcion';
    const label = document.createElement('label');
    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = grupoRol;
    radio.value = rol;
    radio.setAttribute('aria-describedby', `${grupoRol}-${rol}`);
    label.append(radio, NOMBRE_DEL_ROL[rol]);
    const descripcion = document.createElement('small');
    descripcion.id = `${grupoRol}-${rol}`;
    descripcion.textContent = DESCRIPCION_DEL_ROL[rol];
    tarjeta.append(label, descripcion);
    tarjetas.append(tarjeta);
    radios.set(rol, radio);
  }
  fieldset.append(leyenda, tarjetas, errorRol);

  // --- Profesional ---
  const profesional = crearCampo(
    ETIQUETA_PROFESIONAL,
    crearSelector('profesionalId', opcionesDeProfesional(disponibles, 'Elegí un profesional')),
  );
  profesional.control.classList.add('control');
  const etiquetaProfesional = profesional.contenedor.querySelector('label');
  const ayudaProfesional = document.createElement('p');
  ayudaProfesional.className = 'ayuda';
  ayudaProfesional.textContent = 'Solo aparecen los profesionales activos que todavía no tienen cuenta.';
  profesional.contenedor.insertBefore(ayudaProfesional, profesional.contenedor.lastChild);
  const lugarProfesional = document.createElement('div');

  function rolElegido(): RolPersonal | null {
    if (fijo !== undefined) {
      return 'ODONTOLOGO';
    }
    for (const [rol, radio] of radios) {
      if (radio.checked) {
        return rol;
      }
    }
    return null;
  }

  /** El selector de profesional existe solo para Odontólogo (obligatorio) y Administrador (opcional). */
  function ajustarProfesional(): void {
    const rol = rolElegido();
    profesional.mostrarError('');
    if (rol === 'ODONTOLOGO' || rol === 'ADMIN') {
      const obligatorio = rol === 'ODONTOLOGO';
      if (etiquetaProfesional !== null) {
        etiquetaProfesional.textContent = obligatorio ? ETIQUETA_PROFESIONAL : ETIQUETA_PROFESIONAL_OPCIONAL;
      }
      const elegido = profesional.control.value;
      profesional.control.replaceChildren(
        ...opcionesDeProfesional(disponibles, obligatorio ? 'Elegí un profesional' : 'Ninguno').map(aOpcion),
      );
      profesional.control.value = elegido;
      lugarProfesional.replaceChildren(profesional.contenedor);
    } else {
      // Recepción no se vincula a un profesional: el campo no se muestra.
      profesional.control.value = '';
      lugarProfesional.replaceChildren();
    }
  }

  const contenido: HTMLElement[] = [usuario.contenedor];
  if (fijo === undefined) {
    contenido.push(fieldset);
    for (const radio of radios.values()) {
      radio.addEventListener('change', ajustarProfesional);
    }
  } else {
    profesional.control.value = String(fijo.id);
    profesional.control.disabled = true;
    lugarProfesional.replaceChildren(profesional.contenedor);
  }
  contenido.push(lugarProfesional);

  const dialogo = abrirDialogo(fijo === undefined ? 'Agregar usuario' : 'Dar acceso', contenido, 'Crear cuenta', {
    resumen:
      fijo === undefined
        ? 'La cuenta se crea con una contraseña temporal, que vas a ver una sola vez.'
        : `Le das acceso a ${nombreCompleto(fijo)} como odontólogo. La contraseña temporal la vas a ver una sola vez.`,
  });

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    usuario.mostrarError('');
    profesional.mostrarError('');
    errorRol.textContent = '';
    dialogo.general.textContent = '';
    const username = usuario.control.value.trim();
    const rol = rolElegido();
    const profesionalId = profesional.control.value === '' ? null : Number(profesional.control.value);
    let valido = true;
    if (username.length < 3 || username.length > 150) {
      usuario.mostrarError('El usuario debe tener entre 3 y 150 caracteres.');
      valido = false;
    }
    if (rol === null) {
      errorRol.textContent = 'Elegí el rol de la cuenta.';
      valido = false;
    } else if (rol === 'ODONTOLOGO' && profesionalId === null) {
      profesional.mostrarError(ERROR_SIN_PROFESIONAL);
      valido = false;
    }
    if (!valido || rol === null) {
      return;
    }
    const alta = { username, rol, profesionalId: rol === 'RECEPCIONISTA' ? null : profesionalId };
    void guardando(dialogo.enviar, async () => {
      const creada = await crearCuenta(contexto.api, alta);
      dialogo.cerrar();
      listo(creada.cuenta);
      mostrarContrasenaTemporal(creada.cuenta.username, creada.contrasenaTemporal, false);
    }).catch((causa: unknown) => {
      mostrarErrorEnFormulario(causa, dialogo.general, [usuario, profesional], contexto);
    });
  });
}

function opcionesDeProfesional(profesionales: readonly Profesional[], vacio: string): Opcion[] {
  return [{ valor: '', texto: vacio }, ...profesionales.map((p) => ({ valor: String(p.id), texto: nombreCompleto(p) }))];
}

function aOpcion(opcion: Opcion): HTMLOptionElement {
  const elemento = document.createElement('option');
  elemento.value = opcion.valor;
  elemento.textContent = opcion.texto;
  return elemento;
}
