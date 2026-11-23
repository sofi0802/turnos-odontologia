# Tasks

> Governance **ALTO** (datos personales de pacientes): no empezar el grupo 1 hasta que la líder apruebe `proposal.md`, los specs, `design.md` (incluidas las decisiones D1 a D12, resueltas el 2026-11-23) y los mockups de `mockups/`. Strict TDD: en cada tarea de comportamiento, primero el test en rojo (RED), después el mínimo código para pasarlo (GREEN) y después el refactor con los tests en verde. Cada test de escenario lleva el nombre del escenario del spec: `@DisplayName` en JUnit y el título de `it(...)` en Vitest. Solo datos ficticios ("Paciente Uno" `99000001`, "Paciente Dos" `99000002`, "Carla Ficticia" `99000003`, "Julián Ejemplar" `99000004`, emails `@example.com`). Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`.

## 0. Aprobación previa

- [x] 0.1 (hecho: sección "Resolución de decisiones (2026-11-23)" en `design.md`, mockups aprobados, `openspec validate --strict` válido) Registrar en el PR o en `design.md` la respuesta de la líder a cada decisión *(a confirmar)* (D1 a D11) y la aprobación de los mockups (`Approval: approved (<fecha>, by user)` en `design.md` y `mockups/README.md`). Si alguna cambia, sobre todo D7 (V20) o D11 (sobreturno), actualizar specs, mockups y estas tareas con `openspec-update-change` antes de seguir. Verificar con `openspec validate buscador-pacientes --strict`
- [x] 0.2 (hecho: C-09 archivado como `2026-11-23-frontend-agenda-personal`, el MODIFIED coincide con el spec principal; el acuerdo de orden de PR figuró en la descripción del PR #53 y se repite en el PR de apply) Confirmar que C-09 (`frontend-agenda-personal`) está archivado o tiene fecha de archive antes de la de C-36 (gate 5b). Acordar con Juan Cruz (C-33) y Sofia (C-35) el orden de los PR que tocan `formularios.ts`, `pages/agenda.ts` y `styles.css`. Verificar que el acuerdo figura en la descripción del PR
- [x] 0.3 (hecho: aviso a Enzo en la descripción del PR #53; se repite en el PR de apply) Avisar en el PR a Enzo (dueño de `frontend/`: `agenda/formularios.ts`, `styles.css`, archivo nuevo `agenda/buscador-pacientes.ts`). Verificar que el aviso figura en la descripción del PR. No hace falta avisar a Juan Cruz por `PacienteEntity`, porque no se toca (design D8)

## 1. Línea de base

- [x] 1.1 (baseline: backend 445 unitarios + 442 de integración = 887 en verde, frontend 180 en verde, 0 fallas) Safety net: correr `./gradlew test integrationTest` en `backend/` y `npm test` en `frontend/` en la rama sin cambios, y anotar el baseline (cantidad de tests en verde). Si algo falla, frenar y reportarlo como falla previa

## 2. Dominio y aplicación de la búsqueda (spec `busqueda-pacientes`)

- [x] 2.1 RED → GREEN (unitario): `TerminoBusquedaTest` en `usuarios/domain` para `TerminoBusqueda` (design D2 a D4). Casos: dígitos → DNI; letras → palabras normalizadas sin tildes y en minúsculas ("JULIÁN" → "julian", "Ñ" → "n"); recorte de los espacios de los extremos; menos de 2 caracteres, menos de 3 dígitos, más de 50 y vacío → `BusquedaInvalidaException`; `%` y `_` escapados; `toString()` sin el término. Verificar con `./gradlew test --tests '*TerminoBusquedaTest'` y con `grep` que `usuarios/domain` no importa `org.springframework` ni `jakarta.persistence`
- [x] 2.2 RED → GREEN (unitario): `BuscarPacientesTest` con un puerto `BusquedaPacientes` en memoria en `src/test`. Casos: delega por DNI o por texto según el término y no devuelve más de 10 resultados. Implementar `BuscarPacientes` y el record `PacienteResumen` con `toString()` sin datos. Verificar con `./gradlew test --tests '*BuscarPacientesTest'`

## 3. Adaptador JDBC y orden (spec `busqueda-pacientes`)

- [x] 3.1 RED: `BusquedaPacientesJdbcIntegrationTest` (`@Tag("integration")`) con los cuatro pacientes ficticios y 12 pacientes "Prueba". Casos: DNI por prefijo ordenado por DNI; texto por cada palabra en nombre o apellido sin mayúsculas ni tildes; orden por apellido, nombre e id; `LIMIT 10`; `%%` sin resultados. Verificar que falla porque el adaptador no existe
- [x] 3.2 GREEN: `BusquedaPacientesJdbc` en `usuarios/infrastructure` con `JdbcTemplate`, `SELECT id, nombre, apellido, dni` explícito, `translate(lower(...), 'áéíóúüñ', 'aeiouun')`, `LIKE ... ESCAPE '\'` y parámetros, sin concatenar el término (design D3 y D8). Verificar que el test 3.1 pasa y que `RestriccionesTurnoIntegrationTest` sigue en verde
- [x] 3.3 (sin V20 por D7) Si la líder pidió el índice en D7: RED → GREEN `V20MigrationIntegrationTest` (extensión `pg_trgm` e índice GIN creados) y `V20__busqueda_pacientes.sql`. Verificar con `./gradlew integrationTest`. Si D7 queda sin índice, marcar esta tarea como hecha anotando "sin V20 por D7"

