import { cargarProfesionales } from '../paciente/catalogo.ts';
import { ErrorNegocio } from '../shared/api/errores.ts';
import { aIsoConZona, fechaDeInstante, formatearHora, hoy, type Fecha } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearAviso, type Campo, type Opcion } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import { crearBuscadorPacientes, MENSAJE_PACIENTE_OBLIGATORIO } from './buscador-pacientes.ts';
import { nombreDe } from './catalogos.ts';
import type { TurnoAgenda } from './consulta.ts';
import {
  abrirDialogo,
  abrirReprogramacion,
  entrada,
  leerNumeroReserva,
  manejarEnvio,
  MENSAJE_PROFESIONALES_NO_CARGADOS,
  opcionesActivas,
  resumenDe,
  selector,
  validarObligatorios,
  type ControlAgenda,
  type Dialogo,
} from './formularios.ts';

/** C-45: sobreturno (US-013, RN-AG-06) y extensión de un turno (US-019) desde la agenda del personal. */

export const AYUDA_MOTIVO = 'Motivo administrativo. No escribas datos de salud.';
export const NOTA_SILLON = 'El sillón lo asigna el sistema: el habitual del profesional si está libre, o el primero libre con margen.';
export const AYUDA_HORA_SOBRETURNO = 'Puede superponerse con otro turno del profesional. Dura lo que dura la prestación.';
export const AVISO_CHOQUE = 'La extensión choca con otro turno. No se movió nada.';
const LARGO_MAXIMO_MOTIVO = 500;
const PASO_MINUTOS = 5;
const EXTENSION_POR_DEFECTO_MINUTOS = 15;
/** Margen mínimo entre turnos (RN-AG-04): un turno que empieza antes de nuevo fin + margen también choca. */
const MARGEN_MINIMO_MINUTOS = 10;
const MS_POR_MINUTO = 60_000;
const CODIGOS_DE_CHOQUE = new Set(['SOLAPAMIENTO_SILLON', 'SOLAPAMIENTO_PROFESIONAL', 'MARGEN_INSUFICIENTE']);
const ESTADOS_EXTENSIBLES = new Set(['RESERVADO', 'CONFIRMADO']);

/** Lo que trae un sobreturno abierto desde el choque de una extensión (design D7 y D8). */
export interface PrecargaSobreturno {
  /** `null` si la agenda no informó el paciente: entonces se busca, como en un sobreturno nuevo. */
  readonly pacienteId: number | null;
  readonly paciente: string;
  readonly prestacionId: number;
  readonly profesionalId: number;
  readonly fecha: Fecha;
  readonly hora: string;
  readonly motivo: string;
}

/** Turno activo, no sobreturno: el único que ofrece "Extender" (design D5). */
export function permiteExtender(turno: TurnoAgenda): boolean {
  return ESTADOS_EXTENSIBLES.has(turno.estado) && !turno.esSobreturno;
}

