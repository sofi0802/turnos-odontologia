import { crearAviso, crearBoton, crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { cargarEn, crearCabecera, guardando, mostrarErrorEnFormulario, type ContextoSeccion } from './ui.ts';

/** Pestaña "Señas" de Configuración, solo para el `ADMIN` (C-23, RN-SE-01, spec frontend-senas). */

const RUTA = '/senas/configuracion';

export interface ConfiguracionSenas {
  readonly activas: boolean;
  readonly porcentaje: number;
  readonly plazoMinutos: number;
}

/** Lee `{activas, porcentaje, plazoMinutos}`; `null` si la respuesta no tiene esa forma. */
export function leerConfiguracionSenas(cuerpo: unknown): ConfiguracionSenas | null {
  if (typeof cuerpo !== 'object' || cuerpo === null) {
    return null;
  }
  const activas: unknown = Reflect.get(cuerpo, 'activas');
  const porcentaje: unknown = Reflect.get(cuerpo, 'porcentaje');
  const plazoMinutos: unknown = Reflect.get(cuerpo, 'plazoMinutos');
  if (typeof activas !== 'boolean' || typeof porcentaje !== 'number' || typeof plazoMinutos !== 'number') {
    return null;
  }
  return { activas, porcentaje, plazoMinutos };
}

async function cargar(contexto: ContextoSeccion): Promise<ConfiguracionSenas> {
  const configuracion = leerConfiguracionSenas(await contexto.api.obtener<unknown>(RUTA));
  if (configuracion === null) {
    throw new Error(`Respuesta inesperada de ${RUTA}`);
  }
  return configuracion;
}

export function montarSenas(panel: HTMLElement, contexto: ContextoSeccion): void {
  const contenido = document.createElement('div');
  panel.append(crearCabecera('Señas de la reserva online'), contenido);
  cargarEn(
    contenido,
    contexto,
    () => cargar(contexto),
    (configuracion, contenedor) => dibujar(contenedor, configuracion, contexto),
  );
}

function numero(nombre: string, valor: number, minimo: number, maximo: number, paso: number): HTMLInputElement {
  const entrada = crearEntrada('number', nombre);
  entrada.classList.add('control');
  entrada.min = String(minimo);
  entrada.max = String(maximo);
  entrada.step = String(paso);
  entrada.value = String(valor);
  return entrada;
}

function conAyuda(campo: Campo<HTMLInputElement>, texto: string): void {
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.id = `${campo.control.id}-ayuda`;
  ayuda.textContent = texto;
  campo.contenedor.insertBefore(ayuda, campo.contenedor.lastChild);
  const descripcion = campo.control.getAttribute('aria-describedby') ?? '';
  campo.control.setAttribute('aria-describedby', `${ayuda.id} ${descripcion}`.trim());
}

function dibujar(contenedor: HTMLElement, inicial: ConfiguracionSenas, contexto: ContextoSeccion): void {
  const caja = document.createElement('div');
  caja.className = 'panel senas-config';
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent =
    'El paciente que reserva online paga con Mercado Pago un porcentaje de su copago. ' +
    'Los turnos que carga el personal y los sobreturnos no piden seña.';

  const activas = crearEntrada('checkbox', 'activas');
  activas.checked = inicial.activas;
  const etiquetaActivas = document.createElement('label');
  etiquetaActivas.className = 'casilla';
  etiquetaActivas.append(activas, ' Pedir seña en la reserva online');

  const porcentaje = crearCampo('Porcentaje del copago', numero('porcentaje', inicial.porcentaje, 1, 100, 1));
  conAyuda(porcentaje, 'Entero de 1 a 100.');
  const plazo = crearCampo('Plazo para pagar (minutos)', numero('plazoMinutos', inicial.plazoMinutos, 5, 1440, 5));
  conAyuda(plazo, 'Si no se paga a tiempo, el turno se cancela solo.');
  const numeros = document.createElement('div');
  numeros.className = 'dos-campos';
  numeros.append(porcentaje.contenedor, plazo.contenedor);

  const guardar = crearBoton('Guardar', 'submit');
  guardar.className = 'btn btn-principal';
  const pie = document.createElement('div');
  pie.append(guardar);
  const formulario = document.createElement('form');
  formulario.className = 'formulario';
  formulario.noValidate = true;
  formulario.append(etiquetaActivas, numeros, pie);
  const ok = crearAviso('status');
  const error = crearAviso('alert');
  caja.append(ayuda, formulario, ok, error);
  contenedor.append(caja);

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    ok.textContent = '';
    error.textContent = '';
    porcentaje.mostrarError('');
    plazo.mostrarError('');
    // Los rangos los valida la API (422 con el campo); acá solo se manda lo escrito.
    const pedido = {
      activas: activas.checked,
      porcentaje: Number(porcentaje.control.value),
      plazoMinutos: Number(plazo.control.value),
    };
    void guardando(guardar, () => contexto.api.actualizar<unknown>(RUTA, pedido)).then(
      (listo) => {
        if (listo) {
          ok.textContent = 'Configuración de señas guardada.';
        }
      },
      (causa: unknown) => {
        mostrarErrorEnFormulario(causa, error, [porcentaje, plazo], contexto);
      },
    );
  });
}
