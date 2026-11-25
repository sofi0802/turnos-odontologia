import { crearBuscadorPacientes, MENSAJE_PACIENTE_OBLIGATORIO, type PacienteEncontrado } from '../agenda/buscador-pacientes.ts';
import {
  anotarPaciente,
  darDeBaja,
  DIAS_POR_DEFECTO,
  listarListaDeEspera,
  obtenerPlazo,
  validarRango,
  type Inscripcion,
} from '../lista-espera/api.ts';
import {
  anotadoEl,
  profesionalDe,
  rangoCorto,
  SIN_PREFERENCIA,
  textoOfertaRecepcion,
} from '../lista-espera/formato.ts';
import { crearLineaConsentimiento } from '../consentimiento/linea-recepcion.ts';
import { cargarPrestaciones, cargarProfesionales, type PrestacionPublica } from '../paciente/catalogo.ts';
import { ErrorNegocio, ErrorSesion } from '../shared/api/errores.ts';
import { hoy, sumarDias } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearAviso, crearBoton, crearCampo, crearEntrada, crearSelector } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';
import { MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION } from './reportes.ts';

export const MENSAJE_LISTA_VACIA = 'No hay pacientes en la lista de espera.';
export const MENSAJE_SESION_TERMINADA = 'Tu sesión terminó. Ingresá de nuevo.';

/** Nombre para los avisos: el que manda la API o "el paciente". */
function nombreDe(inscripcion: Inscripcion): string {
  return inscripcion.pacienteNombre ?? 'el paciente';
}

