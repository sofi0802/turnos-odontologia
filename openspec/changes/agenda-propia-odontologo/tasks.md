# Tasks

> Antes de la tarea 3.1, el mockup de `design.md` §UI Mockups tiene que estar aprobado por la líder.

## 1. API: agenda propia del odontólogo (agenda-api)

- [x] 1.1 Revisar los tests de integración de `agenda/api` que piden `GET /api/v1/turnos` con `ana.ejemplo` y confirmar que solo esperan turnos de la Dra. A. Si alguno espera turnos de otro profesional, pasarlo a `recepcion` sin cambiar lo que comprueba. Verificación: lista de tests revisados en el PR.
- [x] 1.2 Escribir primero los tests de integración (PostgreSQL, `@Tag("integration")`) de los escenarios nuevos de `agenda-api`: odontólogo sin filtro, odontólogo que pide al Dr. B, odontólogo con sillón compartido, y recepción y admin vinculado que ven todos. Deben fallar antes del cambio.
- [x] 1.3 En `TurnosController.agenda`, recibir el `UsuarioAutenticado` y resolver el `profesionalId` visible con una función pura: `ODONTOLOGO` usa el suyo, o lista vacía si no tiene; los otros roles usan el del pedido. Sumar un test unitario de esa función. Verificación: los tests de 1.2 y el unitario quedan en verde.

## 2. Sesión: profesionalId para el odontólogo (seguridad-autenticacion)

- [x] 2.1 Agregar `profesionalId` a `SesionResponse`, con valor solo para el rol `ODONTOLOGO`, en el login y en `GET /api/v1/public/sesion`. Agregar tests de integración de los escenarios "Sesión de un odontólogo" y "Sesión de recepción sin profesional". Verificación: los dos tests en verde y los tests de sesión existentes sin cambios.
- [x] 2.2 Correr `./gradlew build` en `backend/` (con los tests de integración) y guardar el resumen en `docs/evidencia/c35-backend.txt`. Verificación: build en verde.

## 3. Frontend: agenda fija del odontólogo (frontend-agenda-personal)

- [x] 3.1 Sumar `profesionalId?: number` (opcional) a `Sesion` y a `leerSesion` (solo para `ODONTOLOGO` y entero positivo), con tests en `test/sesion-api.test.ts`. Verificación: `npm test` en verde.
- [x] 3.2 En `pages/agenda.ts`, con `profesionalId` en la sesión: no crear el filtro "Profesional", enviar siempre ese `profesionalId`, conservar "Sillón" y titular "Mi agenda" con el nombre del profesional debajo (solo "Mi agenda" si el catálogo falla). Sin `any` y con `strict`. Tests en `test/agenda.test.ts` de los tres escenarios nuevos de `frontend-agenda-personal`. Verificación: `npm test` y `npm run build` en verde.
- [x] 3.3 Mostrar "Anterior" y "Siguiente" como flechas (SVG en línea con `aria-hidden`; `aria-label` y `title` con la palabra) y hacer que `boton()` de `test/apoyo.ts` también encuentre un botón por su `aria-label`. Agregar el test del escenario "Anterior y Siguiente como flechas". Verificación: `npm test` en verde, incluidos los tests de navegación del período ya existentes.
- [x] 3.4 Verificación visual contra el mockup aprobado: con el backend y Vite levantados, ingresar como `ana.ejemplo` y como `recepcion` y capturar 390x844 y 1440x900 en `mockups/verify/`. Revisar errores de consola y pedidos fallidos y anotar el resultado en `design.md` (`Visual verify`). Verificación: capturas guardadas y diferencias reportadas.

## Workflow follow-up

- Abrir el PR `change/C-35-agenda-propia-odontologo` a `master` con la KB (RN-AG-18) y el código, avisando a Enzo (`frontend/`) y a Juan Sebastián (`usuarios/api`). Coordinar el orden con C-33 y C-36.
- Después del merge: archivar el change y marcar C-35 `[x]` en `CHANGES.md`.
