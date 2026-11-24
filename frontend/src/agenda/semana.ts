import { diaDeLaSemana, fechaDeInstante, formatearHora, hoy, type Fecha } from '../shared/fecha/zona.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { etiquetaDeAsistencia } from './asistencia.ts';
import { nombreDe, type Catalogos } from './catalogos.ts';
import type { TurnoAgenda } from './consulta.ts';
import { diasDe, type Periodo } from './periodo.ts';
import type { AccionesTurno } from './vista.ts';

/**
 * Agenda semanal como grilla horaria (C-39, etapa E): una columna fija por día, una escala horaria compartida,
 * los turnos ubicados según su hora de inicio y su duración, y los simultáneos lado a lado. Las acciones de cada
 * turno están en su detalle (un diálogo), con los mismos permisos que la lista del día.
 */

/** Horario habitual del consultorio: la escala se extiende si algún turno queda afuera. */
const MINUTO_INICIAL = 8 * 60;
const MINUTO_FINAL = 19 * 60;
/** Con 2 o más turnos a la misma hora se agrupan en un bloque (cada tarjeta ocupa siempre todo el ancho de su día). */
export const MAXIMO_CARRILES = 1;

/** Qué nombre lleva la tarjeta: el del profesional (recepción y administración) o el del paciente (el odontólogo). */
export type NombreEnTarjeta = 'profesional' | 'paciente';

export interface TurnoUbicado {
  readonly turno: TurnoAgenda;
  /** Minutos desde las 00:00 del día. */
  readonly inicio: number;
  readonly fin: number;
  readonly carril: number;
  /** Cuántos carriles tiene el grupo de turnos que se superponen con este. */
  readonly carriles: number;
  /** Número del grupo de turnos que se superponen (los de un mismo grupo comparten carriles). */
  readonly grupo: number;
}

function minutosDe(instante: string): number {
  const [h, m] = formatearHora(instante).split(':');
  return Number(h) * 60 + Number(m);
}

/** Minutos del fin: un turno que cruza la medianoche termina a las 24:00 de su día. */
function minutosDeFin(turno: TurnoAgenda): number {
  return fechaDeInstante(turno.fin) === fechaDeInstante(turno.inicio) ? minutosDe(turno.fin) : 24 * 60;
}

/** Reparte los turnos de un día en carriles: los que se superponen quedan lado a lado, los demás ocupan todo el ancho. */
export function ubicarTurnos(turnos: readonly TurnoAgenda[]): TurnoUbicado[] {
  const ordenados = turnos
    .map((turno) => ({ turno, inicio: minutosDe(turno.inicio), fin: Math.max(minutosDeFin(turno), minutosDe(turno.inicio) + 1) }))
    .sort((a, b) => a.inicio - b.inicio || a.fin - b.fin || a.turno.id - b.turno.id);
  const resultado: TurnoUbicado[] = [];
  let grupo: { turno: TurnoAgenda; inicio: number; fin: number; carril: number }[] = [];
  let finDelGrupo = -1;
  let finesPorCarril: number[] = [];
  let numeroDeGrupo = 0;

  const cerrarGrupo = (): void => {
    const carriles = finesPorCarril.length;
    resultado.push(...grupo.map((g) => ({ ...g, carriles, grupo: numeroDeGrupo })));
    numeroDeGrupo += 1;
    grupo = [];
    finesPorCarril = [];
  };

  for (const t of ordenados) {
    if (grupo.length > 0 && t.inicio >= finDelGrupo) {
      cerrarGrupo();
    }
    let carril = finesPorCarril.findIndex((fin) => fin <= t.inicio);
    if (carril === -1) {
      carril = finesPorCarril.length;
      finesPorCarril.push(t.fin);
    } else {
      finesPorCarril[carril] = t.fin;
    }
    grupo.push({ ...t, carril });
    finDelGrupo = grupo.length === 1 ? t.fin : Math.max(finDelGrupo, t.fin);
  }
  if (grupo.length > 0) {
    cerrarGrupo();
  }
  return resultado;
}

