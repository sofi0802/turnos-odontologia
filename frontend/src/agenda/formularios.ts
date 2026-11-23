import { cargarProfesionales } from '../paciente/catalogo.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorNegocio, ErrorNoEncontrado, ErrorValidacion } from '../shared/api/errores.ts';
import { fechaDeInstante, formatearHora, type Fecha } from '../shared/fecha/zona.ts';
import {
  cambiarOpciones,
  crearAviso,
  crearBoton,
  crearCampo,
  crearEntrada,
  crearSelector,
  type Campo,
  type Opcion,
} from '../shared/ui/campos.ts';
import { mensajeDeError, mensajeDeReprogramacion } from '../shared/ui/mensajes.ts';
import { crearCalendario } from '../shared/ui/calendario.ts';
import { crearBuscadorPacientes, MENSAJE_PACIENTE_OBLIGATORIO } from './buscador-pacientes.ts';
import { nombreDe, type Catalogos, type ItemCatalogo } from './catalogos.ts';
import { crearBuscadorHorariosNuevoTurno } from './nuevo-turno.ts';
import type { TurnoAgenda } from './consulta.ts';
import { normalizarNumeroReserva } from './numero-reserva.ts';
import { crearBuscadorHorarios, crearZonaHorarios } from './reprogramacion.ts';

/** Lo que los formularios necesitan de la página de agenda. */
export interface ControlAgenda {
  readonly api: ClienteApi;
  catalogos(): Catalogos;
  /** Fecha que se propone en un turno nuevo (el día mostrado). */
  fechaInicial(): Fecha;
  recargar(): void;
  mostrarExito(mensaje: string): void;
  mostrarError(mensaje: string): void;
  /** Si el error es de sesión vencida, lleva a Mi cuenta y devuelve `true`. */
  sesionVencida(causa: unknown): boolean;
}

type CampoFormulario = Campo<HTMLInputElement> | Campo<HTMLSelectElement>;

interface Dialogo {
  readonly formulario: HTMLFormElement;
  readonly general: HTMLParagraphElement;
  readonly enviar: HTMLButtonElement;
  cerrar(): void;
}

let contadorDialogos = 0;

/** Arma un `<dialog>` modal con título, campos y botones (design D5). */
function abrirDialogo(titulo: string, resumen: string | null, campos: readonly CampoFormulario[], textoEnviar: string): Dialogo {
  contadorDialogos += 1;
  const dialogo = document.createElement('dialog');
  dialogo.className = 'dialogo';
  const idTitulo = `dialogo-titulo-${contadorDialogos}`;
  dialogo.setAttribute('aria-labelledby', idTitulo);

  const encabezado = document.createElement('h2');
  encabezado.id = idTitulo;
  encabezado.textContent = titulo;

  const formulario = document.createElement('form');
  formulario.className = 'formulario';
  formulario.noValidate = true;
  const general = crearAviso('alert');
  const enviar = crearBoton(textoEnviar, 'submit');
  enviar.className = 'boton-principal';
  const cerrarBoton = crearBoton('Cerrar');
  cerrarBoton.className = 'boton-secundario';
  const botones = document.createElement('div');
  botones.className = 'dialogo-acciones';
  botones.append(enviar, cerrarBoton);
  formulario.append(...campos.map((c) => c.contenedor), general, botones);

  dialogo.append(encabezado);
  if (resumen !== null) {
    const parrafo = document.createElement('p');
    parrafo.className = 'dialogo-resumen';
    parrafo.textContent = resumen;
    dialogo.append(parrafo);
  }
  dialogo.append(formulario);

  const cerrar = (): void => dialogo.close();
  cerrarBoton.addEventListener('click', cerrar);
  dialogo.addEventListener('close', () => dialogo.remove());
  document.body.append(dialogo);
  dialogo.showModal();
  campos[0]?.control.focus();
  return { formulario, general, enviar, cerrar };
}

function opcionesActivas(items: readonly ItemCatalogo[], primera: string): Opcion[] {
  return [
    { valor: '', texto: primera },
    ...items.filter((i) => i.activo).map((i) => ({ valor: String(i.id), texto: i.nombre })),
  ];
}

function selector(etiqueta: string, nombre: string, opciones: readonly Opcion[]): Campo<HTMLSelectElement> {
  return crearCampo(etiqueta, crearSelector(nombre, opciones));
}

