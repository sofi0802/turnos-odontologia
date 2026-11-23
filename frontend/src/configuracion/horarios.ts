import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { montarProfesionalElegido } from './profesional-elegido.ts';
import { botonChico, cargarEn, crearCabecera, crearSelectorHora, guardando, type ContextoSeccion } from './ui.ts';

/** Franja de trabajo tal como la usa la API: `dia` 1 (lunes) a 7 (domingo), horas "HH:mm". */
export interface Franja {
  readonly dia: number;
  readonly desde: string;
  readonly hasta: string;
}

/** Franja de la copia local; `nueva` marca las agregadas que todavía no se guardaron (design D5). */
interface FranjaLocal extends Franja {
  readonly nueva: boolean;
}

export const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const;

const HORA = /^(\d{2}):(\d{2})/;

function rutaHorarios(profesionalId: number): string {
  return `/profesionales/${profesionalId}/horarios`;
}

function leerHora(valor: unknown): string | null {
  if (typeof valor !== 'string') {
    return null;
  }
  const partes = HORA.exec(valor);
  return partes === null ? null : `${partes[1]}:${partes[2]}`;
}

function leerFranja(valor: unknown): Franja | null {
  if (typeof valor !== 'object' || valor === null) {
    return null;
  }
  const dia: unknown = Reflect.get(valor, 'dia');
  const desde = leerHora(Reflect.get(valor, 'desde'));
  const hasta = leerHora(Reflect.get(valor, 'hasta'));
  if (typeof dia !== 'number' || !Number.isInteger(dia) || dia < 1 || dia > 7 || desde === null || hasta === null) {
    return null;
  }
  return { dia, desde, hasta };
}