function minutosDelDia(hora: string): number {
  const [h, m] = hora.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

function horaDeMinutos(minutos: number): string {
  return `${String(Math.floor(minutos / 60)).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`;
}

/** Próxima hora múltiplo de 5 si la fecha es hoy; si no, vacía (design D2). */
function horaPropuesta(fecha: Fecha): string {
  if (fecha !== hoy()) {
    return '';
  }
  const ahora = minutosDelDia(formatearHora(new Date()));
  const redondeada = Math.ceil(ahora / PASO_MINUTOS) * PASO_MINUTOS;
  return redondeada >= 24 * 60 ? '' : horaDeMinutos(redondeada);
}

/**
 * Turno de la agenda cargada que choca con extender `turno` hasta `nuevoFin` (design D7): activo, distinto del
 * extendido, del mismo sillón o del mismo profesional, y que empieza entre el fin actual y el nuevo fin más el
 * margen mínimo. Si hay varios, el que empieza primero; si no hay ninguno, `null`.
 */
export function turnoQueChoca(turno: TurnoAgenda, nuevoFin: string, turnos: readonly TurnoAgenda[]): TurnoAgenda | null {
  const desde = Date.parse(turno.fin);
  const hasta = Date.parse(nuevoFin) + MARGEN_MINIMO_MINUTOS * MS_POR_MINUTO;
  const candidatos = turnos.filter((otro) => {
    const inicio = Date.parse(otro.inicio);
    return (
      otro.id !== turno.id &&
      ESTADOS_EXTENSIBLES.has(otro.estado) &&
      (otro.sillonId === turno.sillonId || otro.profesionalId === turno.profesionalId) &&
      inicio >= desde &&
      inicio < hasta
    );
  });
  candidatos.sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
  return candidatos[0] ?? null;
}

/** Suma una ayuda visible al campo y la enlaza con `aria-describedby`, antes del mensaje de error. */
function conAyuda<T extends HTMLInputElement | HTMLSelectElement>(campo: Campo<T>, textoAyuda: string): Campo<T> {
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.id = `${campo.control.id}-ayuda`;
  ayuda.textContent = textoAyuda;
  campo.control.after(ayuda);
  const descripcion = campo.control.getAttribute('aria-describedby') ?? '';
  campo.control.setAttribute('aria-describedby', `${ayuda.id} ${descripcion}`.trim());
  return campo;
}

function campoMotivo(valor = ''): Campo<HTMLInputElement> {
  const motivo = conAyuda(entrada('Motivo', 'text', 'motivo', valor), AYUDA_MOTIVO);
  motivo.control.maxLength = LARGO_MAXIMO_MOTIVO;
  motivo.control.autocomplete = 'off';
  return motivo;
}

function campoHora(etiqueta: string, nombre: string, valor: string, ayuda: string): Campo<HTMLInputElement> {
  const hora = conAyuda(entrada(etiqueta, 'time', nombre, valor), ayuda);
  hora.control.step = String(PASO_MINUTOS * 60);
  return hora;
}

/** Paciente que ya viene del turno: texto fijo con el id guardado (design D8). */
function crearPacienteFijo(nombre: string): { contenedor: HTMLElement; mostrarError(mensaje: string): void } {
  const contenedor = document.createElement('div');
  contenedor.className = 'campo';
  const etiqueta = document.createElement('span');
  etiqueta.className = 'etiqueta-campo';
  etiqueta.textContent = 'Paciente';
  const valor = document.createElement('p');
  valor.className = 'valor-fijo';
  valor.textContent = nombre;
  const error = document.createElement('p');
  error.className = 'campo-error';
  contenedor.append(etiqueta, valor, error);
  return {
    contenedor,
    mostrarError(mensaje) {
      error.textContent = mensaje;
    },
  };
}

function enDosColumnas(...campos: readonly { contenedor: HTMLElement }[]): HTMLDivElement {
  const fila = document.createElement('div');
  fila.className = 'dos-columnas';
  fila.append(...campos.map((c) => c.contenedor));
  return fila;
}

/** "Sobreturno" (design D1 a D4, D8): paciente, prestación, profesional, fecha, hora libre y motivo; sin sillón. */
export function abrirSobreturno(control: ControlAgenda, precarga?: PrecargaSobreturno): void {
  const catalogos = control.catalogos();
  let dialogo: Dialogo | undefined;
  const sesionVencida = (causa: unknown): boolean => {
    const vencida = control.sesionVencida(causa);
    if (vencida) {
      dialogo?.cerrar();
    }
    return vencida;
  };

  const idFijo = precarga?.pacienteId ?? null;
  const buscador = idFijo === null ? crearBuscadorPacientes(control.api, { sesionVencida }) : null;
  const pacienteFijo = idFijo === null || precarga === undefined ? null : crearPacienteFijo(precarga.paciente);
  const paciente = buscador ?? pacienteFijo;
  const pacienteId = (): number | null => (buscador === null ? idFijo : buscador.pacienteId());

  const prestacion = selector('Prestación', 'prestacionId', opcionesActivas(catalogos.prestaciones, 'Elegí una prestación'));
  const fijo = control.profesionalFijo;
  const profesional = selector(
    'Profesional',
    'profesionalId',
    fijo === undefined
      ? [{ valor: '', texto: 'Elegí un profesional' }]
      : [{ valor: String(fijo), texto: nombreDe(catalogos, 'profesionales', fijo) }],
  );
  profesional.control.disabled = true;
  const avisoProfesionales = crearAviso('status');
  avisoProfesionales.className = 'aviso aviso-alerta';
  profesional.contenedor.append(avisoProfesionales);

  const fechaInicial = precarga?.fecha ?? control.fechaInicial();
  const fecha = entrada('Fecha', 'date', 'fecha', fechaInicial);
  usarSelectorFecha(fecha);
  const hora = campoHora('Hora', 'hora', precarga?.hora ?? horaPropuesta(fechaInicial), AYUDA_HORA_SOBRETURNO);
  const motivo = campoMotivo(precarga?.motivo ?? '');
  const nota = document.createElement('p');
  nota.className = 'nota-sillon';
  nota.textContent = NOTA_SILLON;

  dialogo = abrirDialogo('Sobreturno', null, [], 'Cargar sobreturno');
  const abierto = dialogo;
  abierto.formulario.closest('dialog')?.classList.add('dialogo-ancho');
  abierto.general.after(
    paciente?.contenedor ?? document.createElement('div'),
    enDosColumnas(prestacion, profesional),
    enDosColumnas(fecha, hora),
    motivo.contenedor,
    nota,
  );

  let consultaProfesionales = 0;
  /** Recepción y administración: los habilitados para la prestación (design D3). */
  function cargarHabilitados(elegido?: number): void {
    consultaProfesionales += 1;
    const esta = consultaProfesionales;
    avisoProfesionales.textContent = '';
    cambiarOpciones(profesional.control, [{ valor: '', texto: 'Elegí un profesional' }]);
    profesional.control.disabled = true;
    if (prestacion.control.value === '') {
      return;
    }
    cargarProfesionales(control.api, Number(prestacion.control.value))
      .then((habilitados) => {
        if (esta !== consultaProfesionales) {
          return;
        }
        const opciones: Opcion[] = habilitados.map((p) => ({ valor: String(p.id), texto: p.nombre }));
        cambiarOpciones(profesional.control, [{ valor: '', texto: 'Elegí un profesional' }, ...opciones]);
        profesional.control.disabled = false;
        if (elegido !== undefined && habilitados.some((p) => p.id === elegido)) {
          profesional.control.value = String(elegido);
        }
      })
      .catch((causa: unknown) => {
        if (esta !== consultaProfesionales || sesionVencida(causa)) {
          return;
        }
        avisoProfesionales.textContent = MENSAJE_PROFESIONALES_NO_CARGADOS;
      });
  }

  prestacion.control.addEventListener('change', () => {
    prestacion.mostrarError('');
    profesional.mostrarError('');
    if (fijo === undefined) {
      cargarHabilitados();
    }
  });
  profesional.control.addEventListener('change', () => profesional.mostrarError(''));

  if (precarga !== undefined) {
    prestacion.control.value = String(precarga.prestacionId);
    if (fijo === undefined) {
      cargarHabilitados(precarga.profesionalId);
    }
  }

  const porCampo = { pacienteId: paciente ?? motivo, prestacionId: prestacion, profesionalId: profesional, inicio: hora, motivo };

  abierto.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const elegido = pacienteId();
    paciente?.mostrarError(elegido === null ? MENSAJE_PACIENTE_OBLIGATORIO : '');
    const sinPrestacion = prestacion.control.value === '';
    prestacion.mostrarError(sinPrestacion ? 'Elegí una prestación.' : '');
    const sinProfesional = !sinPrestacion && profesional.control.value === '';
    profesional.mostrarError(sinProfesional ? 'Elegí un profesional.' : '');
    const completos = validarObligatorios([
      [fecha, 'Elegí la fecha.'],
      [hora, 'Elegí la hora.'],
      [motivo, 'Escribí el motivo del sobreturno.'],
    ]);
    if (elegido === null || sinPrestacion || sinProfesional || !completos) {
      return;
    }
    const cuerpo = {
      pacienteId: elegido,
      prestacionId: Number(prestacion.control.value),
      profesionalId: Number(profesional.control.value),
      inicio: aIsoConZona(fecha.control.value, hora.control.value),
      motivo: motivo.control.value.trim(),
    };
    manejarEnvio(
      control,
      abierto,
      porCampo,
      () => control.api.crear<unknown>('/turnos/sobreturno', cuerpo),
      (respuesta) => {
        const sillonId = typeof respuesta === 'object' && respuesta !== null ? Reflect.get(respuesta, 'sillonId') : null;
        const sillon = typeof sillonId === 'number' ? nombreDe(catalogos, 'sillones', sillonId) : 'un sillón libre';
        return `Sobreturno cargado en ${sillon}. Número de reserva: ${leerNumeroReserva(respuesta)}.`;
      },
    );
  });
}

