import { crearBuscadorPacientes, type PacienteEncontrado } from '../agenda/buscador-pacientes.ts';
import { crearSeccionCobertura, type SeccionCobertura } from '../coberturas/seccion.ts';
import { crearAusencias, marcarAusenteDisponible } from '../ficha/ausencias.ts';
import {
  guardarFicha,
  marcarAusente,
  obtenerFicha,
  obtenerHistorial,
  obtenerOdontograma,
  registrarCambios,
} from '../ficha/api.ts';
import { crearFichaBasica } from '../ficha/ficha-basica.ts';
import { rangoDelTurno } from '../ficha/formato.ts';
import { crearHistorial } from '../ficha/historial.ts';
import { crearImagenes, MENSAJE_DESCARTAR_IMAGEN, type SeccionImagenes } from '../ficha/imagenes.ts';
import { anularImagen, cargarImagen, descargarArchivo, listarImagenes } from '../ficha/imagenes-api.ts';
import { crearOdontograma, type Odontograma } from '../ficha/odontograma.ts';
import { crearPeriodontograma, MENSAJE_DESCARTAR_PERIODONTOGRAMA, type SeccionPeriodontograma } from '../ficha/periodontograma.ts';
import { listarVersiones, obtenerVersion, registrarVersion } from '../ficha/periodontograma-api.ts';
import { tomarSeleccion, type SeleccionFicha } from '../ficha/seleccion.ts';
import { ErrorNoEncontrado, ErrorPermiso, ErrorSesion } from '../shared/api/errores.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearCargando, crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_ELEGIR_PACIENTE = 'Buscá un paciente para ver su ficha.';
export const MENSAJE_PACIENTE_INEXISTENTE = 'No encontramos a ese paciente.';
export const MENSAJE_SIN_PERMISO_FICHA = 'No tenés permiso para ver la ficha clínica.';
export const MENSAJE_SOLO_LECTURA = 'Solo lectura: la ficha clínica la edita el odontólogo.';
export const MENSAJE_DESCARTAR = 'Hay cambios sin guardar en el odontograma. ¿Descartarlos?';

/** Cada dibujo de la página tiene su número: un pedido que vuelve después de salir no dibuja nada (design D9). */
let dibujo = 0;

function mensajeDeFicha(causa: unknown): string {
  if (causa instanceof ErrorNoEncontrado) {
    return MENSAJE_PACIENTE_INEXISTENTE;
  }
  if (causa instanceof ErrorPermiso) {
    return MENSAJE_SIN_PERMISO_FICHA;
  }
  return mensajeDeError(causa);
}

