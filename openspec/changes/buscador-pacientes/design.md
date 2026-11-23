# Design

## Context

Ver proposal.md, sección Why. Estado actual que condiciona el diseño:

- **Tabla `paciente`** (V1 y V7): `id`, `nombre` y `apellido` (`varchar(100)`, no nulos), `dni` (`varchar(15)`, `UNIQUE`, con su índice btree), `email`, `telefono`, `tiene_whatsapp`, `consentimiento_comunicaciones` y `consentimiento_fecha`. **No hay columna `activo`**: no existen pacientes inactivos.
- **`PacienteEntity`** está en `shared/persistence` (dueño Juan Cruz) y expone email y teléfono. `usuarios/infrastructure/PacienteCuentaJpaRepository` la usa para el registro.
- **Extensiones de PostgreSQL**: V1 crea solo `btree_gist`. `unaccent` y `pg_trgm` vienen con la imagen `postgres:16` de `docker-compose.yml`, pero no están creadas.
- **Seguridad** (C-05, `SecurityConfig`):
  - `/api/v1/public/**` es pública.
  - `/api/v1/paciente/**` es solo para `PACIENTE`. El patrón no abarca `/api/v1/pacientes`.
  - El resto de `/api/v1/**` es para `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`, con `401 NO_AUTENTICADO` y `403 ACCESO_DENEGADO` en `ProblemDetail`.
  - Por lo tanto `GET /api/v1/pacientes` ya queda cerrado al paciente y abierto al personal sin tocar la cadena de filtros. El spec vigente `seguridad-autorizacion` ya prueba `GET /api/v1/pacientes/{id}` → `403` para un paciente.
- **Errores**: `UsuariosExceptionHandler` (paquete `usuarios.api`) ya traduce `MethodArgumentNotValidException` a `422 DATOS_INVALIDOS` con la lista `errores` (`campo` y `mensaje`).
- **Frontend**:
  - `agenda/formularios.ts#abrirNuevoTurno` arma un `<dialog>` modal con el campo "Id del paciente" (`type=number`) y valida que sea un entero positivo.
  - `manejarEnvio` reparte los errores `422` por campo.
  - `ControlAgenda.sesionVencida` lleva al ingreso ante un `401`.
  - **No hay formulario de sobreturno**: C-09 lo dejó fuera de scope y C-08 solo hizo el backend (`POST /api/v1/turnos/sobreturno`).
- **Specs de C-09 sin archivar**: `frontend-agenda-personal` vive solo en `openspec/changes/frontend-agenda-personal/`. `openspec validate` avisa que el archive de C-36 se rechazaría mientras C-09 no esté archivado.
- **Política visual**: `system-and-mockups` en `.active-orchestrator-state.json` y en `design-system/README.md` (Revision 1, aprobada el 2026-11-22).

## Goals / Non-Goals

**Goals:**
- Buscar pacientes de forma predecible: dígitos buscan por DNI y letras por nombre y apellido, con resultados acotados y ordenados.
- Exponer solo `id`, `nombre`, `apellido` y `dni`, y no dejar el término buscado en logs, caché, almacenamiento del navegador ni respuestas de error.
- Un componente de frontend reutilizable y accesible, que cambie lo mínimo de `formularios.ts` para no chocar con C-33 y C-35.

**Non-Goals:**
- Búsqueda de texto completo, por similitud o con tolerancia a errores de tipeo.
- Paginación, auditoría de las búsquedas, alta o edición de pacientes desde la agenda.
- Cambiar la API de turnos, `SecurityConfig` o `PacienteEntity`.

## Decisions

Las decisiones marcadas *(a confirmar)* las responde la líder en el grupo 0 de tasks.md. Cada una dice la opción recomendada, que es la que ya reflejan los specs.

### D1. Ruta y autorización: `GET /api/v1/pacientes?q=` sin cambios en `SecurityConfig`
El controller nuevo vive en `usuarios/api` (`PacientesController`, `@RequestMapping("/api/v1/pacientes")`). La regla general del personal ya da `401` sin sesión y `403` con sesión `PACIENTE` *(a confirmar: 401 sin sesión, recomendado sí, ya es el comportamiento de C-05)*. Los escenarios de roles del spec `busqueda-pacientes` se prueban igual, para que un cambio futuro en la cadena de filtros no abra la ruta sin que falle un test.
- Alternativa descartada: una regla explícita `requestMatchers("/api/v1/pacientes/**")` en `SecurityConfig`. Duplica la regla general y toca `shared/security` sin necesidad.
- Alternativa descartada: `@PreAuthorize` en el método. Hoy ningún controller lo usa.