## 4. Endpoint `GET /api/v1/pacientes` (spec `busqueda-pacientes`)

- [x] 4.1 RED: en `BusquedaPacientesIntegrationTest` (`@Tag("integration")`, extiende `IntegracionSeguridadBase`, `MockMvc` con sesión real), escribir "Búsqueda por DNI exacto", "Búsqueda por el comienzo del DNI" y "DNI sin coincidencias". Verificar que fallan
- [x] 4.2 GREEN: `PacientesController` en `usuarios/api` (`@GetMapping` con `@RequestParam(required = false) String q`) y el record `PacienteResumenResponse(id, nombre, apellido, dni)`. Verificar que los 3 escenarios pasan
- [x] 4.3 RED → GREEN: "Búsqueda por parte del apellido", "Búsqueda por nombre y apellido juntos", "Sin distinguir mayúsculas ni tildes", "Nombre sin coincidencias" y "Los comodines se buscan como texto". Verificar que pasan
- [x] 4.4 RED → GREEN: "La respuesta no trae otros datos" (conjunto exacto de propiedades `id`, `nombre`, `apellido` y `dni`, y el cuerpo sin `paciente.uno@example.com`), "Más coincidencias que el límite" y "Orden por apellido y nombre". Verificar que pasan
- [x] 4.5 RED → GREEN: "Falta el término", "Término de una sola letra", "DNI demasiado corto" y "Término demasiado largo". `UsuariosExceptionHandler` traduce `BusquedaInvalidaException` y `MissingServletRequestParameterException` a `422 DATOS_INVALIDOS` con `errores` del campo `q` y sin repetir `q` (design D4). Verificar que pasan y que los tests de `UsuariosExceptionHandlerTest` y `RegistroPacienteIntegrationTest` siguen en verde
- [x] 4.6 RED → GREEN: "El odontólogo busca un paciente", "El administrador busca un paciente", "Un paciente intenta buscar pacientes" y "Búsqueda sin sesión", sin cambios en `SecurityConfig` (design D1). Verificar que pasan y que `AutorizacionIntegrationTest` sigue en verde
- [x] 4.7 RED → GREEN: "La búsqueda no deja el DNI en los logs" (con `OutputCaptureExtension`) y "La respuesta no se guarda en caché" (`Cache-Control` con `no-store`). Verificar que pasan y, con `grep` en el código nuevo de `backend/src/main`, que ningún `LOG.` recibe el término ni los resultados

## 5. Componente buscador (spec `frontend-buscador-pacientes`)

- [x] 5.1 RED: en `frontend/test/buscador-pacientes.test.ts` (Vitest con jsdom, `vi.useFakeTimers()` y `ServidorFalso` de `apoyo.ts`), escribir "Busca después de la espera", "Escritura rápida" y "Texto demasiado corto". Verificar con `npm test` que fallan
- [x] 5.2 GREEN: `frontend/src/agenda/buscador-pacientes.ts` con la espera de 300 ms, `encodeURIComponent` y las reglas de largo mínimo (design D10), con TypeScript `strict` y sin `any`. Verificar que los 3 escenarios pasan y que `npm run build` (`tsc --noEmit`) termina en verde
- [x] 5.3 RED → GREEN: "Buscando", "Ver las coincidencias", "Límite de resultados alcanzado" y "Sin coincidencias", con las regiones `role="status"` y `role="listbox"`. Verificar que pasan
- [x] 5.4 RED → GREEN: "Elegir con el mouse", "Cambiar el paciente elegido", "Elegir con el teclado" y "Escape cierra la lista y no el diálogo", con el patrón combobox (`aria-expanded`, `aria-controls` y `aria-activedescendant`) y Escape con `preventDefault()`. Verificar que pasan
- [x] 5.5 RED → GREEN: "Respuestas fuera de orden" (contador de búsqueda, design D10). Verificar que pasa
- [x] 5.6 RED → GREEN: "Error del servidor", "Sesión vencida durante la búsqueda" y "Búsqueda sin permiso", reusando `ControlAgenda.sesionVencida` y `mensajeDeError`. Verificar que pasan
- [x] 5.7 RED → GREEN: "Búsqueda sin rastros en el navegador" (`localStorage`, `sessionStorage` y `location.hash`). Verificar que pasa
- [x] 5.8 RED → GREEN: "Buscador en el celular" en `responsivo.test.ts`, siguiendo el patrón de C-09 (regla del CSS: opciones de 44 px como mínimo y ancho completo, porque jsdom no calcula el diseño). Agregar los estilos `.buscador-*` en `styles.css` solo con variables `--ds-*`. Verificar que pasa