function dosDigitos(n: number): string {
  return String(n).padStart(2, '0');
}

function horaDeMinutos(minutos: number): string {
  return `${dosDigitos(Math.floor(minutos / 60))}:${dosDigitos(minutos % 60)}`;
}

function nombrePaciente(turno: TurnoAgenda): string {
  return `${turno.pacienteNombre} ${turno.pacienteApellido}`.trim();
}

const ESTADOS: Readonly<Record<string, string>> = {
  RESERVADO: 'Reservado',
  CONFIRMADO: 'Confirmado',
  ATENDIDO: 'Atendido',
  AUSENTE: 'Ausente',
  CANCELADO: 'Cancelado',
};

/** Texto del estado que se muestra en la tarjeta: el de asistencia si lo hay (el mismo de la lista), y si no, el del turno. */
function textoDeEstado(turno: TurnoAgenda, ahora: Date): string {
  const asistencia = etiquetaDeAsistencia(turno, ahora);
  if (turno.esSobreturno) {
    return asistencia === null ? 'Sobreturno' : `Sobreturno · ${asistencia.texto}`;
  }
  return asistencia?.texto ?? ESTADOS[turno.estado] ?? turno.estado;
}

/** Clase del color del estado: la misma que usa la lista del día. */
function claseDeColor(turno: TurnoAgenda, ahora: Date): string {
  if (turno.esSobreturno) {
    return 'turno-sobreturno';
  }
  const clase = etiquetaDeAsistencia(turno, ahora)?.clase;
  if (clase === 'etiqueta-sin-registrar') {
    return 'turno-sin-registrar';
  }
  if (clase === 'etiqueta-llegada') {
    return 'turno-llego';
  }
  return `turno-${turno.estado.toLowerCase()}`;
}

/** Ícono corto del estado: acompaña al color y al texto, y es lo único que entra en un turno muy corto. */
function iconoDe(turno: TurnoAgenda, ahora: Date): string {
  const clase = claseDeColor(turno, ahora);
  const iconos: Readonly<Record<string, string>> = {
    'turno-reservado': '○',
    'turno-confirmado': '✓',
    'turno-sobreturno': '+',
    'turno-atendido': '●',
    'turno-ausente': '✕',
    'turno-cancelado': '⊘',
    'turno-sin-registrar': '!',
    'turno-llego': '→',
  };
  return iconos[clase] ?? '○';
}

function tarjeta(
  turno: TurnoAgenda,
  catalogos: Catalogos,
  ahora: Date,
  nombreEnTarjeta: NombreEnTarjeta,
  alAbrir: () => void,
): HTMLButtonElement {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = `turno-tarjeta ${claseDeColor(turno, ahora)}`;
  const profesional = nombreDe(catalogos, 'profesionales', turno.profesionalId);
  const sillon = nombreDe(catalogos, 'sillones', turno.sillonId);
  const prestacion = nombreDe(catalogos, 'prestaciones', turno.prestacionId);
  const estado = textoDeEstado(turno, ahora);
  const horario = `${formatearHora(turno.inicio)} a ${formatearHora(turno.fin)}`;

  const hora = document.createElement('span');
  hora.className = 'tarjeta-hora';
  hora.textContent = formatearHora(turno.inicio);
  // Siempre lo mismo, sea cual sea el tamaño: el horario y un nombre. El resto está en el detalle.
  const nombre = document.createElement('span');
  nombre.className = 'tarjeta-nombre';
  nombre.textContent = nombreEnTarjeta === 'profesional' ? profesional : nombrePaciente(turno);
  const linea = document.createElement('span');
  linea.className = 'tarjeta-linea';
  const icono = document.createElement('span');
  icono.className = 'tarjeta-icono';
  icono.setAttribute('aria-hidden', 'true');
  icono.textContent = iconoDe(turno, ahora);
  linea.append(icono, hora);
  boton.append(linea, nombre);

  const completo = `${nombrePaciente(turno)}, ${horario}, ${profesional}, ${sillon}, ${prestacion}, ${estado}`;
  boton.setAttribute('aria-label', completo);
  boton.title = completo;
  boton.addEventListener('click', alAbrir);
  return boton;
}

