import { montarAusencias } from '../configuracion/ausencias.ts';
import { montarBloqueos } from '../configuracion/bloqueos.ts';
import { montarPrestaciones, montarProfesionales, montarSillones } from '../configuracion/catalogo.ts';
import { montarHorarios } from '../configuracion/horarios.ts';
import { montarMargen } from '../configuracion/margen.ts';
import { permisos } from '../configuracion/permisos.ts';
import { montarUsuarios } from '../configuracion/usuarios.ts';
import { crearPestanas, type Pestana } from '../configuracion/pestanas.ts';
import { esErrorDeSesion, type ContextoSeccion, type MontarSeccion } from '../configuracion/ui.ts';
import { MENSAJE_SESION_TERMINADA } from './agenda.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const permisosDelRol = permisos(contexto.sesion);
  const seccion: ContextoSeccion = {
    api: contexto.api,
    sesion: contexto.sesion,
    permisos: permisosDelRol,
    sesionVencida(causa) {
      if (esErrorDeSesion(causa)) {
        contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
        return true;
      }
      return false;
    },
  };

  const definiciones: Array<readonly [string, MontarSeccion]> = [
    ['Profesionales', montarProfesionales],
    ['Sillones', montarSillones],
    ['Prestaciones', montarPrestaciones],
    ['Margen', montarMargen],
    ['Horarios', montarHorarios],
    ['Ausencias', montarAusencias],
  ];
  // Los tres roles ven los bloqueos; registrar y borrar es solo de ADMIN y ODONTOLOGO (design D3).
  if (permisosDelRol.veBloqueos) {
    definiciones.push(['Bloqueos', montarBloqueos]);
  }
  // Las cuentas del personal son solo del administrador (C-41).
  if (permisosDelRol.gestionaUsuarios) {
    definiciones.push(['Usuarios', montarUsuarios]);
  }
  const pestanas: Pestana[] = definiciones.map(([titulo, montar]) => ({
    titulo,
    montar: (panel) => montar(panel, seccion),
  }));

  contenedor.classList.add('pagina-config');
  contenedor.append(crearTitulo('Configuración'), crearPestanas(pestanas, 'Secciones de configuración'));
}

/** Configuración del consultorio para el personal (C-34). */
export const paginaConfiguracion: Pagina = {
  ruta: 'configuracion',
  fondoSuave: true,
  titulo: 'Configuración',
  acceso: 'personal',
  menu: 'personal',
  icono: 'configuracion',
  render: dibujar,
};
