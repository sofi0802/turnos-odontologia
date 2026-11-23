# Tasks

Cada escenario de las specs tiene un test con el mismo nombre. Comandos desde `frontend/`.

## 1. Cliente de API (`frontend-cliente-api`)

- [x] 1.1 En `src/shared/api/errores.ts`, sumar `ErrorCampo` y `ErrorValidacion.errores`, y hacer que `leerProblemDetail` lea `errores` validando su forma (design D1); actualizar en `test/cliente.test.ts` "Inicio en el pasado" y escribir "Datos inválidos con errores de campo"; verificar que pasan
- [x] 1.2 Sumar `ErrorSesion`, `ErrorPermiso` y `ErrorNoEncontrado` y traducir `401`, `403` y `404` en `cliente.ts`; escribir "Sin sesión", "Credenciales inválidas", "Acceso denegado" y "Turno inexistente", y verificar que siguen pasando "Error interno del servidor" y "Servidor inalcanzable"
- [x] 1.3 Verificar que `npm test` pasa con 18 tests en `cliente.test.ts` y que `npm run build` compila sin `any`

## 2. Fechas y sesión

- [x] 2.1 Crear `src/shared/fecha/zona.ts` (D2) con tests unitarios en `test/zona.test.ts` para el inicio del día y de la semana (lunes), `aIsoConZona` con `-03:00` y `formatearHora` de `2026-12-01T13:00:00Z` a "10:00"; verificar que pasan con la zona de la máquina en UTC (`TZ=UTC npm test`)
- [x] 2.2 Crear `src/shared/sesion/sesion.ts` (D1) con `consultarSesion`, `iniciarSesion`, `cerrarSesion` y `esPersonal`; verificar con tests unitarios que login pide la sesión antes si no existe la cookie `XSRF-TOKEN`

## 3. Ingreso y guarda de sesión (`frontend-sesion-personal`, `frontend-navegacion`)

- [x] 3.1 Crear `src/pages/ingreso.ts` (`#/ingreso`, fuera del menú); escribir en `test/sesion.test.ts` "Recepción ingresa con credenciales correctas", "Campos vacíos" y "Contraseña incorrecta", y en `test/navegacion.test.ts` "Abrir la página de ingreso por su dirección"; verificar que pasan
- [x] 3.2 Sumar `privada` a `Pagina` y la guarda en `app.ts` (D3); escribir "Abrir la agenda sin sesión" y "Un paciente abre la agenda"; ajustar los tests de navegación existentes para simular una sesión de `recepcion` y verificar que los 8 siguen pasando sin cambiar su nombre ni lo que comprueban
- [x] 3.3 Mostrar el usuario y el botón "Salir" en el encabezado y manejar `ErrorSesion`/`ErrorPermiso` (D3); escribir "La sesión vence mientras se usa la agenda", "Acción sin permiso" y "Recepción cierra la sesión"; verificar que los 8 escenarios de `frontend-sesion-personal` pasan

## 4. Agenda: consulta y vista (`frontend-agenda-personal`)

- [x] 4.1 Crear `agenda/periodo.ts` y `agenda/consulta.ts` (día/semana, Anterior/Hoy/Siguiente, query con filtros); escribir "Cambiar a la vista semanal", "Ir al día siguiente" y "Volver a hoy" con `vi.setSystemTime` en 2026-12-01; verificar que pasan
- [x] 4.2 Crear `agenda/catalogos.ts` con la lectura de los catálogos de `catalogo-consultorio` y el respaldo por id (D4); escribir "Catálogos no disponibles"; verificar que pasa
- [x] 4.3 Crear `agenda/vista.ts` y la página `pages/agenda.ts` con lista por día, agrupado semanal y etiquetas; escribir "Ver los turnos de hoy", "Día sin turnos", "Hora mostrada en la zona del consultorio", "Turno confirmado", "Sobreturno" y "Turno común sin etiquetas"; verificar que pasan
- [x] 4.4 Sumar los filtros "Profesional" y "Sillón"; escribir "Filtrar por profesional" y "Filtrar por sillón y profesional a la vez"; verificar que pasan

