import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import {
  cargarEspecialidades,
  cargarPrestaciones,
  cargarSillones,
  nombreCompleto,
  type Prestacion,
  type Profesional,
  type Sillon,
} from './api.ts';
import { cargarCuentas, type CuentaPersonal } from './cuentas-api.ts';
import { abrirAltaUsuario } from './usuario-alta.ts';
import { botonChico, cargarEn, crearEstado, guardando, type ContextoSeccion } from './ui.ts';

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
 * `todos` es la lista de profesionales que ya cargó la pestaña: de ahí sale quién tiene cada sillón.
 * `alCambiarSillon` avisa el nuevo sillón habitual para que la lista no quede desactualizada.
 */
export function crearDetalleProfesional(
  profesional: Profesional,
  contexto: ContextoSeccion,
  todos: readonly Profesional[],
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
        dibujarEdicion(contenedor, profesional, datos, contexto, todos, alCambiarSillon);
      } else {
        dibujarLectura(contenedor, profesional, datos);
      }
    },
  );
  // El acceso a la aplicación solo lo ve y lo da el administrador (C-41).
  if (contexto.permisos.gestionaUsuarios) {
    const acceso = document.createElement('div');
    acceso.className = 'acceso';
    detalle.append(acceso);
    const recargar = cargarEn(
      acceso,
      contexto,
      () => cargarCuentas(contexto.api),
      (cuentas, contenedor) => dibujarAcceso(contenedor, profesional, cuentas, todos, contexto, () => recargar()),
    );
  }
  return detalle;
}

/** "Acceso a la aplicación": la cuenta del profesional o, si no tiene, "Dar acceso" (spec frontend-cuentas-personal). */
function dibujarAcceso(
  contenedor: HTMLElement,
  profesional: Profesional,
  cuentas: readonly CuentaPersonal[],
  todos: readonly Profesional[],
  contexto: ContextoSeccion,
  recargar: () => void,
): void {
  const texto = document.createElement('div');
  const titulo = document.createElement('h4');
  titulo.textContent = 'Acceso a la aplicación';
  const estado = document.createElement('p');
  texto.append(titulo, estado);
  contenedor.append(texto);
  const cuenta = cuentas.find((c) => c.profesionalId === profesional.id);
  if (cuenta !== undefined) {
    estado.append('Acceso: ');
    const usuario = document.createElement('b');
    usuario.textContent = cuenta.username;
    estado.append(usuario);
    contenedor.append(crearEstado(cuenta.activo));
    return;
  }
  if (!profesional.activo) {
    estado.textContent = 'No tiene cuenta. Activá el profesional para darle acceso.';
    return;
  }
  estado.textContent = 'Todavía no tiene cuenta para ingresar.';
  const dar = botonChico('Dar acceso', 'principal');
  dar.addEventListener('click', () => {
    abrirAltaUsuario(contexto, { profesionales: todos, cuentas, profesionalFijo: profesional }, recargar);
  });
  contenedor.append(dar);
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
  todos: readonly Profesional[],
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

  // Tarjetas de opción: los sillones activos más el actual (aunque esté inactivo), y "Sin sillón habitual".
  // Un sillón que es el habitual de otro profesional aparece deshabilitado (RN-RO-09, design D11).
  let persistido = profesional.sillonHabitualId;
  const grupoSillon = document.createElement('fieldset');
  grupoSillon.className = 'campo';
  const leyendaSillon = document.createElement('legend');
  leyendaSillon.textContent = 'Sillón habitual';
  const tarjetas = document.createElement('div');
  tarjetas.className = 'opciones opciones-sillon';
  const nombreGrupo = `sillonId-${profesional.id}`;
  const candidatas: Array<{ id: number | null; nombre: string }> = [
    ...datos.sillones
      .filter((sillon) => sillon.activo || sillon.id === profesional.sillonHabitualId)
      .map((sillon) => ({ id: sillon.id, nombre: sillon.nombre })),
    { id: null, nombre: SIN_SILLON_HABITUAL },
  ];
  const opcionesSillon = candidatas.map(({ id, nombre }) => {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'opcion';
    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = nombreGrupo;
    radio.value = id === null ? '' : String(id);
    const cuerpo = document.createElement('span');
    const titulo = document.createElement('span');
    titulo.className = 'opcion-nombre';
    titulo.textContent = nombre;
    const estado = document.createElement('small');
    cuerpo.append(titulo, estado);
    etiqueta.append(radio, cuerpo);
    tarjetas.append(etiqueta);
    radio.addEventListener('change', pintarEstados);
    return { id, radio, etiqueta, estado };
  });
  grupoSillon.append(leyendaSillon, tarjetas);
  const ayudaSillon = document.createElement('span');
  ayudaSillon.className = 'ayuda';
  ayudaSillon.textContent = 'Un sillón ocupado es el habitual de otro profesional. Para pasarlo, quitáselo primero.';
  grupoSillon.append(ayudaSillon);

  /** Marca el estado de cada tarjeta según los sillones que tienen hoy los demás profesionales. */
  function pintarEstados(): void {
    for (const opcion of opcionesSillon) {
      const ocupante =
        opcion.id === null ? undefined : todos.find((p) => p.id !== profesional.id && p.sillonHabitualId === opcion.id);
      opcion.radio.disabled = ocupante !== undefined;
      opcion.etiqueta.classList.toggle('opcion-ocupada', ocupante !== undefined);
      if (ocupante !== undefined) {
        opcion.estado.textContent = `Ocupado · ${nombreCompleto(ocupante)}`;
      } else if (opcion.radio.checked) {
        opcion.estado.textContent = 'Elegido';
      } else {
        opcion.estado.textContent = opcion.id === null ? 'Lo asigna la reserva' : 'Libre';
      }
    }
  }

  function elegirPersistido(): void {
    const actual = opcionesSillon.find((o) => o.id === persistido);
    if (actual !== undefined) {
      actual.radio.checked = true;
    }
    pintarEstados();
  }
  elegirPersistido();

  const guardarSillon = crearBoton('Guardar sillón habitual');
  guardarSillon.className = 'btn';
  guardarSillon.addEventListener('click', () => {
    const sillonId = opcionesSillon.find((o) => o.radio.checked)?.id ?? null;
    limpiar();
    void guardando(guardarSillon, () =>
      contexto.api.actualizar(`/profesionales/${profesional.id}/sillon-habitual`, { sillonId }),
    ).then(
      (ok) => {
        if (ok) {
          persistido = sillonId;
          alCambiarSillon(sillonId);
          pintarEstados();
          aviso.textContent = `Sillón habitual guardado: ${nombreSillon(datos.sillones, sillonId)}.`;
        }
      },
      (causa: unknown) => {
        // El sillón anterior sigue elegido, y los ocupados se vuelven a calcular.
        elegirPersistido();
        mostrarError(causa);
      },
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

  const bloquePrestaciones = document.createElement('div');
  bloquePrestaciones.className = 'grupo-config';
  bloquePrestaciones.append(grupo, guardarPrestaciones);
  const bloqueSillon = document.createElement('div');
  bloqueSillon.className = 'grupo-config';
  bloqueSillon.append(grupoSillon, guardarSillon);
  contenedor.append(bloquePrestaciones, bloqueSillon, aviso, error);
}