function entrada(etiqueta: string, tipo: string, nombre: string, valor = ''): Campo<HTMLInputElement> {
  const campo = crearCampo(etiqueta, crearEntrada(tipo, nombre));
  campo.control.value = valor;
  return campo;
}

/** Marca como obligatorio cada campo vacío; devuelve `true` si todos tienen valor. */
function validarObligatorios(obligatorios: ReadonlyArray<readonly [CampoFormulario, string]>): boolean {
  let validos = true;
  for (const [campo, mensaje] of obligatorios) {
    const vacio = campo.control.value.trim() === '';
    campo.mostrarError(vacio ? mensaje : '');
    validos &&= !vacio;
  }
  return validos;
}

function leerNumeroReserva(cuerpo: unknown): string {
  const valor: unknown = typeof cuerpo === 'object' && cuerpo !== null ? Reflect.get(cuerpo, 'numeroReserva') : null;
  return typeof valor === 'string' ? valor : '';
}

/** Envía el pedido y muestra el resultado; los errores de campo van junto a su campo. */
function manejarEnvio(
  control: ControlAgenda,
  dialogo: Dialogo,
  porCampo: Readonly<Record<string, { mostrarError(mensaje: string): void }>>,
  pedido: () => Promise<unknown>,
  alTerminar: (cuerpo: unknown) => string,
  mensaje: (causa: unknown) => string = mensajeDeError,
): void {
  dialogo.general.textContent = '';
  dialogo.enviar.disabled = true;
  pedido()
    .then((cuerpo) => {
      dialogo.cerrar();
      control.mostrarExito(alTerminar(cuerpo));
      control.recargar();
    })
    .catch((causa: unknown) => {
      if (control.sesionVencida(causa)) {
        dialogo.cerrar();
        return;
      }
      if (causa instanceof ErrorNoEncontrado) {
        dialogo.cerrar();
        control.mostrarError(mensajeDeError(causa));
        control.recargar();
        return;
      }
      if (causa instanceof ErrorValidacion && causa.errores.length > 0) {
        const sinCampo: string[] = [];
        for (const { campo, mensaje } of causa.errores) {
          const destino = porCampo[campo];
          if (destino === undefined) {
            sinCampo.push(mensaje);
          } else {
            destino.mostrarError(mensaje);
          }
        }
        dialogo.general.textContent = sinCampo.length > 0 ? sinCampo.join(' ') : mensaje(causa);
        return;
      }
      dialogo.general.textContent = mensaje(causa);
    })
    .finally(() => {
      dialogo.enviar.disabled = false;
    });
}

function resumenDe(turno: TurnoAgenda): string {
  const paciente = `${turno.pacienteNombre} ${turno.pacienteApellido}`.trim();
  return `${paciente}, ${fechaDeInstante(turno.inicio)} de ${formatearHora(turno.inicio)} a ${formatearHora(turno.fin)}.`;
}

export const MENSAJE_PROFESIONALES_NO_CARGADOS = 'No pudimos cargar los profesionales. Probá elegir la prestación de nuevo.';
export const AYUDA_SIN_PROFESIONAL = 'Elegí primero un profesional.';
export const AYUDA_SIN_SILLON = 'Sin sillón asignado no se pueden elegir días.';

function mensajeSinSillon(profesional: string): string {
  return `${profesional} no tiene un sillón asignado. Pedile al administrador que lo asigne en Configuración.`;
}

/** Sillón del profesional elegido, como texto y sin poder cambiarlo (design D6). */
function crearSillonFijo(): { contenedor: HTMLDivElement; mostrar(sillon: string | null, profesional: string | null): void } {
  const contenedor = document.createElement('div');
  contenedor.className = 'campo sillon-nuevo-turno';
  contenedor.hidden = true;
  const etiqueta = document.createElement('span');
  etiqueta.className = 'etiqueta-campo';
  etiqueta.textContent = 'Sillón';
  const valor = document.createElement('p');
  valor.className = 'sillon-fijo';
  valor.setAttribute('aria-live', 'polite');
  contenedor.append(etiqueta, valor);
  return {
    contenedor,
    mostrar(sillon, profesional) {
      contenedor.hidden = profesional === null;
      if (profesional === null) {
        valor.replaceChildren();
        return;
      }
      if (sillon === null) {
        valor.className = 'aviso aviso-alerta';
        valor.setAttribute('role', 'alert');
        valor.textContent = mensajeSinSillon(profesional);
        return;
      }
      valor.className = 'sillon-fijo';
      valor.removeAttribute('role');
      const nombre = document.createElement('span');
      nombre.className = 'sillon-nombre';
      nombre.textContent = `Sillón: ${sillon}`;
      // No usa `.ayuda`: dentro de `.campo` esa clase pone en mayúscula la primera letra (C-12).
      const ayuda = document.createElement('span');
      ayuda.className = 'sillon-detalle';
      ayuda.textContent = ` · sillón habitual de ${profesional}`;
      valor.replaceChildren(nombre, ayuda);
    },
  };
}