## 5. Agenda: formularios (`frontend-agenda-personal`)

- [x] 5.1 Crear `agenda/mensajes.ts` (D6) y el polyfill de `<dialog>` en `test/setup.ts` (D5); verificar con un test unitario que cada código conocido devuelve su texto y uno desconocido devuelve el `detail`
- [x] 5.2 Crear el formulario "Nuevo turno"; escribir "Recepción crea un turno", "Faltan datos en el formulario", "Solapamiento con el profesional", "Margen insuficiente", "Sillón ocupado", "Inicio en el pasado", "Recurso inactivo", "Fuera del horario del profesional", "Profesional ausente", "Agenda bloqueada", "Código sin mensaje propio" y "Profesional inexistente"; verificar que pasan
- [x] 5.3 Crear el formulario "Cancelar turno"; escribir "Recepción cancela un turno", "Turno ya cancelado" y "Turno que ya no existe"; verificar que pasan
- [x] 5.4 Crear el formulario "Reprogramar"; escribir "Recepción reprograma un turno" y "Reprogramación rechazada por el sillón"; verificar que pasan

## 6. Responsivo y verificación integral

- [x] 6.1 Ajustar `styles.css` mobile first (una columna, controles de 44 px, formularios a todo el ancho); escribir en jsdom la parte comprobable de "Agenda en el celular" y "Agenda en la computadora"
- [x] 6.2 Levantar `npm run dev` con la API simulada o el backend local (perfil `dev`, con turnos dentro de las franjas del seed: Ana Ejemplo lunes a viernes de 8 a 13, Bruno Prueba de 10 a 18), capturar la agenda (día, semana y formulario abierto) a 390 × 844 y 1440 × 900 en un navegador real, guardar las capturas en `openspec/changes/frontend-agenda-personal/verify/` y verificar que no hay desplazamiento horizontal ni errores en la consola
- [x] 6.3 Correr `npm run build` y `npm test` y verificar que pasan todos los tests: los 21 previos más uno por cada escenario nuevo (8 de sesión, 31 de agenda, 5 de cliente y 1 de navegación), con el nombre igual al escenario
- [x] 6.4 Verificar que `grep -rnE ":\s*any\b|as any" frontend/src frontend/test` no devuelve resultados, que no hay frameworks en `package.json` y que tests y capturas usan solo datos ficticios
- [x] 6.5 Correr `openspec validate frontend-agenda-personal --strict` y verificar que es válido

## Notas de implementación

- `mensajes.ts` quedó en `src/shared/ui/` (no en `agenda/`) porque también lo usa la página de ingreso; los módulos de agenda están en `src/agenda/`.
- La spec citaba "Dra. Ana Ejemplo", pero el catálogo de C-04 solo trae `nombre` y `apellido`: se corrigió el texto de los escenarios a "Ana Ejemplo", como ya decía design D4.
- Orden de TDD: `mensajes.ts` se escribió en el grupo 3 (lo necesitaba el ingreso) y los escenarios de la 3.3 se escribieron después de la agenda, porque dependen de ella. Esos tests pasaron sin una etapa roja; se comprobó que no son triviales rompiendo a propósito la redirección por sesión vencida y el `POST /logout` (los dos fallaron y se restauró el código).
- Vitest deja vacío el CSS aunque se importe con `?raw`; `vite.config.ts` habilita el procesamiento solo para `styles.css` (`test.css.include`) para los tests de diseño responsivo.
- Capturas (6.2) con Playwright, contra el servidor de desarrollo y una API simulada en la página (sin backend): `verify/390-*.png` y `verify/1440-*.png`. Sin desplazamiento horizontal en los dos anchos. El único error de consola es el `404` del primer `GET /public/sesion`, anterior a la simulación, porque no había backend.
- En la verificación visual se corrigió `text-transform: capitalize` del período ("Semana Del Lunes…"): ahora solo va en mayúscula la primera letra.

## Workflow follow-up

- Abrir el PR de `change/C-09-frontend-agenda-personal` a `master` con la evidencia y marcar `[x]` en C-09 de `CHANGES.md`.
- Archivar el change después de que la líder lo revise y lo mergee.
