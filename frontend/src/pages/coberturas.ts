import { crearBuscadorPacientes, type PacienteEncontrado } from '../agenda/buscador-pacientes.ts';
import { crearSeccionCobertura, type SeccionCobertura } from '../coberturas/seccion.ts';
import { crearSeccionPresupuestos, type SeccionPresupuestos } from '../presupuestos/seccion.ts';
import { ErrorSesion } from '../shared/api/errores.ts';
import { crearBoton } from '../shared/ui/campos.ts';
import { crearEstadoVacio } from '../shared/ui/estado-vacio.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';
import { MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION } from './reportes.ts';

export const MENSAJE_ELEGIR_PACIENTE_COBERTURA = 'Buscá un paciente para ver y cargar su cobertura.';

/** Cada dibujo tiene su número: un pedido que vuelve después de salir no dibuja nada. */
let dibujo = 0;

/**
 * "Coberturas" (C-49, design D2): la recepción y la administración buscan un paciente y ven o cargan solo su
 * cobertura. No importa nada de `ficha/` ni pide datos clínicos (RN-CL-01). El paciente elegido vive solo en la
 * memoria de la página: no va a la dirección ni al almacenamiento del navegador.
 */
function dibujarCoberturas(contenedor: HTMLElement, contexto: ContextoPagina): void {
  dibujo += 1;
  const este = dibujo;
  const vigente = (): boolean => este === dibujo && contenedor.isConnected;

  const pagina = document.createElement('div');
  pagina.className = 'pagina-coberturas';
  const cuerpo = document.createElement('div');
  cuerpo.className = 'pagina-coberturas';
  pagina.append(crearTitulo('Coberturas'), cuerpo);
  contenedor.replaceChildren(pagina);

  let seccion: SeccionCobertura | null = null;
  let presupuestos: SeccionPresupuestos | null = null;

  function soltar(): void {
    seccion?.destruir();
    seccion = null;
    presupuestos?.destruir();
    presupuestos = null;
  }

  function sesionVencida(causa: unknown): boolean {
    if (causa instanceof ErrorSesion) {
      contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
      return true;
    }
    return false;
  }

  function mostrarBuscador(): void {
    soltar();
    const panel = document.createElement('section');
    panel.className = 'panel ficha-seccion';
    const titulo = document.createElement('h2');
    titulo.textContent = 'Buscar paciente';
    const buscador = crearBuscadorPacientes(contexto.api, {
      sesionVencida,
      alElegir: (paciente: PacienteEncontrado) => mostrarPaciente(paciente),
    });
    panel.append(titulo, buscador.contenedor);
    cuerpo.replaceChildren(
      panel,
      crearEstadoVacio({ titulo: 'Todavía no elegiste un paciente', texto: MENSAJE_ELEGIR_PACIENTE_COBERTURA, chico: true }),
    );
  }

  function mostrarPaciente(paciente: PacienteEncontrado): void {
    soltar();
    const nombreCompleto = `${paciente.nombre} ${paciente.apellido}`.trim();

    const cabecera = document.createElement('section');
    cabecera.className = 'panel ficha-paciente';
    const datos = document.createElement('div');
    datos.className = 'ficha-paciente-datos';
    const quien = document.createElement('div');
    const nombre = document.createElement('h2');
    nombre.className = 'ficha-paciente-nombre';
    nombre.textContent = nombreCompleto;
    const dni = document.createElement('p');
    dni.className = 'ficha-paciente-dni';
    dni.textContent = `DNI ${paciente.dni}`;
    quien.append(nombre, dni);
    const cambiar = crearBoton('Cambiar de paciente');
    cambiar.className = 'btn';
    cambiar.addEventListener('click', mostrarBuscador);
    datos.append(quien, cambiar);
    cabecera.append(datos);

    seccion = crearSeccionCobertura({
      pacienteId: paciente.id,
      nombrePaciente: nombreCompleto,
      editable: true,
      api: contexto.api,
      mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
      vigente,
    });
    // C-50 (design D3): los presupuestos en solo lectura; la recepción, sin pieza ni cara.
    presupuestos = crearSeccionPresupuestos({
      pacienteId: paciente.id,
      nombrePaciente: nombreCompleto,
      modo: contexto.sesion.rol === 'RECEPCIONISTA' ? 'recepcion' : 'lectura',
      api: contexto.api,
      mensajeDeError: (causa) => (sesionVencida(causa) ? '' : mensajeDeError(causa)),
      vigente,
    });
    cuerpo.replaceChildren(cabecera, seccion.contenedor, presupuestos.contenedor);
  }

  mostrarBuscador();
}

/** Coberturas de los pacientes, solo para ADMIN y RECEPCIONISTA (C-49, design D9). */
export const paginaCoberturas: Pagina = {
  ruta: 'coberturas',
  titulo: 'Coberturas',
  acceso: 'personal',
  menu: 'personal',
  icono: 'credencial',
  fondoSuave: true,
  roles: ['ADMIN', 'RECEPCIONISTA'],
  avisoSinRol: MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION,
  render: dibujarCoberturas,
};