function introduccion(plazo: number | null): string {
  const base = 'Cuando se cancela o se reprograma un turno, el horario se le ofrece al primer paciente compatible, en este orden.';
  if (plazo === null) {
    return `${base} Tiene un plazo para aceptarlo desde el mensaje.`;
  }
  return `${base} Tiene ${plazo} ${plazo === 1 ? 'hora' : 'horas'} para aceptarlo desde el mensaje.`;
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const anotar = crearBoton('Anotar paciente');
  anotar.className = 'btn btn-principal';
  const cabecera = document.createElement('div');
  cabecera.className = 'le-cabecera';
  cabecera.append(crearTitulo('Lista de espera'), anotar);
  const intro = document.createElement('p');
  intro.className = 'le-intro';
  intro.textContent = introduccion(null);
  const avisoOk = crearAviso('status');
  const avisoError = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.setAttribute('aria-busy', 'true');
  cuerpo.append(crearCargando('Cargando la lista de espera…'));
  contenedor.replaceChildren(cabecera, intro, avisoOk, avisoError, cuerpo);

  const vigente = (): boolean => contenedor.contains(cuerpo);
  let inscripciones: Inscripcion[] = [];

  function sesionVencida(causa: unknown): boolean {
    if (causa instanceof ErrorSesion) {
      contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
      return true;
    }
    return false;
  }

  function anunciar(ok: string, error = ''): void {
    avisoOk.textContent = ok;
    avisoError.textContent = error;
  }

  function dibujarLista(): void {
    cuerpo.removeAttribute('aria-busy');
    if (inscripciones.length === 0) {
      const vacio = crearEstadoVacio({
        titulo: MENSAJE_LISTA_VACIA,
        texto: 'Anotá a un paciente que quiera un turno antes del primero libre.',
      });
      const accion = crearBoton('Anotar paciente');
      accion.className = 'btn btn-principal';
      accion.addEventListener('click', () => abrirAlta(accion));
      vacio.append(accion);
      cuerpo.replaceChildren(vacio);
      return;
    }
    const lista = document.createElement('ol');
    lista.className = 'le-lista';
    lista.setAttribute('aria-label', 'Pacientes en la lista de espera, en orden de llegada');
    const dia = hoy();
    inscripciones.forEach((inscripcion, indice) => lista.append(fila(inscripcion, indice + 1, dia)));
    cuerpo.replaceChildren(lista);
  }

  function fila(inscripcion: Inscripcion, orden: number, dia: string): HTMLLIElement {
    const item = document.createElement('li');
    item.className = 'le-fila';
    item.dataset['inscripcion'] = String(inscripcion.id);
    const numero = document.createElement('span');
    numero.className = 'le-orden';
    numero.setAttribute('aria-label', `Orden ${orden}`);
    numero.textContent = String(orden);

    const datos = document.createElement('div');
    datos.className = 'le-datos';
    const quien = document.createElement('p');
    quien.className = 'le-meta';
    const paciente = document.createElement('span');
    paciente.className = 'le-paciente';
    paciente.textContent = inscripcion.pacienteNombre ?? '';
    const dni = document.createElement('span');
    dni.className = 'le-dni';
    dni.textContent = inscripcion.pacienteDni === null ? '' : `DNI ${inscripcion.pacienteDni}`;
    quien.append(paciente, ' ', dni);
    const que = document.createElement('p');
    que.className = 'le-meta';
    for (const parte of [inscripcion.prestacionNombre, profesionalDe(inscripcion), rangoCorto(inscripcion),
      anotadoEl(inscripcion, dia)]) {
      const span = document.createElement('span');
      span.textContent = parte;
      que.append(span);
    }
    datos.append(quien, que);
    if (inscripcion.oferta !== null) {
      const oferta = document.createElement('p');
      oferta.className = 'le-oferta';
      oferta.append(crearIcono('espera'), textoOfertaRecepcion(inscripcion.oferta));
      datos.append(oferta);
    }

    const acciones = document.createElement('div');
    acciones.className = 'le-acciones';
    const baja = crearBoton('Dar de baja');
    baja.className = 'btn btn-chico';
    baja.addEventListener('click', () => abrirBaja(inscripcion, baja));
    acciones.append(baja);
    item.append(numero, datos, acciones);
    return item;
  }

  async function cargar(): Promise<void> {
    try {
      const lista = await listarListaDeEspera(contexto.api);
      if (vigente()) {
        inscripciones = lista;
        dibujarLista();
      }
    } catch (error) {
      if (!vigente() || sesionVencida(error)) {
        return;
      }
      cuerpo.removeAttribute('aria-busy');
      cuerpo.replaceChildren();
      anunciar('', mensajeDeError(error));
    }
  }

  // ---------- Anotar paciente ----------

  function abrirAlta(origen: HTMLButtonElement): void {
    let elegido: PacienteEncontrado | null = null;
    let prestaciones: PrestacionPublica[] = [];
    // C-17: la lista de espera es de ADMIN y RECEPCIONISTA, que gestionan el consentimiento.
    const consentimiento = crearLineaConsentimiento({
      api: contexto.api,
      visible: true,
      sesionVencida,
      // Con el consentimiento ya registrado, el rechazo SIN_CONSENTIMIENTO deja de valer.
      alCambiar: (estado) => {
        if (estado.otorgado) {
          general.textContent = '';
        }
      },
    });
    const buscador = crearBuscadorPacientes(contexto.api, {
      sesionVencida,
      alElegir: (paciente) => {
        elegido = paciente;
        buscador.mostrarError('');
        consentimiento.mostrarPaciente(paciente.id);
      },
      alQuitar: () => {
        elegido = null;
        consentimiento.mostrarPaciente(null);
      },
    });
    const prestacion = crearCampo('Prestación', crearSelector('prestacion', []));
    const profesional = crearCampo('Profesional', crearSelector('profesional', [{ valor: '', texto: SIN_PREFERENCIA }]));
    const desdeEntrada = crearEntrada('date', 'desde');
    const hastaEntrada = crearEntrada('date', 'hasta');
    const desde = crearCampo('Desde', desdeEntrada);
    const hasta = crearCampo('Hasta', hastaEntrada);
    for (const control of [prestacion.control, profesional.control, desdeEntrada, hastaEntrada]) {
      control.classList.add('control');
    }
    const dia = hoy();
    desdeEntrada.value = dia;
    hastaEntrada.value = sumarDias(dia, DIAS_POR_DEFECTO);
    const rango = document.createElement('div');
    rango.className = 'le-rango';
    rango.append(desde.contenedor, hasta.contenedor);
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = 'Hasta 60 días. Una sola inscripción por prestación.';

    const { formulario, general, principal, cerrar } = abrirDialogo({
      titulo: 'Anotar paciente',
      resumen: 'Le avisamos por mensaje cuando se libere un horario que le sirva.',
      contenido: [
        buscador.contenedor,
        consentimiento.contenedor,
        prestacion.contenedor,
        profesional.contenedor,
        rango,
        ayuda,
      ],
      textoDescartar: 'Volver',
      textoPrincipal: 'Anotar',
      alCerrar: () => origen.focus(),
    });

    async function cargarProfesionalesDe(prestacionId: number): Promise<void> {
      try {
        const lista = await cargarProfesionales(contexto.api, prestacionId);
        if (Number(prestacion.control.value) === prestacionId) {
          cambiarOpciones(profesional.control, [
            { valor: '', texto: SIN_PREFERENCIA },
            ...lista.map((p) => ({ valor: String(p.id), texto: p.nombre })),
          ]);
        }
      } catch (error) {
        if (!sesionVencida(error)) {
          general.textContent = mensajeDeError(error);
        }
      }
    }

    prestacion.control.addEventListener('change', () => {
      cambiarOpciones(profesional.control, [{ valor: '', texto: SIN_PREFERENCIA }]);
      const id = Number(prestacion.control.value);
      if (Number.isInteger(id) && id > 0) {
        void cargarProfesionalesDe(id);
      }
    });

    void cargarPrestaciones(contexto.api).then(
      (lista) => {
        prestaciones = lista;
        cambiarOpciones(prestacion.control, lista.map((p) => ({ valor: String(p.id), texto: `${p.nombre} (${p.duracionMinutos} min)` })));
        const primera = lista[0];
        if (primera !== undefined) {
          prestacion.control.value = String(primera.id);
          void cargarProfesionalesDe(primera.id);
        }
      },
      (error: unknown) => {
        if (!sesionVencida(error)) {
          general.textContent = mensajeDeError(error);
        }
      },
    );

    let enviando = false;
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      if (enviando) {
        return;
      }
      general.textContent = '';
      const pacienteId = buscador.pacienteId();
      buscador.mostrarError(pacienteId === null ? MENSAJE_PACIENTE_OBLIGATORIO : '');
      const errores = validarRango(desdeEntrada.value, hastaEntrada.value, hoy());
      desde.mostrarError(errores.desde);
      hasta.mostrarError(errores.hasta);
      const prestacionId = Number(prestacion.control.value);
      const hayPrestacion = prestaciones.some((p) => p.id === prestacionId);
      prestacion.mostrarError(hayPrestacion ? '' : 'Elegí una prestación.');
      if (pacienteId === null || errores.desde !== '' || errores.hasta !== '' || !hayPrestacion) {
        return;
      }
      const profesionalId = profesional.control.value === '' ? null : Number(profesional.control.value);
      enviando = true;
      principal.disabled = true;
      anotarPaciente(contexto.api, pacienteId, {
        prestacionId,
        profesionalId,
        desde: desdeEntrada.value,
        hasta: hastaEntrada.value,
      })
        .then((creada) => {
          cerrar();
          inscripciones = [...inscripciones, creada];
          dibujarLista();
          const nombre = creada.pacienteNombre ?? (elegido === null ? 'el paciente' : `${elegido.nombre} ${elegido.apellido}`);
          anunciar(`Anotaste a ${nombre} en la lista de espera.`);
        })
        .catch((error: unknown) => {
          enviando = false;
          principal.disabled = false;
          if (!sesionVencida(error)) {
            // Los rechazos se muestran en el diálogo, que sigue abierto.
            general.textContent = mensajeDeError(error);
            // C-17: sin consentimiento, la recepción lo puede registrar ahí mismo y volver a anotar.
            if (error instanceof ErrorNegocio && error.codigo === 'SIN_CONSENTIMIENTO') {
              consentimiento.destacarRegistro();
            }
          }
        });
    });
  }

  // ---------- Dar de baja ----------

  function abrirBaja(inscripcion: Inscripcion, origen: HTMLButtonElement): void {
    const nombre = nombreDe(inscripcion);
    const { formulario, general, principal, cerrar } = abrirDialogo({
      titulo: `¿Dar de baja a ${nombre} de la lista de espera?`,
      ...(inscripcion.oferta === null
        ? {}
        : { resumen: 'Tiene una oferta pendiente: se le ofrece al siguiente de la lista.' }),
      textoDescartar: 'Volver',
      textoPrincipal: 'Dar de baja',
      peligro: true,
      alCerrar: () => {
        if (origen.isConnected) {
          origen.focus();
        }
      },
    });

    const quitar = (): void => {
      inscripciones = inscripciones.filter((i) => i.id !== inscripcion.id);
      dibujarLista();
    };

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      principal.disabled = true;
      darDeBaja(contexto.api, inscripcion.id)
        .then(() => {
          cerrar();
          quitar();
          anunciar(`Diste de baja a ${nombre}.`);
        })
        .catch((error: unknown) => {
          if (sesionVencida(error)) {
            cerrar();
            return;
          }
          if (error instanceof ErrorNegocio && error.codigo === 'INSCRIPCION_NO_ACTIVA') {
            cerrar();
            quitar();
            anunciar('', mensajeDeError(error));
            return;
          }
          principal.disabled = false;
          general.textContent = mensajeDeError(error);
        });
    });
  }

  anotar.addEventListener('click', () => abrirAlta(anotar));
  void obtenerPlazo(contexto.api).then(
    (plazo) => {
      intro.textContent = introduccion(plazo);
    },
    () => undefined,
  );
  void cargar();
}

/** C-15: la lista de espera, para administración y recepción, inmediatamente después de "Agenda" en el menú. */
export const paginaListaEspera: Pagina = {
  ruta: 'lista-espera',
  titulo: 'Lista de espera',
  acceso: 'personal',
  menu: 'personal',
  icono: 'espera',
  roles: ['ADMIN', 'RECEPCIONISTA'],
  avisoSinRol: MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION,
  fondoSuave: true,
  render: dibujar,
};
