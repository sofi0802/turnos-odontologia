import { ErrorNegocio, ErrorValidacion } from '../shared/api/errores.ts';
import type { Campo } from '../shared/ui/campos.ts';
import { abrirDialogo, type DialogoAbierto, type OpcionesDialogo } from '../shared/ui/dialogo.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';

/** Piezas de los diálogos de coberturas (C-49), compartidas por el catálogo y la sección del paciente. */

export type CampoFormulario = Campo<HTMLInputElement> | Campo<HTMLSelectElement>;

/** Abre un diálogo con el molde de la aplicación y, al cerrarse, devuelve el foco al botón que lo abrió. */
export function abrirDialogoDesde(origen: HTMLElement, opciones: OpcionesDialogo): DialogoAbierto {
  const abierto = abrirDialogo({
    ...opciones,
    alCerrar: () => {
      opciones.alCerrar?.();
      if (origen.isConnected) {
        origen.focus();
      }
    },
  });
  abierto.formulario.classList.add('cob-form');
  return abierto;
}

/**
 * Muestra la validación previa: cada campo con su mensaje (o sin error) y el foco en el primero con error.
 * Devuelve `true` si no hay errores.
 */
export function marcarErrores(errores: readonly (readonly [CampoFormulario, string])[]): boolean {
  let primero: CampoFormulario | null = null;
  for (const [campo, mensaje] of errores) {
    campo.mostrarError(mensaje);
    if (mensaje !== '' && primero === null) {
      primero = campo;
    }
  }
  primero?.control.focus();
  return primero === null;
}

export interface DestinoDeCampo {
  readonly campo: CampoFormulario;
  /** Mensaje propio: el backend solo dice "valor inválido" (design D7). */
  readonly mensaje: string;
}

export interface OpcionesRechazo {
  /** Aviso `role="alert"` del diálogo, para los errores que no son de un campo. */
  readonly general: HTMLElement;
  /** Campo de la API (`campo` de `errores[]`) → dónde se muestra y con qué texto. */
  readonly campos: Readonly<Record<string, DestinoDeCampo>>;
  /** Códigos de negocio que se muestran junto a un campo, como `AFILIADO_DUPLICADO` → `numeroAfiliado`. */
  readonly codigosDeCampo?: Readonly<Record<string, string>>;
  sesionVencida(causa: unknown): boolean;
}

/**
 * Muestra un rechazo de la API dentro del diálogo, que queda abierto con los datos: los errores de un campo junto a
 * él y los demás arriba. Ningún texto repite lo que escribió la persona.
 */
export function mostrarRechazo(causa: unknown, opciones: OpcionesRechazo): void {
  if (opciones.sesionVencida(causa)) {
    return;
  }
  const destinos = Object.values(opciones.campos);
  for (const { campo } of destinos) {
    campo.mostrarError('');
  }
  opciones.general.textContent = '';

  if (causa instanceof ErrorNegocio) {
    const nombre = opciones.codigosDeCampo?.[causa.codigo];
    const destino = nombre === undefined ? undefined : opciones.campos[nombre];
    if (destino !== undefined) {
      destino.campo.mostrarError(mensajeDeError(causa));
      destino.campo.control.focus();
      return;
    }
  }
  if (causa instanceof ErrorValidacion && causa.errores.length > 0) {
    const ubicados = causa.errores
      .map((error) => opciones.campos[error.campo])
      .filter((d): d is DestinoDeCampo => d !== undefined);
    for (const { campo, mensaje } of ubicados) {
      campo.mostrarError(mensaje);
    }
    ubicados[0]?.campo.control.focus();
    if (ubicados.length === causa.errores.length) {
      return;
    }
  }
  opciones.general.textContent = mensajeDeError(causa);
}

/** Fila de dos campos que en pantallas anchas van uno al lado del otro. */
export function filaDeCampos(...campos: CampoFormulario[]): HTMLDivElement {
  const fila = document.createElement('div');
  fila.className = 'cob-form-fila';
  fila.append(...campos.map((c) => c.contenedor));
  return fila;
}

/** Ayuda fija debajo de un control, enlazada además del error por `aria-describedby`. */
export function agregarAyuda(campo: CampoFormulario, texto: string): void {
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.id = `${campo.control.id}-ayuda`;
  ayuda.textContent = texto;
  campo.control.after(ayuda);
  const descrito = campo.control.getAttribute('aria-describedby') ?? '';
  campo.control.setAttribute('aria-describedby', `${ayuda.id} ${descrito}`.trim());
}
