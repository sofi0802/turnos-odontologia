# Tasks

Cada escenario de los specs tiene un test con el mismo nombre.

Los comandos se corren desde `frontend/`. Antes de empezar, `npm test` pasa con 264 tests (`master` en `cf63e2a`, con C-33, C-35 y C-36). El `profesionalId` de la sesión lo agregó C-35. Las secciones 1 a 8 son la primera versión, que no cambia el backend. Las secciones 9 a 12 son los ajustes de la segunda prueba (2026-11-23): la 9 cambia el backend y se corre desde `backend/`.

**Gate de diseño:** no empezar el apply hasta que los mockups de `mockups/` tengan `Approval: approved` en su `README.md` y en la sección UI Mockups de `design.md`. Los mockups aprobados son el criterio de aceptación visual de cada pestaña. Las clases y los valores salen de `design-system/` Revision 1 (design D11).

## 1. Base de la página

- [x] 1.1 Sumar a `MENSAJES_POR_CODIGO` los códigos de D10. Verificar en `test/mensajes.test.ts` que cada uno devuelve su texto.
- [x] 1.2 Crear `src/configuracion/permisos.ts` con `permisos(sesion)` (D3). Verificar con un test unitario la tabla de permisos de `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`.
- [x] 1.3 Crear `src/configuracion/pestanas.ts` (D2) y reemplazar `pages/configuracion.ts`. La página debe dibujar las pestañas del rol, cargar cada panel la primera vez que se muestra y aceptar las flechas del teclado.
  - Escribir en `test/configuracion-pestanas.test.ts` "Pestañas del administrador", "Recepción no ve los bloqueos", "Cambiar de pestaña" y "Abrir Configuración sin sesión" (la página pasa a `acceso: 'personal'`).
  - Quitar `paginaConTitulo` de `pagina.ts` si queda sin uso.
- [x] 1.4 Sumar a `styles.css` las clases de D11, copiadas de los mockups aprobados y solo con variables `--ds-*`. Verificar que `npm run build` pasa.

## 2. Catálogos

- [x] 2.1 Crear `src/configuracion/api.ts` con la lectura defensiva de profesionales, sillones y prestaciones (registro completo), y `src/configuracion/catalogo.ts` con la lista y el estado "Activo" o "Inactivo".
  - Escribir "Lista de prestaciones" y "Recepción y odontólogo solo leen los catálogos" en `test/configuracion-catalogos.test.ts`.
- [x] 2.2 Agregar "Agregar", "Editar", "Activar" y "Desactivar" para `ADMIN`, con el `<dialog>` y el bloqueo del doble envío (D9).
  - Escribir "El administrador agrega un sillón", "El administrador edita un profesional", "El administrador desactiva una prestación", "Matrícula repetida", "Duración que no es múltiplo de 5", "Acción rechazada por permisos" y "Doble envío".
- [x] 2.3 Mostrar el error y "Reintentar" cuando falla la carga de una lista. Escribir "Falla la carga de sillones".

## 3. Especialidades y sillón habitual

- [x] 3.1 Crear `src/configuracion/profesional-detalle.ts` con las prestaciones del profesional y su sillón habitual. Escribir "Ver las especialidades" en `test/configuracion-catalogos.test.ts`.
- [x] 3.2 Agregar para `ADMIN` las casillas de prestaciones activas y el selector de sillón habitual, con sus `PUT`.
  - Escribir "El administrador habilita una prestación" y "El administrador quita el sillón habitual".

## 4. Margen

- [x] 4.1 Crear `src/configuracion/margen.ts`.
  - Escribir en `test/configuracion-margen.test.ts` "El administrador cambia el margen", "Margen menor que el mínimo" y "Recepción solo ve el margen".

## 5. Profesional elegido y horarios

- [x] 5.1 Crear `src/configuracion/profesional-elegido.ts`. Para `ADMIN` y `RECEPCIONISTA` es un selector de profesionales activos. Para `ODONTOLOGO` es el profesional fijo de `sesion.profesionalId` (de C-35), o el aviso si no tiene uno.
  - Escribir "El odontólogo ve su propio horario" y "Odontólogo sin profesional vinculado" en `test/configuracion-agenda.test.ts`.
