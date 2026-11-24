import { diaDeLaSemana, fechaDeInstante, formatearHora, type Fecha } from '../shared/fecha/zona.ts';
import type { Rol } from '../shared/sesion/sesion.ts';
import { crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import {
  accionesDeAsistencia,
  etiquetaDeAsistencia,
  permiteCancelarYReprogramar,
  type AccionAsistencia,
} from './asistencia.ts';
import { nombreDe, type Catalogos } from './catalogos.ts';
import type { TurnoAgenda } from './consulta.ts';
import { diasDe, type Periodo } from './periodo.ts';

export interface AccionesTurno {
  cancelar(turno: TurnoAgenda): void;
  reprogramar(turno: TurnoAgenda): void;
  /** C-43: el rol de la sesión decide los botones de asistencia; sin esto, no se ofrecen. */
  readonly asistencia?: {
    readonly rol: Rol | null;
    registrar(turno: TurnoAgenda, accion: AccionAsistencia): void;
  };
  /** C-44: abre la ficha clínica del paciente; solo se pasa para ADMIN y ODONTOLOGO (RN-CL-01). */
  verFicha?(turno: TurnoAgenda): void;
}

function fechaCorta(fecha: Fecha): string {
  const [, mes, dia] = fecha.split('-');
  return `${diaDeLaSemana(fecha)} ${dia ?? ''}/${mes ?? ''}`;
}

function etiqueta(textoEtiqueta: string, clase: string): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = `etiqueta ${clase}`;
  span.textContent = textoEtiqueta;
  return span;
}

function nombrePaciente(turno: TurnoAgenda): string {
  return `${turno.pacienteNombre} ${turno.pacienteApellido}`.trim();
}

function botonAsistencia(texto: string, clase: string, descripcion: string, alElegir: () => void): HTMLButtonElement {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = clase;
  boton.textContent = texto;
  boton.setAttribute('aria-label', `${texto}: ${descripcion}`);
  boton.addEventListener('click', alElegir);
  return boton;
}

/** C-43: "Llegó", "Ausente" (deshabilitado hasta la tolerancia) y "Deshacer ausencia", solo para la recepción. */
function botonesDeAsistencia(turno: TurnoAgenda, acciones: AccionesTurno, descripcion: string, ahora: Date): HTMLElement | null {
  const asistencia = acciones.asistencia;
  if (asistencia === undefined) {
    return null;
  }
  const disponibles = accionesDeAsistencia(turno, asistencia.rol, ahora);
  const grupo = document.createElement('div');
  grupo.className = 'turno-asistencia';
  if (disponibles.llego) {
    grupo.append(
      botonAsistencia('Llegó', 'btn btn-llego', descripcion, () => asistencia.registrar(turno, 'llegada')),
    );
  }
  if (disponibles.ausente !== null) {
    const ausente = botonAsistencia('Ausente', 'btn', descripcion, () => asistencia.registrar(turno, 'ausencia'));
    grupo.append(ausente);
    if (!disponibles.ausente.habilitado) {
      ausente.disabled = true;
      const pista = document.createElement('span');
      pista.className = 'turno-pista';
      pista.id = `pista-asistencia-${turno.id}`;
      pista.textContent = `Desde las ${disponibles.ausente.desde}`;
      ausente.setAttribute('aria-describedby', pista.id);
      grupo.append(pista);
    }
  }
  if (disponibles.deshacer) {
    grupo.append(
      botonAsistencia('Deshacer ausencia', 'btn', descripcion, () => asistencia.registrar(turno, 'deshacer-ausencia')),
    );
  }
  return grupo.childElementCount === 0 ? null : grupo;
}

/** Clase del borde: un sobreturno se distingue por encima de todo (C-39); después, la asistencia (C-43). */
function claseDelTurno(turno: TurnoAgenda, claseEtiqueta: string | undefined): string {
  if (turno.esSobreturno) {
    return 'turno-sobreturno';
  }
  if (claseEtiqueta === 'etiqueta-sin-registrar') {
    return 'turno-sin-registrar';
  }
  if (claseEtiqueta === 'etiqueta-llegada') {
    return 'turno-llego';
  }
  return `turno-${turno.estado.toLowerCase()}`;
}

