import { formatearHora } from '../shared/fecha/zona.ts';
import type { Horario } from './disponibilidad.ts';

/**
 * Grilla de botones de horario (`.chips`), compartida por la reserva, las alternativas y el
 * cambio de horario. Cada botón muestra su hora `HH:mm` en la zona del consultorio; el elegido
 * queda con `aria-pressed="true"`.
 */
export function crearGrillaHorarios(
  horarios: readonly Horario[],
  elegido: string | null,
  alElegir: (horario: Horario) => void,
  etiqueta = 'Horarios libres',
): HTMLElement {
  const grupo = document.createElement('div');
  grupo.className = 'chips';
  grupo.setAttribute('role', 'group');
  grupo.setAttribute('aria-label', etiqueta);
  const botones = horarios.map((horario) => {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'chip';
    boton.textContent = formatearHora(horario.inicio);
    boton.setAttribute('aria-pressed', String(horario.inicio === elegido));
    boton.addEventListener('click', () => {
      for (const otro of botones) {
        otro.setAttribute('aria-pressed', String(otro === boton));
      }
      alElegir(horario);
    });
    return boton;
  });
  grupo.append(...botones);
  return grupo;
}
