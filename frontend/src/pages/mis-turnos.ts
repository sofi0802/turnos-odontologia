import { cargarProfesionales, type ProfesionalPublico } from '../paciente/catalogo.ts';
import { consultarDisponibilidad, type Horario } from '../paciente/disponibilidad.ts';
import { crearGrillaHorarios } from '../paciente/horarios.ts';
import type { TurnoPaciente } from '../paciente/reserva.ts';
import { cancelarTurno, ESTADOS, listarMisTurnos, reprogramarTurno, separarTurnos } from '../paciente/turnos.ts';
import { ErrorNegocio, ErrorSesion } from '../shared/api/errores.ts';
import { fechaDeInstante, fechaLarga, formatearHora, hoy } from '../shared/fecha/zona.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector, type Opcion } from '../shared/ui/campos.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_SIN_TURNOS = 'Todavía no tenés turnos.';
export const MENSAJE_FUERA_DE_PLAZO = 'Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.';
export const MENSAJE_TURNO_CANCELADO = 'Tu turno se canceló.';
export const MENSAJE_HORARIO_OCUPADO = 'Ese horario se ocupó recién. Elegí otro.';
export const MENSAJE_SESION_TERMINADA = 'Tu sesión terminó. Ingresá de nuevo.';

const SIN_PREFERENCIA = '';

