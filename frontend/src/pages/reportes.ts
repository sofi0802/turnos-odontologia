import { cargarCatalogos } from '../agenda/catalogos.ts';
import { consultarReportes } from '../reportes/consulta.ts';
import { errorDelPeriodo, semanaEnCurso, type PeriodoReporte } from '../reportes/periodo.ts';
import { dibujarReporte } from '../reportes/vista.ts';
import { ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import { hoy } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearAviso, crearCampo, crearEntrada, crearSelector, type Campo } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION = 'Esta sección es solo para administración y recepción.';

function campoFecha(etiqueta: string, nombre: string, valor: string): Campo<HTMLInputElement> {
  const campo = crearCampo(etiqueta, crearEntrada('date', nombre));
  campo.control.value = valor;
  usarSelectorFecha(campo);
  return campo;
}

/** Pantalla Reportes (US-210, C-32): período, filtro por profesional y los cuatro bloques. */
function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const semana = semanaEnCurso(hoy());
  const desde = campoFecha('Desde', 'desde', semana.desde);
  const hasta = campoFecha('Hasta', 'hasta', semana.hasta);
  const profesional = crearCampo('Profesional', crearSelector('profesionalId', [{ valor: '', texto: 'Todos' }]));
  const porCampo: Readonly<Record<string, { mostrarError(mensaje: string): void }>> = {
    desde,
    hasta,
    profesionalId: profesional,
  };

  const filtros = document.createElement('div');
  filtros.className = 'reportes-filtros';
  filtros.append(desde.contenedor, hasta.contenedor, profesional.contenedor);

  const error = crearAviso('alert');
  const resultado = document.createElement('div');
  resultado.className = 'reportes-resultado';
  resultado.setAttribute('aria-live', 'polite');

  contenedor.replaceChildren(crearTitulo('Reportes'), error, filtros, resultado);

  let consulta = 0;

  function limpiarErrores(): void {
    error.textContent = '';
    desde.mostrarError('');
    hasta.mostrarError('');
    profesional.mostrarError('');
  }

  async function cargar(): Promise<void> {
    limpiarErrores();
    const periodo: PeriodoReporte = { desde: desde.control.value, hasta: hasta.control.value };
    const invalido = errorDelPeriodo(periodo);
    if (invalido !== null) {
      porCampo[invalido.campo]?.mostrarError(invalido.mensaje);
      return;
    }
    consulta += 1;
    const esta = consulta;
    const profesionalId = profesional.control.value === '' ? null : Number(profesional.control.value);
    resultado.setAttribute('aria-busy', 'true');
    try {
      const reporte = await consultarReportes(contexto.api, periodo, profesionalId);
      if (esta === consulta) {
        dibujarReporte(resultado, reporte);
      }
    } catch (causa) {
      if (esta !== consulta) {
        return;
      }
      if (causa instanceof ErrorSesion) {
        contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
        return;
      }
      if (causa instanceof ErrorValidacion && causa.errores.length > 0) {
        for (const { campo, mensaje } of causa.errores) {
          const destino = porCampo[campo];
          if (destino === undefined) {
            error.textContent = mensaje;
          } else {
            destino.mostrarError(mensaje);
          }
        }
        return;
      }
      error.textContent = mensajeDeError(causa);
    } finally {
      if (esta === consulta) {
        resultado.removeAttribute('aria-busy');
      }
    }
  }

  for (const control of [desde.control, hasta.control, profesional.control]) {
    control.addEventListener('change', () => void cargar());
  }

  void cargarCatalogos(contexto.api).then((catalogos) => {
    const elegido = profesional.control.value;
    cambiarOpciones(profesional.control, [
      { valor: '', texto: 'Todos' },
      ...catalogos.profesionales.filter((p) => p.activo).map((p) => ({ valor: String(p.id), texto: p.nombre })),
    ]);
    profesional.control.value = elegido;
  });
  void cargar();
}

/** Reportes del consultorio: solo administración y recepción (RN-RE-01, delta de frontend-navegacion). */
export const paginaReportes: Pagina = {
  ruta: 'reportes',
  fondoSuave: true,
  titulo: 'Reportes',
  acceso: 'personal',
  menu: 'personal',
  icono: 'reportes',
  roles: ['ADMIN', 'RECEPCIONISTA'],
  avisoSinRol: MENSAJE_SOLO_ADMINISTRACION_Y_RECEPCION,
  render: dibujar,
};
