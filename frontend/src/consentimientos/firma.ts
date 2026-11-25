import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorApi, ErrorGenerico, ErrorNegocio, ErrorPermiso, ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import { crearBoton, crearCampo } from '../shared/ui/campos.ts';
import { abrirDialogo, type DialogoAbierto } from '../shared/ui/dialogo.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { firmar as pedirFirma, obtenerPlantilla } from './api.ts';
import { ponerMarca } from './marca.ts';
import { MENSAJE_SIN_ACEPTAR, validarDni, validarNombre, type ConsentimientoVista, type PlantillaVista } from './modelo.ts';
import { crearTextoPlano, nuevoId } from './ui.ts';

/**
 * Vista de firma a pantalla completa (C-48, design D5 y D15): el paciente lee el texto de la versión elegida y firma con
 * su nombre y su DNI. Es un `<dialog>` modal de fondo opaco: tapa la ficha y deja el resto de la página inerte. Del
 * paciente solo se muestran el nombre y el apellido; el DNI no está en la vista, ni en su DOM ni en un atributo.
 */

/** Cómo terminó la firma: de cualquiera de estas formas, lo que sigue es el bloqueo (design D5 y D12). */
export type FinDeFirma =
  | { readonly tipo: 'firmada'; readonly consentimiento: ConsentimientoVista }
  | { readonly tipo: 'cancelada' }
  /** "Terminar" después de una plantilla desactivada. */
  | { readonly tipo: 'incompleta' }
  | { readonly tipo: 'sesion-vencida' }
  /** Envío dudoso: no se sabe si se guardó (design D13). Lleva lo enviado, para comprobarlo después. */
  | { readonly tipo: 'dudosa'; readonly plantillaId: number; readonly versionNumero: number; readonly titulo: string };

export interface OpcionesFirma {
  readonly api: ClienteApi;
  readonly pacienteId: number;
  readonly paciente: { readonly nombre: string; readonly apellido: string };
  readonly plantilla: PlantillaVista;
  /** La vista ya se cerró y se quitó de la página, con lo escrito. */
  alTerminar(fin: FinDeFirma): void;
  /** Cuánto espera el envío antes de cortarlo (por defecto, 30 segundos). */
  readonly esperaMaxima?: number;
}

export const ESPERA_MAXIMA_DE_LA_FIRMA = 30_000;
export const MENSAJE_TEXTO_CAMBIO = 'El texto del consentimiento cambió mientras se leía. Leé la versión nueva antes de firmar.';

export interface VistaDeFirma {
  readonly dialogo: HTMLDialogElement;
}

export const EXPLICACION_DE_LA_FIRMA =
  'se guardan el nombre y el DNI que escribís, la fecha y hora, el profesional que te atiende, la dirección IP y el dispositivo, junto con una copia exacta de este texto.';