/** Lee `{"profesionalId":1,"franjas":[...]}` descartando las franjas con forma inesperada. */
export function leerHorario(cuerpo: unknown, ruta: string): Franja[] {
  const franjas: unknown = typeof cuerpo === 'object' && cuerpo !== null ? Reflect.get(cuerpo, 'franjas') : undefined;
  if (!Array.isArray(franjas)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return ordenar(franjas.map(leerFranja).filter((f): f is Franja => f !== null));
}

function ordenar<T extends Franja>(franjas: readonly T[]): T[] {
  return [...franjas].sort((a, b) => a.dia - b.dia || a.desde.localeCompare(b.desde) || a.hasta.localeCompare(b.hasta));
}

async function cargarHorario(api: ClienteApi, profesionalId: number): Promise<Franja[]> {
  const ruta = rutaHorarios(profesionalId);
  return leerHorario(await api.obtener<unknown>(ruta), ruta);
}

/**
 * Pestaña "Horarios" (requisitos "Horarios de trabajo" y "Edición de horarios por el administrador").
 * Muestra las franjas del profesional elegido por día; `ADMIN` edita una copia local y la guarda
 * entera con un `PUT` (design D5). Los demás roles solo leen.
 */
export function montarHorarios(panel: HTMLElement, contexto: ContextoSeccion): void {
  panel.append(crearCabecera('Horarios de trabajo'));
  const contenido = document.createElement('div');

  montarProfesionalElegido(panel, contexto, 'Tus horarios', (profesionalId) => {
    // Un contenedor nuevo por profesional: si una carga anterior termina tarde, dibuja fuera de la página.
    const destino = document.createElement('div');
    contenido.replaceChildren(destino);
    cargarEn(destino, contexto, () => cargarHorario(contexto.api, profesionalId), (franjas, contenedor) => {
      dibujarHorario(contenedor, contexto, profesionalId, franjas);
    });
  });

  panel.append(contenido);
}

function dibujarHorario(
  contenedor: HTMLElement,
  contexto: ContextoSeccion,
  profesionalId: number,
  franjasLeidas: readonly Franja[],
): void {
  const editable = contexto.permisos.editaHorarios;
  let leidas: readonly Franja[] = franjasLeidas;
  let local: FranjaLocal[] = leidas.map((f) => ({ ...f, nueva: false }));

  const error = crearAviso('alert');
  const exito = crearAviso('status');
  const lista = document.createElement('ul');
  lista.className = 'franjas';

  const limpiarAvisos = (): void => {
    error.replaceChildren();
    exito.textContent = '';
  };

  const pintarLista = (): void => {
    lista.replaceChildren(
      ...DIAS.map((nombre, indice) => {
        const dia = indice + 1;
        const item = document.createElement('li');
        const etiqueta = document.createElement('span');
        etiqueta.className = 'dia';
        etiqueta.textContent = nombre;
        item.append(etiqueta);
        const delDia = local.filter((f) => f.dia === dia);
        if (delDia.length === 0) {
          const vacio = document.createElement('span');
          vacio.className = 'no-atiende';
          vacio.textContent = 'No atiende';
          item.append(vacio);
          return item;
        }
        const franjas = document.createElement('div');
        franjas.append(...delDia.map((f) => crearFranja(nombre, f)));
        item.append(franjas);
        return item;
      }),
    );
  };

  const crearFranja = (nombreDia: string, franja: FranjaLocal): HTMLDivElement => {
    const fila = document.createElement('div');
    fila.className = franja.nueva ? 'franja franja-nueva' : 'franja';
    const horas = document.createElement('span');
    horas.className = 'franja-horas';
    horas.textContent = `${franja.desde} a ${franja.hasta}`;
    fila.append(horas);
    if (editable) {
      const quitar = botonChico('Quitar');
      quitar.setAttribute('aria-label', `Quitar ${nombreDia} ${franja.desde} a ${franja.hasta}`);
      quitar.addEventListener('click', () => {
        local = local.filter((f) => f !== franja);
        limpiarAvisos();
        pintarLista();
      });
      fila.append(quitar);
    }
    return fila;
  };

  pintarLista();
  contenedor.append(error, exito, lista);
  if (!editable) {
    return;
  }

  // Alta de franjas en la copia local: una franja con el mismo horario por cada día marcado.
  const formulario = document.createElement('form');
  formulario.className = 'agregar-franja-dias';
  formulario.noValidate = true;
  formulario.setAttribute('aria-label', 'Agregar franja');
  const grupoDias = document.createElement('fieldset');
  grupoDias.className = 'dias-semana';
  const leyenda = document.createElement('legend');
  leyenda.textContent = 'Días de la franja';
  grupoDias.append(leyenda);
  const casillas = DIAS.map((nombre, indice) => {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'casilla';
    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.name = 'dias';
    casilla.value = String(indice + 1);
    casilla.setAttribute('aria-label', nombre);
    etiqueta.append(casilla, ` ${nombre.slice(0, 2)}`);
    grupoDias.append(etiqueta);
    return casilla;
  });
  const errorDias = document.createElement('p');
  errorDias.className = 'campo-error';
  errorDias.setAttribute('role', 'alert');
  const desde = crearSelectorHora('desde', 'Desde', { valor: '09:00', ariaHora: 'Hora de inicio', ariaMinutos: 'Minutos del inicio' });
  const hasta = crearSelectorHora('hasta', 'Hasta', { valor: '12:00', ariaHora: 'Hora de fin', ariaMinutos: 'Minutos del fin' });
  const horas = document.createElement('div');
  horas.className = 'horas';
  horas.append(desde.contenedor, hasta.contenedor);
  const ayudaHoras = document.createElement('span');
  ayudaHoras.className = 'ayuda';
  ayudaHoras.textContent = 'Los minutos avanzan de 5 en 5. La franja se agrega a todos los días marcados.';
  const agregar = crearBoton('Agregar franja', 'submit');
  agregar.className = 'btn';
  const cajaAgregar = document.createElement('div');
  cajaAgregar.append(agregar);
  formulario.append(grupoDias, errorDias, horas, ayudaHoras, cajaAgregar);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    errorDias.textContent = '';
    desde.mostrarError('');
    hasta.mostrarError('');
    const elegidos = casillas.filter((c) => c.checked).map((c) => Number(c.value));
    if (elegidos.length === 0) {
      errorDias.textContent = 'Elegí al menos un día.';
      return;
    }
    const inicio = desde.valor();
    const fin = hasta.valor();
    if (inicio >= fin) {
      hasta.mostrarError('"Hasta" tiene que ser posterior a "Desde".');
      return;
    }
    local = ordenar([...local, ...elegidos.map((d) => ({ dia: d, desde: inicio, hasta: fin, nueva: true }))]);
    limpiarAvisos();
    pintarLista();
  });

  const descartar = crearBoton('Descartar cambios');
  descartar.className = 'btn';
  const guardar = crearBoton('Guardar horario');
  guardar.className = 'btn btn-principal';
  const acciones = document.createElement('div');
  acciones.className = 'acciones-config';
  acciones.append(descartar, guardar);

  descartar.addEventListener('click', () => {
    local = leidas.map((f) => ({ ...f, nueva: false }));
    limpiarAvisos();
    pintarLista();
  });

  guardar.addEventListener('click', () => {
    const ruta = rutaHorarios(profesionalId);
    const franjas = ordenar(local).map(({ dia: d, desde: de, hasta: ha }) => ({ dia: d, desde: de, hasta: ha }));
    limpiarAvisos();
    void guardando(guardar, async () => {
      const respuesta = await contexto.api.actualizar<unknown>(ruta, { franjas });
      // La respuesta es el nuevo estado; si llegara vacía o con otra forma, vale lo enviado.
      try {
        leidas = leerHorario(respuesta, ruta);
      } catch {
        leidas = franjas;
      }
    }).then(
      (ok) => {
        if (ok) {
          local = leidas.map((f) => ({ ...f, nueva: false }));
          pintarLista();
          exito.textContent = 'Horario guardado.';
        }
      },
      (causa: unknown) => {
        if (contexto.sesionVencida(causa)) {
          return;
        }
        // Se conserva la copia local para que el administrador corrija lo editado.
        const titulo = document.createElement('strong');
        titulo.textContent = 'No se guardó el horario.';
        error.replaceChildren(titulo, ` ${mensajeDeError(causa)}`);
      },
    );
  });

  contenedor.append(formulario, acciones);
}