function boton(texto: string, clase = 'btn'): HTMLButtonElement {
  const nuevo = crearBoton(texto);
  nuevo.className = clase;
  return nuevo;
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const avisoOk = crearAviso('status');
  const avisoError = crearAviso('alert');
  const listas = document.createElement('div');
  listas.className = 'pila';
  listas.setAttribute('aria-busy', 'true');
  listas.append(crearCargando());
  const pagina = document.createElement('div');
  pagina.className = 'pagina-paciente';
  pagina.append(crearTitulo('Mis turnos'), avisoOk, avisoError, listas);
  contenedor.replaceChildren(pagina);

  const vigente = (): boolean => contenedor.contains(listas);
  let consulta = 0;

  function sesionTerminada(): void {
    contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
  }

  async function cargar(): Promise<void> {
    consulta += 1;
    const esta = consulta;
    listas.setAttribute('aria-busy', 'true');
    listas.replaceChildren(crearCargando());
    try {
      const turnos = await listarMisTurnos(contexto.api);
      if (esta === consulta && vigente()) {
        dibujarListas(turnos);
      }
    } catch (error) {
      if (!vigente()) {
        return;
      }
      if (error instanceof ErrorSesion) {
        sesionTerminada();
        return;
      }
      listas.removeAttribute('aria-busy');
      avisoError.textContent = mensajeDeError(error);
    }
  }

  function dibujarListas(turnos: readonly TurnoPaciente[]): void {
    listas.removeAttribute('aria-busy');
    if (turnos.length === 0) {
      listas.replaceChildren(
        crearEstadoVacio({ texto: MENSAJE_SIN_TURNOS, accion: { texto: 'Reservar turno', ruta: 'reserva' } }),
      );
      return;
    }
    const { proximos, anteriores } = separarTurnos(turnos, new Date());
    const partes: HTMLElement[] = [];
    if (proximos.length > 0) {
      partes.push(seccion('Próximos turnos', proximos.map((t) => tarjeta(t, true))));
    }
    if (anteriores.length > 0) {
      partes.push(seccion('Turnos anteriores', anteriores.map((t) => tarjeta(t, false))));
    }
    const reservar = document.createElement('a');
    reservar.className = 'btn';
    reservar.href = '#/reserva';
    reservar.textContent = 'Reservar otro turno';
    listas.replaceChildren(...partes, reservar);
  }

  function seccion(titulo: string, tarjetas: readonly HTMLElement[]): HTMLElement {
    const contenedorSeccion = document.createElement('section');
    contenedorSeccion.setAttribute('aria-label', titulo);
    const encabezado = document.createElement('h2');
    encabezado.className = 'seccion-titulo';
    encabezado.textContent = titulo;
    const lista = document.createElement('ul');
    lista.className = 'lista-turnos';
    lista.append(...tarjetas);
    contenedorSeccion.append(encabezado, lista);
    return contenedorSeccion;
  }

  function tarjeta(turno: TurnoPaciente, proximo: boolean): HTMLLIElement {
    const item = document.createElement('li');
    item.className = proximo ? 'turno-paciente' : 'turno-paciente turno-anterior';
    item.dataset['reserva'] = turno.numeroReserva;

    const cabecera = document.createElement('div');
    cabecera.className = 'turno-cabecera';
    const fecha = document.createElement('p');
    fecha.className = 'turno-fecha';
    fecha.textContent = `${fechaLarga(turno.inicio)} · ${formatearHora(turno.inicio)}`;
    const estado = document.createElement('span');
    const datosEstado = ESTADOS[turno.estado];
    estado.className = `estado ${datosEstado?.clase ?? ''}`.trim();
    estado.textContent = datosEstado?.texto ?? turno.estado;
    cabecera.append(fecha, estado);

    const meta = document.createElement('p');
    meta.className = 'turno-meta';
    meta.textContent = [turno.prestacion.nombre, turno.profesional.nombre].filter((t) => t !== '').join(' · ');
    const numero = document.createElement('p');
    numero.className = 'turno-numero';
    numero.textContent = `Reserva ${turno.numeroReserva}`;
    item.append(cabecera, meta, numero);

    if (!proximo) {
      return item;
    }
    if (!turno.modificableOnline) {
      const aviso = document.createElement('p');
      aviso.className = 'aviso aviso-alerta';
      aviso.textContent = MENSAJE_FUERA_DE_PLAZO;
      item.append(aviso);
      return item;
    }
    const cambiar = boton('Cambiar horario');
    const cancelar = boton('Cancelar turno', 'btn btn-peligro');
    const accionesTurno = document.createElement('div');
    accionesTurno.className = 'turno-acciones';
    accionesTurno.append(cambiar, cancelar);
    const zonaCambio = document.createElement('div');
    item.append(accionesTurno, zonaCambio);

    cancelar.addEventListener('click', () => abrirCancelacion(turno, cancelar));
    cambiar.addEventListener('click', () => {
      if (zonaCambio.childElementCount === 0) {
        abrirCambio(turno, zonaCambio);
      }
    });
    return item;
  }

  function mostrarRechazo(error: unknown): void {
    if (error instanceof ErrorSesion) {
      sesionTerminada();
      return;
    }
    avisoOk.textContent = '';
    avisoError.textContent = mensajeDeError(error);
    void cargar();
  }

  // ---------- Cancelar ----------
  function abrirCancelacion(turno: TurnoPaciente, origen: HTMLButtonElement): void {
    const { formulario, principal: confirmar, cerrar } = abrirDialogo({
      titulo: '¿Cancelar el turno?',
      resumen: `${fechaLarga(turno.inicio)} a las ${formatearHora(turno.inicio)} con ${turno.profesional.nombre}.`,
      textoDescartar: 'Volver',
      textoPrincipal: 'Sí, cancelar turno',
      peligro: true,
      contenedor: pagina,
      alCerrar: () => origen.focus(),
    });

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      confirmar.disabled = true;
      cancelarTurno(contexto.api, turno.numeroReserva)
        .then(() => {
          cerrar();
          avisoError.textContent = '';
          avisoOk.textContent = MENSAJE_TURNO_CANCELADO;
          void cargar();
        })
        .catch((error: unknown) => {
          cerrar();
          mostrarRechazo(error);
        });
    });
  }

  // ---------- Cambiar horario ----------
  function abrirCambio(turno: TurnoPaciente, zona: HTMLElement): void {
    if (turno.prestacion.id === null) {
      avisoError.textContent = mensajeDeError(null);
      return;
    }
    const prestacionId: number = turno.prestacion.id;
    let profesionales: ProfesionalPublico[] =
      turno.profesional.id === null ? [] : [{ id: turno.profesional.id, nombre: turno.profesional.nombre }];
    let elegido: Horario | null = null;
    let consultaHorarios = 0;

    const opciones = (): Opcion[] => [
      { valor: SIN_PREFERENCIA, texto: 'Sin preferencia' },
      ...profesionales.map((p) => ({ valor: String(p.id), texto: p.nombre })),
    ];
    const selector = crearSelector('profesional', opciones());
    selector.classList.add('control');
    selector.value = turno.profesional.id === null ? SIN_PREFERENCIA : String(turno.profesional.id);
    const campoProfesional = crearCampo('Profesional', selector);

    const entrada = crearEntrada('date', 'fecha');
    entrada.classList.add('control');
    entrada.min = hoy();
    entrada.value = fechaDeInstante(turno.inicio) < hoy() ? hoy() : fechaDeInstante(turno.inicio);
    const campoDia = crearCampo('Día', entrada);
    usarSelectorFecha(campoDia);

    const avisoCambio = crearAviso('alert');
    const zonaHorarios = document.createElement('div');
    const confirmar = boton('Cambiar horario', 'btn btn-principal');
    confirmar.disabled = true;
    const noCambiar = boton('No cambiar');
    const accionesCambio = document.createElement('div');
    accionesCambio.className = 'acciones';
    accionesCambio.append(noCambiar, confirmar);

    const cambio = document.createElement('div');
    cambio.className = 'cambio';
    cambio.append(campoProfesional.contenedor, campoDia.contenedor, avisoCambio, zonaHorarios, accionesCambio);
    zona.replaceChildren(cambio);

    const profesionalElegido = (): number | null => (selector.value === SIN_PREFERENCIA ? null : Number(selector.value));

    function elegir(horario: Horario | null): void {
      elegido = horario;
      confirmar.disabled = horario === null;
      confirmar.textContent = horario === null ? 'Cambiar horario' : `Cambiar a las ${formatearHora(horario.inicio)}`;
    }

    function mostrarHorarios(lista: readonly Horario[], etiqueta: string): void {
      if (lista.length === 0) {
        const vacio = document.createElement('p');
        vacio.className = 'vacio';
        vacio.textContent = 'No hay horarios libres para ese día. Probá con otra fecha o con otro profesional.';
        zonaHorarios.replaceChildren(vacio);
        return;
      }
      zonaHorarios.replaceChildren(crearGrillaHorarios(lista, null, (horario) => elegir(horario), etiqueta));
    }

    async function pedirHorarios(): Promise<void> {
      consultaHorarios += 1;
      const esta = consultaHorarios;
      elegir(null);
      avisoCambio.textContent = '';
      zonaHorarios.replaceChildren();
      zonaHorarios.setAttribute('aria-busy', 'true');
      try {
        const lista = await consultarDisponibilidad(contexto.api, {
          prestacionId,
          fecha: entrada.value,
          profesionalId: profesionalElegido(),
        });
        if (esta === consultaHorarios) {
          zonaHorarios.removeAttribute('aria-busy');
          mostrarHorarios(lista, 'Horarios libres');
        }
      } catch (error) {
        if (esta === consultaHorarios) {
          zonaHorarios.removeAttribute('aria-busy');
          avisoCambio.textContent = mensajeDeError(error);
        }
      }
    }

    selector.addEventListener('change', () => void pedirHorarios());
    entrada.addEventListener('change', () => {
      const minimo = hoy();
      if (entrada.value === '' || entrada.value < minimo) {
        entrada.value = minimo;
      }
      void pedirHorarios();
    });
    noCambiar.addEventListener('click', () => zona.replaceChildren());

    confirmar.addEventListener('click', () => {
      if (elegido === null) {
        return;
      }
      const horario = elegido;
      confirmar.disabled = true;
      reprogramarTurno(contexto.api, turno.numeroReserva, horario.inicio, profesionalElegido())
        .then((nuevo) => {
          const numero = nuevo?.numeroReserva ?? '';
          avisoError.textContent = '';
          avisoOk.textContent =
            `Cambiamos tu turno al ${fechaLarga(horario.inicio)} a las ${formatearHora(horario.inicio)}.` +
            (numero === '' ? '' : ` Tu nuevo número de reserva es ${numero}.`);
          void cargar();
        })
        .catch((error: unknown) => {
          if (error instanceof ErrorNegocio && error.codigo === 'SIN_DISPONIBILIDAD') {
            // El turno original sigue igual: se ofrecen los horarios que dio el servidor.
            elegir(null);
            mostrarHorarios(error.alternativas, 'Otros horarios');
            avisoCambio.textContent = MENSAJE_HORARIO_OCUPADO;
            return;
          }
          mostrarRechazo(error);
        });
    });

    cargarProfesionales(contexto.api, prestacionId)
      .then((lista) => {
        const actual = selector.value;
        profesionales = lista.some((p) => p.id === turno.profesional.id) ? lista : [...profesionales, ...lista];
        const opcionesNuevas = opciones();
        selector.replaceChildren(
          ...opcionesNuevas.map(({ valor, texto }) => {
            const opcion = document.createElement('option');
            opcion.value = valor;
            opcion.textContent = texto;
            return opcion;
          }),
        );
        selector.value = actual;
      })
      .catch(() => {
        // Sin la lista quedan el profesional del turno y "Sin preferencia".
      });
    void pedirHorarios();
  }

  void cargar();
}

/** Turnos del paciente (`#/mis-turnos`): solo con sesión de paciente. */
export const paginaMisTurnos: Pagina = {
  ruta: 'mis-turnos',
  fondoSuave: true,
  titulo: 'Mis turnos',
  acceso: 'paciente',
  menu: 'paciente',
  icono: 'turnos',
  render: dibujar,
};
