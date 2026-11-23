/** Una pestaña: su texto y cómo se monta el panel la primera vez que se muestra (design D2). */
export interface Pestana {
  readonly titulo: string;
  montar(panel: HTMLElement): void;
}

let contador = 0;

/**
 * Pestañas con el patrón ARIA `tablist`: botones `role="tab"` con `aria-selected` y
 * `aria-controls`, paneles `role="tabpanel"` con `hidden`, flechas izquierda y derecha (más Inicio
 * y Fin) para moverse, y la pestaña elegida llevada a la vista en pantallas chicas.
 */
export function crearPestanas(pestanas: readonly Pestana[], etiqueta: string): HTMLElement {
  contador += 1;
  const base = `config-${contador}`;
  const raiz = document.createElement('div');
  const lista = document.createElement('div');
  lista.className = 'pestanas-config';
  lista.setAttribute('role', 'tablist');
  lista.setAttribute('aria-label', etiqueta);
  raiz.append(lista);

  const botones: HTMLButtonElement[] = [];
  const paneles: HTMLElement[] = [];
  const montadas = new Set<number>();

  pestanas.forEach((pestana, indice) => {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'pestana';
    boton.id = `${base}-tab-${indice}`;
    boton.setAttribute('role', 'tab');
    boton.setAttribute('aria-controls', `${base}-panel-${indice}`);
    boton.textContent = pestana.titulo;
    const panel = document.createElement('section');
    panel.id = `${base}-panel-${indice}`;
    panel.className = 'panel-config';
    panel.setAttribute('role', 'tabpanel');
    panel.setAttribute('aria-labelledby', boton.id);
    panel.hidden = true;
    botones.push(boton);
    paneles.push(panel);
    lista.append(boton);
    raiz.append(panel);
  });

  function elegir(indice: number, foco: boolean): void {
    botones.forEach((boton, i) => {
      const elegida = i === indice;
      boton.setAttribute('aria-selected', String(elegida));
      boton.tabIndex = elegida ? 0 : -1;
      const panel = paneles[i];
      if (panel !== undefined) {
        panel.hidden = !elegida;
      }
    });
    const boton = botones[indice];
    const panel = paneles[indice];
    const pestana = pestanas[indice];
    if (boton === undefined || panel === undefined || pestana === undefined) {
      return;
    }
    if (foco) {
      boton.focus();
    }
    // jsdom no implementa scrollIntoView.
    if (typeof boton.scrollIntoView === 'function') {
      boton.scrollIntoView({ block: 'nearest', inline: 'center' });
    }
    if (!montadas.has(indice)) {
      montadas.add(indice);
      pestana.montar(panel);
    }
  }

  botones.forEach((boton, indice) => {
    boton.addEventListener('click', () => elegir(indice, false));
    boton.addEventListener('keydown', (evento) => {
      const ultimo = botones.length - 1;
      const destino =
        evento.key === 'ArrowRight' ? (indice === ultimo ? 0 : indice + 1)
        : evento.key === 'ArrowLeft' ? (indice === 0 ? ultimo : indice - 1)
        : evento.key === 'Home' ? 0
        : evento.key === 'End' ? ultimo
        : null;
      if (destino !== null) {
        evento.preventDefault();
        elegir(destino, true);
      }
    });
  });

  if (pestanas.length > 0) {
    elegir(0, false);
  }
  return raiz;
}