## 6. "Nuevo turno" con el buscador (spec `frontend-agenda-personal`)

- [x] 6.1 RED: en `formularios.test.ts`, adaptar "Recepción crea un turno" para elegir "Uno, Paciente · DNI 99000001" con el buscador, y escribir "Paciente escrito pero no elegido", "Ya no se pide el id del paciente" y "Error de la API en el paciente". Verificar que fallan. El cambio de "Recepción crea un turno" sigue el MODIFIED del spec; no es un test debilitado
- [x] 6.2 GREEN: en `abrirNuevoTurno`, reemplazar el campo "Id del paciente" por el buscador como primer campo, validar con `pacienteId() !== null` ("Elegí un paciente de la lista.") y mapear el `422` de `pacienteId` al buscador. No tocar `abrirCancelacion` ni `abrirReprogramacion` (C-33). Verificar que los 4 escenarios y "Faltan datos en el formulario" pasan, y que el resto de `formularios.test.ts`, `agenda.test.ts` y `navegacion.test.ts` sigue en verde
- [x] 6.3 Verificar con `grep -rn "Id del paciente" frontend/src` que no queda ninguna referencia, y con `grep -rn ": any\|as any" frontend/src` que no se agregó `any`

## 7. Verificación integral

- [x] 7.1 (backend: 461 unitarios + 470 de integración; frontend: 207) Correr `./gradlew build integrationTest` con PostgreSQL levantado y `npm test` y `npm run build` en `frontend/`. Verificar que hay un test por cada uno de los 43 escenarios (21 de `busqueda-pacientes`, 17 de `frontend-buscador-pacientes` y 5 de `frontend-agenda-personal`), todos en verde, más el baseline de la tarea 1.1
- [x] 7.2 Verificar que ningún controller nuevo devuelve una entidad JPA (los tipos de retorno son records de `usuarios/api`) y, con `grep`, que el código nuevo no contiene `PacienteEntity`, `email` ni `telefono`
- [x] 7.3 Visual verify (design, sección UI Mockups): levantar el backend y `npm run dev`, abrir "Nuevo turno" con una sesión de `recepcion` y el seed ficticio, y capturar a 390 × 844 y 1440 × 900 los estados de los tres mockups. Guardar las capturas en `mockups/verify/`, revisar errores de consola y pedidos fallidos, y detener los servidores. Anotar las diferencias por prioridad (blocker, major o minor) en `mockups/verify/README.md` y `Visual verify:` en `design.md`
- [ ] 7.4 Prueba manual con teclado y lector de pantalla (NVDA o el narrador de Windows): buscar "fict", moverse con flechas, elegir con Enter y cerrar con Escape sin cerrar el diálogo. Anotar el resultado en `mockups/verify/README.md` — **Pendiente al archivar (2026-11-23):** el teclado quedó verificado en el navegador (`mockups/verify/README.md`); la prueba con lector de pantalla la hace a mano Juan Sebastián y se anota en un PR aparte.
- [x] 7.5 Correr `openspec validate buscador-pacientes --strict` y verificar que es válido

## Workflow follow-up

- Abrir el PR de `change/C-36-buscador-pacientes` a `master` con los avisos de las tareas 0.2 y 0.3. Sofia revisa y mergea.
- Marcar `[x]` en C-36 de `CHANGES.md` con la evidencia. Si D11 deja el sobreturno fuera, pedir a la líder la ficha nueva para el formulario de sobreturno.
- Anotar para C-14 que no se active un log de accesos con query string (design D9).
- Archivar después del archive de C-09 con `openspec archive buscador-pacientes`, y verificar `openspec/specs/busqueda-pacientes/`, `frontend-buscador-pacientes/` y el requisito "Crear un turno" de `frontend-agenda-personal/`.
