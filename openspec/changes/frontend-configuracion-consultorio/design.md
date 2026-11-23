# Design: página de Configuración del consultorio (C-34)

## Context

**Frontend**
- `frontend/src/pages/configuracion.ts` es hoy `paginaConTitulo('configuracion', 'Configuración', 'personal')`.
- La página ya está en el menú del personal, pero hoy no exige sesión (no tiene `acceso`). C-34 la marca con `acceso: 'personal'`, como Agenda, porque necesita el rol.

**Lo que ya existe y se reutiliza**
- C-09:
  - `ClienteApi` (CSRF, errores tipados: `ErrorNegocio`, `ErrorValidacion`, `ErrorPermiso`, `ErrorNoEncontrado`);
  - `mensajeDeError` y la tabla `MENSAJES_POR_CODIGO`;
  - `campos.ts` (`crearCampo`, `crearSelector`, `crearAviso`, `crearBoton`);
  - `zona.ts` (`aIsoConZona`, `formatearHora`, `fechaLarga`);
  - el `<dialog>` de los formularios de la agenda.
- C-12: las clases del design system en `styles.css` (`.chip`, `.btn`, `.aviso`, `.dialogo`).

**Backend: lo que exponen C-04 y C-07**

| Sección | Endpoint | Escribe |
|---|---|---|
| Profesionales | `GET/POST /profesionales`, `PUT /{id}`, `POST /{id}/activar` y `/{id}/desactivar` | `ADMIN` |
| Sillones y prestaciones | Mismo esquema en `/sillones` y `/prestaciones` | `ADMIN` |
| Especialidades | `GET/PUT /profesionales/{id}/prestaciones` (`{prestacionIds}`) | `ADMIN` |
| Sillón habitual | `PUT /profesionales/{id}/sillon-habitual` (`{sillonId}`); se lee de `sillonHabitualId` | `ADMIN` |
| Margen | `GET/PUT /configuracion/margen` (`{minutos}`) | `ADMIN` |
| Horarios | `GET/PUT /profesionales/{id}/horarios` (`{franjas:[{dia,desde,hasta}]}`) | `ADMIN` |
| Ausencias | `GET/POST /profesionales/{id}/ausencias`, `DELETE /{ausenciaId}`, `POST /{ausenciaId}/turnos/{turnoId}/reasignar` | `ADMIN`, `RECEPCIONISTA` |
| Bloqueos | `GET/POST /profesionales/{id}/bloqueos`, `DELETE /{bloqueoId}` (todo el controlador es `ADMIN` u `ODONTOLOGO`) | `ADMIN`, `ODONTOLOGO` propio |

