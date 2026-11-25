import { json, type ServidorFalso } from './apoyo.ts';

/** Datos ficticios de los escenarios de C-49 (spec `frontend-coberturas`). */

export const OBRAS_SOCIALES = [
  {
    id: 1,
    nombre: 'Obra Social Ejemplo',
    sigla: 'OSE',
    tipo: 'OBRA_SOCIAL',
    activa: true,
    planes: [
      { id: 11, obraSocialId: 1, nombre: 'Plan Base', activo: true },
      { id: 12, obraSocialId: 1, nombre: 'Plan Integral', activo: false },
    ],
  },
  {
    id: 2,
    nombre: 'Prepaga Prueba',
    sigla: null,
    tipo: 'PREPAGA',
    activa: true,
    planes: [{ id: 21, obraSocialId: 2, nombre: 'Plan Oro', activo: true }],
  },
];

export const NOMENCLADOR = [
  { id: 101, codigo: 'N-0101', descripcion: 'Consulta', prestacion: { id: 1, nombre: 'Consulta o control' }, activo: true },
  { id: 102, codigo: 'N-0201', descripcion: 'Limpieza', prestacion: { id: 2, nombre: 'Limpieza (profilaxis)' }, activo: true },
  { id: 103, codigo: 'N-0301', descripcion: 'Extracción simple', prestacion: null, activo: false },
];

const OSE = { id: 1, nombre: 'Obra Social Ejemplo', sigla: 'OSE', tipo: 'OBRA_SOCIAL' };
const PP = { id: 2, nombre: 'Prepaga Prueba', sigla: null, tipo: 'PREPAGA' };

/** "Paciente Uno" con "Plan Base", `OSE-000001`, desde 2026-01-01 sin fecha de fin. */
export const AFILIACION_UNO = {
  id: 31,
  obraSocial: OSE,
  plan: { id: 11, nombre: 'Plan Base' },
  numeroAfiliado: 'OSE-000001',
  vigenteDesde: '2026-01-01',
  vigenteHasta: null,
  vigente: true,
};

/** "Paciente Dos" tuvo "Plan Oro" con `PP-77` del 2026-01-01 al 2026-10-31. */
export const AFILIACION_DOS = {
  id: 32,
  obraSocial: PP,
  plan: { id: 21, nombre: 'Plan Oro' },
  numeroAfiliado: 'PP-77',
  vigenteDesde: '2026-01-01',
  vigenteHasta: '2026-10-31',
  vigente: false,
};

export const COBERTURA_UNO = { particular: false, afiliacion: AFILIACION_UNO };
export const PARTICULAR = { particular: true, afiliacion: null };

/** Responde la cobertura y el historial de los dos pacientes del seed y el catálogo. */
export function responderCoberturas(servidor: ServidorFalso): void {
  servidor.responder('GET /obras-sociales', () => json(200, OBRAS_SOCIALES));
  servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR));
  servidor.responder('GET /pacientes/1/cobertura', () => json(200, COBERTURA_UNO));
  servidor.responder('GET /pacientes/1/afiliaciones', () => json(200, [AFILIACION_UNO]));
  servidor.responder('GET /pacientes/2/cobertura', () => json(200, PARTICULAR));
  servidor.responder('GET /pacientes/2/afiliaciones', () => json(200, [AFILIACION_DOS]));
}