/**
 * "Nuevo turno" guiado (C-40, design D1): paciente; después prestación → profesional habilitado →
 * día del calendario → horario libre, con el sillón habitual del profesional a la vista.
 */
export function abrirNuevoTurno(control: ControlAgenda): void {
  const catalogos = control.catalogos();
  // La sesión vencida cierra también el diálogo, que es modal y taparía Mi cuenta.
  let dialogo: Dialogo | undefined;
  const sesionVencida = (causa: unknown): boolean => {
    const vencida = control.sesionVencida(causa);
    if (vencida) {
      dialogo?.cerrar();
    }
    return vencida;
  };
  const paciente = crearBuscadorPacientes(control.api, { sesionVencida });
  const prestacion = selector('Prestación', 'prestacionId', opcionesActivas(catalogos.prestaciones, 'Elegí una prestación'));
  const profesional = selector('Profesional', 'profesionalId', [{ valor: '', texto: 'Elegí un profesional' }]);
  profesional.control.disabled = true;
  const avisoProfesionales = crearAviso('status');
  avisoProfesionales.className = 'aviso aviso-alerta';
  profesional.contenedor.append(avisoProfesionales);
  const sillon = crearSillonFijo();

  const zona = crearZonaHorarios({
    ayuda: null,
    sinConsulta: 'Elegí un día para ver los horarios libres.',
    sinHorarios: 'No hay horarios libres ese día. Probá con otro día.',
    error: 'No pudimos cargar los horarios. Elegí el día de nuevo para volver a intentar.',
  });
  const mostrarHorarios = (): void => {
    zona.mostrar(buscador.estado(), buscador.elegido(), (horario) => {
      buscador.elegir(horario);
      zona.mostrarError('');
    });
  };
  const buscador = crearBuscadorHorariosNuevoTurno(control.api, mostrarHorarios);

  let sillonId: number | null = null;
  const calendario = crearCalendario({
    leyenda: 'Fecha',
    alElegir(fecha) {
      if (sillonId === null) {
        return;
      }
      zona.mostrarError('');
      buscador.buscar({
        prestacionId: Number(prestacion.control.value),
        profesionalId: Number(profesional.control.value),
        sillonId,
        fecha,
      });
    },
  });
  calendario.habilitar(false, AYUDA_SIN_PROFESIONAL);

  dialogo = abrirDialogo('Nuevo turno', null, [paciente, prestacion, profesional], 'Crear turno');
  const dialogoAbierto = dialogo;
  dialogoAbierto.formulario.closest('dialog')?.classList.add('dialogo-ancho');
  const columnas = document.createElement('div');
  columnas.className = 'dos-columnas';
  prestacion.contenedor.before(columnas);
  columnas.append(prestacion.contenedor, profesional.contenedor);
  const fechaYHorario = document.createElement('div');
  fechaYHorario.className = 'fecha-y-horario';
  fechaYHorario.append(calendario.contenedor, zona.contenedor);
  columnas.after(sillon.contenedor, fechaYHorario);
  mostrarHorarios();

  /** Vacía el día y el horario (al cambiar el profesional o la prestación). */
  function vaciarDiaYHorario(): void {
    calendario.limpiar();
    buscador.limpiar();
    zona.mostrarError('');
  }

  let consultaProfesionales = 0;
  prestacion.control.addEventListener('change', () => {
    consultaProfesionales += 1;
    const esta = consultaProfesionales;
    prestacion.mostrarError('');
    profesional.mostrarError('');
    avisoProfesionales.textContent = '';
    cambiarOpciones(profesional.control, [{ valor: '', texto: 'Elegí un profesional' }]);
    profesional.control.value = '';
    profesional.control.disabled = true;
    sillonId = null;
    sillon.mostrar(null, null);
    vaciarDiaYHorario();
    calendario.habilitar(false, AYUDA_SIN_PROFESIONAL);
    if (prestacion.control.value === '') {
      return;
    }
    cargarProfesionales(control.api, Number(prestacion.control.value))
      .then((habilitados) => {
        if (esta !== consultaProfesionales) {
          return;
        }
        cambiarOpciones(profesional.control, [
          { valor: '', texto: 'Elegí un profesional' },
          ...habilitados.map((p) => ({ valor: String(p.id), texto: p.nombre })),
        ]);
        profesional.control.disabled = false;
      })
      .catch((causa: unknown) => {
        if (esta !== consultaProfesionales || sesionVencida(causa)) {
          return;
        }
        avisoProfesionales.textContent = MENSAJE_PROFESIONALES_NO_CARGADOS;
      });
  });

  profesional.control.addEventListener('change', () => {
    profesional.mostrarError('');
    vaciarDiaYHorario();
    if (profesional.control.value === '') {
      sillonId = null;
      sillon.mostrar(null, null);
      calendario.habilitar(false, AYUDA_SIN_PROFESIONAL);
      return;
    }
    const id = Number(profesional.control.value);
    const nombre = profesional.control.selectedOptions[0]?.textContent ?? nombreDe(catalogos, 'profesionales', id);
    sillonId = catalogos.profesionales.find((p) => p.id === id)?.sillonHabitualId ?? null;
    sillon.mostrar(sillonId === null ? null : nombreDe(catalogos, 'sillones', sillonId), nombre);
    calendario.habilitar(sillonId !== null, sillonId === null ? AYUDA_SIN_SILLON : undefined);
  });

  const porCampo = { pacienteId: paciente, prestacionId: prestacion, profesionalId: profesional, inicio: zona };

  dialogoAbierto.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    // El paciente cuenta solo si se eligió de la lista: el texto escrito no alcanza.
    const pacienteId = paciente.pacienteId();
    paciente.mostrarError(pacienteId === null ? MENSAJE_PACIENTE_OBLIGATORIO : '');
    const sinPrestacion = prestacion.control.value === '';
    prestacion.mostrarError(sinPrestacion ? 'Elegí una prestación.' : '');
    const sinProfesional = !sinPrestacion && profesional.control.value === '';
    profesional.mostrarError(sinProfesional ? 'Elegí un profesional.' : '');
    const horario = buscador.elegido();
    const puedeElegirHorario = !sinPrestacion && !sinProfesional && sillonId !== null;
    zona.mostrarError(puedeElegirHorario && horario === null ? 'Elegí un horario.' : '');
    if (pacienteId === null || horario === null || sillonId === null || sinPrestacion || sinProfesional) {
      return;
    }
    const cuerpo = {
      pacienteId,
      prestacionId: Number(prestacion.control.value),
      profesionalId: Number(profesional.control.value),
      sillonId,
      inicio: horario.inicio,
    };
    manejarEnvio(
      control,
      dialogoAbierto,
      porCampo,
      () => control.api.crear<unknown>('/turnos', cuerpo),
      (respuesta) => `Turno creado. Número de reserva: ${leerNumeroReserva(respuesta)}.`,
    );
  });
}

