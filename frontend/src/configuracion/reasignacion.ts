import { aIsoConZona, fechaDeInstante, formatearHora } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearCampo, crearEntrada, crearSelector } from '../shared/ui/campos.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import {
  cargarEspecialidades,
  cargarSillones,
  nombreCompleto,
  type Prestacion,
  type Profesional,
  type Sillon,
} from './api.ts';
import { textoDeTurno, type ClasePeriodo, type TurnoAfectado } from './periodos.ts';
import { abrirDialogo, crearSelectorHora, guardando, mostrarErrorEnFormulario, type ContextoSeccion } from './ui.ts';

/**
 * Datos que el diálogo "Reasignar" consulta y que quedan en memoria mientras la pestaña está
 * abierta (design D8): las prestaciones habilitadas de cada profesional y los sillones.
 * Si un pedido falla, no se guarda, para poder reintentarlo la próxima vez.
 */
export interface RecursosReasignacion {
  especialidades(profesionalId: number): Promise<readonly Prestacion[]>;
  sillones(): Promise<readonly Sillon[]>;
}

export function crearRecursosReasignacion(api: ClienteApi): RecursosReasignacion {
  const especialidades = new Map<number, Promise<readonly Prestacion[]>>();
  let sillones: Promise<readonly Sillon[]> | null = null;
  return {
    especialidades(profesionalId) {
      let pedido = especialidades.get(profesionalId);
      if (pedido === undefined) {
        pedido = cargarEspecialidades(api, profesionalId);
        especialidades.set(profesionalId, pedido);
        pedido.catch(() => especialidades.delete(profesionalId));
      }
      return pedido;
    },
    sillones() {
      if (sillones === null) {
        const pedido = cargarSillones(api);
        sillones = pedido;
        pedido.catch(() => {
          sillones = null;
        });
        return pedido;
      }
      return sillones;
    },
  };
}

export interface OpcionesReasignacion {
  readonly contexto: ContextoSeccion;
  readonly recursos: RecursosReasignacion;
  /** Si el turno está afectado por una ausencia o por un bloqueo (cambia la ruta, design D16). */
  readonly clase: ClasePeriodo;
  /** Profesional ausente (o con la agenda bloqueada), dueño del período y del turno. */
  readonly ausenteId: number;
  readonly periodoId: number;
  readonly turno: TurnoAfectado;
  /** Nombre de la prestación del turno, para el resumen y la ayuda. */
  readonly prestacion: string;
  readonly profesionales: readonly Profesional[];
  /** Se llama cuando la API aceptó la reasignación, con el diálogo ya cerrado. */
  alReasignar(): void;
}

/** Profesionales activos, distintos del ausente, que hacen la prestación del turno (design D8). */
async function habilitados(opciones: OpcionesReasignacion): Promise<Profesional[]> {
  const candidatos = opciones.profesionales.filter((p) => p.activo && p.id !== opciones.ausenteId);
  const resultado = await Promise.all(
    candidatos.map(async (profesional) => {
      const prestaciones = await opciones.recursos.especialidades(profesional.id);
      return prestaciones.some((p) => p.id === opciones.turno.prestacionId) ? profesional : null;
    }),
  );
  return resultado.filter((p): p is Profesional => p !== null);
}

/**
 * Diálogo "Reasignar" de un turno afectado (requisito "Reasignación de un turno afectado"):
 * nuevo profesional obligatorio y, opcionales, otra fecha, otra hora y otro sillón. Lo que queda
 * vacío se envía como `null` y el backend conserva el dato del turno.
 */