function celda(inicio: number, fin: number, desdeGrilla: number, carril: number, carriles: number): HTMLLIElement {
  const item = document.createElement('li');
  item.className = 'turno-celda';
  item.style.setProperty('--desde', String(inicio - desdeGrilla));
  item.style.setProperty('--dur', String(fin - inicio));
  item.style.setProperty('--carril', String(carril));
  item.style.setProperty('--carriles', String(carriles));
  return item;
}

/** Las acciones del detalle cierran el diálogo antes de abrir el suyo (cancelar, reprogramar) o de pedir la agenda. */
function cerrandoAntes(acciones: AccionesTurno, cerrar: () => void): AccionesTurno {
  const asistencia = acciones.asistencia;
  return {
    cancelar: (turno) => {
      cerrar();
      acciones.cancelar(turno);
    },
    reprogramar: (turno) => {
      cerrar();
      acciones.reprogramar(turno);
    },
    ...(asistencia === undefined
      ? {}
      : {
          asistencia: {
            rol: asistencia.rol,
            registrar: (turno, accion) => {
              cerrar();
              asistencia.registrar(turno, accion);
            },
          },
        }),
  };
}

export type CrearTurnoCompleto = (turno: TurnoAgenda, acciones: AccionesTurno) => HTMLElement;

function abrirDetalle(
  titulo: string,
  turnos: readonly TurnoAgenda[],
  acciones: AccionesTurno,
  completo: CrearTurnoCompleto,
): void {
  const lista = document.createElement('ul');
  lista.className = 'turnos';
  const dialogo = abrirDialogo({
    titulo,
    contenido: [lista],
    textoDescartar: 'Cerrar',
    textoPrincipal: 'Cerrar',
  });
  dialogo.principal.hidden = true;
  const propias = cerrandoAntes(acciones, dialogo.cerrar);
  lista.append(...turnos.map((t) => completo(t, propias)));
  // Sin campos, el foco va a "Cerrar" y no al botón oculto.
  dialogo.descartar.focus();
}

function columnaDelDia(
  dia: Fecha,
  delDia: readonly TurnoAgenda[],
  desdeGrilla: number,
  catalogos: Catalogos,
  acciones: AccionesTurno,
  completo: CrearTurnoCompleto,
  ahora: Date,
  nombreEnTarjeta: NombreEnTarjeta,
): HTMLElement {
  const seccion = document.createElement('section');
  seccion.className = 'agenda-dia';
  seccion.setAttribute('aria-label', `${nombreDelDia(dia)}`);
  if (dia === hoy()) {
    seccion.classList.add('es-hoy');
  }
  // Un día sin turnos queda en blanco.
  if (delDia.length === 0) {
    return seccion;
  }

  const lista = document.createElement('ul');
  lista.className = 'turnos-semana';
  const ubicados = ubicarTurnos(delDia);
  const agrupados = ubicados.filter((u) => u.carriles > MAXIMO_CARRILES);
  const sueltos = ubicados.filter((u) => u.carriles <= MAXIMO_CARRILES);

  for (const u of sueltos) {
    const item = celda(u.inicio, u.fin, desdeGrilla, u.carril, u.carriles);
    item.append(
      tarjeta(u.turno, catalogos, ahora, nombreEnTarjeta, () =>
        abrirDetalle(`Turno de ${nombrePaciente(u.turno)}`, [u.turno], acciones, completo),
      ),
    );
    lista.append(item);
  }

  // Los grupos con demasiados turnos a la vez se resumen en un solo bloque; el detalle los lista todos.
  const grupos = new Map<number, TurnoUbicado[]>();
  for (const u of agrupados) {
    grupos.set(u.grupo, [...(grupos.get(u.grupo) ?? []), u]);
  }
  for (const miembros of grupos.values()) {
    const inicio = Math.min(...miembros.map((m) => m.inicio));
    const fin = Math.max(...miembros.map((m) => m.fin));
    const item = celda(inicio, fin, desdeGrilla, 0, 1);
    item.classList.add('turno-celda-grupo');
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'turno-grupo';
    const total = miembros.length;
    boton.textContent = `${total} turnos`;
    const horario = `${horaDeMinutos(inicio)} a ${horaDeMinutos(fin)}`;
    boton.setAttribute('aria-label', `${total} turnos simultáneos, ${horario}. Ver todos`);
    boton.title = `${total} turnos entre ${horario}`;
    boton.addEventListener('click', () =>
      abrirDetalle(
        `Turnos de ${horaDeMinutos(inicio)} a ${horaDeMinutos(fin)}`,
        miembros.map((m) => m.turno),
        acciones,
        completo,
      ),
    );
    item.append(boton);
    lista.append(item);
  }
  seccion.append(lista);
  return seccion;
}

