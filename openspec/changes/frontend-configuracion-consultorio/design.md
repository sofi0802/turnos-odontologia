# Design: página de Configuración del consultorio (C-34)

## Context

**Frontend**
- `frontend/src/pages/configuracion.ts` es hoy `paginaConTitulo('configuracion', 'Configuración', 'personal')`.
- La página ya está en el menú del personal, y el guard de `app.ts` exige una sesión del personal para entrar.

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

- Los `GET` de horarios, ausencias y bloqueos pasan por `Solicitante.exigirPropioSiEsOdontologo`: un odontólogo que pide otro profesional recibe `403`.
- `SesionResponse` es `(autenticado, username, rol)`. `UsuarioAutenticado` ya tiene `profesionalId()`, pero no se expone.

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
{ editaCatalogos, editaHorarios, gestionaAusencias, veBloqueos, gestionaBloqueos, profesionalFijo }
```

- Cada panel dibuja los botones según esos permisos.
- Si la API igual responde `403` (por ejemplo, porque cambió el rol en otra pestaña), se muestra `MENSAJE_SIN_PERMISO`. Un `401` sigue la sesión vencida de C-09.

### D4. `profesionalId` en la sesión (backend, cambio mínimo)
- `SesionResponse` suma `Long profesionalId`.
- `SesionResponse.de(...)` recibe el `UsuarioAutenticado` (o el `Usuario`) y completa el campo solo si `rol == ODONTOLOGO`. Con `@JsonInclude(NON_NULL)`, no aparece para los demás.
- Lo usan el login y `GET /public/sesion`.
- En el frontend, `Sesion` suma `profesionalId: number | null` y `leerSesion` lo valida como número.

**Alternativas descartadas:**
- Que el odontólogo elija el profesional en un selector y reciba `403` con los ajenos: es confuso y muestra a todo el equipo.
- Esperar a C-35: dejaría al odontólogo sin bloqueos en el MVP.

**Coordinación:** `usuarios/` es de Juan Sebastián (se le avisa en el PR), y C-35 puede reutilizar el campo.

### D5. Horarios: edición local y `PUT` del conjunto completo
- La API reemplaza todas las franjas de un profesional con un único `PUT`, así que la pestaña edita una copia local. "Agregar franja" y "Quitar" solo la cambian; "Guardar horario" la envía entera.
- "Descartar cambios" vuelve a lo último leído.
- Antes de enviar, el frontend no valida superposiciones: la regla vive en el dominio (`FRANJAS_SUPERPUESTAS`) y se muestra su mensaje. Solo exige que "desde" sea anterior a "hasta", para no enviar un pedido que se sabe inválido.

### D6. Fechas de ausencias y bloqueos en la zona del consultorio
- Los formularios piden fecha y hora de inicio y de fin con `<input type="date">` y `<input type="time">`, y las convierten con `aIsoConZona` (offset de Buenos Aires), como la agenda de C-09.
- Para mostrar los períodos se usan `fechaLarga` y `formatearHora`.

### D7. Turnos afectados: respuesta del alta o consulta a la agenda
- **Al registrar:** se muestran los `turnosAfectados` de la respuesta.
- **Para una ausencia ya registrada:** "Ver turnos afectados" pide `GET /turnos?desde&hasta&profesionalId`, con el período exacto de la ausencia, y filtra los `RESERVADO` o `CONFIRMADO`.
- Así no hace falta un endpoint nuevo. El backend vuelve a verificar que el turno esté afectado al reasignar (`TURNO_NO_AFECTADO`).
- Solo se muestran fecha, hora y prestación. El nombre del paciente que trae `GET /turnos` no se dibuja, para que las dos fuentes muestren lo mismo y no se expongan más datos de los necesarios.

### D8. Reasignar: profesionales habilitados para la prestación
- El diálogo "Reasignar" carga `GET /profesionales/{id}/prestaciones` de cada profesional activo (menos el ausente) y ofrece solo a los que hacen la prestación del turno.
- Los resultados quedan en memoria mientras la pestaña está abierta.
- Inicio y sillón son opcionales: vacíos, se envían como `null` y el backend conserva los del turno.

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
| `TURNO_NO_AFECTADO` | "Ese turno ya no está afectado por la ausencia." |

### D11. Estilos
- Se reutilizan los tokens y las clases de C-12.
- Se agregan a `styles.css`:
  - `.pestanas-config` y `.pestana` (con el estado `aria-selected`). `.pestanas` ya existe en C-12 para las dos pestañas de Mi cuenta, así que no se reutiliza;
  - `.lista-config` (filas con nombre, datos y acciones, que se apilan en 390 px);
  - `.estado-activo` y `.estado-inactivo` (punto y texto, no solo color);
  - `.franjas` (grilla de días).
- Los botones de acción respetan `--ds-alto-control` (44 px).

## Risks / Trade-offs

- **[Mucho alcance en un change]**
  - **Riesgo:** nueve secciones en un PR grande.
  - **Mitigación:** tareas ordenadas por sección, cada una con sus tests, para poder revisar y commitear por partes.
- **[Rol cambiado en otra pestaña]**
  - **Riesgo:** los botones se derivan del rol en memoria.
  - **Mitigación:** la API sigue rechazando con `403` y se muestra el mensaje.
- **[`GET /turnos` filtrado en C-35]**
  - **Riesgo:** C-35 va a restringir la agenda del odontólogo.
  - **Mitigación:** "Ver turnos afectados" solo lo usan `ADMIN` y `RECEPCIONISTA`, así que no se ve afectado.
- **[N pedidos en "Reasignar"]**
  - **Riesgo:** se piden las especialidades de cada profesional.
  - **Mitigación:** un consultorio chico tiene pocos profesionales, y se cachean mientras la pestaña está abierta.
- **[Cambio de backend en un módulo ajeno]**
  - **Riesgo:** toca `usuarios/api`, que no es de Emanuel.
  - **Mitigación:** el cambio es aditivo, con su test, y se avisa a Juan Sebastián en el PR.

## Migration Plan

No hay migraciones de base de datos. El campo nuevo de la sesión es opcional, así que el frontend viejo lo ignora y el nuevo tolera su ausencia.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22).
- Decision: yes, 2026-11-23. El modo es `system-and-mockups` en `.active-orchestrator-state.json` y en `design-system/README.md`.
- Mockups: `openspec/changes/frontend-configuracion-consultorio/mockups/` (ver su `README.md`). Fuente de referencia: `design-system/components.html`.
- Approval: pending.
- Preview: rendered (2026-11-23). Las capturas están en `mockups/previews/`, a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores en la consola.
- Visual verify: pendiente.