function esChoque(causa: unknown): causa is ErrorNegocio {
  return causa instanceof ErrorNegocio && CODIGOS_DE_CHOQUE.has(causa.codigo);
}

/** Aviso del choque con sus salidas (design D7): reprogramar el turno que choca, si se conoce, o cargar un sobreturno. */
function crearAvisoChoque(
  causa: ErrorNegocio,
  siguiente: TurnoAgenda | null,
  alReprogramar: (turno: TurnoAgenda) => void,
  alCargarSobreturno: () => void,
): HTMLDivElement {
  const aviso = document.createElement('div');
  aviso.className = 'aviso-choque';
  aviso.setAttribute('role', 'alert');
  const titulo = document.createElement('p');
  const fuerte = document.createElement('strong');
  fuerte.textContent = AVISO_CHOQUE;
  titulo.append(fuerte);
  const motivo = document.createElement('p');
  motivo.textContent = mensajeDeError(causa);
  const salida = document.createElement('p');
  salida.textContent =
    siguiente === null
      ? 'Podés cargar la atención como sobreturno.'
      : 'Podés reprogramar el turno que choca o cargar la atención como sobreturno.';
  const acciones = document.createElement('div');
  acciones.className = 'aviso-choque-acciones';
  if (siguiente !== null) {
    const reprogramar = document.createElement('button');
    reprogramar.type = 'button';
    reprogramar.className = 'btn';
    reprogramar.textContent = `Reprogramar el turno de las ${formatearHora(siguiente.inicio)}`;
    reprogramar.addEventListener('click', () => alReprogramar(siguiente));
    acciones.append(reprogramar);
  }
  const sobreturno = document.createElement('button');
  sobreturno.type = 'button';
  sobreturno.className = 'btn';
  sobreturno.textContent = 'Cargar como sobreturno';
  sobreturno.addEventListener('click', alCargarSobreturno);
  acciones.append(sobreturno);
  aviso.append(titulo, motivo, salida, acciones);
  return aviso;
}