**Segunda prueba (2026-11-23, con la página ya implementada en el PR #60).** Emanuel pidió seis ajustes, y dos de ellos necesitan backend:
- `ProfesionalService.asignarSillonHabitual` acepta un sillón que ya es habitual de otro profesional.
- `ServicioReasignacion.reasignar` solo busca el período en `Clase.AUSENCIA`, y `BloqueosController` no deja entrar a `RECEPCIONISTA`.
- El `POST` de bloqueos ya devuelve los `turnosAfectados`, igual que el de ausencias.

- Los `GET` de horarios, ausencias y bloqueos pasan por `Solicitante.exigirPropioSiEsOdontologo`: un odontólogo que pide otro profesional recibe `403`.
- Desde C-35, `SesionResponse` incluye `profesionalId` para un `ODONTOLOGO`, y el frontend lo lee en `Sesion.profesionalId?: number`. C-35 también hace que `GET /turnos` devuelva al odontólogo solo los turnos de su profesional.

**Respuestas y errores**
- `POST` de ausencias y de bloqueos responde `PeriodoRegistradoRespuesta {periodo, turnosAfectados:[{turnoId, inicio, fin, prestacionId}]}`.
- Configuración responde:
  - `409` para `MATRICULA_DUPLICADA` y `NOMBRE_DUPLICADO`;
  - `422` para `DURACION_INVALIDA`, `PRESTACION_INACTIVA`, `SILLON_INACTIVO`, `MARGEN_INVALIDO` y `DATOS_INVALIDOS`.
- Disponibilidad responde `422` para `HORARIO_INVALIDO`, `FRANJAS_SUPERPUESTAS` y `PERIODO_INVALIDO`.
- La reasignación puede responder `409` con `TURNO_NO_AFECTADO`, `PROFESIONAL_NO_HABILITADO` o los rechazos de la agenda (solapamientos, margen, fuera de horario).

## Goals / Non-Goals

**Goals:**
- Cubrir con la interfaz todo lo que C-04 y C-07 exponen para configurar el consultorio.
- Mostrar a cada rol solo las acciones que la API le va a aceptar. La API sigue siendo la que decide, y un `403` se muestra igual.
- Mantener el estilo de C-09 y C-12: TypeScript vanilla, módulos chicos por sección y tests con `ServidorFalso`.

**Non-Goals:**
- **Gestión de usuarios del personal:** no hay endpoint.
- **Agenda propia del odontólogo:** es C-35.
- **Reprogramar desde la agenda:** es C-33.
- **Avisar a los pacientes de una reasignación:** ya lo hace el backend.
- **Borrar profesionales, sillones o prestaciones:** solo se desactivan, como define la API.

## Decisions

### D1. Módulo `src/configuracion/`, un archivo por sección
`pages/configuracion.ts` arma el título y las pestañas, y delega cada panel a `src/configuracion/`:

- `pestanas.ts`: pestañas accesibles.
- `catalogo.ts`: los tres catálogos comparten un componente parametrizado.
- `profesional-detalle.ts`: especialidades y sillón habitual.
- `margen.ts`, `horarios.ts`, `ausencias.ts`, `reasignacion.ts` y `bloqueos.ts`.
- `profesional-elegido.ts`: selector o profesional fijo.
- `api.ts`: lectura defensiva de las respuestas.

Así cada archivo queda chico y testeable, y se evita un archivo de más de 800 líneas.

**Alternativa descartada:** reutilizar `agenda/catalogos.ts`. Esa función normaliza los catálogos solo para los selectores (une nombre y apellido, descarta matrícula y duración); acá hace falta el registro completo.

### D2. Pestañas con el patrón ARIA `tablist`
- Hay un `role="tablist"` con botones `role="tab"`, `aria-selected` y `aria-controls`, y paneles `role="tabpanel"` con `hidden`.
- Las flechas izquierda y derecha mueven el foco entre pestañas.
- Cada panel carga sus datos la primera vez que se muestra, no al abrir la página: así un error en una sección no afecta a las demás ni se hacen pedidos que el usuario no necesita.
- **Pantallas chicas (390 px):** la lista de pestañas se desplaza en horizontal dentro de su propio contenedor (`overflow-x: auto`), sin desplazar la página. La pestaña elegida se lleva a la vista con `scrollIntoView({ block: 'nearest', inline: 'center' })`.
- La pestaña elegida no se guarda en la URL. **Alternativa descartada:** sub-rutas `#/configuracion/margen`, porque obligan a tocar el router de C-09 y su spec.

### D3. Permisos derivados del rol, con la API como autoridad
Una función `permisos(sesion)` devuelve qué puede hacer el rol:

```ts
{ editaCatalogos, editaHorarios, gestionaAusencias, veBloqueos, gestionaBloqueos, reasigna, veTodos, profesionalFijo }
```

- Desde la segunda prueba, `veBloqueos` vale para los tres roles. `gestionaBloqueos` es solo de `ADMIN` y `ODONTOLOGO`. `reasigna` y `veTodos` son de `ADMIN` y `RECEPCIONISTA`.

- Cada panel dibuja los botones según esos permisos.
- Si la API igual responde `403` (por ejemplo, porque cambió el rol en otra pestaña), se muestra `MENSAJE_SIN_PERMISO`. Un `401` sigue la sesión vencida de C-09.

### D4. El profesional del odontólogo sale de la sesión de C-35
- Para un `ODONTOLOGO`, las pestañas Horarios, Ausencias y Bloqueos usan `sesion.profesionalId`, que C-35 ya agrega en el login y en `GET /public/sesion`.
- Si falta, como en un usuario `ODONTOLOGO` sin profesional vinculado, se muestra el aviso del spec y no se pide nada.
- C-34 no cambia `sesion.ts`. Los únicos cambios de backend son los de D15 y D16.

**Alternativa descartada:** que el odontólogo elija el profesional en un selector y reciba `403` con los ajenos. Es confuso y muestra a todo el equipo.

### D5. Horarios: edición local y `PUT` del conjunto completo
- La API reemplaza todas las franjas de un profesional con un único `PUT`, así que la pestaña edita una copia local. "Agregar franja" y "Quitar" solo la cambian; "Guardar horario" la envía entera.
- "Descartar cambios" vuelve a lo último leído.
- Antes de enviar, el frontend no valida superposiciones: la regla vive en el dominio (`FRANJAS_SUPERPUESTAS`) y se muestra su mensaje. Solo exige que "desde" sea anterior a "hasta", para no enviar un pedido que se sabe inválido.

### D6. Fechas de ausencias y bloqueos en la zona del consultorio
- Los formularios piden la fecha con `<input type="date">` y la hora con el selector de D13, y las convierten con `aIsoConZona` (offset de Buenos Aires), como la agenda de C-09.
- Para mostrar los períodos se usan `fechaLarga` y `formatearHora`.

### D7. Turnos afectados: respuesta del alta o consulta a la agenda
- **Al registrar:** se muestran los `turnosAfectados` de la respuesta.
- **Para una ausencia o un bloqueo ya registrados:** "Ver turnos afectados" pide `GET /turnos?desde&hasta&profesionalId`, con el período exacto, y filtra los `RESERVADO` o `CONFIRMADO`. Lo usan `ADMIN` y `RECEPCIONISTA`.
- El mismo componente sirve para los dos tipos de período: recibe la clase (`ausencias` o `bloqueos`) y arma la ruta de "Reasignar" con ella.
- Así no hace falta un endpoint nuevo. El backend vuelve a verificar que el turno esté afectado al reasignar (`TURNO_NO_AFECTADO`).
- Solo se muestran fecha, hora y prestación. El nombre del paciente que trae `GET /turnos` no se dibuja, para que las dos fuentes muestren lo mismo y no se expongan más datos de los necesarios.

### D8. Reasignar: profesionales habilitados para la prestación
- El diálogo "Reasignar" carga `GET /profesionales/{id}/prestaciones` de cada profesional activo (menos el ausente) y ofrece solo a los que hacen la prestación del turno.
- Los resultados quedan en memoria mientras la pestaña está abierta.
- Inicio y sillón son opcionales: vacíos, se envían como `null` y el backend conserva los del turno.
- El diálogo es el mismo para ausencias y bloqueos. Solo cambia la ruta (D16).

### D9. Formularios en `<dialog>` y doble envío
- Alta y edición de catálogos, ausencias, bloqueos y reasignación usan el mismo `<dialog>` con `.dialogo` que la agenda.
- Mientras el pedido está en curso, el botón de guardar queda `disabled`.
- Los errores de validación con `errores[]` se muestran junto al campo, como en C-09. Los de negocio se muestran en un aviso `role="alert"` dentro del diálogo.
- Borrar una ausencia o un bloqueo pide confirmación con el mismo diálogo.

### D10. Mensajes nuevos
Se agregan a `MENSAJES_POR_CODIGO`:

| Código | Mensaje |
|---|---|
| `MATRICULA_DUPLICADA` | "Ya existe un profesional con esa matrícula." |
| `NOMBRE_DUPLICADO` | "Ya existe otro con ese nombre." |
| `DURACION_INVALIDA` | "La duración tiene que ser mayor que 0 y múltiplo de 5." |
| `PRESTACION_INACTIVA` | "Hay una prestación inactiva entre las elegidas." |
| `SILLON_INACTIVO` | "El sillón elegido está inactivo." |
| `MARGEN_INVALIDO` | "El margen tiene que ser de 10 minutos o más." |
| `HORARIO_INVALIDO` | "Revisá los días y las horas de las franjas." |
| `FRANJAS_SUPERPUESTAS` | "Hay franjas del mismo día que se superponen." |
| `PERIODO_INVALIDO` | "El fin tiene que ser posterior al inicio." |
| `TURNO_NO_AFECTADO` | "Ese turno ya no está afectado por ese período." (antes decía "por la ausencia"; ahora también aplica a los bloqueos) |
| `SILLON_HABITUAL_OCUPADO` | "Ese sillón ya es el habitual de otro profesional." |

Textos fijos nuevos: "Elegí al menos un día.", "Avisale a la recepción para reasignarlos." y "El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón."

### D11. Estilos
- Se reutilizan los tokens y las clases de C-12.
- Se agregan a `styles.css`:
  - `.pestanas-config` y `.pestana` (con el estado `aria-selected`). `.pestanas` ya existe en C-12 para las dos pestañas de Mi cuenta, así que no se reutiliza;
  - `.lista-config` (filas con nombre, datos y acciones, que se apilan en 390 px);
  - `.estado-activo` y `.estado-inactivo` (punto y texto, no solo color);
  - `.franjas` (grilla de días).
- Los botones de acción respetan `--ds-alto-control` (44 px).
- **Espaciado (segunda prueba):** en el detalle del profesional, cada bloque (prestaciones y sillón habitual) es un `.grupo-config` con `gap: var(--ds-espacio-1)` (8 px) entre el campo y su botón, y `--ds-espacio-3` (24 px) entre bloques. Antes, el botón "Guardar sillón habitual" quedaba pegado al selector.
- Se agregan `.dias-semana` (casillas de días en fila, que se apilan en 390 px) y `.hora-5` (dos selectores, hora y minutos, en línea).

### D12. "Todos los profesionales": un pedido por profesional
- No hay un endpoint que devuelva las ausencias o los bloqueos de todos. La vista "Todos" pide `GET /profesionales/{id}/ausencias` (o `/bloqueos`) de cada profesional activo en paralelo (`Promise.all`), y une y ordena por inicio.
- Si falla uno de los pedidos, la pestaña muestra el error y "Reintentar" (la misma regla de "Errores al cargar o guardar"). No se muestra una lista parcial, para no dar a entender que un profesional no tiene períodos.
- Al registrar con "Todos" elegido, el diálogo suma un selector de profesional. Después del alta se recarga la vista "Todos".
- **Alternativa descartada:** un endpoint nuevo `GET /ausencias`. Sumaría backend y permisos para algo que en un consultorio chico se resuelve con pocos pedidos.

### D13. Horas con dos selectores, de 5 en 5
- `crearSelectorHora(nombre, valor?)` en `src/configuracion/ui.ts` dibuja un selector de hora (00 a 23) y otro de minutos (00 a 55, de 5 en 5), y devuelve `"HH:MM"`.
- Si el valor que llega no es múltiplo de 5 (una franja cargada por la API), se agrega esa opción a los minutos para mostrarla tal cual.
- Se usa en las franjas, en ausencias y bloqueos (con el `<input type="date">`) y en el inicio opcional de "Reasignar".
- El margen sigue siendo un `<input type="number">`, con `step="5"` y `min="10"`.
- **Alternativa descartada:** `<input type="time" step="300">`. Chrome y Safari ignoran el `step` en su selector o permiten escribir cualquier minuto, así que no garantiza el pedido.

### D14. Alta del profesional en tres pedidos
- La API no recibe prestaciones ni sillón en el `POST /profesionales`. El alta encadena `POST`, después `PUT .../prestaciones` (si se marcó alguna) y después `PUT .../sillon-habitual` (si se eligió uno).
- Si falla el `POST`, el diálogo sigue abierto, como hoy.
- Si falla un pedido posterior, el profesional ya existe: el diálogo se cierra, la lista se recarga y se muestra el aviso de falla parcial con el mensaje del error. Se puede corregir desde el detalle.
- **Alternativa descartada:** cambiar el `POST` para que reciba todo. Toca el contrato de C-04 y sus tests sin necesidad.

### D15. Backend: sillón habitual exclusivo (RN-RO-09)
- `ProfesionalService.asignarSillonHabitual` busca, antes de guardar, si otro profesional (activo o inactivo) tiene ese sillón (`existsBySillonHabitualIdAndIdNot`). Si lo tiene, lanza el error de negocio `SILLON_HABITUAL_OCUPADO` (`409`, en `ProblemDetail`, como `MATRICULA_DUPLICADA`).
- El profesional inactivo también ocupa su sillón: así, al reactivarlo, no aparece un conflicto. Para liberar el sillón hay que quitárselo.
- **Sin índice único (decisión de Emanuel):** no hace falta reservar una migración. **Riesgo aceptado:** dos administradores que asignan el mismo sillón en el mismo instante podrían pasar los dos la validación. En un consultorio con un administrador es improbable, y el sillón habitual solo es una preferencia de RN-RO-06: no causa solapamientos, que siguen protegidos por la exclusión de la base.
- El frontend arma "(de X)" con los `sillonHabitualId` de la lista de profesionales que ya carga. No hace falta un endpoint nuevo.

### D16. Backend: reasignar turnos afectados por un bloqueo (RN-AG-19)
- `ServicioReasignacion.reasignar` recibe la `Clase` del período (`AUSENCIA` o `BLOQUEO`) en lugar de fijarla, y la usa para buscar el período y comprobar que el turno esté afectado. El resto (cancelar y crear en una transacción, aviso al paciente, códigos de rechazo) no cambia.
- `BloqueosController` pasa de un `@PreAuthorize` en la clase a uno por método:
  - `GET`: `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`. El odontólogo sigue limitado a su profesional por `exigirPropioSiEsOdontologo`.
  - `POST` y `DELETE`: `ADMIN` y `ODONTOLOGO`, como hoy.
  - `POST /{bloqueoId}/turnos/{turnoId}/reasignar`: `ADMIN` y `RECEPCIONISTA`, como en las ausencias.
- La seguridad sigue denegando por defecto. El cambio solo agrega permisos explícitos, y cada uno tiene un escenario con su test de integración.
- **Gobernanza:** el permiso de lectura para la recepción es un cambio de autorización. El PR se lo señala a la líder.

## Risks / Trade-offs

- **[Mucho alcance en un change]**
  - **Riesgo:** nueve secciones en un PR grande.
  - **Mitigación:** tareas ordenadas por sección, cada una con sus tests, para poder revisar y commitear por partes.
- **[Rol cambiado en otra pestaña]**
  - **Riesgo:** los botones se derivan del rol en memoria.
  - **Mitigación:** la API sigue rechazando con `403` y se muestra el mensaje.
- **[`GET /turnos` filtrado en C-35]**
  - **Riesgo:** desde C-35, la agenda del odontólogo está restringida a su profesional.
  - **Mitigación:** "Ver turnos afectados" solo lo usan `ADMIN` y `RECEPCIONISTA`, así que no se ve afectado.
- **[N pedidos en "Todos los profesionales"]**
  - **Riesgo:** un pedido por profesional activo.
  - **Mitigación:** son pocos y van en paralelo; se piden solo al abrir la pestaña o al cambiar el selector.
- **[Sillón habitual sin índice único]**
  - Ver D15. Es un riesgo aceptado.
- **[N pedidos en "Reasignar"]**
  - **Riesgo:** se piden las especialidades de cada profesional.
  - **Mitigación:** un consultorio chico tiene pocos profesionales, y se cachean mientras la pestaña está abierta.

## Migration Plan

- No hay migraciones de base de datos.
- Los cambios de backend (D15 y D16) son compatibles: solo agregan un rechazo y dos permisos. Ningún cliente existente usa un sillón compartido, porque el seed no asigna sillones habituales (V2 solo agrega la columna).

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22). Los ajustes no cambian tokens ni assets compartidos.
- Decision: yes, 2026-11-23. El modo es `system-and-mockups` en `.active-orchestrator-state.json` y en `design-system/README.md`.
- Mockups: `openspec/changes/frontend-configuracion-consultorio/mockups/` (ver su `README.md`). Fuente de referencia: `design-system/components.html`.
- Approval: approved (2026-11-23, by Sofia Indovino) para la revisión 1 de los mockups. La aprobó al mergear el PR #51, que la pedía.
- **Revisión 2 de los mockups (segunda prueba, 2026-11-23):** Approval: pending. Se actualizan `profesionales.html`, `profesional-formulario.html`, `horarios.html`, `ausencias.html`, `bloqueos-odontologo.html` y `reasignar.html`, y se agrega `bloqueos.html` (vista de recepción y de "Todos los profesionales"). El apply de las tareas de frontend espera la aprobación de Emanuel.
- Preview: rendered (2026-11-23). Las capturas están en `mockups/previews/`, a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores en la consola.
- Visual verify: differences (2026-11-23). Solo hay diferencias minor (el encabezado de C-09, los botones de los diálogos y el tono del recuadro de turnos afectados cuando no hay ninguno); no hay blockers ni majors. Detalle y capturas en `mockups/verify/README.md`.
