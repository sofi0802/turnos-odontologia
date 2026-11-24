import { ErrorValidacion } from '../shared/api/errores.ts';
import { crearAviso, crearBoton } from '../shared/ui/campos.ts';
import { MAXIMO_TEXTO, type DatosFicha, type Ficha } from './api.ts';
import { fechaYHora } from './formato.ts';

/** Ficha básica (C-44): antecedentes, alergias y medicación; editable solo para el odontólogo (RN-CL-01). */

export const MENSAJE_FICHA_VACIA = 'Todavía no se cargó la ficha.';
export const MENSAJE_FICHA_GUARDADA = 'Ficha guardada.';
export const MENSAJE_TEXTO_LARGO = 'Máximo 4000 caracteres.';
export const MENSAJE_FICHA_INVALIDA = 'Revisá los datos de la ficha.';

const DATOS: ReadonlyArray<readonly [keyof DatosFicha, string]> = [
  ['antecedentes', 'Antecedentes'],
  ['alergias', 'Alergias'],
  ['medicacion', 'Medicación'],
];

/** Texto de ejemplo de cada campo (opción B de los campos, elegida el 2026-11-24). */
const EJEMPLOS: Readonly<Record<keyof DatosFicha, string>> = {
  antecedentes: 'Ej.: hipertensión, diabetes, cirugías previas',
  alergias: 'Ej.: penicilina, látex, anestesia local',
  medicacion: 'Ej.: anticoagulantes, con dosis',
};

export interface OpcionesFichaBasica {
  readonly ficha: Ficha;
  readonly editable: boolean;
  guardar?(datos: DatosFicha): Promise<Ficha>;
  mensajeDeError?(causa: unknown): string;
}

let contador = 0;

function estaVacia(ficha: Ficha): boolean {
  return ficha.actualizadoEn === null && ficha.antecedentes === '' && ficha.alergias === '' && ficha.medicacion === '';
}

export function crearFichaBasica(opciones: OpcionesFichaBasica): HTMLElement {
  contador += 1;
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion';
  const titulo = document.createElement('h2');
  titulo.id = `titulo-ficha-${contador}`;
  titulo.textContent = 'Ficha básica';
  seccion.setAttribute('aria-labelledby', titulo.id);
  const firma = document.createElement('p');
  firma.className = 'firma';
  const aviso = document.createElement('p');
  aviso.className = 'aviso aviso-info';
  seccion.append(titulo, firma, aviso);

  function mostrarFirma(ficha: Ficha): void {
    firma.hidden = ficha.actualizadoPor === null || ficha.actualizadoEn === null;
    firma.textContent =
      ficha.actualizadoPor === null || ficha.actualizadoEn === null
        ? ''
        : `Actualizada por ${ficha.actualizadoPor} el ${fechaYHora(ficha.actualizadoEn)}`;
    aviso.hidden = !estaVacia(ficha);
    aviso.textContent = aviso.hidden ? '' : MENSAJE_FICHA_VACIA;
  }
  mostrarFirma(opciones.ficha);

  if (!opciones.editable) {
    const lista = document.createElement('dl');
    lista.className = 'ficha-dato';
    for (const [clave, etiqueta] of DATOS) {
      const termino = document.createElement('dt');
      termino.textContent = etiqueta;
      const valor = document.createElement('dd');
      const texto = opciones.ficha[clave];
      valor.textContent = texto === '' ? 'Sin datos' : texto;
      valor.classList.toggle('vacio-dato', texto === '');
      lista.append(termino, valor);
    }
    seccion.append(lista);
    return seccion;
  }

  const formulario = document.createElement('form');
  formulario.className = 'ficha-seccion';
  formulario.noValidate = true;
  formulario.setAttribute('aria-label', 'Ficha básica');
  const campos = DATOS.map(([clave, etiqueta]) => {
    const id = `ficha-${clave}-${contador}`;
    const label = document.createElement('label');
    label.htmlFor = id;
    label.textContent = etiqueta;
    const area = document.createElement('textarea');
    area.id = id;
    area.name = clave;
    area.maxLength = MAXIMO_TEXTO;
    area.value = opciones.ficha[clave];
    area.placeholder = EJEMPLOS[clave];
    const error = document.createElement('p');
    error.className = 'campo-error';
    error.id = `${id}-error`;
    area.setAttribute('aria-describedby', error.id);
    const cuenta = document.createElement('p');
    cuenta.className = 'contador';
    const actualizar = (): void => {
      cuenta.textContent = `${area.value.length} / ${MAXIMO_TEXTO}`;
    };
    actualizar();
    area.addEventListener('input', actualizar);
    cuenta.id = `${id}-contador`;
    area.setAttribute('aria-describedby', `${error.id} ${cuenta.id}`);
    const cabecera = document.createElement('div');
    cabecera.className = 'campo-suave-cabecera';
    cabecera.append(label, cuenta);
    const contenedor = document.createElement('div');
    contenedor.className = 'campo-suave';
    contenedor.append(cabecera, area, error);
    formulario.append(contenedor);
    return { clave, area, error, actualizar };
  });
  const exito = crearAviso('status');
  const general = crearAviso('alert');
  const acciones = document.createElement('div');
  acciones.className = 'acciones-odontograma';
  const guardar = crearBoton('Guardar ficha', 'submit');
  guardar.className = 'btn btn-principal';
  acciones.append(guardar);
  formulario.append(general, exito, acciones);
  seccion.append(formulario);

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void enviar();
  });

  async function enviar(): Promise<void> {
    exito.textContent = '';
    general.textContent = '';
    let valido = true;
    for (const { area, error } of campos) {
      const largo = area.value.length > MAXIMO_TEXTO;
      error.textContent = largo ? MENSAJE_TEXTO_LARGO : '';
      if (largo) {
        area.setAttribute('aria-invalid', 'true');
        valido = false;
      } else {
        area.removeAttribute('aria-invalid');
      }
    }
    if (!valido || opciones.guardar === undefined) {
      return;
    }
    const datos: DatosFicha = {
      antecedentes: campos[0]?.area.value ?? '',
      alergias: campos[1]?.area.value ?? '',
      medicacion: campos[2]?.area.value ?? '',
    };
    guardar.disabled = true;
    try {
      const ficha = await opciones.guardar(datos);
      for (const campo of campos) {
        campo.area.value = ficha[campo.clave];
        campo.actualizar();
      }
      mostrarFirma(ficha);
      exito.textContent = MENSAJE_FICHA_GUARDADA;
    } catch (causa) {
      general.textContent =
        causa instanceof ErrorValidacion ? MENSAJE_FICHA_INVALIDA : (opciones.mensajeDeError?.(causa) ?? '');
    } finally {
      guardar.disabled = false;
    }
  }

  return seccion;
}
