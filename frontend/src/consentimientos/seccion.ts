import { fechaYHora } from '../ficha/formato.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { crearEstadoVacio, crearCargando } from '../shared/ui/estado-vacio.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { guardando } from '../configuracion/ui.ts';
import { ErrorNegocio, ErrorNoEncontrado } from '../shared/api/errores.ts';
import { hoy } from '../shared/fecha/zona.ts';
import { abrirDialogo, type DialogoAbierto } from '../shared/ui/dialogo.ts';
import { listarConsentimientos, listarPlantillas, obtenerPlantilla, revocar } from './api.ts';
import { abrirFirma, type FinDeFirma } from './firma.ts';
import { abrirDocumento, type DocumentoAbierto } from './documento.ts';
import {
  fechaDeFirma,
  firmadoHoy,
  firmaNueva,
  horaDeFirma,
  LIMITE_MOTIVO,
  nombreAccesible,
  validarMotivo,
  type ConsentimientoResumen,
  type IntentoDeFirma,
  type PlantillaResumen,
  type PlantillaVista,
  type ResultadoPrevio,
} from './modelo.ts';
import { abrirDialogoDesde, conIcono, crearCampoConContador, crearEtiqueta, nuevoId } from './ui.ts';

/**
 * Sección "Consentimientos" de la ficha (C-48, design D3): el listado de lo firmado, con "Ver" y "Revocar", y la firma.
 * Carga aparte del resto de la ficha, con su propio error y "Reintentar". Todo texto viene con `textContent`.
 */

export interface OpcionesConsentimientos {
  readonly pacienteId: number;
  readonly paciente: { readonly nombre: string; readonly apellido: string };
  /** Profesional de la sesión: la comprobación de un envío dudoso busca firmas suyas (design D13). */
  readonly profesionalId: number | null;
  /** Lo que dejó la firma antes del bloqueo y la ficha tiene que mostrar al reabrirse (design D12 y D13). */
  readonly previo: ResultadoPrevio | null;
  /**
   * La firma terminó (de cualquier forma) y la vista ya se cerró: quien monta la sección quita la ficha de la página y
   * muestra el bloqueo (design D12). `previo` es lo que la ficha va a mostrar después de desbloquear.
   */
  alTerminarFirma(fin: FinDeFirma, previo: ResultadoPrevio | null): void;
  /** Solo el odontólogo firma y revoca; el administrador consulta (RN-CL-01). */
  readonly editable: boolean;
  readonly api: ClienteApi;
  /** Mensaje de un error de la API; vacío si ya se llevó al ingreso (sesión vencida). */
  mensajeDeError(causa: unknown): string;
  /** `false` cuando la página ya se dibujó de nuevo o se salió de ella: un pedido viejo no dibuja nada. */
  vigente(): boolean;
}

export interface SeccionConsentimientos {
  readonly contenedor: HTMLElement;
  /** Cierra los diálogos abiertos y suelta los datos que sigan en memoria. */
  destruir(): void;
}

let contador = 0;

function parrafoDe(texto: string): HTMLParagraphElement {
  const p = document.createElement('p');
  p.textContent = texto;
  return p;
}

function nombreDelPaciente(paciente: { nombre: string; apellido: string }): string {
  return `${paciente.nombre} ${paciente.apellido}`.trim();
}