- [x] 5.2 Crear `src/configuracion/horarios.ts`, con la vista por día y "No atiende". Escribir "Ver el horario de un profesional".
- [x] 5.3 Agregar para `ADMIN` la edición local, "Guardar horario" y "Descartar cambios" (D5).
  - Escribir "El administrador agrega una franja" y "Franjas superpuestas".

## 6. Ausencias y reasignación

- [x] 6.1 Crear `src/configuracion/ausencias.ts` con la lista. Escribir "El odontólogo solo ve sus ausencias" en `test/configuracion-ausencias.test.ts`.
- [x] 6.2 Agregar para `ADMIN` y `RECEPCIONISTA` el registro con fechas en la zona del consultorio (D6) y el borrado con confirmación.
  - Escribir "Recepción registra una licencia" y "Fin anterior al inicio".
- [x] 6.3 Mostrar los turnos afectados al registrar y con "Ver turnos afectados" (D7).
  - Escribir "Registrar una ausencia con turnos dados" y "Ver los turnos afectados de una ausencia ya registrada".
- [x] 6.4 Crear `src/configuracion/reasignacion.ts` con el diálogo "Reasignar" y los profesionales habilitados (D8).
  - Escribir "Reasignar un turno afectado" y "El otro profesional no hace la prestación".

## 7. Bloqueos

- [x] 7.1 Crear `src/configuracion/bloqueos.ts` con la lista, el registro y el borrado.
  - Escribir en `test/configuracion-bloqueos.test.ts` "El odontólogo bloquea su agenda", "Bloqueo que se cruza con turnos" y "Borrar un bloqueo".

## 8. Navegación y cierre

- [x] 8.1 Revisar que los escenarios de "Páginas disponibles" de `test/navegacion.test.ts` siguen pasando con el texto modificado del spec (`frontend-navegacion`).
- [x] 8.2 Verificar `test/responsivo.test.ts` (o sumar un caso): las pestañas se desplazan dentro de su contenedor y la página no tiene desplazamiento horizontal a 390 px.
- [x] 8.3 Correr `npm test`, `npm run build` y `npm run lint` (si existe) en `frontend/`, todo en verde.
- [x] 8.4 Hacer una prueba manual con el backend y el frontend de desarrollo como `admin`, `recepcion` y `ana.ejemplo`.
  - Hacer la verificación visual contra los mockups a 390 × 844 y 1440 × 900, con capturas en `mockups/verify/`.
  - Registrar el resultado en `design.md` (Visual verify).
- [x] 8.5 Marcar C-34 en `CHANGES.md`. En el PR, avisar a Enzo (`frontend/`).

## 9. Backend de los ajustes (desde `backend/`)