function dibujarPacientes(contenedor: HTMLElement, contexto: ContextoPagina): void {
  dibujo += 1;
  const este = dibujo;
  const vigente = (): boolean => este === dibujo && contenedor.isConnected;
  const editable = contexto.sesion.rol === 'ODONTOLOGO';

  const pagina = document.createElement('div');
  pagina.className = 'pagina-ficha';
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pagina-ficha';
  pagina.append(crearTitulo('Pacientes'), exito, error, cuerpo);
  contenedor.replaceChildren(pagina);

  let odontograma: Odontograma | null = null;
  let periodontograma: SeccionPeriodontograma | null = null;
  let imagenes: SeccionImagenes | null = null;
  let cobertura: SeccionCobertura | null = null;
  // C-49: el administrador carga la cobertura aunque la ficha clínica sea de solo lectura para él.
  const editaCobertura = contexto.sesion.rol === 'ADMIN' || contexto.sesion.rol === 'RECEPCIONISTA';

  function soltarCobertura(): void {
    cobertura?.destruir();
    cobertura = null;
  }

  function sesionVencida(causa: unknown): boolean {
    if (causa instanceof ErrorSesion) {
      contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
      return true;
    }
    return false;
  }

  function mostrarBuscador(): void {
    odontograma = null;
    periodontograma = null;
    imagenes?.destruir();
    imagenes = null;
    soltarCobertura();
    const panel = document.createElement('section');
    panel.className = 'panel ficha-seccion';
    const titulo = document.createElement('h2');
    titulo.textContent = 'Buscar paciente';
    const buscador = crearBuscadorPacientes(contexto.api, {
      sesionVencida,
      alElegir: (paciente: PacienteEncontrado) => {
        error.textContent = '';
        mostrarFicha({ paciente, turno: null });
      },
    });
    panel.append(titulo, buscador.contenedor);
    cuerpo.replaceChildren(panel, crearEstadoVacio({ titulo: 'Todavía no elegiste un paciente', texto: MENSAJE_ELEGIR_PACIENTE }));
  }

  function cambiarDePaciente(): void {
    // C-46: también pregunta si hay un periodontograma sin guardar.
    const pendientes = [
      odontograma !== null && odontograma.pendientes() > 0 ? MENSAJE_DESCARTAR : null,
      periodontograma?.sinGuardar() === true ? MENSAJE_DESCARTAR_PERIODONTOGRAMA : null,
      // C-47: también pregunta si hay una imagen sin cargar.
      imagenes?.sinGuardar() === true ? MENSAJE_DESCARTAR_IMAGEN : null,
    ].filter((m): m is string => m !== null);
    if (pendientes.length === 0) {
      exito.textContent = '';
      mostrarBuscador();
      return;
    }
    const solo = pendientes.length === 1 ? pendientes[0] : undefined;
    const dialogo = abrirDialogo({
      titulo:
        solo === MENSAJE_DESCARTAR_PERIODONTOGRAMA
          ? '¿Descartar el periodontograma?'
          : solo === MENSAJE_DESCARTAR_IMAGEN
            ? '¿Descartar la imagen?'
            : '¿Descartar los cambios?',
      resumen: pendientes.join(' '),
      textoDescartar: 'Seguir editando',
      textoPrincipal: 'Descartar',
      peligro: true,
    });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.cerrar();
      exito.textContent = '';
      mostrarBuscador();
    });
  }

  function cabeceraDelPaciente(seleccion: SeleccionFicha): HTMLElement {
    const panel = document.createElement('section');
    panel.className = 'panel ficha-paciente';
    const datos = document.createElement('div');
    datos.className = 'ficha-paciente-datos';
    const quien = document.createElement('div');
    const nombre = document.createElement('h2');
    nombre.className = 'ficha-paciente-nombre';
    nombre.textContent = `${seleccion.paciente.nombre} ${seleccion.paciente.apellido}`.trim();
    quien.append(nombre);
    if (seleccion.paciente.dni !== null) {
      const dni = document.createElement('p');
      dni.className = 'ficha-paciente-dni';
      dni.textContent = `DNI ${seleccion.paciente.dni}`;
      quien.append(dni);
    }
    const cambiar = crearBoton('Cambiar de paciente');
    cambiar.className = 'btn';
    cambiar.addEventListener('click', cambiarDePaciente);
    datos.append(quien, cambiar);
    panel.append(datos);
    if (!editable) {
      const aviso = document.createElement('p');
      aviso.className = 'aviso aviso-info';
      aviso.textContent = MENSAJE_SOLO_LECTURA;
      panel.append(aviso);
    }
    const turno = seleccion.turno;
    if (turno !== null) {
      const franja = document.createElement('div');
      franja.className = 'ficha-turno';
      const texto = document.createElement('p');
      texto.append(crearIcono('reloj'), `Turno del ${rangoDelTurno(turno.inicio, turno.fin)}`);
      franja.append(texto);
      const disponible = marcarAusenteDisponible(turno, contexto.sesion.rol, contexto.sesion.profesionalId, new Date());
      if (disponible !== null) {
        const caja = document.createElement('div');
        const boton = crearBoton('Marcar ausente');
        boton.className = 'btn btn-peligro';
        caja.append(boton);
        if (!disponible.habilitado) {
          boton.disabled = true;
          const pista = document.createElement('span');
          pista.className = 'ayuda-boton';
          pista.id = `pista-ausente-${turno.id}`;
          pista.textContent = `Desde las ${disponible.desde}`;
          boton.setAttribute('aria-describedby', pista.id);
          caja.append(pista);
        }
        boton.addEventListener('click', () => void registrarAusencia(seleccion, boton));
        franja.append(caja);
      }
      panel.append(franja);
    }
    return panel;
  }

  async function registrarAusencia(seleccion: SeleccionFicha, boton: HTMLButtonElement): Promise<void> {
    const turno = seleccion.turno;
    if (turno === null) {
      return;
    }
    boton.disabled = true;
    exito.textContent = '';
    error.textContent = '';
    let siguiente: SeleccionFicha = seleccion;
    try {
      await marcarAusente(contexto.api, seleccion.paciente.id, turno.id);
      siguiente = { ...seleccion, turno: { ...turno, estado: 'AUSENTE' } };
      exito.textContent = 'Ausencia registrada.';
    } catch (causa) {
      if (sesionVencida(causa)) {
        return;
      }
      error.textContent = mensajeDeError(causa);
    }
    if (vigente()) {
      mostrarFicha(siguiente, false);
    }
  }

  function mostrarFicha(seleccion: SeleccionFicha, limpiarAvisos = true): void {
    const pacienteId = seleccion.paciente.id;
    imagenes?.destruir();
    imagenes = null;
    soltarCobertura();
    if (limpiarAvisos) {
      exito.textContent = '';
    }
    cuerpo.replaceChildren(cabeceraDelPaciente(seleccion), crearCargando('Cargando la ficha…'));
    void (async () => {
      try {
        const [ficha, registros] = await Promise.all([
          obtenerFicha(contexto.api, pacienteId),
          obtenerOdontograma(contexto.api, pacienteId),
        ]);
        if (!vigente()) {
          return;
        }
        const historial = crearHistorial(
          () => obtenerHistorial(contexto.api, pacienteId),
          (causa) => (sesionVencida(causa) ? '' : mensajeDeFicha(causa)),
        );
        odontograma = crearOdontograma({
          registros,
          editable,
          guardar: (cambios) => registrarCambios(contexto.api, pacienteId, cambios),
          mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
          alGuardar: () => historial.recargar(),
        });
        const basica = crearFichaBasica({
          ficha,
          editable,
          guardar: (datos) => guardarFicha(contexto.api, pacienteId, datos),
          mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
        });
        // C-46: el periodontograma se carga aparte, con su propio error y "Reintentar" (design D2).
        periodontograma = crearPeriodontograma({
          editable,
          listar: () => listarVersiones(contexto.api, pacienteId),
          obtener: (versionId) => obtenerVersion(contexto.api, pacienteId, versionId),
          registrar: (pedido) => registrarVersion(contexto.api, pacienteId, pedido),
          mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
          vigente,
        });
        // C-47: las imágenes y estudios también se cargan aparte, con su propio error y "Reintentar" (design D4).
        imagenes = crearImagenes({
          editable,
          nombrePaciente: `${seleccion.paciente.nombre} ${seleccion.paciente.apellido}`.trim(),
          listar: (filtro) => listarImagenes(contexto.api, pacienteId, filtro),
          anular: (imagenId, motivo) => anularImagen(contexto.api, pacienteId, imagenId, motivo),
          cargar: (carga) => cargarImagen(contexto.api, pacienteId, carga),
          obtenerArchivo: (imagenId, descarga) => descargarArchivo(contexto.api, pacienteId, imagenId, descarga),
          mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
          vigente,
        });
        // C-49: la cobertura va entre la cabecera y el odontograma, y también carga aparte (design D4).
        cobertura = crearSeccionCobertura({
          pacienteId,
          nombrePaciente: `${seleccion.paciente.nombre} ${seleccion.paciente.apellido}`.trim(),
          editable: editaCobertura,
          api: contexto.api,
          mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
          vigente,
        });
        const columnas = document.createElement('div');
        columnas.className = 'ficha-columnas';
        const derecha = document.createElement('div');
        derecha.className = 'ficha-seccion';
        derecha.append(crearAusencias(ficha.ausencias), historial.elemento);
        columnas.append(basica, derecha);
        cuerpo.replaceChildren(
          cabeceraDelPaciente(seleccion),
          cobertura.contenedor,
          odontograma.elemento,
          periodontograma.contenedor,
          imagenes.contenedor,
          columnas,
        );
      } catch (causa) {
        if (!vigente() || sesionVencida(causa)) {
          return;
        }
        error.textContent = mensajeDeFicha(causa);
        if (causa instanceof ErrorNoEncontrado || causa instanceof ErrorPermiso) {
          mostrarBuscador();
          return;
        }
        const reintentar = crearBoton('Reintentar');
        reintentar.className = 'btn btn-principal';
        reintentar.addEventListener('click', () => {
          error.textContent = '';
          mostrarFicha(seleccion);
        });
        cuerpo.replaceChildren(cabeceraDelPaciente(seleccion), reintentar);
      }
    })();
  }

  const seleccion = tomarSeleccion();
  if (seleccion === null) {
    mostrarBuscador();
  } else {
    mostrarFicha(seleccion);
  }
}

/** Pacientes: búsqueda del paciente y su ficha clínica, solo para ADMIN y ODONTOLOGO (C-44, RN-CL-01). */
export const paginaPacientes: Pagina = {
  ruta: 'pacientes',
  titulo: 'Pacientes',
  acceso: 'personal',
  menu: 'personal',
  icono: 'pacientes',
  fondoSuave: true,
  roles: ['ADMIN', 'ODONTOLOGO'],
  render: dibujarPacientes,
};
