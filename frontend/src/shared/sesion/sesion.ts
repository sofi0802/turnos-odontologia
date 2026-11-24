import { hayTokenCsrf, type ClienteApi } from '../api/cliente.ts';

export type Rol = 'ADMIN' | 'RECEPCIONISTA' | 'ODONTOLOGO' | 'PACIENTE';

const ROLES: readonly Rol[] = ['ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO', 'PACIENTE'];
const ROLES_DEL_PERSONAL: readonly Rol[] = ['ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO'];

/** Estado de la sesión según `GET /public/sesion` (C-05). */
export interface Sesion {
  readonly autenticado: boolean;
  readonly username: string | null;
  readonly rol: Rol | null;
  /** Profesional del odontólogo (RN-AG-18, C-35); no existe para los otros roles. */
  readonly profesionalId?: number;
  /** La cuenta del personal entró con una contraseña temporal y tiene que cambiarla (C-41). Solo existe si es `true`. */
  readonly debeCambiarContrasena?: true;
}

export const SIN_SESION: Sesion = { autenticado: false, username: null, rol: null };

function esRol(valor: unknown): valor is Rol {
  return typeof valor === 'string' && (ROLES as readonly string[]).includes(valor);
}

/** Interpreta la respuesta del servidor; cualquier forma inesperada es "sin sesión". */
function leerSesion(cuerpo: unknown): Sesion {
  if (typeof cuerpo !== 'object' || cuerpo === null) {
    return SIN_SESION;
  }
  const autenticado: unknown = Reflect.get(cuerpo, 'autenticado');
  const username: unknown = Reflect.get(cuerpo, 'username');
  const rol: unknown = Reflect.get(cuerpo, 'rol');
  if (autenticado !== true || typeof username !== 'string' || !esRol(rol)) {
    return SIN_SESION;
  }
  const profesionalId: unknown = Reflect.get(cuerpo, 'profesionalId');
  const conProfesional =
    rol === 'ODONTOLOGO' && typeof profesionalId === 'number' && Number.isInteger(profesionalId) && profesionalId > 0
      ? { profesionalId }
      : {};
  const conTemporal =
    esPersonal(rol) && Reflect.get(cuerpo, 'debeCambiarContrasena') === true ? { debeCambiarContrasena: true as const } : {};
  return { autenticado: true, username, rol, ...conProfesional, ...conTemporal };
}

/** La sesión del personal tiene una contraseña temporal que cambiar antes de seguir (C-41). */
export function debeCambiarContrasena(sesion: Sesion | null): boolean {
  return sesion?.debeCambiarContrasena === true;
}

/** Cambia la contraseña de la cuenta de la sesión (`POST /cuenta/contrasena`, C-41). */
export async function cambiarContrasena(api: ClienteApi, actual: string, nueva: string): Promise<void> {
  await api.crear<unknown>('/cuenta/contrasena', { actual, nueva });
}

export async function consultarSesion(api: ClienteApi): Promise<Sesion> {
  return leerSesion(await api.obtener<unknown>('/public/sesion'));
}

/** Inicia sesión; antes pide la sesión si todavía no hay token CSRF (el login también lo exige). */
export async function iniciarSesion(api: ClienteApi, username: string, password: string): Promise<Sesion> {
  if (!hayTokenCsrf()) {
    await api.obtener<unknown>('/public/sesion');
  }
  return leerSesion(await api.crear<unknown>('/public/login', { username, password }));
}

/** Cierra la sesión en el servidor; si falla, la pantalla sale igual. */
export async function cerrarSesion(api: ClienteApi): Promise<void> {
  try {
    await api.crear<unknown>('/logout');
  } catch {
    // La salida no depende de la respuesta (spec "Cierre de sesión").
  }
}

export function esPersonal(rol: Rol | null): boolean {
  return rol !== null && ROLES_DEL_PERSONAL.includes(rol);
}

export function esPaciente(rol: Rol | null): boolean {
  return rol === 'PACIENTE';
}

/** Datos del registro del paciente, sin la repetición de la contraseña. */
export interface DatosRegistro {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string | null;
  readonly password: string;
}

/** Crea la cuenta del paciente; el registro no inicia la sesión (spec "Sesión iniciada después del registro"). */
export async function registrarPaciente(api: ClienteApi, datos: DatosRegistro): Promise<void> {
  if (!hayTokenCsrf()) {
    await api.obtener<unknown>('/public/sesion');
  }
  const { telefono, ...resto } = datos;
  await api.crear<unknown>('/public/pacientes/registro', telefono === null ? resto : { ...resto, telefono });
}