Antes de empezar, `./gradlew test` y `./gradlew integrationTest` pasan. Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`. Cada escenario de los deltas `catalogo-consultorio` y `disponibilidad-profesional` tiene un test con su nombre en `@DisplayName`.

- [ ] 9.1 Sillón habitual exclusivo (D15): agregar `existsBySillonHabitualIdAndIdNot` al repositorio y la validación en `ProfesionalService.asignarSillonHabitual`, con el error `SILLON_HABITUAL_OCUPADO` (`409`, `ProblemDetail`).
  - Escribir primero los tests y verlos fallar: "Sillón habitual de otro profesional", "Sillón habitual de un profesional inactivo", "Volver a asignar el mismo sillón" y "Liberar un sillón y asignarlo a otro profesional", en `EspecialidadesYSillonHabitualIntegrationTest` y en un test unitario del servicio.
  - Los escenarios anteriores del requisito siguen pasando.
- [ ] 9.2 `ServicioReasignacion.reasignar` recibe la `Clase` del período (D16). `AusenciasController` pasa `Clase.AUSENCIA`, y los tests de `ReasignacionIntegrationTest` siguen pasando sin cambios.
- [ ] 9.3 `BloqueosController` con un `@PreAuthorize` por método y el endpoint `POST /{bloqueoId}/turnos/{turnoId}/reasignar` (D16).
  - Escribir "La recepción consulta los bloqueos", "La recepción registra un bloqueo" y "La recepción borra un bloqueo" en `AusenciasYBloqueosIntegrationTest`, reemplazando el test anterior que esperaba `403` en la consulta.
  - Escribir "Reasignar a otro profesional el turno de un bloqueo", "Turno que no está afectado por el bloqueo", "El nuevo horario también cae en el bloqueo", "Bloqueo inexistente" y "El odontólogo intenta reasignar el turno de su bloqueo" en `ReasignacionIntegrationTest`.
- [ ] 9.4 Correr `./gradlew build` y `./gradlew integrationTest`, todo en verde.

## 10. Mockups revisión 2 (gate de diseño)

- [x] 10.1 Actualizar los mockups:
  - `profesionales.html`: el detalle espaciado, con el sillón ocupado deshabilitado y "(de X)".
  - `profesional-formulario.html`: el alta con prestaciones y sillón, y el aviso de falla parcial.
  - `horarios.html`: "Agregar franja" con casillas de días y la hora en dos selectores.
  - `ausencias.html`: "Todos los profesionales" con la columna Profesional, y el formulario con el selector de profesional y la hora de 5 en 5.
  - `bloqueos-odontologo.html`: los turnos afectados con "Avisale a la recepción para reasignarlos.".
  - `reasignar.html`: el inicio de 5 en 5.
  - Agregar `bloqueos.html`, con la vista de recepción y de "Todos" y los turnos afectados con "Reasignar".
  - Actualizar `mockups/README.md` con `Revision: 2` y `Approval: pending`.
- [x] 10.2 Renderizar los mockups a 390 × 844 y 1440 × 900 en `mockups/previews/`, revisarlos y mostrárselos a Emanuel.
- [x] 10.3 Esperar la aprobación explícita y registrar `Approval: approved` de la revisión 2 en `mockups/README.md` y en `design.md`. Las tareas 11.x no empiezan antes.

## 11. Frontend de los ajustes (desde `frontend/`)

- [x] 11.1 `crearSelectorHora` en `src/configuracion/ui.ts` (D13), usado en horarios, ausencias, bloqueos y reasignación; el margen con `step="5"`.
  - Escribir "Los minutos avanzan de 5 en 5" y "Minutos del inicio de una ausencia".
  - Adaptar a la hora en dos selectores los tests existentes que completan horas.
- [x] 11.2 "Agregar franja" con casillas de días. Escribir "Una franja para varios días" y "Agregar franja sin días".
- [x] 11.3 Sillones ocupados en el detalle y el espaciado de D11.
  - Sumar `SILLON_HABITUAL_OCUPADO` y el nuevo texto de `TURNO_NO_AFECTADO` a `mensajes.ts`, con su test.
  - Escribir "El sillón de otro profesional no se puede elegir" y "El sillón lo tomó otro profesional mientras tanto".
- [x] 11.4 Alta completa del profesional (D14). Escribir "Alta con prestaciones y sillón", "Alta sin prestaciones ni sillón" y "Falla el sillón después del alta".
- [ ] 11.5 "Todos los profesionales" en Ausencias y Bloqueos (D12).
  - Escribir "Ausencias de todos los profesionales", "Bloqueos de todos los profesionales", "Registrar con todos los profesionales elegidos", "El odontólogo no ve la opción de todos" y "Falla la carga de un profesional en la vista de todos".
- [ ] 11.6 Bloqueos para recepción y la reasignación por bloqueo (D3, D7 y D8).
  - Actualizar `permisos.ts` y su test.
  - Reemplazar "Recepción no ve los bloqueos" por "Recepción ve los bloqueos sin poder registrarlos".
  - Escribir "Bloqueo que se cruza con turnos" (actualizado), "El administrador reasigna desde un bloqueo nuevo", "Recepción ve los turnos afectados de un bloqueo" y "Reasignar un turno afectado por un bloqueo".

## 12. Cierre de los ajustes

- [ ] 12.1 Correr `npm test`, `npm run build` y `npm run lint` (si existe) en `frontend/`, y `./gradlew build` e `integrationTest` en `backend/`, todo en verde.
- [ ] 12.2 Hacer la prueba manual como `admin`, `recepcion` y `ana.ejemplo` y la verificación visual contra la revisión 2 de los mockups, con capturas en `mockups/verify/`. Registrar Visual verify en `design.md`.
- [ ] 12.3 Actualizar el PR #60. La descripción tiene que señalar a Sofia el cambio de autorización de bloqueos (D16) y avisarle a Enzo por `frontend/`. Marcar C-34 en `CHANGES.md`.
