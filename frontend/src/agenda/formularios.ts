import { cargarProfesionales } from '../paciente/catalogo.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorNegocio, ErrorNoEncontrado, ErrorValidacion } from '../shared/api/errores.ts';
import { aIsoConZona, fechaDeInstante, formatearHora, type Fecha } from '../shared/fecha/zona.ts';
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
import { crearBuscadorPacientes, MENSAJE_PACIENTE_OBLIGATORIO } from './buscador-pacientes.ts';
import type { Catalogos, ItemCatalogo } from './catalogos.ts';
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
  porCampo: Readonly<Record<string, CampoFormulario>>,
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

export function abrirNuevoTurno(control: ControlAgenda): void {
  const catalogos = control.catalogos();
  // La sesión vencida cierra también el diálogo, que es modal y taparía Mi cuenta.
  let dialogo: Dialogo | undefined;
  const paciente = crearBuscadorPacientes(control.api, {
    sesionVencida: (causa) => {
      const vencida = control.sesionVencida(causa);
      if (vencida) {
        dialogo?.cerrar();
      }
      return vencida;
    },
  });
  const profesional = selector('Profesional', 'profesionalId', opcionesActivas(catalogos.profesionales, 'Elegí un profesional'));
  const sillon = selector('Sillón', 'sillonId', opcionesActivas(catalogos.sillones, 'Elegí un sillón'));
  const prestacion = selector('Prestación', 'prestacionId', opcionesActivas(catalogos.prestaciones, 'Elegí una prestación'));
  const fecha = entrada('Fecha', 'date', 'fecha', control.fechaInicial());
  const hora = entrada('Hora', 'time', 'hora');

  dialogo = abrirDialogo('Nuevo turno', null, [paciente, profesional, sillon, prestacion, fecha, hora], 'Crear turno');
  const dialogoAbierto = dialogo;
  const porCampo = { pacienteId: paciente, profesionalId: profesional, sillonId: sillon, prestacionId: prestacion, inicio: hora };

  dialogoAbierto.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const completos = validarObligatorios([
      [profesional, 'Elegí un profesional.'],
      [sillon, 'Elegí un sillón.'],
      [prestacion, 'Elegí una prestación.'],
      [fecha, 'Elegí la fecha.'],
      [hora, 'Elegí la hora.'],
    ]);
    // El paciente cuenta solo si se eligió de la lista: el texto escrito no alcanza.
    const pacienteId = paciente.pacienteId();
    paciente.mostrarError(pacienteId === null ? MENSAJE_PACIENTE_OBLIGATORIO : '');
    if (!completos || pacienteId === null) {
      return;
    }
    const cuerpo = {
      pacienteId,
      profesionalId: Number(profesional.control.value),
      sillonId: Number(sillon.control.value),
      prestacionId: Number(prestacion.control.value),
      inicio: aIsoConZona(fecha.control.value, hora.control.value),
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