function nombreDelDia(dia: Fecha): string {
  const [, mes, numero] = dia.split('-');
  return `${diaDeLaSemana(dia)} ${numero ?? ''}/${mes ?? ''}`;
}

/** Dibuja la semana: encabezados de los días, escala horaria y una columna por día. */
export function dibujarSemana(
  contenedor: HTMLElement,
  periodo: Periodo,
  turnos: readonly TurnoAgenda[],
  catalogos: Catalogos,
  acciones: AccionesTurno,
  completo: CrearTurnoCompleto,
  nombreEnTarjeta: NombreEnTarjeta,
): void {
  const ahora = new Date();
  const todos = turnos.map((t) => ({ inicio: minutosDe(t.inicio), fin: minutosDeFin(t) }));
  const desdeGrilla = Math.min(MINUTO_INICIAL, ...todos.map((t) => Math.floor(t.inicio / 60) * 60));
  const hastaGrilla = Math.max(MINUTO_FINAL, ...todos.map((t) => Math.ceil(t.fin / 60) * 60));
  const horas = (hastaGrilla - desdeGrilla) / 60;

  const grilla = document.createElement('div');
  grilla.className = 'semana-grilla';
  grilla.setAttribute('role', 'group');
  grilla.setAttribute('aria-label', 'Agenda de la semana');
  grilla.style.setProperty('--semana-horas', String(horas));

  const esquina = document.createElement('div');
  esquina.className = 'semana-esquina';
  esquina.setAttribute('aria-hidden', 'true');
  grilla.append(esquina);

  const dias = diasDe(periodo);
  for (const dia of dias) {
    const encabezado = document.createElement('h2');
    encabezado.className = 'semana-encabezado';
    encabezado.textContent = nombreDelDia(dia);
    if (dia === hoy()) {
      encabezado.setAttribute('aria-current', 'date');
    }
    grilla.append(encabezado);
  }

  const escala = document.createElement('div');
  escala.className = 'semana-escala';
  escala.setAttribute('aria-hidden', 'true');
  for (let i = 0; i < horas; i += 1) {
    const hora = document.createElement('span');
    hora.className = 'semana-hora';
    hora.style.setProperty('--desde', String(i * 60));
    hora.textContent = horaDeMinutos(desdeGrilla + i * 60);
    escala.append(hora);
  }
  grilla.append(escala);

  for (const dia of dias) {
    const delDia = turnos.filter((t) => fechaDeInstante(t.inicio) === dia);
    grilla.append(columnaDelDia(dia, delDia, desdeGrilla, catalogos, acciones, completo, ahora, nombreEnTarjeta));
  }
  contenedor.replaceChildren(grilla);
}
