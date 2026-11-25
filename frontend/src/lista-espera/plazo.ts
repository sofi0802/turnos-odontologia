import type { ContextoSeccion } from '../configuracion/ui.ts';
import { guardando, mostrarErrorEnFormulario } from '../configuracion/ui.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { guardarPlazo, obtenerPlazo, PLAZO_MAXIMO, PLAZO_MINIMO, plazoValido } from './api.ts';

export const TITULO_PLAZO = 'Plazo para aceptar una oferta de la lista de espera';
export const MENSAJE_PLAZO_INVALIDO = 'Ingresá un número entero de 1 a 24.';

function horas(cantidad: number): string {
  return cantidad === 1 ? '1 hora' : `${cantidad} horas`;
}

/**
 * C-15: el plazo para aceptar una oferta, dentro de la pestaña "Margen". El administrador lo cambia; la recepción
 * lo ve en solo lectura y el odontólogo no lo ve (la API le respondería 403).
 */
export function montarPlazo(caja: HTMLElement, contexto: ContextoSeccion): void {
  const rol = contexto.sesion.rol;
  if (rol !== 'ADMIN' && rol !== 'RECEPCIONISTA') {
    return;
  }
  const seccion = document.createElement('section');
  seccion.className = 'le-plazo';
  const titulo = document.createElement('h3');
  titulo.id = 'plazo-lista-espera';
  titulo.textContent = TITULO_PLAZO;
  seccion.setAttribute('aria-labelledby', titulo.id);
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Si el paciente no acepta en este plazo, el horario se le ofrece al siguiente.';
  const vigente = document.createElement('p');
  const valor = document.createElement('span');
  valor.className = 'margen-valor';
  const unidad = document.createTextNode('');
  vigente.append('Plazo vigente: ', valor, unidad);
  const error = crearAviso('alert');
  seccion.append(titulo, ayuda, vigente, error);
  caja.append(seccion);

  function mostrar(plazo: number): void {
    valor.textContent = String(plazo);
    unidad.textContent = plazo === 1 ? ' hora' : ' horas';
  }

  void obtenerPlazo(contexto.api).then(
    (plazo) => {
      mostrar(plazo);
      if (rol === 'ADMIN') {
        dibujarFormulario(plazo);
      } else {
        const soloLectura = document.createElement('p');
        soloLectura.className = 'ayuda';
        soloLectura.textContent = 'Solo el administrador puede cambiar el plazo.';
        seccion.append(soloLectura);
      }
    },
    (causa: unknown) => {
      if (!contexto.sesionVencida(causa)) {
        vigente.hidden = true;
        error.textContent = mensajeDeError(causa);
      }
    },
  );

  function dibujarFormulario(inicial: number): void {
    const entrada = crearEntrada('number', 'plazoHoras');
    entrada.classList.add('control');
    entrada.min = String(PLAZO_MINIMO);
    entrada.max = String(PLAZO_MAXIMO);
    entrada.step = '1';
    entrada.value = String(inicial);
    const campo = crearCampo('Nuevo plazo', entrada);
    const guardar = crearBoton('Guardar plazo', 'submit');
    guardar.className = 'btn btn-principal';
    const formulario = document.createElement('form');
    formulario.className = 'campo-margen';
    formulario.noValidate = true;
    formulario.append(campo.contenedor, guardar);
    const ok = crearAviso('status');
    seccion.append(formulario, ok);

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      ok.textContent = '';
      error.textContent = '';
      const pedido = plazoValido(entrada.value);
      campo.mostrarError(pedido === null ? MENSAJE_PLAZO_INVALIDO : '');
      if (pedido === null) {
        return;
      }
      let guardado = pedido;
      void guardando(guardar, async () => {
        guardado = await guardarPlazo(contexto.api, pedido);
      }).then(
        (listo) => {
          if (listo) {
            mostrar(guardado);
            ok.textContent = `Guardaste el plazo: ${horas(guardado)}.`;
          }
        },
        (causa: unknown) => {
          mostrarErrorEnFormulario(causa, error, [campo], contexto);
        },
      );
    });
  }
}
