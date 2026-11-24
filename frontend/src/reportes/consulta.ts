import type { ClienteApi } from '../shared/api/cliente.ts';
import type { PeriodoReporte } from './periodo.ts';

/** Respuesta de `GET /api/v1/reportes` (spec reportes, design D9 de C-32), sin datos de pacientes. */
export interface FilaOcupacion {
  readonly id: number;
  readonly nombre: string;
  readonly minutosOcupados: number;
  readonly minutosAtencion: number;
  /** `null` sin minutos de atención ("Sin horario"). */
  readonly porcentaje: number | null;
}

export interface AusenciaProfesional {
  readonly nombre: string;
  readonly tipo: string;
  readonly cantidad: number;
  readonly minutos: number;
}

export interface Reporte {
  readonly profesionalId: number | null;
  readonly ocupacion: { readonly profesionales: readonly FilaOcupacion[]; readonly sillones: readonly FilaOcupacion[] };
  readonly origen: { readonly online: number; readonly consultorio: number; readonly sinDato: number };
  readonly ausencias: {
    readonly pacientes: {
      readonly ausentes: number;
      readonly atendidos: number;
      readonly tasa: number | null;
      readonly sinRegistrar: number;
    };
    readonly profesionales: readonly AusenciaProfesional[];
  };
  readonly cancelaciones: {
    readonly porPaciente: number;
    readonly porConsultorio: number;
    readonly porReprogramacion: number;
    readonly sinDato: number;
  };
}

/** La respuesta no tiene la forma esperada. */
export class RespuestaInvalida extends Error {}

function objeto(valor: unknown): object {
  if (typeof valor !== 'object' || valor === null) {
    throw new RespuestaInvalida('Respuesta de reportes inválida');
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

function lista(origen: object, clave: string): unknown[] {
  const valor: unknown = Reflect.get(origen, clave);
  if (!Array.isArray(valor)) {
    throw new RespuestaInvalida(`Falta la lista ${clave}`);
  }
  return valor;
}

function fila(valor: unknown, clave: 'profesionalId' | 'sillonId'): FilaOcupacion {
  const datos = objeto(valor);
  return {
    id: numero(datos, clave),
    nombre: cadena(datos, 'nombre'),
    minutosOcupados: numero(datos, 'minutosOcupados'),
    minutosAtencion: numero(datos, 'minutosAtencion'),
    porcentaje: numeroONulo(datos, 'porcentaje'),
  };
}

export function leerReporte(cuerpo: unknown): Reporte {
  const datos = objeto(cuerpo);
  const ocupacion = objeto(Reflect.get(datos, 'ocupacion'));
  const origen = objeto(Reflect.get(datos, 'origen'));
  const ausencias = objeto(Reflect.get(datos, 'ausencias'));
  const pacientes = objeto(Reflect.get(ausencias, 'pacientes'));
  const cancelaciones = objeto(Reflect.get(datos, 'cancelaciones'));
  return {
    profesionalId: numeroONulo(datos, 'profesionalId'),
    ocupacion: {
      profesionales: lista(ocupacion, 'profesionales').map((f) => fila(f, 'profesionalId')),
      sillones: lista(ocupacion, 'sillones').map((f) => fila(f, 'sillonId')),
    },
    origen: {
      online: numero(origen, 'online'),
      consultorio: numero(origen, 'consultorio'),
      sinDato: numero(origen, 'sinDato'),
    },
    ausencias: {
      pacientes: {
        ausentes: numero(pacientes, 'ausentes'),
        atendidos: numero(pacientes, 'atendidos'),
        tasa: numeroONulo(pacientes, 'tasa'),
        sinRegistrar: numero(pacientes, 'sinRegistrar'),
      },
      profesionales: lista(ausencias, 'profesionales').map((a) => {
        const ausencia = objeto(a);
        return {
          nombre: cadena(ausencia, 'nombre'),
          tipo: cadena(ausencia, 'tipo'),
          cantidad: numero(ausencia, 'cantidad'),
          minutos: numero(ausencia, 'minutos'),
        };
      }),
    },
    cancelaciones: {
      porPaciente: numero(cancelaciones, 'porPaciente'),
      porConsultorio: numero(cancelaciones, 'porConsultorio'),
      porReprogramacion: numero(cancelaciones, 'porReprogramacion'),
      sinDato: numero(cancelaciones, 'sinDato'),
    },
  };
}

export function rutaDeReportes(periodo: PeriodoReporte, profesionalId: number | null): string {
  const parametros = new URLSearchParams({ desde: periodo.desde, hasta: periodo.hasta });
  if (profesionalId !== null) {
    parametros.set('profesionalId', String(profesionalId));
  }
  return `/reportes?${parametros.toString()}`;
}

export async function consultarReportes(
  api: ClienteApi,
  periodo: PeriodoReporte,
  profesionalId: number | null,
): Promise<Reporte> {
  return leerReporte(await api.obtener<unknown>(rutaDeReportes(periodo, profesionalId)));
}