function camposDeIdentidad(): [Campo<HTMLInputElement>, Campo<HTMLInputElement>] {
  const dni = entrada('DNI del paciente', 'text', 'dni');
  dni.control.inputMode = 'numeric';
  dni.control.autocomplete = 'off';
  const numero = entrada('Número de reserva', 'text', 'numeroReserva');
  numero.control.autocomplete = 'off';
  return [dni, numero];
}

export function abrirCancelacion(control: ControlAgenda, turno: TurnoAgenda): void {
  const [dni, numero] = camposDeIdentidad();
  const dialogo = abrirDialogo('Cancelar turno', resumenDe(turno), [dni, numero], 'Confirmar cancelación');

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    if (!validarObligatorios([[dni, 'Ingresá el DNI.'], [numero, 'Ingresá el número de reserva.']])) {
      return;
    }
    const cuerpo = { dni: dni.control.value.trim(), numeroReserva: numero.control.value.trim() };
    manejarEnvio(
      control,
      dialogo,
      { dni, numeroReserva: numero },
      () => control.api.crear<unknown>(`/turnos/${turno.id}/cancelar`, cuerpo),
      () => 'Turno cancelado.',
    );
  });
}

export function abrirReprogramacion(control: ControlAgenda, turno: TurnoAgenda): void {
  const catalogos = control.catalogos();
  const [dni, numero] = camposDeIdentidad();
  const profesional = selector('Profesional', 'profesionalId', [{ valor: '', texto: 'Sin cambios' }]);
  const fecha = entrada('Fecha', 'date', 'fecha', fechaDeInstante(turno.inicio));
  const sillon = selector('Sillón', 'sillonId', opcionesActivas(catalogos.sillones, 'Sin cambios'));
  const dialogo = abrirDialogo(
    'Reprogramar turno',
    resumenDe(turno),
    [dni, numero, profesional, fecha, sillon],
    'Confirmar reprogramación',
  );
  const identificacion = document.createElement('div');
  identificacion.className = 'dos-campos';
  dni.contenedor.before(identificacion);
  identificacion.append(dni.contenedor, numero.contenedor);

  // Horarios libres del profesional elegido ese día (design D4); el estado vive en reprogramacion.ts.
  const zona = crearZonaHorarios();
  fecha.contenedor.after(zona.contenedor);
  const mostrarHorarios = (): void => {
    zona.mostrarError('');
    zona.mostrar(buscador.estado(), buscador.elegido(), (horario) => {
      buscador.elegir(horario);
      zona.mostrarError('');
    });
  };
  const buscador = crearBuscadorHorarios(control.api, turno.prestacionId, mostrarHorarios);
  const buscarHorarios = (): void => {
    const elegido = profesional.control.value;
    buscador.buscar(fecha.control.value, elegido === '' ? turno.profesionalId : Number(elegido));
  };
  profesional.control.addEventListener('change', buscarHorarios);
  fecha.control.addEventListener('change', buscarHorarios);
  sillon.control.addEventListener('change', () => sillon.mostrarError(''));
  buscarHorarios();

  // Solo los profesionales habilitados para la prestación del turno (design D3).
  cargarProfesionales(control.api, turno.prestacionId)
    .then((habilitados) =>
      cambiarOpciones(profesional.control, [
        { valor: '', texto: 'Sin cambios' },
        ...habilitados.map((p) => ({ valor: String(p.id), texto: p.nombre })),
      ]),
    )
    .catch((causa: unknown) => {
      if (control.sesionVencida(causa)) {
        dialogo.cerrar();
        return;
      }
      const aviso = crearAviso('status');
      aviso.className = 'aviso aviso-alerta';
      aviso.textContent = 'No pudimos cargar los profesionales. Podés reprogramar con el profesional actual.';
      profesional.contenedor.append(aviso);
    });

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    sillon.mostrarError('');
    const completos = validarObligatorios([
      [dni, 'Ingresá el DNI.'],
      [numero, 'Ingresá el número de reserva.'],
      [fecha, 'Elegí la fecha.'],
    ]);
    const horario = buscador.elegido();
    zona.mostrarError(horario === null ? 'Elegí un horario.' : '');
    if (!completos || horario === null) {
      return;
    }
    const cuerpo: Record<string, string | number> = {
      dni: dni.control.value.trim(),
      numeroReserva: normalizarNumeroReserva(numero.control.value),
      inicio: horario.inicio,
    };
    if (profesional.control.value !== '') {
      cuerpo['profesionalId'] = Number(profesional.control.value);
    }
    if (sillon.control.value !== '') {
      cuerpo['sillonId'] = Number(sillon.control.value);
    }
    manejarEnvio(
      control,
      dialogo,
      { dni, numeroReserva: numero, inicio: fecha, profesionalId: profesional, sillonId: sillon },
      () =>
        control.api.crear<unknown>(`/turnos/${turno.id}/reprogramar`, cuerpo).catch((causa: unknown) => {
          if (causa instanceof ErrorNegocio && causa.codigo === 'SOLAPAMIENTO_SILLON') {
            sillon.mostrarError('Elegí otro sillón para ese horario.');
          }
          throw causa;
        }),
      (respuesta) => `Turno reprogramado. Nuevo número de reserva: ${leerNumeroReserva(respuesta)}.`,
      mensajeDeReprogramacion,
    );
  });
}