export function abrirFirma(opciones: OpcionesFirma): VistaDeFirma {
  let plantilla = opciones.plantilla;

  const rotulo = document.createElement('p');
  rotulo.className = 'firma-rotulo';
  rotulo.append(crearIcono('firma'), 'Consentimiento informado · Firma electrónica');

  const version = document.createElement('p');
  version.className = 'firma-version';
  version.textContent = `Versión ${plantilla.version.numero}`;

  const para = document.createElement('p');
  para.className = 'firma-para';
  const quien = document.createElement('strong');
  quien.textContent = `${opciones.paciente.nombre} ${opciones.paciente.apellido}`.trim();
  para.append('Para: ', quien, '. Si no sos vos, avisale al profesional.');

  const texto = crearTextoPlano(plantilla.version.texto, 'cons-texto firma-texto');

  // --- Campos de la firma: los dos empiezan vacíos, sin autocompletado (design D5 y D15) ---
  const idFormulario = nuevoId('firma-formulario');
  const encabezadoFormulario = document.createElement('h3');
  encabezadoFormulario.id = idFormulario;
  encabezadoFormulario.textContent = 'Firma electrónica del paciente';

  const entradaNombre = document.createElement('input');
  entradaNombre.type = 'text';
  entradaNombre.name = 'firmanteNombre';
  entradaNombre.autocomplete = 'off';
  entradaNombre.setAttribute('autocapitalize', 'words');
  entradaNombre.setAttribute('spellcheck', 'false');
  const nombre = crearCampo('Nombre completo', entradaNombre);

  const entradaDni = document.createElement('input');
  entradaDni.type = 'text';
  entradaDni.name = 'firmanteDni';
  entradaDni.autocomplete = 'off';
  entradaDni.setAttribute('inputmode', 'numeric');
  entradaDni.setAttribute('spellcheck', 'false');
  const dni = crearCampo('DNI', entradaDni);
  const ayudaDni = document.createElement('p');
  ayudaDni.className = 'ayuda';
  ayudaDni.id = `${entradaDni.id}-ayuda`;
  ayudaDni.textContent = 'Escribí tu DNI, con o sin puntos.';
  entradaDni.setAttribute('aria-describedby', `${ayudaDni.id} ${entradaDni.getAttribute('aria-describedby') ?? ''}`.trim());
  dni.contenedor.insertBefore(ayudaDni, dni.contenedor.querySelector('.campo-error'));

  const campos = document.createElement('div');
  campos.className = 'firma-campos';
  campos.append(nombre.contenedor, dni.contenedor);

  const explicacion = document.createElement('p');
  explicacion.className = 'firma-explicacion';
  const enfasis = document.createElement('strong');
  enfasis.textContent = 'firma electrónica';
  explicacion.append('Es una ', enfasis, `: ${EXPLICACION_DE_LA_FIRMA}`);

  const casilla = document.createElement('input');
  casilla.type = 'checkbox';
  casilla.name = 'aceptado';
  const errorCasilla = document.createElement('p');
  errorCasilla.className = 'campo-error';
  errorCasilla.id = nuevoId('firma-acepto-error');
  casilla.setAttribute('aria-describedby', errorCasilla.id);
  const acepto = document.createElement('label');
  acepto.className = 'cons-acepto';
  acepto.append(casilla, 'Leí y acepto este consentimiento');

  // --- El `<dialog>` modal sale del molde de los diálogos (todos los de la aplicación lo usan) y se reacomoda: ocupa toda
  // la pantalla, con fondo opaco, y "Cancelar" y "Firmar" son sus dos botones (design D5) ---
  const abierto = abrirDialogo({
    titulo: plantilla.version.titulo,
    contenido: [campos, explicacion, acepto, errorCasilla],
    textoDescartar: 'Cancelar',
    textoPrincipal: 'Firmar',
  });
  const dialogo = abierto.dialogo;
  const formulario = abierto.formulario;
  const cancelar = abierto.descartar;
  const firmar = abierto.principal;
  const aviso = abierto.general;
  dialogo.classList.remove('dialogo');
  dialogo.classList.add('firma-pantalla');
  formulario.classList.add('firma-form');
  formulario.setAttribute('aria-labelledby', idFormulario);
  formulario.prepend(encabezadoFormulario);
  aviso.className = 'aviso aviso-alerta';
  formulario.querySelector('.dialogo-acciones')?.classList.replace('dialogo-acciones', 'firma-acciones');
  const textoFirmar = document.createElement('span');
  textoFirmar.textContent = 'Firmar';
  firmar.replaceChildren(crearIcono('firma'), textoFirmar);

  const titulo = dialogo.querySelector('h2') as HTMLHeadingElement;
  titulo.tabIndex = -1;
  const encabezado = document.createElement('div');
  encabezado.className = 'firma-encabezado';
  encabezado.append(titulo, version);
  const contenido = document.createElement('div');
  contenido.className = 'firma-contenido';
  contenido.append(rotulo, encabezado, para, texto, formulario);
  dialogo.replaceChildren(contenido);

  function mostrarErrorDeCasilla(mensaje: string): void {
    errorCasilla.textContent = mensaje;
    acepto.classList.toggle('con-error', mensaje !== '');
    if (mensaje === '') {
      casilla.removeAttribute('aria-invalid');
    } else {
      casilla.setAttribute('aria-invalid', 'true');
    }
  }

  /** Valida lo escrito: marca cada campo, lleva el foco al primero con error y devuelve `true` si no hay ninguno. */
  function validar(): boolean {
    const errorNombre = validarNombre(entradaNombre.value);
    const errorDni = validarDni(entradaDni.value);
    const errorAcepto = casilla.checked ? '' : MENSAJE_SIN_ACEPTAR;
    nombre.mostrarError(errorNombre);
    dni.mostrarError(errorDni);
    mostrarErrorDeCasilla(errorAcepto);
    if (errorNombre !== '') {
      entradaNombre.focus();
    } else if (errorDni !== '') {
      entradaDni.focus();
    } else if (errorAcepto !== '') {
      casilla.focus();
    }
    return errorNombre === '' && errorDni === '' && errorAcepto === '';
  }

  // --- Envío, rechazos y final ---

  let enviando = false;
  let terminada = false;
  let pregunta: DialogoAbierto | null = null;
  let temporizador: ReturnType<typeof setTimeout> | null = null;

  function hayDatos(): boolean {
    return entradaNombre.value.trim() !== '' || entradaDni.value.trim() !== '' || casilla.checked;
  }

  /** Cierra la vista, la quita de la página con lo escrito y avisa cómo terminó (design D5). */
  function terminar(fin: FinDeFirma): void {
    if (terminada) {
      return;
    }
    terminada = true;
    if (temporizador !== null) {
      clearTimeout(temporizador);
      temporizador = null;
    }
    pregunta?.cerrar();
    pregunta = null;
    entradaNombre.value = '';
    entradaDni.value = '';
    casilla.checked = false;
    if (dialogo.open) {
      dialogo.close();
    }
    dialogo.remove();
    opciones.alTerminar(fin);
  }

  /** El envío en curso quedó sin respuesta: se guarda lo que se envió para comprobarlo (design D13). */
  function finDudoso(): FinDeFirma {
    return { tipo: 'dudosa', plantillaId: plantilla.id, versionNumero: plantilla.version.numero, titulo: plantilla.version.titulo };
  }

  function preguntarSiCancela(): void {
    pregunta?.cerrar();
    const confirmar = abrirDialogo({
      titulo: '¿Cancelar la firma?',
      resumen: 'Lo escrito se descarta. Después, devolvé el equipo al profesional.',
      textoDescartar: 'Seguir firmando',
      textoPrincipal: 'Cancelar la firma',
      peligro: true,
      alCerrar: () => {
        if (pregunta === confirmar) {
          pregunta = null;
        }
        if (!terminada) {
          cancelar.focus();
        }
      },
    });
    pregunta = confirmar;
    confirmar.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      terminar({ tipo: 'cancelada' });
    });
  }

  /** "Cancelar" y Escape: sin nada escrito cierran; con algo escrito preguntan. Mientras se envía no hacen nada. */
  function cancelarFirma(): void {
    if (enviando || terminada) {
      return;
    }
    if (hayDatos()) {
      preguntarSiCancela();
    } else {
      terminar({ tipo: 'cancelada' });
    }
  }

  // En la fase de captura, antes de que el molde de los diálogos cierre con "Cancelar".
  formulario.addEventListener(
    'click',
    (evento) => {
      if (evento.target instanceof Node && cancelar.contains(evento.target)) {
        evento.stopPropagation();
        cancelarFirma();
      }
    },
    true,
  );
  dialogo.addEventListener('cancel', (evento) => {
    evento.preventDefault();
    cancelarFirma();
  });
  // Si el navegador cierra la vista sin que la aplicación lo pida, es una cancelación o, con un envío en curso, dudoso.
  dialogo.addEventListener('close', () => {
    if (!terminada) {
      terminar(enviando ? finDudoso() : { tipo: 'cancelada' });
    }
  });

  function mostrarAviso(mensaje: string, enfocar = false): void {
    aviso.textContent = mensaje;
    if (enfocar) {
      aviso.tabIndex = -1;
      aviso.focus();
    }
  }

  function ponerEnvio(en: boolean): void {
    enviando = en;
    firmar.disabled = en;
    cancelar.disabled = en;
    textoFirmar.textContent = en ? 'Firmando…' : 'Firmar';
  }

  /** Con `409 VERSION_DESACTUALIZADA`: se vuelve a pedir la plantilla y el paciente tiene que leer la versión nueva. */
  async function releerPlantilla(): Promise<void> {
    try {
      const nueva = await obtenerPlantilla(opciones.api, plantilla.id);
      if (terminada) {
        return;
      }
      plantilla = nueva;
      titulo.textContent = nueva.version.titulo;
      version.textContent = `Versión ${nueva.version.numero}`;
      texto.textContent = nueva.version.texto;
      casilla.checked = false;
      mostrarErrorDeCasilla('');
      mostrarAviso(MENSAJE_TEXTO_CAMBIO, true);
    } catch (causa) {
      if (!terminada) {
        mostrarAviso(mensajeDeError(causa));
      }
    }
  }

  /** Con `409 PLANTILLA_INACTIVA`: ya no se puede firmar; queda solo "Terminar". */
  function mostrarInactiva(causa: unknown): void {
    const encabezadoInactiva = document.createElement('h3');
    encabezadoInactiva.textContent = 'Firma';
    const terminarBoton = crearBoton('Terminar');
    terminarBoton.className = 'btn btn-principal';
    terminarBoton.addEventListener('click', () => terminar({ tipo: 'incompleta' }));
    const accionesInactiva = document.createElement('div');
    accionesInactiva.className = 'firma-acciones';
    accionesInactiva.append(terminarBoton);
    mostrarAviso(mensajeDeError(causa));
    formulario.replaceChildren(encabezadoInactiva, aviso, accionesInactiva);
    terminarBoton.focus();
  }

  function rechazar(causa: unknown): void {
    // 401 y 403 se rechazan antes del controlador: la firma no se guardó (design D13).
    if (causa instanceof ErrorSesion || causa instanceof ErrorPermiso) {
      terminar({ tipo: 'sesion-vencida' });
      return;
    }
    // Sin red, tiempo agotado, 5xx o una respuesta ilegible: no se sabe si se guardó.
    if ((causa instanceof ErrorGenerico && (causa.estado === null || causa.estado >= 500)) || !(causa instanceof ErrorApi)) {
      terminar(finDudoso());
      return;
    }
    if (causa instanceof ErrorValidacion && causa.codigo === 'FIRMANTE_NO_COINCIDE') {
      // El mensaje es fijo: no repite el DNI escrito ni el del paciente.
      dni.mostrarError(mensajeDeError(causa));
      entradaDni.focus();
      return;
    }
    if (causa instanceof ErrorNegocio && causa.codigo === 'VERSION_DESACTUALIZADA') {
      void releerPlantilla();
      return;
    }
    if (causa instanceof ErrorNegocio && causa.codigo === 'PLANTILLA_INACTIVA') {
      mostrarInactiva(causa);
      return;
    }
    mostrarAviso(mensajeDeError(causa));
  }

  async function enviar(): Promise<void> {
    mostrarAviso('');
    ponerEnvio(true);
    const controlador = new AbortController();
    temporizador = setTimeout(() => controlador.abort(), opciones.esperaMaxima ?? ESPERA_MAXIMA_DE_LA_FIRMA);
    try {
      const consentimiento = await pedirFirma(
        opciones.api,
        opciones.pacienteId,
        {
          plantillaId: plantilla.id,
          versionNumero: plantilla.version.numero,
          firmanteNombre: entradaNombre.value,
          firmanteDni: entradaDni.value,
        },
        controlador.signal,
      );
      terminar({ tipo: 'firmada', consentimiento });
    } catch (causa) {
      if (terminada) {
        return;
      }
      if (temporizador !== null) {
        clearTimeout(temporizador);
        temporizador = null;
      }
      ponerEnvio(false);
      rechazar(causa);
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    if (enviando || terminada || !validar()) {
      return;
    }
    void enviar();
  });

  // La marca avisa, si la página se recarga durante la firma o el bloqueo, que hay una sesión que cerrar (design D12).
  ponerMarca();
  // El foco empieza en el título: el lector de pantalla lee el consentimiento desde el principio.
  titulo.focus();
  return { dialogo };
}
