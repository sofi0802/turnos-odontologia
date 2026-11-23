# Tasks

Cada escenario de los specs tiene un test con el mismo nombre.

- **Frontend:** los comandos se corren desde `frontend/`. Antes de empezar, `npm test` pasa con 180 tests.
- **Backend:** los comandos se corren desde `backend/` con el PostgreSQL de desarrollo levantado.

**Gate de diseño:** no empezar el apply hasta que los mockups de `mockups/` tengan `Approval: approved` en su `README.md` y en la sección UI Mockups de `design.md`. Los mockups aprobados son el criterio de aceptación visual de cada pestaña. Las clases y los valores salen de `design-system/` Revision 1 (design D11).

## 1. Profesional del odontólogo en la sesión (`seguridad-autenticacion`)

- [ ] 1.1 Sumar `Long profesionalId` a `SesionResponse` y completarlo solo para `ODONTOLOGO`, desde el login y desde `GET /public/sesion` en `AutenticacionController` (design D4).
  - Escribir en `AutenticacionIntegrationTest` (`@Tag("integration")`) "Login de un odontólogo vinculado a un profesional", "Sesión de un odontólogo" y "Sesión de recepción sin profesional", con un usuario `ODONTOLOGO` ficticio vinculado a un profesional ficticio.
  - Verificar que `./gradlew test` y `./gradlew integrationTest` (o el task de integración del proyecto) siguen en verde.
- [ ] 1.2 Sumar `profesionalId: number | null` a `Sesion` en `src/shared/sesion/sesion.ts`, leerlo validando que sea número y dejarlo en `null` si falta.
  - Verificar en `test/sesion-api.test.ts` los tres casos: con número, sin el campo y con un valor que no es número.

## 2. Base de la página

- [ ] 2.1 Sumar a `MENSAJES_POR_CODIGO` los códigos de D10. Verificar en `test/mensajes.test.ts` que cada uno devuelve su texto.
- [ ] 2.2 Crear `src/configuracion/permisos.ts` con `permisos(sesion)` (D3). Verificar con un test unitario la tabla de permisos de `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`.
- [ ] 2.3 Crear `src/configuracion/pestanas.ts` (D2) y reemplazar `pages/configuracion.ts`. La página debe dibujar las pestañas del rol, cargar cada panel la primera vez que se muestra y aceptar las flechas del teclado.
  - Escribir en `test/configuracion-pestanas.test.ts` "Pestañas del administrador", "Recepción no ve los bloqueos" y "Cambiar de pestaña".
  - Quitar `paginaConTitulo` de `pagina.ts` si queda sin uso.
- [ ] 2.4 Sumar a `styles.css` las clases de D11, copiadas de los mockups aprobados y solo con variables `--ds-*`. Verificar que `npm run build` pasa.

## 3. Catálogos

- [ ] 3.1 Crear `src/configuracion/api.ts` con la lectura defensiva de profesionales, sillones y prestaciones (registro completo), y `src/configuracion/catalogo.ts` con la lista y el estado "Activo" o "Inactivo".
  - Escribir "Lista de prestaciones" y "Recepción y odontólogo solo leen los catálogos" en `test/configuracion-catalogos.test.ts`.
- [ ] 3.2 Agregar "Agregar", "Editar", "Activar" y "Desactivar" para `ADMIN`, con el `<dialog>` y el bloqueo del doble envío (D9).
  - Escribir "El administrador agrega un sillón", "El administrador edita un profesional", "El administrador desactiva una prestación", "Matrícula repetida", "Duración que no es múltiplo de 5", "Acción rechazada por permisos" y "Doble envío".
- [ ] 3.3 Mostrar el error y "Reintentar" cuando falla la carga de una lista. Escribir "Falla la carga de sillones".

## 4. Especialidades y sillón habitual

- [ ] 4.1 Crear `src/configuracion/profesional-detalle.ts` con las prestaciones del profesional y su sillón habitual. Escribir "Ver las especialidades" en `test/configuracion-catalogos.test.ts`.
- [ ] 4.2 Agregar para `ADMIN` las casillas de prestaciones activas y el selector de sillón habitual, con sus `PUT`.
  - Escribir "El administrador habilita una prestación" y "El administrador quita el sillón habitual".

## 5. Margen

- [ ] 5.1 Crear `src/configuracion/margen.ts`.
  - Escribir en `test/configuracion-margen.test.ts` "El administrador cambia el margen", "Margen menor que el mínimo" y "Recepción solo ve el margen".

## 6. Profesional elegido y horarios

- [ ] 6.1 Crear `src/configuracion/profesional-elegido.ts`. Para `ADMIN` y `RECEPCIONISTA` es un selector de profesionales activos. Para `ODONTOLOGO` es el profesional fijo de la sesión, o el aviso si no tiene uno.
  - Escribir "El odontólogo ve su propio horario" y "Odontólogo sin profesional vinculado" en `test/configuracion-agenda.test.ts`.
- [ ] 6.2 Crear `src/configuracion/horarios.ts`, con la vista por día y "No atiende". Escribir "Ver el horario de un profesional".
- [ ] 6.3 Agregar para `ADMIN` la edición local, "Guardar horario" y "Descartar cambios" (D5).
  - Escribir "El administrador agrega una franja" y "Franjas superpuestas".

## 7. Ausencias y reasignación

- [ ] 7.1 Crear `src/configuracion/ausencias.ts` con la lista. Escribir "El odontólogo solo ve sus ausencias" en `test/configuracion-ausencias.test.ts`.
- [ ] 7.2 Agregar para `ADMIN` y `RECEPCIONISTA` el registro con fechas en la zona del consultorio (D6) y el borrado con confirmación.
  - Escribir "Recepción registra una licencia" y "Fin anterior al inicio".
- [ ] 7.3 Mostrar los turnos afectados al registrar y con "Ver turnos afectados" (D7).
  - Escribir "Registrar una ausencia con turnos dados" y "Ver los turnos afectados de una ausencia ya registrada".
- [ ] 7.4 Crear `src/configuracion/reasignacion.ts` con el diálogo "Reasignar" y los profesionales habilitados (D8).
  - Escribir "Reasignar un turno afectado" y "El otro profesional no hace la prestación".

## 8. Bloqueos

- [ ] 8.1 Crear `src/configuracion/bloqueos.ts` con la lista, el registro y el borrado.
  - Escribir en `test/configuracion-bloqueos.test.ts` "El odontólogo bloquea su agenda", "Bloqueo que se cruza con turnos" y "Borrar un bloqueo".

## 9. Navegación y cierre

- [ ] 9.1 Revisar que los escenarios de "Páginas disponibles" de `test/navegacion.test.ts` siguen pasando con el texto modificado del spec (`frontend-navegacion`).
- [ ] 9.2 Verificar `test/responsivo.test.ts` (o sumar un caso): las pestañas se desplazan dentro de su contenedor y la página no tiene desplazamiento horizontal a 390 px.
- [ ] 9.3 Correr `npm test`, `npm run build` y `npm run lint` (si existe) en `frontend/`, y `./gradlew build` en `backend/`, todo en verde.
- [ ] 9.4 Hacer una prueba manual con el backend y el frontend de desarrollo como `admin`, `recepcion` y `ana.ejemplo`.
  - Hacer la verificación visual contra los mockups a 390 × 844 y 1440 × 900, con capturas en `mockups/verify/`.
  - Registrar el resultado en `design.md` (Visual verify).
- [ ] 9.5 Marcar C-34 en `CHANGES.md`. En el PR, avisar a Enzo (`frontend/`) y a Juan Sebastián (`usuarios/api`).