export function abrirReasignacion(opciones: OpcionesReasignacion): void {
  const { contexto, turno } = opciones;
  const ausente = opciones.profesionales.find((p) => p.id === opciones.ausenteId);
  const quien = ausente === undefined ? 'el profesional' : nombreCompleto(ausente);

  const nuevo = crearCampo('Nuevo profesional', crearSelector('profesionalId', []));
  const ayudaProfesional = document.createElement('span');
  ayudaProfesional.className = 'ayuda';
  ayudaProfesional.textContent = 'Cargando profesionales…';
  nuevo.contenedor.append(ayudaProfesional);

  const fecha = crearCampo('Otra fecha (opcional)', crearEntrada('date', 'inicio'));
  usarSelectorFecha(fecha, { opcional: true });
  const hora = crearSelectorHora('horaInicio', 'Otra hora (opcional)', {
    opcional: true,
    ariaHora: 'Hora de inicio',
    ariaMinutos: 'Minutos del inicio',
  });
  const grupoHorario = document.createElement('div');
  grupoHorario.className = 'dos-campos';
  grupoHorario.append(fecha.contenedor, hora.contenedor);
  const ayudaHorario = document.createElement('span');
  ayudaHorario.className = 'ayuda';
  ayudaHorario.textContent = 'Si no elegís fecha ni hora, el turno queda en el mismo horario.';

  const sillon = crearCampo(
    'Otro sillón (opcional)',
    crearSelector('sillonId', [{ valor: '', texto: 'El mismo' }]),
  );
  const grupoSillon = document.createElement('div');
  grupoSillon.className = 'dos-campos';
  grupoSillon.append(sillon.contenedor);

  const dialogo = abrirDialogo(
    'Reasignar turno',
    [nuevo.contenedor, grupoHorario, ayudaHorario, grupoSillon],
    'Reasignar',
    {
      resumen: `${textoDeTurno(turno.inicio)} · ${opciones.prestacion}. Hoy lo tiene ${quien}, ${
        opciones.clase === 'bloqueos' ? 'que tiene la agenda bloqueada' : 'que va a estar ausente'
      }.`,
    },
  );
  dialogo.enviar.disabled = true;
  const campos = [nuevo, fecha, hora, sillon];

  void Promise.all([habilitados(opciones), opciones.recursos.sillones().catch((): readonly Sillon[] => [])]).then(
    ([profesionales, sillones]) => {
      cambiarOpciones(
        nuevo.control,
        profesionales.map((p) => ({ valor: String(p.id), texto: nombreCompleto(p) })),
      );
      cambiarOpciones(sillon.control, [
        { valor: '', texto: 'El mismo' },
        ...sillones.filter((s) => s.activo).map((s) => ({ valor: String(s.id), texto: s.nombre })),
      ]);
      if (profesionales.length === 0) {
        ayudaProfesional.textContent = `Ningún otro profesional activo hace ${opciones.prestacion}.`;
        return;
      }
      ayudaProfesional.textContent = `Solo aparecen los profesionales activos que hacen ${opciones.prestacion}.`;
      dialogo.enviar.disabled = false;
    },
    (causa: unknown) => {
      if (contexto.sesionVencida(causa)) {
        dialogo.cerrar();
        return;
      }
      ayudaProfesional.textContent = '';
      dialogo.general.textContent = mensajeDeError(causa);
    },
  );

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    if (nuevo.control.value === '') {
      nuevo.mostrarError('Elegí un profesional.');
      return;
    }
    const profesionalId = Number(nuevo.control.value);
    // Si se elige solo la fecha o solo la hora, el otro dato se conserva del turno.
    const otraFecha = fecha.control.value;
    const otraHora = hora.valor();
    const inicio =
      otraFecha === '' && otraHora === ''
        ? null
        : aIsoConZona(
            otraFecha === '' ? fechaDeInstante(turno.inicio) : otraFecha,
            otraHora === '' ? formatearHora(turno.inicio) : otraHora,
          );
    const sillonId = sillon.control.value === '' ? null : Number(sillon.control.value);
    const ruta =
      `/profesionales/${opciones.ausenteId}/${opciones.clase}/${opciones.periodoId}` +
      `/turnos/${turno.turnoId}/reasignar`;
    void guardando(dialogo.enviar, () => contexto.api.crear(ruta, { profesionalId, inicio, sillonId })).then(
      (ok) => {
        if (ok) {
          dialogo.cerrar();
          opciones.alReasignar();
        }
      },
      (causa: unknown) => {
        if (mostrarErrorEnFormulario(causa, dialogo.general, campos, contexto)) {
          dialogo.cerrar();
        }
      },
    );
  });
}