function elementoTurno(turno: TurnoAgenda, catalogos: Catalogos, acciones: AccionesTurno, ahora: Date): HTMLLIElement {
  const item = document.createElement('li');
  const asistencia = etiquetaDeAsistencia(turno, ahora);
  item.className = `turno ${claseDelTurno(turno, asistencia?.clase)}`;
  item.dataset['id'] = String(turno.id);

  const horario = document.createElement('p');
  horario.className = 'turno-horario';
  horario.textContent = `${formatearHora(turno.inicio)} – ${formatearHora(turno.fin)}`;
  if (turno.estado === 'CONFIRMADO') {
    horario.append(' ', etiqueta('Confirmado', 'etiqueta-confirmado'));
  }
  if (turno.esSobreturno) {
    horario.append(' ', etiqueta('Sobreturno', 'etiqueta-sobreturno'));
  }
  if (asistencia !== null) {
    horario.append(' ', etiqueta(asistencia.texto, asistencia.clase));
  }

  const paciente = document.createElement('p');
  paciente.className = 'turno-persona';
  paciente.textContent = nombrePaciente(turno);

  const detalle = document.createElement('p');
  detalle.className = 'turno-detalle';
  detalle.textContent = [
    nombreDe(catalogos, 'profesionales', turno.profesionalId),
    nombreDe(catalogos, 'sillones', turno.sillonId),
    nombreDe(catalogos, 'prestaciones', turno.prestacionId),
  ].join(' · ');

  const descripcion = `${nombrePaciente(turno)}, ${formatearHora(turno.inicio)} – ${formatearHora(turno.fin)}`;
  item.append(horario, paciente, detalle);
  const grupoAsistencia = botonesDeAsistencia(turno, acciones, descripcion, ahora);
  if (grupoAsistencia !== null) {
    item.append(grupoAsistencia);
  }
  const botones = document.createElement('div');
  botones.className = 'turno-acciones';
  const verFicha = acciones.verFicha;
  if (verFicha !== undefined && turno.pacienteId !== null) {
    const ficha = document.createElement('button');
    ficha.type = 'button';
    ficha.className = 'btn';
    ficha.textContent = 'Ver ficha';
    ficha.setAttribute('aria-label', `Ver ficha de ${nombrePaciente(turno)}`);
    ficha.addEventListener('click', () => verFicha(turno));
    botones.append(ficha);
  }
  if (!permiteCancelarYReprogramar(turno)) {
    if (botones.childElementCount > 0) {
      item.append(botones);
    }
    return item;
  }

  const cancelar = document.createElement('button');
  cancelar.type = 'button';
  cancelar.className = 'btn btn-peligro';
  cancelar.textContent = 'Cancelar turno';
  cancelar.setAttribute('aria-label', `Cancelar turno de ${descripcion}`);
  cancelar.addEventListener('click', () => acciones.cancelar(turno));
  const reprogramar = document.createElement('button');
  reprogramar.type = 'button';
  reprogramar.className = 'btn';
  reprogramar.textContent = 'Reprogramar';
  reprogramar.setAttribute('aria-label', `Reprogramar turno de ${descripcion}`);
  reprogramar.addEventListener('click', () => acciones.reprogramar(turno));
  botones.append(cancelar, reprogramar);

  item.append(botones);
  return item;
}

function listaDeTurnos(turnos: readonly TurnoAgenda[], catalogos: Catalogos, acciones: AccionesTurno): HTMLUListElement {
  const lista = document.createElement('ul');
  lista.className = 'turnos';
  const ahora = new Date();
  lista.append(...turnos.map((t) => elementoTurno(t, catalogos, acciones, ahora)));
  return lista;
}

function mensajeVacio(textoMensaje: string): HTMLElement {
  const vacio = crearEstadoVacio({ texto: textoMensaje, chico: true });
  vacio.classList.add('agenda-vacia');
  return vacio;
}

/** Dibuja los turnos del período: una lista para el día o un grupo por día para la semana. */
export function dibujarAgenda(
  contenedor: HTMLElement,
  periodo: Periodo,
  turnos: readonly TurnoAgenda[],
  catalogos: Catalogos,
  acciones: AccionesTurno,
): void {
  contenedor.classList.toggle('semana', periodo.vista === 'semana');
  if (periodo.vista === 'dia') {
    contenedor.replaceChildren(
      turnos.length === 0 ? mensajeVacio('No hay turnos para este día.') : listaDeTurnos(turnos, catalogos, acciones),
    );
    return;
  }
  const secciones = diasDe(periodo).map((dia) => {
    const delDia = turnos.filter((t) => fechaDeInstante(t.inicio) === dia);
    const seccion = document.createElement('section');
    seccion.className = 'agenda-dia';
    const titulo = document.createElement('h2');
    titulo.textContent = fechaCorta(dia);
    seccion.append(
      titulo,
      delDia.length === 0 ? mensajeVacio('Sin turnos.') : listaDeTurnos(delDia, catalogos, acciones),
    );
    return seccion;
  });
  contenedor.replaceChildren(...secciones);
}

export { fechaCorta };
