import { fechaNumerica, fechaYHora } from '../ficha/formato.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { crearBoton } from '../shared/ui/campos.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { crearIcono, type NombreIcono } from '../shared/ui/iconos.ts';
import { obtenerConsentimiento, verificar } from './api.ts';
import type { ConsentimientoResumen, ConsentimientoVista, Verificacion } from './modelo.ts';
import { abrirDialogoDeLectura, crearEtiqueta, crearTextoPlano } from './ui.ts';

/**
 * Documento firmado y su verificación (C-48, design D7). El documento y la verificación se piden a la vez; el estado
 * de integridad sale solo de la respuesta de la verificación: nunca se muestra "Íntegro" por defecto. Todo texto
 * (incluido el de la evidencia) se asigna con `textContent`.
 */

export const MENSAJE_ALTERADO =
  'Alterado: el contenido guardado no coincide con su hash. Avisá al administrador y no lo uses como prueba hasta revisarlo.';

export interface OpcionesDocumento {
  /** El botón "Ver" que lo abrió: el foco vuelve ahí al cerrar. */
  readonly origen: HTMLElement;
  readonly api: ClienteApi;
  readonly pacienteId: number;
  readonly consentimiento: ConsentimientoResumen;
  /** Mensaje de un error de la API; vacío si ya se llevó al ingreso (sesión vencida). */
  mensajeDeError(causa: unknown): string;
}

export interface DocumentoAbierto {
  cerrar(): void;
}

type EstadoDeVerificacion =
  | { readonly tipo: 'verificando' }
  | { readonly tipo: 'resultado'; readonly verificacion: Verificacion }
  | { readonly tipo: 'fallo' };

function parrafo(texto: string, clase = ''): HTMLParagraphElement {
  const p = document.createElement('p');
  if (clase !== '') {
    p.className = clase;
  }
  p.textContent = texto;
  return p;
}

function datos(pares: ReadonlyArray<readonly [string, string, string?]>): HTMLElement {
  const dl = document.createElement('dl');
  dl.className = 'cons-doc-datos';
  for (const [termino, valor, clase] of pares) {
    const dt = document.createElement('dt');
    dt.textContent = termino;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    if (clase !== undefined) {
      dd.className = clase;
    }
    dl.append(dt, dd);
  }
  return dl;
}

function estadoDeLaVerificacion(icono: NombreIcono, texto: string, id: string): HTMLParagraphElement {
  const p = document.createElement('p');
  p.className = 'cons-verif-estado';
  p.id = id;
  p.append(crearIcono(icono), texto);
  return p;
}

