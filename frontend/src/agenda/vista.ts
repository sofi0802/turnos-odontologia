import { diaDeLaSemana, fechaDeInstante, formatearHora, type Fecha } from '../shared/fecha/zona.ts';
import { nombreDe, type Catalogos } from './catalogos.ts';
import type { TurnoAgenda } from './consulta.ts';
import { diasDe, type Periodo } from './periodo.ts';

export interface AccionesTurno {
  cancelar(turno: TurnoAgenda): void;
  reprogramar(turno: TurnoAgenda): void;
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

function elementoTurno(turno: TurnoAgenda, catalogos: Catalogos, acciones: AccionesTurno): HTMLLIElement {
  const item = document.createElement('li');
  item.className = 'turno';
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

  const paciente = document.createElement('p');
  paciente.className = 'turno-paciente';
  paciente.textContent = nombrePaciente(turno);

  const detalle = document.createElement('p');
  detalle.className = 'turno-detalle';
  detalle.textContent = [
    nombreDe(catalogos, 'profesionales', turno.profesionalId),
    nombreDe(catalogos, 'sillones', turno.sillonId),
    nombreDe(catalogos, 'prestaciones', turno.prestacionId),
  ].join(' · ');

  const botones = document.createElement('div');
  botones.className = 'turno-acciones';
  const descripcion = `${nombrePaciente(turno)}, ${horario.textContent}`;
  const cancelar = document.createElement('button');
  cancelar.type = 'button';
  cancelar.className = 'boton-secundario';
  cancelar.textContent = 'Cancelar turno';
  cancelar.setAttribute('aria-label', `Cancelar turno de ${descripcion}`);
  cancelar.addEventListener('click', () => acciones.cancelar(turno));
  const reprogramar = document.createElement('button');
  reprogramar.type = 'button';
  reprogramar.className = 'boton-secundario';
  reprogramar.textContent = 'Reprogramar';
  reprogramar.setAttribute('aria-label', `Reprogramar turno de ${descripcion}`);
  reprogramar.addEventListener('click', () => acciones.reprogramar(turno));
  botones.append(cancelar, reprogramar);

  item.append(horario, paciente, detalle, botones);
  return item;
}

function listaDeTurnos(turnos: readonly TurnoAgenda[], catalogos: Catalogos, acciones: AccionesTurno): HTMLUListElement {
  const lista = document.createElement('ul');
  lista.className = 'turnos';
  lista.append(...turnos.map((t) => elementoTurno(t, catalogos, acciones)));
  return lista;
}

function mensajeVacio(textoMensaje: string): HTMLParagraphElement {
  const parrafo = document.createElement('p');
  parrafo.className = 'agenda-vacia';
  parrafo.textContent = textoMensaje;
  return parrafo;
}

/** Dibuja los turnos del período: una lista para el día o un grupo por día para la semana. */
export function dibujarAgenda(
  contenedor: HTMLElement,
  periodo: Periodo,
  turnos: readonly TurnoAgenda[],
  catalogos: Catalogos,
  acciones: AccionesTurno,
): void {
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