### D2. Regla de coincidencia *(a confirmar)*
- **`q` con solo dígitos → DNI por prefijo** (`dni LIKE q || '%'`). Incluye el DNI exacto y sirve mientras se tipea. Recomendado sobre "solo DNI exacto", que obligaría a escribir los 8 dígitos antes de ver algo. El prefijo solo lo usa el personal, que igual puede ver la lista completa de la agenda.
- **Si `q` tiene letras → nombre y apellido.** Se separa en palabras por espacios, hasta 5 palabras. Cada palabra tiene que aparecer como parte (`contains`) del nombre o del apellido. Así "carla fic" encuentra a "Carla Ficticia" y "paciente dos" a "Paciente Dos".
- Un texto con letras y dígitos se trata como texto: en la práctica no encuentra a nadie, y es lo esperable.
- `%`, `_` y `\` se escapan antes del `LIKE` (con `ESCAPE '\'`), y la consulta va siempre con parámetros (`JdbcTemplate`), nunca concatenada.

### D3. Sin distinguir mayúsculas ni tildes, con `lower()` y `translate()` *(a confirmar)*
Se compara `translate(lower(apellido), 'áéíóúüñ', 'aeiouun')` contra la palabra normalizada igual en Java (`Normalizer` NFD sin diacríticos y `toLowerCase(Locale.ROOT)`; la `ñ` pasa a `n` en los dos lados).
- Recomendado sobre la extensión `unaccent`, que habría que crear en una migración. Además `unaccent()` no es `IMMUTABLE`, así que para indexarla haría falta una función envoltorio. `translate` y `lower` vienen con PostgreSQL, son inmutables y alcanzan para el español.
- Alternativa: `ILIKE` solo, sin tildes. Es más simple, pero "julian" no encontraría a "Julián", que es el error más común al tipear en la recepción.
- Se prueba con PostgreSQL real (`@Tag("integration")`), no con H2.

### D4. Largo mínimo y máximo de `q` y `422` *(a confirmar)*
- Sin los espacios de los extremos: **mínimo 2 caracteres** si tiene letras, **3 dígitos** si es DNI, y **máximo 50**.
- `q` ausente o vacía → `422 DATOS_INVALIDOS` con `errores: [{campo: "q", mensaje: "…"}]` (recomendado sobre devolver `200 []`, porque una lista vacía haría pensar que no hay pacientes).
- El mensaje es fijo ("Escribí al menos 2 letras o 3 números del DNI." / "El texto es demasiado largo.") y **nunca repite `q`**.
- La validación vive en un value object de dominio `TerminoBusqueda` (puro, sin Spring), que lanza `BusquedaInvalidaException`. `UsuariosExceptionHandler` la traduce a `422`. Una `q` ausente llega como `MissingServletRequestParameterException` y se traduce igual. No se usa `@Validated` sobre `@RequestParam`, para no depender de `HandlerMethodValidationException`.

### D5. Límite y orden *(a confirmar)*
- **Máximo 10 resultados** (`LIMIT 10`), sin paginación. La respuesta es un arreglo JSON simple. El frontend avisa "Se muestran las primeras 10…" cuando recibe 10.
- Alternativa: pedir 11 y devolver `hayMas`. Es más preciso, pero cambia la forma de la respuesta y no aporta para elegir un paciente.
- **Orden**: DNI por `dni`. Texto por apellido y nombre normalizados (D3) y después `id`, para que el orden sea estable.

### D6. Pacientes inactivos *(a confirmar)*
La tabla no tiene `activo`, así que **todos los pacientes aparecen**. Si un change futuro agrega la baja lógica, ese change decide si se filtran. Recomendado: no agregar la columna en C-36.