export function abrirDocumento(opciones: OpcionesDocumento): DocumentoAbierto {
  const { consentimiento } = opciones;
  const lectura = abrirDialogoDeLectura({
    titulo: consentimiento.titulo,
    contenido: [crearCargando('Cargando el documento…')],
    clase: 'cons-documento',
    origen: opciones.origen,
    alCerrar: () => {
      abierto = false;
    },
  });
  let abierto = true;
  let vista: ConsentimientoVista | null = null;
  let estado: EstadoDeVerificacion = { tipo: 'verificando' };
  let pedidoDeVerificacion = 0;
  const franja = document.createElement('aside');
  franja.className = 'cons-verificacion';

  function dibujarVerificacion(): void {
    const id = `${lectura.dialogo.getAttribute('aria-labelledby') ?? 'cons-doc'}-verificacion`;
    franja.removeAttribute('role');
    franja.removeAttribute('aria-busy');
    franja.className = 'cons-verificacion';
    franja.setAttribute('aria-labelledby', id);
    if (estado.tipo === 'verificando') {
      franja.setAttribute('aria-busy', 'true');
      franja.setAttribute('role', 'status');
      franja.replaceChildren(estadoDeLaVerificacion('reloj', 'Verificando…', id));
      return;
    }
    if (estado.tipo === 'fallo') {
      const reintentar = crearBoton('Verificar de nuevo');
      reintentar.className = 'btn';
      reintentar.addEventListener('click', () => void pedirVerificacion());
      franja.replaceChildren(
        estadoDeLaVerificacion('alterado', 'Sin verificar', id),
        parrafo('No pudimos comprobar si el documento está íntegro. Probá de nuevo.'),
        reintentar,
      );
      return;
    }
    const { verificacion } = estado;
    const cuando = `Verificado el ${fechaYHora(verificacion.verificadoEn)}.`;
    if (verificacion.integro) {
      franja.classList.add('integro');
      franja.replaceChildren(
        estadoDeLaVerificacion('verificado', 'Íntegro', id),
        parrafo(`El contenido coincide con su hash ${verificacion.algoritmo === '' ? 'SHA-256' : verificacion.algoritmo}.`),
        parrafo(cuando),
      );
      return;
    }
    franja.classList.add('alterado');
    franja.setAttribute('role', 'alert');
    const hashGuardado = datos([['Hash guardado', verificacion.hashGuardado, 'cons-hash']]);
    const hashCalculado = datos([['Hash calculado', verificacion.hashCalculado, 'cons-hash']]);
    franja.replaceChildren(
      estadoDeLaVerificacion('alterado', 'Alterado', id),
      parrafo(MENSAJE_ALTERADO),
      hashGuardado,
      hashCalculado,
      parrafo(cuando),
    );
  }

  async function pedirVerificacion(): Promise<void> {
    pedidoDeVerificacion += 1;
    const este = pedidoDeVerificacion;
    estado = { tipo: 'verificando' };
    if (vista !== null) {
      dibujarVerificacion();
    }
    try {
      const verificacion = await verificar(opciones.api, opciones.pacienteId, consentimiento.id);
      if (abierto && este === pedidoDeVerificacion) {
        estado = { tipo: 'resultado', verificacion };
      }
    } catch (causa) {
      if (abierto && este === pedidoDeVerificacion) {
        opciones.mensajeDeError(causa);
        estado = { tipo: 'fallo' };
      }
    }
    if (abierto && este === pedidoDeVerificacion && vista !== null) {
      dibujarVerificacion();
    }
  }

  function dibujarDocumento(documento: ConsentimientoVista): void {
    const revocado = documento.estado === 'REVOCADO';
    const etiquetas = document.createElement('p');
    etiquetas.className = 'cons-doc-etiquetas';
    etiquetas.append(
      crearEtiqueta(revocado ? 'Revocado' : 'Vigente', revocado ? 'etiqueta-revocado' : 'etiqueta-vigente'),
      crearEtiqueta('Firma electrónica', 'etiqueta-firma'),
      crearEtiqueta(`Versión ${documento.versionNumero}`, 'etiqueta-version'),
    );
    const principal = document.createElement('div');
    principal.className = 'cons-doc-principal';
    principal.append(etiquetas);
    if (documento.revocacion !== null) {
      const { revocacion } = documento;
      principal.append(
        parrafo(`Revocado el ${fechaNumerica(revocacion.revocadoEn)} por ${revocacion.profesional}: ${revocacion.motivo}`, 'cons-revocacion'),
      );
    }
    principal.append(
      datos([
        ['Firmante', documento.firmante.nombre],
        ['DNI', documento.firmante.dni],
        ['Firmado', fechaYHora(documento.firmadoEn).replace(' a las ', ' ')],
        ['Profesional', documento.profesional],
      ]),
    );

    const titulo = document.createElement('h3');
    titulo.className = 'solo-lectores';
    titulo.textContent = 'Texto firmado';
    const evidencia = document.createElement('details');
    evidencia.className = 'cons-evidencia';
    const resumen = document.createElement('summary');
    resumen.textContent = 'Evidencia de la firma';
    evidencia.append(
      resumen,
      datos([
        ['IP', documento.evidencia.ip],
        ['Dispositivo', documento.evidencia.dispositivo],
        [
          'Algoritmo',
          documento.hash.formato === null ? documento.hash.algoritmo : `${documento.hash.algoritmo} · formato ${documento.hash.formato}`,
        ],
        ['Hash', documento.hash.valor, 'cons-hash'],
      ]),
    );
    const texto = document.createElement('div');
    texto.className = 'cons-doc-principal';
    texto.append(titulo, crearTextoPlano(documento.texto), evidencia);

    const cuerpo = document.createElement('div');
    cuerpo.className = 'cons-doc-cuerpo';
    cuerpo.append(principal, franja, texto);
    lectura.cuerpo.replaceChildren(cuerpo);
    dibujarVerificacion();
  }

  async function pedirDocumento(): Promise<void> {
    lectura.cuerpo.replaceChildren(crearCargando('Cargando el documento…'));
    try {
      const documento = await obtenerConsentimiento(opciones.api, opciones.pacienteId, consentimiento.id);
      if (!abierto) {
        return;
      }
      vista = documento;
      dibujarDocumento(documento);
    } catch (causa) {
      if (!abierto) {
        return;
      }
      const mensaje = opciones.mensajeDeError(causa);
      if (mensaje === '') {
        lectura.cerrar();
        return;
      }
      const aviso = document.createElement('p');
      aviso.setAttribute('role', 'alert');
      aviso.className = 'aviso aviso-error';
      aviso.textContent = mensaje;
      const reintentar = crearBoton('Reintentar');
      reintentar.className = 'btn';
      reintentar.addEventListener('click', () => void pedirDocumento());
      lectura.cuerpo.replaceChildren(aviso, reintentar);
    }
  }

  // El documento y la verificación salen a la vez; el estado de integridad solo cambia con la respuesta de la verificación.
  void pedirVerificacion();
  void pedirDocumento();

  return {
    cerrar() {
      lectura.cerrar();
    },
  };
}
