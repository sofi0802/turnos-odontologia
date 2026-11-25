import { listarNomenclador, type CodigoNomenclador } from '../coberturas/api.ts';
import { abrirDialogoDesde, marcarErrores, type CampoFormulario } from '../coberturas/ui.ts';
import type { ContextoSeccion } from '../configuracion/ui.ts';
import { leerPorcentaje } from '../presupuestos/modelo.ts';
import { crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { cambiarPorcentaje, listarCoberturaPlan } from './api.ts';

/**
 * Diálogo "Cobertura" de un plan (C-50, design D2): los códigos activos del nomenclador con el porcentaje que cubre el
 * plan. Guarda solo los que cambiaron, uno por uno, y si uno falla dice qué ya quedó guardado. No borra: se carga 0.
 */

export const MENSAJE_COBERTURA_GUARDADA = 'Cobertura guardada.';

interface FilaPorcentaje {
  readonly codigo: CodigoNomenclador;
  readonly campo: Campo<HTMLInputElement>;
  /** El porcentaje guardado en el backend, o `null` si no tiene. Se actualiza al guardar cada uno. */
  guardado: number | null;
}

export interface OpcionesCoberturaPlan {
  readonly origen: HTMLButtonElement;
  readonly obraSocial: { readonly id: number; readonly nombre: string };
  readonly plan: { readonly id: number; readonly nombre: string };
  readonly contexto: ContextoSeccion;
  /** Se llama cuando todo quedó guardado y el diálogo se cerró. */
  alGuardar(): void;
}

function campoPorcentaje(codigo: CodigoNomenclador, valor: number | null): Campo<HTMLInputElement> {
  const entrada = crearEntrada('text', `porcentaje-${codigo.id}`);
  entrada.classList.add('control');
  entrada.inputMode = 'numeric';
  entrada.autocomplete = 'off';
  entrada.value = valor === null ? '' : String(valor);
  const campo = crearCampo('Porcentaje', entrada);
  // Se ve "Porcentaje"; el lector de pantalla también oye el código.
  const label = campo.contenedor.querySelector('label');
  const oculto = document.createElement('span');
  oculto.className = 'solo-lectores';
  oculto.textContent = ` de ${codigo.codigo}`;
  label?.append(oculto);
  const caja = document.createElement('div');
  caja.className = 'pre-porcentaje';
  const signo = document.createElement('span');
  signo.setAttribute('aria-hidden', 'true');
  signo.textContent = '%';
  entrada.replaceWith(caja);
  caja.append(entrada, signo);
  return campo;
}

function dibujarFila(fila: FilaPorcentaje): HTMLLIElement {
  const item = document.createElement('li');
  item.className = 'pre-cob-fila';
  const quien = document.createElement('div');
  const titulo = document.createElement('p');
  titulo.className = 'pre-cob-codigo';
  const codigo = document.createElement('span');
  codigo.className = 'cob-codigo';
  codigo.textContent = fila.codigo.codigo;
  titulo.append(codigo, ` ${fila.codigo.descripcion}`);
  quien.append(titulo);
  if (fila.guardado === null) {
    const nota = document.createElement('p');
    nota.className = 'pre-cob-nota';
    nota.textContent = 'No cubre (0 %)';
    quien.append(nota);
  }
  item.append(quien, fila.campo.contenedor);
  return item;
}

export function abrirCoberturaPlan(opciones: OpcionesCoberturaPlan): void {
  const { contexto } = opciones;
  const lista = document.createElement('ul');
  lista.className = 'pre-cob-lista';
  lista.setAttribute('aria-label', `Cobertura de ${opciones.plan.nombre} por código`);
  lista.append(crearCargando('Cargando la cobertura…'));
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Números enteros de 0 a 100. Para dejar de cubrir un código, cargá 0. Los presupuestos ya emitidos no cambian.';
  const dialogo = abrirDialogoDesde(opciones.origen, {
    titulo: `Cobertura de ${opciones.plan.nombre}`,
    resumen: `${opciones.obraSocial.nombre}. Porcentaje del precio particular que cubre el plan por código. Un código vacío no está cubierto (0 %).`,
    contenido: [lista, ayuda],
    textoDescartar: 'Cancelar',
    textoPrincipal: 'Guardar',
  });
  dialogo.enviar.disabled = true;
  let filas: FilaPorcentaje[] = [];

  Promise.all([listarNomenclador(contexto.api), listarCoberturaPlan(contexto.api, opciones.obraSocial.id, opciones.plan.id)]).then(
    ([codigos, cargados]) => {
      const porId = new Map(cargados.map((c) => [c.nomencladorId, c.porcentaje]));
      filas = codigos
        .filter((c) => c.activo)
        .map((codigo) => {
          const guardado = porId.get(codigo.id) ?? null;
          return { codigo, campo: campoPorcentaje(codigo, guardado), guardado };
        });
      if (filas.length === 0) {
        const vacio = document.createElement('li');
        vacio.className = 'pre-cob-fila';
        vacio.textContent = 'No hay códigos activos en el nomenclador.';
        lista.replaceChildren(vacio);
        return;
      }
      lista.replaceChildren(...filas.map(dibujarFila));
      dialogo.enviar.disabled = false;
      filas[0]?.campo.control.focus();
    },
    (causa: unknown) => {
      if (!contexto.sesionVencida(causa)) {
        lista.replaceChildren();
        dialogo.general.textContent = mensajeDeError(causa);
      }
    },
  );

  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    dialogo.general.textContent = '';
    const lecturas = filas.map((fila) => ({ fila, leido: leerPorcentaje(fila.campo.control.value, fila.guardado !== null) }));
    const errores: [CampoFormulario, string][] = lecturas.map(({ fila, leido }) => [fila.campo, 'error' in leido ? leido.error : '']);
    if (!marcarErrores(errores) || dialogo.enviar.disabled) {
      return;
    }
    const cambios = lecturas.filter(
      (l): l is { fila: FilaPorcentaje; leido: { porcentaje: number } } => 'porcentaje' in l.leido && l.leido.porcentaje !== l.fila.guardado,
    );
    if (cambios.length === 0) {
      dialogo.cerrar();
      return;
    }
    dialogo.enviar.disabled = true;
    void (async () => {
      const hechos: string[] = [];
      for (const { fila, leido } of cambios) {
        try {
          await cambiarPorcentaje(contexto.api, opciones.obraSocial.id, opciones.plan.id, fila.codigo.id, leido.porcentaje);
          fila.guardado = leido.porcentaje;
          hechos.push(fila.codigo.codigo);
        } catch (causa) {
          dialogo.enviar.disabled = false;
          if (contexto.sesionVencida(causa)) {
            return;
          }
          const faltan = cambios.filter((c) => c.fila.guardado !== c.leido.porcentaje).map((c) => c.fila.codigo.codigo);
          const parcial = hechos.length === 0 ? '' : ` Se guardó ${hechos.join(', ')}; falta ${faltan.join(', ')}.`;
          dialogo.general.textContent = `${mensajeDeError(causa)}${parcial}`;
          return;
        }
      }
      dialogo.enviar.disabled = false;
      dialogo.cerrar();
      opciones.alGuardar();
    })();
  });
}