### D7. Sin índice nuevo y sin migración V20 *(a confirmar)*
Un consultorio chico tiene de cientos a pocos miles de pacientes. Recorrer la tabla con `translate(lower(...))` cuesta milisegundos, y el `LIMIT 10` corta antes. El DNI ya tiene el índice del `UNIQUE`. Con una collation distinta de `C`, ese índice no se usa para `LIKE 'x%'`, pero tampoco hace falta.
- **Recomendado: no crear V20.** El número queda reservado.
- Si la líder prefiere preparar el volumen, la alternativa es V20 con `CREATE EXTENSION IF NOT EXISTS pg_trgm` y un índice GIN `gin_trgm_ops` sobre `translate(lower(apellido || ' ' || nombre), …)`, más su test de migración como `V8MigrationIntegrationTest`. Suma una extensión y una prueba por poca ganancia en el MVP.

### D8. Adaptador de lectura JDBC en `usuarios/` y DTO mínimo
- Puerto `BusquedaPacientes` en `usuarios/application` y caso de uso `BuscarPacientes`. Devuelve un record de dominio `PacienteResumen(id, nombre, apellido, dni)`.
- Adaptador `BusquedaPacientesJdbc` en `usuarios/infrastructure`, con `JdbcTemplate` y un `SELECT id, nombre, apellido, dni` explícito, como `ReservasPacienteJdbc` y `AusenciasPacienteJdbc`.
- La API responde con el record `PacienteResumenResponse(id, nombre, apellido, dni)`.
- Ventaja: no se toca ni se carga `PacienteEntity`, que tiene email y teléfono, y la consulta no puede traer columnas de más. Tampoco hace falta avisar a Juan Cruz.
- Alternativa descartada: un método derivado en `PacienteCuentaJpaRepository`. Carga entidades completas y no expresa bien `translate()`.

### D9. El término buscado es un dato personal
- **Logs**: ningún `LOG` en el código nuevo recibe `q` ni los resultados. `TerminoBusqueda` y `PacienteResumen` sobrescriben `toString()` sin datos, para que no terminen en un log por accidente. Se prueba con `OutputCaptureExtension`.
- **Caché**: Spring Security ya agrega `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`. El escenario "La respuesta no se guarda en caché" lo fija.
- **Query string** *(a confirmar)*: el DNI viaja en la URL de un `GET`, como pide CHANGES.md. Tomcat no tiene el log de accesos activo por defecto. Recomendado: mantener `GET` y anotar para C-14 que no se active un log de accesos con query string. La alternativa `POST /api/v1/pacientes/busqueda` con el término en el cuerpo evita el riesgo, pero necesita CSRF para una lectura y se aparta del scope.

### D10. Frontend: componente `agenda/buscador-pacientes.ts`
- `crearBuscadorPacientes(api, opciones)` devuelve `{ contenedor, control, pacienteId(): number | null, mostrarError(m), alSesionVencida }`. Cumple la interfaz `Campo<HTMLInputElement>`, así `manejarEnvio` le asigna el error `422` de `pacienteId`. La obligatoriedad no se valida con `validarObligatorios`, porque el texto puede estar escrito sin un paciente elegido: se valida con `pacienteId() !== null` ("Elegí un paciente de la lista.").
- **Espera** *(a confirmar)*: 300 ms con `setTimeout`. Los tests usan `vi.useFakeTimers()`.
- **Respuestas viejas**: un contador de búsqueda; se descarta toda respuesta que no sea la última. Se suma `AbortController` si `ClienteApi` lo admite sin cambiar su firma, y si no, alcanza el contador.
- **Accesibilidad** *(a confirmar)*:
  - Patrón combobox de ARIA 1.2 con lista emergente: `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`, `aria-controls` y `aria-activedescendant`.
  - Opciones `role="option"` con `aria-selected`, y una región `role="status"` para "Buscando…" y la cantidad.
  - Escape cierra la lista con `preventDefault()` y `stopPropagation()`, para que el `<dialog>` no se cierre con el evento `cancel`.
  - Enter con una opción activa la elige y no envía el formulario.
- **Elegido**: se muestra un resumen con "Cambiar paciente". Al editar el texto se borra la elección.
- **Falla** *(a confirmar)*: "No pudimos buscar pacientes. Probá de nuevo." en el error del campo. **No se ofrece volver a escribir el id a mano** (recomendado), porque es justo lo que este change elimina.
- **Sin rastros**: el texto vive solo en el `<input>`. Nada de almacenamiento ni de hash.
- **Estilos**: en `styles.css`, con las variables `--ds-*` de `tokens.css` que importa C-12. Opciones de 44 px de alto como mínimo, lista dentro del flujo del diálogo y no flotante, para no desbordar en 390 px.

