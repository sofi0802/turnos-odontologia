import { aIsoConZona, fechaLarga, hoy, lunesDeLaSemana, sumarDias, ZONA, type Fecha } from '../fecha/zona.ts';

/** Calendario mensual para elegir un día desde hoy en adelante (C-40, design D3). */
export interface Calendario {
  readonly contenedor: HTMLFieldSetElement;
  elegido(): Fecha | null;
  /** Quita el día elegido y vuelve al mes de hoy. */
  limpiar(): void;
  /** Habilita o deshabilita la elección; `ayuda` reemplaza el texto bajo el calendario. */
  habilitar(si: boolean, ayuda?: string): void;
}

export interface OpcionesCalendario {
  readonly leyenda: string;
  alElegir(fecha: Fecha): void;
}

export const AYUDA_CALENDARIO = 'Podés elegir desde hoy en adelante. Los días pasados no se pueden elegir.';

const MES = new Intl.DateTimeFormat('es-AR', { timeZone: ZONA, month: 'long', year: 'numeric' });
const DIAS_SEMANA = ['lu', 'ma', 'mi', 'ju', 'vi', 'sá', 'do'] as const;

function primeroDelMes(fecha: Fecha): Fecha {
  return `${fecha.slice(0, 7)}-01`;
}

function mesSiguiente(primero: Fecha): Fecha {
  return primeroDelMes(sumarDias(primero, 32));
}

function mesAnterior(primero: Fecha): Fecha {
  return primeroDelMes(sumarDias(primero, -1));
}

function etiquetaDelDia(fecha: Fecha, esHoy: boolean): string {
  return `${fechaLarga(aIsoConZona(fecha, '12:00'))} de ${fecha.slice(0, 4)}${esHoy ? ', hoy' : ''}`;
}

let contador = 0;

export function crearCalendario(opciones: OpcionesCalendario): Calendario {
  contador += 1;
  const idMes = `calendario-mes-${contador}`;
  let mes = primeroDelMes(hoy());
  let elegido: Fecha | null = null;
  let habilitado = true;

  const contenedor = document.createElement('fieldset');
  contenedor.className = 'campo campo-calendario';
  const leyenda = document.createElement('legend');
  leyenda.textContent = opciones.leyenda;

  const marco = document.createElement('div');
  marco.className = 'calendario';
  const cabecera = document.createElement('div');
  cabecera.className = 'cal-cabecera';
  const anterior = document.createElement('button');
  anterior.type = 'button';
  anterior.className = 'cal-nav';
  anterior.setAttribute('aria-label', 'Mes anterior');
  anterior.textContent = '‹';
  const titulo = document.createElement('p');
  titulo.className = 'cal-mes';
  titulo.id = idMes;
  titulo.setAttribute('aria-live', 'polite');
  const siguiente = document.createElement('button');
  siguiente.type = 'button';
  siguiente.className = 'cal-nav';
  siguiente.setAttribute('aria-label', 'Mes siguiente');
  siguiente.textContent = '›';
  cabecera.append(anterior, titulo, siguiente);

  const semana = document.createElement('div');
  semana.className = 'cal-semana';
  semana.setAttribute('aria-hidden', 'true');
  semana.append(
    ...DIAS_SEMANA.map((d) => {
      const celda = document.createElement('span');
      celda.textContent = d;
      return celda;
    }),
  );

  const grilla = document.createElement('div');
  grilla.className = 'cal-dias';
  grilla.setAttribute('role', 'group');
  grilla.setAttribute('aria-labelledby', idMes);
  marco.append(cabecera, semana, grilla);

  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda cal-ayuda';
  ayuda.textContent = AYUDA_CALENDARIO;
  contenedor.append(leyenda, marco, ayuda);

  function dibujar(): void {
    const deHoy = hoy();
    titulo.textContent = MES.format(new Date(aIsoConZona(mes, '12:00')));
    anterior.disabled = !habilitado || mes <= primeroDelMes(deHoy);
    siguiente.disabled = !habilitado;
    const finDelMes = mesSiguiente(mes);
    const botones: HTMLButtonElement[] = [];
    for (let fecha = lunesDeLaSemana(mes); fecha < finDelMes || botones.length % 7 !== 0; fecha = sumarDias(fecha, 1)) {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.className = 'cal-dia';
      boton.textContent = String(Number(fecha.slice(8, 10)));
      if (fecha < mes || fecha >= finDelMes) {
        boton.classList.add('otro-mes');
        boton.disabled = true;
        boton.setAttribute('aria-hidden', 'true');
        boton.tabIndex = -1;
      } else {
        const esHoy = fecha === deHoy;
        boton.classList.toggle('hoy', esHoy);
        boton.dataset['fecha'] = fecha;
        boton.setAttribute('aria-label', etiquetaDelDia(fecha, esHoy));
        boton.setAttribute('aria-pressed', String(fecha === elegido));
        boton.disabled = !habilitado || fecha < deHoy;
        const esta = fecha;
        boton.addEventListener('click', () => {
          elegido = esta;
          for (const otro of grilla.querySelectorAll<HTMLButtonElement>('.cal-dia[data-fecha]')) {
            otro.setAttribute('aria-pressed', String(otro === boton));
          }
          opciones.alElegir(esta);
        });
      }
      botones.push(boton);
    }
    grilla.replaceChildren(...botones);
  }

  anterior.addEventListener('click', () => {
    mes = mesAnterior(mes);
    dibujar();
  });
  siguiente.addEventListener('click', () => {
    mes = mesSiguiente(mes);
    dibujar();
  });

  dibujar();

  return {
    contenedor,
    elegido: () => elegido,
    limpiar() {
      elegido = null;
      mes = primeroDelMes(hoy());
      dibujar();
    },
    habilitar(si, texto) {
      habilitado = si;
      contenedor.disabled = !si;
      contenedor.classList.toggle('campo-deshabilitado', !si);
      marco.classList.toggle('bloqueado', !si);
      ayuda.textContent = texto ?? AYUDA_CALENDARIO;
      dibujar();
    },
  };
}
