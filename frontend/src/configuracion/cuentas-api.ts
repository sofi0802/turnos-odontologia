import type { ClienteApi } from '../shared/api/cliente.ts';
import { leerLista } from './api.ts';

/** Rol de una cuenta del personal (C-41). */
export type RolPersonal = 'ADMIN' | 'RECEPCIONISTA' | 'ODONTOLOGO';

/** Cuenta del personal según `GET /usuarios` (spec cuentas-personal). Nunca trae contraseñas. */
export interface CuentaPersonal {
  readonly id: number;
  readonly username: string;
  readonly rol: RolPersonal;
  readonly profesionalId: number | null;
  readonly activo: boolean;
  readonly debeCambiarContrasena: boolean;
}

/** Cuenta recién creada o restablecida, con la contraseña temporal que solo viaja en esa respuesta. */
export interface CuentaConTemporal {
  readonly cuenta: CuentaPersonal;
  readonly contrasenaTemporal: string;
}

export interface AltaCuenta {
  readonly username: string;
  readonly rol: RolPersonal;
  readonly profesionalId: number | null;
}

const ROLES: readonly RolPersonal[] = ['ADMIN', 'RECEPCIONISTA', 'ODONTOLOGO'];

/** Nombre del rol en palabras, como lo ve la persona. */
export const NOMBRE_DEL_ROL: Readonly<Record<RolPersonal, string>> = {
  ADMIN: 'Administrador',
  RECEPCIONISTA: 'Recepción',
  ODONTOLOGO: 'Odontólogo',
};

function esRol(valor: unknown): valor is RolPersonal {
  return typeof valor === 'string' && (ROLES as readonly string[]).includes(valor);
}

export function leerCuenta(valor: unknown): CuentaPersonal | null {
  if (typeof valor !== 'object' || valor === null) {
    return null;
  }
  const id: unknown = Reflect.get(valor, 'id');
  const username: unknown = Reflect.get(valor, 'username');
  const rol: unknown = Reflect.get(valor, 'rol');
  const profesionalId: unknown = Reflect.get(valor, 'profesionalId');
  if (typeof id !== 'number' || typeof username !== 'string' || !esRol(rol)) {
    return null;
  }
  return {
    id,
    username,
    rol,
    profesionalId: typeof profesionalId === 'number' ? profesionalId : null,
    activo: Reflect.get(valor, 'activo') !== false,
    debeCambiarContrasena: Reflect.get(valor, 'debeCambiarContrasena') === true,
  };
}

function leerConTemporal(cuerpo: unknown, ruta: string): CuentaConTemporal {
  const cuenta = leerCuenta(cuerpo);
  const temporal: unknown = typeof cuerpo === 'object' && cuerpo !== null ? Reflect.get(cuerpo, 'contrasenaTemporal') : null;
  if (cuenta === null || typeof temporal !== 'string' || temporal === '') {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return { cuenta, contrasenaTemporal: temporal };
}

function leerUna(cuerpo: unknown, ruta: string): CuentaPersonal {
  const cuenta = leerCuenta(cuerpo);
  if (cuenta === null) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuenta;
}

export async function cargarCuentas(api: ClienteApi): Promise<CuentaPersonal[]> {
  return leerLista(await api.obtener<unknown>('/usuarios'), leerCuenta, '/usuarios');
}

export async function crearCuenta(api: ClienteApi, alta: AltaCuenta): Promise<CuentaConTemporal> {
  return leerConTemporal(await api.crear<unknown>('/usuarios', alta), '/usuarios');
}

export async function desactivarCuenta(api: ClienteApi, id: number): Promise<CuentaPersonal> {
  const ruta = `/usuarios/${id}/desactivar`;
  return leerUna(await api.crear<unknown>(ruta), ruta);
}

export async function activarCuenta(api: ClienteApi, id: number): Promise<CuentaPersonal> {
  const ruta = `/usuarios/${id}/activar`;
  return leerUna(await api.crear<unknown>(ruta), ruta);
}

export async function restablecerContrasena(api: ClienteApi, id: number): Promise<CuentaConTemporal> {
  const ruta = `/usuarios/${id}/restablecer-contrasena`;
  return leerConTemporal(await api.crear<unknown>(ruta), ruta);
}