export function crearConsentimientos(opciones: OpcionesConsentimientos): SeccionConsentimientos {
  contador += 1;
  const id = `consentimientos-${contador}`;
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion cons-seccion';
  seccion.setAttribute('aria-labelledby', `${id}-titulo`);
  const cabecera = document.createElement('div');
  cabecera.className = 'ficha-seccion-cabecera';
  const encabezado = document.createElement('div');
  const titulo = document.createElement('h2');
  titulo.id = `${id}-titulo`;
  titulo.textContent = 'Consentimientos';
  const resumen = document.createElement('p');
  resumen.className = 'cons-contador';
  resumen.hidden = true;
  encabezado.append(titulo, resumen);
  const acciones = document.createElement('div');
  acciones.className = 'cons-cabecera-acciones';
  cabecera.append(encabezado, acciones);
  if (opciones.editable) {
    const firmar = crearBoton('Firmar consentimiento');
    firmar.className = 'btn btn-principal';
    conIcono(firmar, 'firma');
    firmar.addEventListener('click', () => abrirEleccion(firmar));
    acciones.append(firmar);
  }
  const exito = crearAviso('status');
  const error = crearAviso('alert');
  const resultados = document.createElement('div');
  resultados.className = 'cons-resultados';
  const caja = document.createElement('div');
  caja.className = 'cons-caja';
  seccion.append(cabecera, exito, error, caja, resultados);

  let pedido = 0;
  let destruida = false;
  let lista: ConsentimientoResumen[] = [];
  let documento: DocumentoAbierto | null = null;
  /** La firma que hay que comprobar con el listado (envío dudoso); `null` si no hay nada que comprobar. */
  let comprobacion: { readonly titulo: string; readonly intento: IntentoDeFirma } | null =
    opciones.previo?.tipo === 'comprobar' ? { titulo: opciones.previo.titulo, intento: opciones.previo.intento } : null;

  function activa(este: number): boolean {
    return !destruida && este === pedido && opciones.vigente();
  }

  function verDocumento(origen: HTMLButtonElement, consentimiento: ConsentimientoResumen): void {
    documento?.cerrar();
    documento = abrirDocumento({
      origen,
      api: opciones.api,
      pacienteId: opciones.pacienteId,
      consentimiento,
      mensajeDeError: opciones.mensajeDeError,
    });
  }

  function dibujarFila(consentimiento: ConsentimientoResumen): HTMLLIElement {
    const fila = document.createElement('li');
    fila.className = consentimiento.estado === 'REVOCADO' ? 'cons-fila cons-fila-revocada' : 'cons-fila';
    const tipo = document.createElement('span');
    tipo.className = 'cons-tipo';
    tipo.append(crearIcono('documento'));
    const nombre = document.createElement('span');
    nombre.textContent = consentimiento.titulo;
    const estado = crearEtiqueta(
      consentimiento.estado === 'REVOCADO' ? 'Revocado' : 'Vigente',
      consentimiento.estado === 'REVOCADO' ? 'etiqueta-revocado' : 'etiqueta-vigente',
    );
    const encabezadoFila = document.createElement('p');
    encabezadoFila.className = 'cons-titulo';
    encabezadoFila.append(nombre, estado);
    const meta = document.createElement('p');
    meta.className = 'cons-meta';
    meta.textContent = `Versión ${consentimiento.versionNumero} · Firmado el ${fechaYHora(consentimiento.firmadoEn)} · ${consentimiento.profesional}`;
    const datos = document.createElement('div');
    datos.className = 'cons-datos';
    datos.append(encabezadoFila, meta);
    const botones = document.createElement('div');
    botones.className = 'cons-acciones';
    const ver = crearBoton('Ver');
    ver.className = 'btn';
    conIcono(ver, 'ver');
    ver.setAttribute('aria-label', nombreAccesible('Ver', consentimiento.titulo, consentimiento.firmadoEn));
    ver.addEventListener('click', () => verDocumento(ver, consentimiento));
    botones.append(ver);
    if (opciones.editable && consentimiento.estado === 'VIGENTE') {
      const revocarBoton = crearBoton('Revocar');
      revocarBoton.className = 'btn btn-peligro';
      revocarBoton.setAttribute('aria-label', nombreAccesible('Revocar', consentimiento.titulo, consentimiento.firmadoEn));
      revocarBoton.addEventListener('click', () => abrirRevocacion(revocarBoton, consentimiento));
      botones.append(revocarBoton);
    }
    fila.append(tipo, datos, botones);
    return fila;
  }

  function dibujarLista(): void {
    resumen.hidden = lista.length === 0;
    resumen.textContent = lista.length === 1 ? '1 firmado' : `${lista.length} firmados`;
    if (lista.length === 0) {
      resultados.replaceChildren(crearEstadoVacio({ texto: 'Todavía no hay consentimientos firmados.', chico: true }));
      return;
    }
    const ul = document.createElement('ul');
    ul.className = 'cons-lista';
    ul.setAttribute('aria-label', `Consentimientos de ${nombreDelPaciente(opciones.paciente)}`);
    ul.append(...lista.map(dibujarFila));
    resultados.replaceChildren(ul);
  }

  /** Pide el listado: mientras carga muestra "Cargando consentimientos…" y, si falla, su error con "Reintentar". */
  async function cargar(): Promise<void> {
    pedido += 1;
    const este = pedido;
    error.textContent = '';
    resumen.hidden = true;
    if (comprobacion !== null) {
      mostrarComprobando(comprobacion);
    }
    resultados.setAttribute('aria-busy', 'true');
    resultados.replaceChildren(crearCargando('Cargando consentimientos…'));
    try {
      const recibida = await listarConsentimientos(opciones.api, opciones.pacienteId);
      if (!activa(este)) {
        return;
      }
      lista = recibida;
      resultados.removeAttribute('aria-busy');
      dibujarLista();
      if (comprobacion !== null) {
        resolverComprobacion(comprobacion, recibida);
      }
    } catch (causa) {
      if (!activa(este)) {
        return;
      }
      resultados.removeAttribute('aria-busy');
      const mensaje = opciones.mensajeDeError(causa);
      if (mensaje === '') {
        return;
      }
      if (comprobacion !== null) {
        // Nunca se dice "no se guardó" sin una respuesta del listado (design D13).
        mostrarSinComprobar();
        resultados.replaceChildren();
        return;
      }
      error.textContent = mensaje;
      const reintentar = crearBoton('Reintentar');
      reintentar.className = 'btn';
      reintentar.addEventListener('click', () => void cargar());
      resultados.replaceChildren(reintentar);
    }
  }

  // --- Comprobar si la firma quedó guardada después de un envío dudoso (design D13) ---

  interface OpcionesCaja {
    readonly clase: string;
    readonly rol: 'status' | 'alert';
    readonly icono: 'reloj' | 'verificado' | 'alterado';
    readonly titulo: string;
    readonly detalle: Array<Node | string>;
    readonly botones?: readonly HTMLButtonElement[];
  }

  function dibujarCaja(opcionesCaja: OpcionesCaja): void {
    const titulo = document.createElement('h3');
    titulo.append(crearIcono(opcionesCaja.icono), opcionesCaja.titulo);
    const detalle = document.createElement('p');
    detalle.append(...opcionesCaja.detalle);
    const contenidoCaja: HTMLElement[] = [titulo, detalle];
    if ((opcionesCaja.botones ?? []).length > 0) {
      const botones = document.createElement('div');
      botones.className = 'cons-resultado-acciones';
      botones.append(...(opcionesCaja.botones ?? []));
      contenidoCaja.push(botones);
    }
    const resultado = document.createElement('div');
    resultado.className = `cons-resultado ${opcionesCaja.clase}`;
    resultado.setAttribute('role', opcionesCaja.rol);
    resultado.append(...contenidoCaja);
    caja.replaceChildren(resultado);
  }

  function negrita(texto: string): HTMLElement {
    const fuerte = document.createElement('strong');
    fuerte.textContent = texto;
    return fuerte;
  }

  function mostrarComprobando(buscada: { readonly titulo: string; readonly intento: IntentoDeFirma }): void {
    dibujarCaja({
      clase: 'cons-resultado-pendiente',
      rol: 'status',
      icono: 'reloj',
      titulo: 'Comprobando si la firma quedó guardada…',
      detalle: [`${buscada.titulo}, versión ${buscada.intento.versionNumero}.`],
    });
  }

  function mostrarSinComprobar(): void {
    const comprobarDeNuevo = crearBoton('Comprobar de nuevo');
    comprobarDeNuevo.className = 'btn btn-principal';
    comprobarDeNuevo.addEventListener('click', () => void cargar());
    dibujarCaja({
      clase: 'cons-resultado-pendiente',
      rol: 'alert',
      icono: 'alterado',
      titulo: 'No pudimos comprobar si la firma quedó guardada.',
      detalle: ['Revisá la conexión y probá de nuevo antes de volver a hacerla firmar.'],
      botones: [comprobarDeNuevo],
    });
  }

  /** Con el listado: la firma quedó guardada solo si hay una nueva de la misma plantilla y versión, de este profesional. */
  function resolverComprobacion(buscada: { readonly titulo: string; readonly intento: IntentoDeFirma }, recibida: readonly ConsentimientoResumen[]): void {
    comprobacion = null;
    const hallada = firmaNueva(buscada.intento, recibida);
    if (hallada !== null) {
      const ver = crearBoton('Ver');
      ver.className = 'btn';
      conIcono(ver, 'ver');
      ver.setAttribute('aria-label', nombreAccesible('Ver', hallada.titulo, hallada.firmadoEn));
      ver.addEventListener('click', () => verDocumento(ver, hallada));
      const cuando = fechaDeFirma(hallada.firmadoEn) === fechaDeFirma(new Date().toISOString()) ? 'hoy' : `el ${fechaDeFirma(hallada.firmadoEn)}`;
      dibujarCaja({
        clase: 'cons-resultado-ok',
        rol: 'status',
        icono: 'verificado',
        titulo: 'La firma quedó guardada.',
        detalle: [negrita(hallada.titulo), `, versión ${hallada.versionNumero}, firmado ${cuando} a las ${horaDeFirma(hallada.firmadoEn)}.`],
        botones: [ver],
      });
      return;
    }
    const otraVez = crearBoton('Hacer firmar de nuevo');
    otraVez.className = 'btn btn-principal';
    conIcono(otraVez, 'firma');
    otraVez.addEventListener('click', () => firmarDeNuevo(otraVez, buscada.intento.plantillaId));
    dibujarCaja({
      clase: 'cons-resultado-error',
      rol: 'alert',
      icono: 'alterado',
      titulo: 'La firma no se guardó.',
      detalle: [`${buscada.titulo}, versión ${buscada.intento.versionNumero}. Si hace falta, volvé a hacerla firmar.`],
      botones: [otraVez],
    });
  }

  /** "Hacer firmar de nuevo": igual que "Continuar" con la misma plantilla, con el aviso de doble firma (design D14). */
  function firmarDeNuevo(origen: HTMLButtonElement, plantillaId: number): void {
    exito.textContent = '';
    error.textContent = '';
    const pedida: { plantilla: PlantillaVista | null; antes: ConsentimientoResumen[] } = { plantilla: null, antes: [] };
    void guardando(origen, async () => {
      const [vista, listado] = await Promise.all([
        obtenerPlantilla(opciones.api, plantillaId),
        listarConsentimientos(opciones.api, opciones.pacienteId),
      ]);
      pedida.plantilla = vista;
      pedida.antes = listado;
    }).then(
      (ok) => {
        if (ok && pedida.plantilla !== null && !destruida) {
          caja.replaceChildren();
          continuarConLaFirma(null, pedida.plantilla, pedida.antes);
        }
      },
      (causa: unknown) => {
        if (destruida) {
          return;
        }
        const mensaje = opciones.mensajeDeError(causa);
        if (causa instanceof ErrorNoEncontrado) {
          error.textContent = 'Esa plantilla ya no está disponible. Elegí otra con "Firmar consentimiento".';
        } else if (mensaje !== '') {
          error.textContent = mensaje;
        }
      },
    );
  }

  /** Revoca con el motivo obligatorio; el documento no se borra ni cambia (design D8). */
  function abrirRevocacion(origen: HTMLButtonElement, consentimiento: ConsentimientoResumen): void {
    exito.textContent = '';
    error.textContent = '';
    const motivo = crearCampoConContador({
      etiqueta: 'Motivo',
      limite: LIMITE_MOTIVO,
      multilinea: true,
      filas: 3,
      placeholder: 'Ej.: el paciente decidió no hacer el tratamiento',
    });
    const dialogo = abrirDialogoDesde(origen, {
      titulo: '¿Revocar el consentimiento?',
      resumen: `${consentimiento.titulo}, firmado el ${fechaDeFirma(consentimiento.firmadoEn)}. El documento no se borra ni cambia: queda como revocado, con tu nombre, la fecha y el motivo.`,
      contenido: [motivo.contenedor],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Revocar',
      peligro: true,
    });
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      dialogo.general.textContent = '';
      const mensaje = validarMotivo(motivo.control.value);
      motivo.mostrarError(mensaje);
      if (mensaje !== '') {
        motivo.control.focus();
        return;
      }
      void guardando(dialogo.enviar, () => revocar(opciones.api, opciones.pacienteId, consentimiento.id, motivo.control.value)).then(
        (ok) => {
          if (ok && !destruida) {
            dialogo.cerrar();
            exito.textContent = 'Consentimiento revocado.';
            void cargar();
          }
        },
        (causa: unknown) => {
          const texto = opciones.mensajeDeError(causa);
          if (texto === '' || destruida) {
            return;
          }
          if (causa instanceof ErrorNegocio && causa.codigo === 'CONSENTIMIENTO_YA_REVOCADO') {
            dialogo.cerrar();
            void cargar();
            error.textContent = texto;
            return;
          }
          dialogo.general.textContent = texto;
        },
      );
    });
  }

  // --- Firmar: elegir la plantilla, avisar de una doble firma y abrir la vista de firma (design D6 y D14) ---

  function abrirEleccion(origen: HTMLButtonElement): void {
    exito.textContent = '';
    error.textContent = '';
    caja.replaceChildren();
    const cuerpo = document.createElement('div');
    const dialogo = abrirDialogoDesde(origen, {
      titulo: 'Elegí el consentimiento',
      resumen:
        'Después se abre la pantalla de firma, que tapa la ficha: dásela al paciente para que lea y firme. Al terminar, el equipo queda bloqueado hasta que escribas tu contraseña.',
      contenido: [cuerpo],
      textoDescartar: 'Cancelar',
      textoPrincipal: 'Continuar',
    });
    dialogo.principal.disabled = true;
    let plantillas: PlantillaResumen[] = [];
    const grupo = nuevoId('cons-plantilla');

    function elegida(): PlantillaResumen | null {
      const radio = cuerpo.querySelector<HTMLInputElement>('input[type="radio"]:checked');
      return plantillas.find((p) => String(p.id) === radio?.value) ?? null;
    }

    async function cargarPlantillas(): Promise<void> {
      dialogo.principal.disabled = true;
      cuerpo.replaceChildren(crearCargando('Cargando plantillas…'));
      try {
        plantillas = await listarPlantillas(opciones.api, false);
      } catch (causa) {
        const mensaje = opciones.mensajeDeError(causa);
        if (mensaje !== '' && dialogo.dialogo.open) {
          cuerpo.replaceChildren();
          dialogo.general.textContent = mensaje;
        }
        return;
      }
      if (!dialogo.dialogo.open) {
        return;
      }
      if (plantillas.length === 0) {
        cuerpo.replaceChildren(
          parrafoDe('No hay plantillas activas. Pedile al administrador que cargue una en Configuración → Consentimientos.'),
        );
        dialogo.descartar.textContent = 'Cerrar';
        dialogo.principal.remove();
        return;
      }
      const conjunto = document.createElement('fieldset');
      conjunto.className = 'cons-opciones';
      const leyenda = document.createElement('legend');
      leyenda.className = 'solo-lectores';
      leyenda.textContent = 'Plantillas activas';
      conjunto.append(leyenda);
      plantillas.forEach((plantilla, indice) => {
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = grupo;
        radio.value = String(plantilla.id);
        radio.checked = indice === 0;
        const texto = document.createElement('span');
        texto.className = 'cons-opcion-texto';
        const nombre = document.createElement('span');
        nombre.textContent = plantilla.titulo;
        const version = document.createElement('small');
        version.textContent = `Versión ${plantilla.versionActual}`;
        texto.append(nombre, version);
        const etiqueta = document.createElement('label');
        etiqueta.className = 'cons-opcion';
        etiqueta.append(radio, texto);
        conjunto.append(etiqueta);
      });
      cuerpo.replaceChildren(conjunto);
      dialogo.principal.disabled = false;
    }

    void cargarPlantillas();

    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const plantilla = elegida();
      if (plantilla === null) {
        return;
      }
      dialogo.general.textContent = '';
      const resultado: { plantilla: PlantillaVista | null; antes: ConsentimientoResumen[] } = { plantilla: null, antes: [] };
      void guardando(dialogo.enviar, async () => {
        const [vista, listado] = await Promise.all([
          obtenerPlantilla(opciones.api, plantilla.id),
          listarConsentimientos(opciones.api, opciones.pacienteId),
        ]);
        resultado.plantilla = vista;
        resultado.antes = listado;
      }).then(
        (ok) => {
          if (!ok || resultado.plantilla === null || destruida) {
            return;
          }
          continuarConLaFirma(dialogo, resultado.plantilla, resultado.antes);
        },
        (causa: unknown) => {
          if (destruida) {
            return;
          }
          const mensaje = opciones.mensajeDeError(causa);
          if (causa instanceof ErrorNoEncontrado) {
            dialogo.general.textContent = 'Esa plantilla ya no está disponible. Elegí otra.';
            void cargarPlantillas();
            return;
          }
          if (mensaje !== '') {
            dialogo.general.textContent = mensaje;
          }
        },
      );
    });
  }

  /** Con el texto y el listado ya pedidos: avisa si ya se firmó hoy y, si no, abre la vista de firma. */
  function continuarConLaFirma(eleccion: DialogoAbierto | null, plantilla: PlantillaVista, antes: ConsentimientoResumen[]): void {
    const previa = firmadoHoy(antes, plantilla.id, plantilla.version.numero, hoy());
    if (previa === null) {
      eleccion?.cerrar();
      empezarFirma(plantilla, antes);
      return;
    }
    const pregunta = abrirDialogo({
      titulo: 'Ya firmó este consentimiento hoy',
      resumen: `\u201c${previa.titulo}\u201d, versión ${previa.versionNumero}, quedó firmado hoy a las ${horaDeFirma(previa.firmadoEn)} y está vigente. ¿Hacerlo firmar otra vez?`,
      textoDescartar: 'No firmar',
      textoPrincipal: 'Firmar otra vez',
    });
    // El foco empieza en "No firmar": lo seguro es no firmar dos veces.
    pregunta.descartar.focus();
    pregunta.descartar.addEventListener('click', () => eleccion?.cerrar());
    pregunta.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      pregunta.cerrar();
      eleccion?.cerrar();
      empezarFirma(plantilla, antes);
    });
  }

  function empezarFirma(plantilla: PlantillaVista, antes: readonly ConsentimientoResumen[]): void {
    abrirFirma({
      api: opciones.api,
      pacienteId: opciones.pacienteId,
      paciente: opciones.paciente,
      plantilla,
      alTerminar: (fin) => opciones.alTerminarFirma(fin, resultadoPrevioDe(fin, antes)),
    });
  }

  /** Lo que la ficha va a mostrar después de desbloquear, según cómo terminó la firma. */
  function resultadoPrevioDe(fin: FinDeFirma, antes: readonly ConsentimientoResumen[]): ResultadoPrevio | null {
    switch (fin.tipo) {
      case 'firmada':
        return {
          tipo: 'firmada',
          titulo: fin.consentimiento.titulo,
          versionNumero: fin.consentimiento.versionNumero,
          firmadoEn: fin.consentimiento.firmadoEn,
        };
      case 'sesion-vencida':
        return { tipo: 'sesion-vencida' };
      case 'dudosa':
        return {
          tipo: 'comprobar',
          titulo: fin.titulo,
          intento: {
            plantillaId: fin.plantillaId,
            versionNumero: fin.versionNumero,
            profesionalId: opciones.profesionalId ?? -1,
            antes: antes.map((c) => c.id),
          },
        };
      default:
        return null;
    }
  }

  // Lo que dejó la firma antes del bloqueo (design D12 y D13): el aviso de "firmada", el de sesión vencida o la comprobación.
  if (opciones.previo?.tipo === 'firmada') {
    exito.textContent = `Consentimiento firmado: ${opciones.previo.titulo}, versión ${opciones.previo.versionNumero}, a las ${horaDeFirma(opciones.previo.firmadoEn)}.`;
  }

  void cargar();
  if (opciones.previo?.tipo === 'sesion-vencida') {
    // Después de `cargar`, que limpia los avisos al empezar.
    error.textContent = 'La firma no se guardó porque la sesión había vencido. Si hace falta, hacela firmar de nuevo.';
  }

  return {
    contenedor: seccion,
    destruir() {
      destruida = true;
      lista = [];
      documento?.cerrar();
      documento = null;
      seccion.remove();
    },
  };
}
