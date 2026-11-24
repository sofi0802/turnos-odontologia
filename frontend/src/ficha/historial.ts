import { crearCargando } from '../shared/ui/estado-vacio.ts';
import type { RegistroOdontograma } from './api.ts';
import { fechaNumerica } from './formato.ts';
import { NOMBRE_ESTADO, nombreDeCara } from './odontograma-modelo.ts';
import { formatearHora } from '../shared/fecha/zona.ts';

/**
 * Historial del odontograma (C-44, design D3): plegado al principio, se pide recién al abrirlo y se puede volver a
 * pedir después de guardar. Solo lectura: nunca ofrece editar ni borrar (RN-CL-02).
 */

export interface Historial {
  readonly elemento: HTMLElement;
  /** Vuelve a pedirlo si ya se abrió alguna vez. */
  recargar(): void;
}

export function crearHistorial(
  pedir: () => Promise<readonly RegistroOdontograma[]>,
  mensajeDeError: (causa: unknown) => string,
): Historial {
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion';
  const desplegable = document.createElement('details');
  desplegable.className = 'ficha-desplegable';
  const resumen = document.createElement('summary');
  resumen.textContent = 'Historial del odontograma';
  const cuerpo = document.createElement('div');
  desplegable.append(resumen, cuerpo);
  seccion.append(desplegable);
  let pedido = false;
  let vez = 0;

  async function cargar(): Promise<void> {
    pedido = true;
    vez += 1;
    const esta = vez;
    cuerpo.replaceChildren(crearCargando());
    try {
      const registros = await pedir();
      if (esta === vez) {
        cuerpo.replaceChildren(tabla(registros));
      }
    } catch (causa) {
      if (esta === vez) {
        const error = document.createElement('p');
        error.className = 'aviso aviso-error';
        error.setAttribute('role', 'alert');
        error.textContent = mensajeDeError(causa);
        cuerpo.replaceChildren(error);
        pedido = false;
      }
    }
  }

  desplegable.addEventListener('toggle', () => {
    if (desplegable.open && !pedido) {
      void cargar();
    }
  });

  return {
    elemento: seccion,
    recargar() {
      if (pedido) {
        void cargar();
      }
    },
  };
}

function tabla(registros: readonly RegistroOdontograma[]): HTMLElement {
  if (registros.length === 0) {
    const vacio = document.createElement('p');
    vacio.className = 'firma';
    vacio.textContent = 'Sin registros en el odontograma.';
    return vacio;
  }
  const elemento = document.createElement('table');
  elemento.className = 'tabla-historial';
  const cabeza = document.createElement('thead');
  const filaCabeza = document.createElement('tr');
  for (const texto of ['Fecha', 'Pieza', 'Cara', 'Estado', 'Profesional']) {
    const celda = document.createElement('th');
    celda.scope = 'col';
    celda.textContent = texto;
    filaCabeza.append(celda);
  }
  cabeza.append(filaCabeza);
  const cuerpo = document.createElement('tbody');
  for (const registro of registros) {
    const fila = document.createElement('tr');
    const valores = [
      `${fechaNumerica(registro.registradoEn)} ${formatearHora(registro.registradoEn)}`,
      String(registro.pieza),
      registro.cara === null ? 'Pieza completa' : nombreDeCara(registro.cara),
      NOMBRE_ESTADO[registro.estado],
      registro.profesional,
    ];
    for (const valor of valores) {
      const celda = document.createElement('td');
      celda.textContent = valor;
      fila.append(celda);
    }
    cuerpo.append(fila);
  }
  elemento.append(cabeza, cuerpo);
  return elemento;
}
