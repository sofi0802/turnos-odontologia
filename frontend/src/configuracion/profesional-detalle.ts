import { crearAviso, crearBoton, crearCampo, crearSelector } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import {
  cargarEspecialidades,
  cargarPrestaciones,
  cargarSillones,
  type Prestacion,
  type Profesional,
  type Sillon,
} from './api.ts';
import { cargarEn, guardando, type ContextoSeccion } from './ui.ts';

export const SIN_SILLON_HABITUAL = 'Sin sillón habitual';

interface DatosDetalle {
  readonly especialidades: Prestacion[];
  readonly sillones: Sillon[];
  /** Todas las prestaciones, para las casillas; solo se piden si el rol las edita. */
  readonly prestaciones: Prestacion[];
}

/**
 * Detalle de un profesional (requisitos "Especialidades y sillón habitual" y su edición):
 * sus prestaciones habilitadas y su sillón habitual. Para `ADMIN`, casillas con las prestaciones
 * activas y un selector de sillón habitual, cada uno con su `PUT`.
 * `alCambiarSillon` avisa el nuevo sillón habitual para que la lista no quede desactualizada.
 */
export function crearDetalleProfesional(
  profesional: Profesional,
  contexto: ContextoSeccion,
  alCambiarSillon: (sillonId: number | null) => void,
): HTMLDivElement {
  const detalle = document.createElement('div');
  detalle.className = 'detalle-profesional';
  const titulo = document.createElement('h3');
  titulo.textContent = 'Especialidades y sillón habitual';
  const cuerpo = document.createElement('div');
  detalle.append(titulo, cuerpo);

  const edita = contexto.permisos.editaCatalogos;
  cargarEn(
    cuerpo,
    contexto,
    async (): Promise<DatosDetalle> => {
      const [especialidades, sillones, prestaciones] = await Promise.all([
        cargarEspecialidades(contexto.api, profesional.id),
        cargarSillones(contexto.api),
        edita ? cargarPrestaciones(contexto.api) : Promise.resolve([]),
      ]);
      return { especialidades, sillones, prestaciones };
    },
    (datos, contenedor) => {
      if (edita) {
        dibujarEdicion(contenedor, profesional, datos, contexto, alCambiarSillon);
      } else {
        dibujarLectura(contenedor, profesional, datos);
      }
    },
  );
  return detalle;
}

function nombreSillon(sillones: readonly Sillon[], sillonId: number | null): string {
  if (sillonId === null) {
    return SIN_SILLON_HABITUAL;
  }
  return sillones.find((s) => s.id === sillonId)?.nombre ?? `Sillón ${sillonId}`;
}

function dibujarLectura(contenedor: HTMLElement, profesional: Profesional, datos: DatosDetalle): void {
  const subtitulo = document.createElement('p');
  subtitulo.className = 'item-nombre';
  subtitulo.textContent = 'Prestaciones que hace';
  contenedor.append(subtitulo);
  if (datos.especialidades.length === 0) {
    const vacio = document.createElement('p');
    vacio.className = 'ayuda';
    vacio.textContent = 'No tiene prestaciones habilitadas.';
    contenedor.append(vacio);
  } else {
    const etiquetas = document.createElement('ul');
    etiquetas.className = 'etiquetas';
    etiquetas.append(
      ...datos.especialidades.map((p) => {
        const item = document.createElement('li');
        item.textContent = p.nombre;
        return item;
      }),
    );
    contenedor.append(etiquetas);
  }
  const sillon = document.createElement('p');
  sillon.textContent = `Sillón habitual: ${nombreSillon(datos.sillones, profesional.sillonHabitualId)}`;
  contenedor.append(sillon);
}

function dibujarEdicion(
  contenedor: HTMLElement,
  profesional: Profesional,
  datos: DatosDetalle,
  contexto: ContextoSeccion,
  alCambiarSillon: (sillonId: number | null) => void,
): void {
  const habilitadas = new Set(datos.especialidades.map((p) => p.id));
  const aviso = crearAviso('status');
  const error = crearAviso('alert');

  // Casillas: solo las prestaciones activas (requisito "Edición de especialidades y sillón habitual").
  const grupo = document.createElement('fieldset');
  const leyenda = document.createElement('legend');
  leyenda.textContent = 'Prestaciones que hace';
  const casillas = document.createElement('div');
  casillas.className = 'casillas';
  const controles = datos.prestaciones
    .filter((p) => p.activo)
    .map((p) => {
      const etiqueta = document.createElement('label');
      etiqueta.className = 'casilla';
      const casilla = document.createElement('input');
      casilla.type = 'checkbox';
      casilla.name = 'prestacionIds';
      casilla.value = String(p.id);
      casilla.checked = habilitadas.has(p.id);
      etiqueta.append(casilla, ` ${p.nombre}`);
      casillas.append(etiqueta);
      return { casilla, id: p.id };
    });
  grupo.append(leyenda, casillas);

  const guardarPrestaciones = crearBoton('Guardar prestaciones');
  guardarPrestaciones.className = 'btn btn-principal';
  guardarPrestaciones.addEventListener('click', () => {
    const prestacionIds = controles.filter((c) => c.casilla.checked).map((c) => c.id);
    limpiar();
    void guardando(guardarPrestaciones, () =>
      contexto.api.actualizar(`/profesionales/${profesional.id}/prestaciones`, { prestacionIds }),
    ).then(
      (ok) => {
        if (ok) {
          aviso.textContent = 'Prestaciones guardadas.';
        }
      },
      mostrarError,
    );
  });

  // Selector: los sillones activos más el actual (aunque esté inactivo), o ninguno.
  const opciones = [
    { valor: '', texto: SIN_SILLON_HABITUAL },
    ...datos.sillones
      .filter((s) => s.activo || s.id === profesional.sillonHabitualId)
      .map((s) => ({ valor: String(s.id), texto: s.nombre })),
  ];
  const selector = crearSelector('sillonId', opciones);
  selector.classList.add('control');
  selector.value = profesional.sillonHabitualId === null ? '' : String(profesional.sillonHabitualId);
  const campoSillon = crearCampo('Sillón habitual', selector);

  const guardarSillon = crearBoton('Guardar sillón habitual');
  guardarSillon.className = 'btn';
  guardarSillon.addEventListener('click', () => {
    const sillonId = selector.value === '' ? null : Number(selector.value);
    limpiar();
    void guardando(guardarSillon, () =>
      contexto.api.actualizar(`/profesionales/${profesional.id}/sillon-habitual`, { sillonId }),
    ).then(
      (ok) => {
        if (ok) {
          alCambiarSillon(sillonId);
          aviso.textContent = `Sillón habitual guardado: ${nombreSillon(datos.sillones, sillonId)}.`;
        }
      },
      mostrarError,
    );
  });

  function limpiar(): void {
    aviso.textContent = '';
    error.textContent = '';
  }

  function mostrarError(causa: unknown): void {
    if (!contexto.sesionVencida(causa)) {
      error.textContent = mensajeDeError(causa);
    }
  }

  contenedor.append(grupo, guardarPrestaciones, campoSillon.contenedor, guardarSillon, aviso, error);
}
