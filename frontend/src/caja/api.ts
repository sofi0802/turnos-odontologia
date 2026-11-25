import type { ClienteApi } from '../shared/api/cliente.ts';

/** Respuestas de la API de la caja (design D9 de C-22), leídas sin `any`. */

export type MedioPago = 'EFECTIVO' | 'DEBITO' | 'CREDITO' | 'TRANSFERENCIA';

export const MEDIOS: readonly { readonly valor: MedioPago; readonly texto: string }[] = [
  { valor: 'EFECTIVO', texto: 'Efectivo' },
  { valor: 'DEBITO', texto: 'Débito' },
  { valor: 'CREDITO', texto: 'Crédito' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
];

export interface Cobro {
  readonly id: number;
  readonly cargoId: number;
  readonly paciente: string;
  readonly registradoEn: string;
  readonly medio: MedioPago;
  readonly importe: number;
  readonly anulado: boolean;
}

export interface Caja {
  readonly id: number;
  readonly fecha: string;
  readonly montoInicial: number;
  readonly abiertaPor: string;
  readonly abiertaEn: string;
  readonly cerradaPor: string | null;
  readonly totales: Readonly<Record<MedioPago, number>>;
  readonly efectivoEsperado: number;
  readonly efectivoContado: number | null;
  readonly diferencia: number | null;
  readonly motivoDiferencia: string | null;
  readonly cobros: readonly Cobro[];
}

export interface Pendiente {
  readonly id: number;
  readonly paciente: string;
  readonly inicio: string;
  readonly prestacion: string;
  readonly copago: number;
  readonly saldo: number;
}

export interface SinPrecio {
  readonly turnoId: number;
  readonly paciente: string;
  readonly inicio: string;
  readonly prestacion: string;
}

export interface Pendientes {
  readonly cargos: readonly Pendiente[];
  readonly sinPrecio: readonly SinPrecio[];
}

export interface CargoCuenta {
  readonly id: number;
  readonly inicio: string;
  readonly prestacion: string;
  readonly copago: number;
  readonly pagado: number;
  readonly saldo: number;
}

export interface CuentaCorriente {
  readonly paciente: string;
  readonly cargos: readonly CargoCuenta[];
  readonly saldoTotal: number;
}

/** La respuesta no tiene la forma esperada. */
export class RespuestaInvalida extends Error {}

function objeto(valor: unknown): object {
  if (typeof valor !== 'object' || valor === null) {
    throw new RespuestaInvalida('Respuesta de la caja inválida');
  }
  return valor;
}

function numero(origen: object, clave: string): number {
  const valor: unknown = Reflect.get(origen, clave);
  if (typeof valor !== 'number' || !Number.isFinite(valor)) {
    throw new RespuestaInvalida(`Falta el número ${clave}`);
  }
  return valor;
}

function numeroONulo(origen: object, clave: string): number | null {
  const valor: unknown = Reflect.get(origen, clave);
  return valor === null || valor === undefined ? null : numero(origen, clave);
}

function cadena(origen: object, clave: string): string {
  const valor: unknown = Reflect.get(origen, clave);
  if (typeof valor !== 'string') {
    throw new RespuestaInvalida(`Falta el texto ${clave}`);
  }
  return valor;
}

function cadenaONula(origen: object, clave: string): string | null {
  const valor: unknown = Reflect.get(origen, clave);
  return typeof valor === 'string' ? valor : null;
}

function lista(origen: object, clave: string): unknown[] {
  const valor: unknown = Reflect.get(origen, clave);
  return Array.isArray(valor) ? valor : [];
}

function medio(origen: object): MedioPago {
  const valor = cadena(origen, 'medio');
  const conocido = MEDIOS.find((m) => m.valor === valor);
  if (conocido === undefined) {
    throw new RespuestaInvalida(`Medio de pago desconocido: ${valor}`);
  }
  return conocido.valor;
}

function leerCobro(valor: unknown): Cobro {
  const datos = objeto(valor);
  return {
    id: numero(datos, 'id'),
    cargoId: numero(datos, 'cargoId'),
    paciente: cadena(datos, 'paciente'),
    registradoEn: cadena(datos, 'registradoEn'),
    medio: medio(datos),
    importe: numero(datos, 'importe'),
    anulado: Reflect.get(datos, 'anulado') === true,
  };
}

export function leerCaja(valor: unknown): Caja {
  const datos = objeto(valor);
  const totales = objeto(Reflect.get(datos, 'totales'));
  return {
    id: numero(datos, 'id'),
    fecha: cadena(datos, 'fecha'),
    montoInicial: numero(datos, 'montoInicial'),
    abiertaPor: cadena(datos, 'abiertaPor'),
    abiertaEn: cadena(datos, 'abiertaEn'),
    cerradaPor: cadenaONula(datos, 'cerradaPor'),
    totales: {
      EFECTIVO: numero(totales, 'EFECTIVO'),
      DEBITO: numero(totales, 'DEBITO'),
      CREDITO: numero(totales, 'CREDITO'),
      TRANSFERENCIA: numero(totales, 'TRANSFERENCIA'),
    },
    efectivoEsperado: numero(datos, 'efectivoEsperado'),
    efectivoContado: numeroONulo(datos, 'efectivoContado'),
    diferencia: numeroONulo(datos, 'diferencia'),
    motivoDiferencia: cadenaONula(datos, 'motivoDiferencia'),
    cobros: lista(datos, 'cobros').map(leerCobro),
  };
}

export function leerPendientes(valor: unknown): Pendientes {
  const datos = objeto(valor);
  return {
    cargos: lista(datos, 'cargos').map((c) => {
      const cargo = objeto(c);
      return {
        id: numero(cargo, 'id'),
        paciente: cadena(cargo, 'paciente'),
        inicio: cadena(cargo, 'inicio'),
        prestacion: cadena(cargo, 'prestacion'),
        copago: numero(cargo, 'copago'),
        saldo: numero(cargo, 'saldo'),
      };
    }),
    sinPrecio: lista(datos, 'sinPrecio').map((s) => {
      const turno = objeto(s);
      return {
        turnoId: numero(turno, 'turnoId'),
        paciente: cadena(turno, 'paciente'),
        inicio: cadena(turno, 'inicio'),
        prestacion: cadena(turno, 'prestacion'),
      };
    }),
  };
}

export function leerCuenta(valor: unknown): CuentaCorriente {
  const datos = objeto(valor);
  return {
    paciente: cadena(datos, 'paciente'),
    saldoTotal: numero(datos, 'saldoTotal'),
    cargos: lista(datos, 'cargos').map((c) => {
      const cargo = objeto(c);
      return {
        id: numero(cargo, 'id'),
        inicio: cadena(cargo, 'inicio'),
        prestacion: cadena(cargo, 'prestacion'),
        copago: numero(cargo, 'copago'),
        pagado: numero(cargo, 'pagado'),
        saldo: numero(cargo, 'saldo'),
      };
    }),
  };
}

/** `null` si no hay caja abierta (`204`). */
export async function cajaAbierta(api: ClienteApi): Promise<Caja | null> {
  const cuerpo = await api.obtener<unknown>('/cajas/abierta');
  return cuerpo === undefined || cuerpo === null ? null : leerCaja(cuerpo);
}

export async function consultarPendientes(api: ClienteApi): Promise<Pendientes> {
  return leerPendientes(await api.obtener<unknown>('/cargos/pendientes'));
}

export async function consultarHistorial(api: ClienteApi, desde: string, hasta: string): Promise<Caja[]> {
  const cuerpo = await api.obtener<unknown>(`/cajas?${new URLSearchParams({ desde, hasta }).toString()}`);
  return Array.isArray(cuerpo) ? cuerpo.map(leerCaja) : [];
}

export async function consultarCuenta(api: ClienteApi, pacienteId: number): Promise<CuentaCorriente> {
  return leerCuenta(await api.obtener<unknown>(`/pacientes/${pacienteId}/cuenta-corriente`));
}

export function leerSaldoDelCobro(cuerpo: unknown): number {
  return numero(objeto(cuerpo), 'saldo');
}
