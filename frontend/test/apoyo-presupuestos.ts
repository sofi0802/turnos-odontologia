import { json, type ServidorFalso } from './apoyo.ts';
import { responderCoberturas } from './apoyo-coberturas.ts';

/** Datos ficticios de los escenarios de C-50 (specs `frontend-aranceles` y `frontend-presupuestos`). */

export const ARANCELES = [
  { prestacion: { id: 1, nombre: 'Consulta o control' }, activa: true, precio: 15000.0, actualizadoEn: '2026-11-24T09:00:00Z' },
  { prestacion: { id: 2, nombre: 'Limpieza (profilaxis)' }, activa: true, precio: 25000.0, actualizadoEn: '2026-11-24T09:00:00Z' },
  { prestacion: { id: 3, nombre: 'Tratamiento de conducto' }, activa: true, precio: 90000.0, actualizadoEn: '2026-11-24T09:00:00Z' },
  { prestacion: { id: 4, nombre: 'Extracción simple' }, activa: true, precio: 30000.0, actualizadoEn: '2026-11-24T09:00:00Z' },
];

/** Nomenclador con N-0301 vinculado a "Extracción simple" (seed de C-19). */
export const NOMENCLADOR_C19 = [
  { id: 101, codigo: 'N-0101', descripcion: 'Consulta', prestacion: { id: 1, nombre: 'Consulta o control' }, activo: true },
  { id: 102, codigo: 'N-0201', descripcion: 'Limpieza', prestacion: { id: 2, nombre: 'Limpieza (profilaxis)' }, activo: true },
  { id: 103, codigo: 'N-0301', descripcion: 'Extracción simple', prestacion: { id: 4, nombre: 'Extracción simple' }, activo: true },
];

/** "Plan Base" (id 11 de la obra social 1) cubre N-0101 al 100 % y N-0201 al 50 %. */
export const COBERTURA_PLAN_BASE = [
  { nomencladorId: 101, codigo: 'N-0101', descripcion: 'Consulta', activo: true, porcentaje: 100 },
  { nomencladorId: 102, codigo: 'N-0201', descripcion: 'Limpieza', activo: true, porcentaje: 50 },
];

const COBERTURA_USADA = { afiliacionId: 31, obraSocial: 'Obra Social Ejemplo', plan: 'Plan Base', numeroAfiliado: 'OSE-000001' };

/** Presupuesto 41 de Paciente Uno: pendiente, con Plan Base. */
export const PRESUPUESTO_41 = {
  id: 41,
  fecha: '2026-11-24',
  venceEl: '2026-12-24',
  estado: 'PENDIENTE',
  vencido: false,
  profesional: { id: 1, nombre: 'Dra. Ana Ejemplo' },
  particular: false,
  cobertura: COBERTURA_USADA,
  items: [
    { id: 1, prestacion: { id: 1, nombre: 'Consulta o control' }, codigoNomenclador: 'N-0101', pieza: null, cara: null, cantidad: 1, precioUnitario: 15000.0, porcentajeCobertura: 100, importe: 15000.0, cobertura: 15000.0, copago: 0.0 },
    { id: 2, prestacion: { id: 2, nombre: 'Limpieza (profilaxis)' }, codigoNomenclador: 'N-0201', pieza: null, cara: null, cantidad: 1, precioUnitario: 25000.0, porcentajeCobertura: 50, importe: 25000.0, cobertura: 12500.0, copago: 12500.0 },
  ],
  totales: { precio: 40000.0, cobertura: 27500.0, copago: 12500.0 },
  creadoEn: '2026-11-24T12:00:00Z',
};

/** Presupuesto 40 de Paciente Uno: pendiente y vencido, con "Extracción simple" en la pieza 36. */
export const PRESUPUESTO_40 = {
  ...PRESUPUESTO_41,
  id: 40,
  fecha: '2026-10-01',
  venceEl: '2026-10-31',
  vencido: true,
  items: [
    { id: 3, prestacion: { id: 4, nombre: 'Extracción simple' }, codigoNomenclador: 'N-0301', pieza: 36, cara: null, cantidad: 1, precioUnitario: 30000.0, porcentajeCobertura: 0, importe: 30000.0, cobertura: 0.0, copago: 30000.0 },
  ],
  totales: { precio: 30000.0, cobertura: 0.0, copago: 30000.0 },
  creadoEn: '2026-10-01T12:00:00Z',
};

/** El 40 como lo recibe la recepción: sin pieza ni cara (PA-24). */
export function sinDatosClinicos(presupuesto: typeof PRESUPUESTO_40): Record<string, unknown> {
  return {
    ...presupuesto,
    items: presupuesto.items.map(({ pieza: _pieza, cara: _cara, ...resto }) => resto),
  };
}

export const SUGERENCIAS = [
  { pieza: 16, cara: 'O', estado: 'CARIES' },
  { pieza: 36, cara: null, estado: 'A_TRATAR' },
];

/** Catálogo, cobertura de los pacientes (C-49) y todo lo de presupuestos de los datos ficticios. */
export function responderPresupuestos(servidor: ServidorFalso): void {
  responderCoberturas(servidor);
  servidor.responder('GET /aranceles', () => json(200, ARANCELES));
  servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR_C19));
  servidor.responder('GET /obras-sociales/1/planes/11/coberturas', () => json(200, COBERTURA_PLAN_BASE));
  servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [PRESUPUESTO_41, PRESUPUESTO_40]));
  servidor.responder('GET /pacientes/2/presupuestos', () => json(200, []));
  servidor.responder('GET /pacientes/1/presupuestos/sugerencias', () => json(200, SUGERENCIAS));
  servidor.responder('GET /pacientes/2/presupuestos/sugerencias', () => json(200, []));
}
