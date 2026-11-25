import { montarPlazo } from '../lista-espera/plazo.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { cargarEn, crearCabecera, guardando, mostrarErrorEnFormulario, type ContextoSeccion } from './ui.ts';

const RUTA = '/configuracion/margen';

/** Lee `{minutos}` de `GET/PUT /configuracion/margen`; `null` si la respuesta no tiene esa forma. */
export function leerMargen(cuerpo: unknown): number | null {
  if (typeof cuerpo !== 'object' || cuerpo === null) {
    return null;
  }
  const minutos: unknown = Reflect.get(cuerpo, 'minutos');
  return typeof minutos === 'number' && Number.isFinite(minutos) ? minutos : null;
}

async function cargarMargen(contexto: ContextoSeccion): Promise<number> {
  const minutos = leerMargen(await contexto.api.obtener<unknown>(RUTA));
  if (minutos === null) {
    throw new Error(`Respuesta inesperada de ${RUTA}`);
  }
  return minutos;
}

/**
 * Pestaña "Margen" (requisito "Margen entre turnos"): muestra el margen vigente y, para `ADMIN`,
 * permite cambiarlo. Si la API lo rechaza, el margen vigente no cambia.
 */
export function montarMargen(panel: HTMLElement, contexto: ContextoSeccion): void {
  const contenido = document.createElement('div');
  panel.append(crearCabecera('Margen entre turnos'), contenido);
  cargarEn(
    contenido,
    contexto,
    () => cargarMargen(contexto),
    (minutos, contenedor) => dibujar(contenedor, minutos, contexto),
  );
}

function dibujar(contenedor: HTMLElement, inicial: number, contexto: ContextoSeccion): void {
  const caja = document.createElement('div');
  caja.className = 'panel';
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Tiempo mínimo entre dos turnos del mismo profesional o del mismo sillón.';
  const vigente = document.createElement('p');
  const valor = document.createElement('span');
  valor.className = 'margen-valor';
  valor.textContent = String(inicial);
  vigente.append('Margen vigente: ', valor, ' minutos');
  caja.append(ayuda, vigente);
  contenedor.append(caja);

  if (!contexto.permisos.editaMargen) {
    const soloLectura = document.createElement('p');
    soloLectura.className = 'ayuda';
    soloLectura.textContent = 'Solo el administrador puede cambiar el margen.';
    caja.append(soloLectura);
    montarPlazo(caja, contexto);
    return;
  }

  const entrada = crearEntrada('number', 'minutos');
  entrada.classList.add('control');
  entrada.min = '10';
  entrada.step = '5';
  entrada.value = String(inicial);
  const campo = crearCampo('Nuevo margen', entrada);
  const guardar = crearBoton('Guardar margen', 'submit');
  guardar.className = 'btn btn-principal';
  const formulario = document.createElement('form');
  formulario.className = 'campo-margen';
  formulario.noValidate = true;
  formulario.append(campo.contenedor, guardar);
  const ok = crearAviso('status');
  const error = crearAviso('alert');
  caja.append(formulario, ok, error);
  montarPlazo(caja, contexto);

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    ok.textContent = '';
    error.textContent = '';
    campo.mostrarError('');
    const texto = entrada.value.trim();
    const pedido = texto === '' || !Number.isFinite(Number(texto)) ? null : Number(texto);
    let guardado: number | null = null;
    void guardando(guardar, async () => {
      // El mínimo lo valida el dominio (`MARGEN_INVALIDO`); acá solo se envía el valor.
      guardado = leerMargen(await contexto.api.actualizar<unknown>(RUTA, { minutos: pedido })) ?? pedido;
    }).then(
      (listo) => {
        if (listo && guardado !== null) {
          valor.textContent = String(guardado);
          ok.textContent = `Margen guardado: ${guardado} minutos.`;
        }
      },
      (causa: unknown) => {
        // El margen vigente sigue siendo el anterior.
        mostrarErrorEnFormulario(causa, error, [campo], contexto);
      },
    );
  });
}