/** "Extender" (design D6 y D7): nuevo fin del mismo día, posterior al actual, y motivo. */
export function abrirExtension(control: ControlAgenda, turno: TurnoAgenda): void {
  const finActual = formatearHora(turno.fin);
  const fechaDelTurno = fechaDeInstante(turno.inicio);
  const propuesto = Math.min(minutosDelDia(finActual) + EXTENSION_POR_DEFECTO_MINUTOS, 23 * 60 + 55);
  const nuevoFin = campoHora('Nuevo fin', 'fin', horaDeMinutos(propuesto), `Tiene que ser después de las ${finActual}.`);
  const motivo = campoMotivo();
  const dialogo = abrirDialogo('Extender turno', resumenDe(turno), [nuevoFin, motivo], 'Extender turno');
  let aviso: HTMLDivElement | null = null;

  const cerrarYReprogramar = (siguiente: TurnoAgenda): void => {
    dialogo.cerrar();
    abrirReprogramacion(control, siguiente);
  };
  const cerrarYCargarSobreturno = (): void => {
    dialogo.cerrar();
    abrirSobreturno(control, {
      pacienteId: turno.pacienteId,
      paciente: `${turno.pacienteApellido}, ${turno.pacienteNombre}`,
      prestacionId: turno.prestacionId,
      profesionalId: turno.profesionalId,
      fecha: fechaDelTurno,
      hora: finActual,
      motivo: motivo.control.value.trim(),
    });
  };

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    aviso?.remove();
    aviso = null;
    const hora = nuevoFin.control.value;
    const posterior = hora !== '' && minutosDelDia(hora) > minutosDelDia(finActual);
    nuevoFin.mostrarError(posterior ? '' : `Elegí una hora posterior a las ${finActual}.`);
    const conMotivo = validarObligatorios([[motivo, 'Escribí el motivo de la extensión.']]);
    if (!posterior || !conMotivo) {
      return;
    }
    const fin = aIsoConZona(fechaDelTurno, hora);
    manejarEnvio(
      control,
      dialogo,
      { fin: nuevoFin, motivo },
      () =>
        control.api.crear<unknown>(`/turnos/${turno.id}/extender`, { fin, motivo: motivo.control.value.trim() }).catch(
          (causa: unknown) => {
            if (esChoque(causa)) {
              const siguiente = turnoQueChoca(turno, fin, control.turnosCargados());
              aviso = crearAvisoChoque(causa, siguiente, cerrarYReprogramar, cerrarYCargarSobreturno);
              dialogo.general.after(aviso);
            }
            throw causa;
          },
        ),
      () => `Turno extendido hasta las ${hora}.`,
      (causa) => (esChoque(causa) ? '' : mensajeDeError(causa)),
    );
  });
}