### D11. Formulario "Sobreturno" *(a confirmar)*
CHANGES.md pide el buscador en "Nuevo turno" y "Sobreturno", pero `frontend/` no tiene formulario de sobreturno.
- **Recomendado: C-36 cubre solo "Nuevo turno"** y deja el componente reutilizable. Crear el formulario de sobreturno es una funcionalidad nueva, con sus propias reglas (motivo obligatorio, `SIN_SILLON_DISPONIBLE` y la etiqueta en la agenda), y conviene sumarlo al roadmap como ficha propia o dentro de C-33.
- **Alternativa**: sumar a C-36 un botón "Sobreturno" con un formulario mínimo (buscador, profesional, prestación, fecha, hora y motivo) sobre `POST /api/v1/turnos/sobreturno`. Si la líder la elige, se actualizan specs, mockups y tareas con `openspec-update-change` antes del apply.

### D12. Delta sobre una capacidad todavía no archivada
El MODIFIED de "Crear un turno" se escribió sobre el texto de C-09 sin archivar. Solo cambia ese requisito, sin tocar "Mensajes por código de rechazo", "Errores de campo ante un 422" ni "Reprogramar", que tocan C-33 y C-35.

## Risks / Trade-offs

- **[C-09 sin archivar]** → el archive de C-36 falla (`target spec does not exist`).
  - Mitigación: archivar C-09 antes del archive de C-36, como pide el gate 5b. Si el archive de C-09 cambia el texto de "Crear un turno", rehacer el MODIFIED con `openspec-update-change`.
  - El apply no depende de esto: el código de C-09 ya está en `master`.
- **[Conflictos con C-33 y C-35 en `formularios.ts`, `pages/agenda.ts` y `styles.css`]** → C-36 suma un archivo nuevo y cambia solo `abrirNuevoTurno`.
  - Mitigación: acordar el orden de los PR con Juan Cruz y Sofia, y rebasar antes de pedir la revisión.
  - Los tests de `formularios.test.ts` que usan "Id del paciente" (como "Recepción crea un turno") se adaptan al buscador. Es un cambio de comportamiento pedido por el spec, no un test debilitado.
- **[Enumeración de DNI por prefijo]** → un usuario del personal puede listar pacientes tipeando prefijos. Es aceptable: el personal ya ve los pacientes de la agenda. La respuesta se limita a 10 resultados con datos mínimos.
- **[DNI en la query string]** → puede quedar en logs de proxies o de accesos (D9). Mitigación: no activar logs de accesos con query string (nota para C-14).
- **[`translate` no cubre todas las tildes]** → solo las del español (`áéíóúüñ`). Otros diacríticos no se normalizan. Es aceptable para el MVP.
- **[Sin índice]** → si la base creciera a cientos de miles de pacientes, la búsqueda recorrería la tabla. La mitigación queda documentada en D7 (V20 con `pg_trgm`).

## Migration Plan

- Sin migración de datos ni de esquema (salvo que la líder pida V20 en D7).
- Deploy: backend y frontend juntos en el mismo PR. El endpoint nuevo no rompe a ningún cliente, y el frontend nuevo necesita el endpoint.
- Rollback: revertir el PR. No hay datos que deshacer.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22).
- Decision: yes, 2026-11-23. El modo es `system-and-mockups` en `.active-orchestrator-state.json` y en `design-system/README.md`.
- Mockups: `openspec/changes/buscador-pacientes/mockups/` (ver su `README.md`). Fuente de referencia: `design-system/components.html`, secciones "Formularios y avisos" y "Diálogo de confirmación", y la captura de C-09 `openspec/changes/frontend-agenda-personal/verify/1440-nuevo-turno-fuera-de-horario.png`.
- Approval: pending.
- Preview: rendered (2026-11-23), capturas en `mockups/previews/` a 390 × 844 y 1440 × 900 (`nuevo-turno-resultados`, `nuevo-turno-seleccionado` y `estados-buscador`), sin desplazamiento horizontal ni mensajes en la consola.
- Visual verify: pendiente (se completa en verify).
