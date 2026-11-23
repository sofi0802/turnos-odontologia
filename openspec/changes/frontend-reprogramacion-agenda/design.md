# Design

## Context

Lo observado en el código de `master`, que condiciona el diseño:

- **La reprogramación del personal** vive en `frontend/src/agenda/formularios.ts` (`abrirReprogramacion`): DNI, número de reserva, `<input type="date">`, `<input type="time">`, un selector con **todos** los profesionales activos (`catalogos.profesionales`) y otro con todos los sillones. Envía `POST /api/v1/turnos/{id}/reprogramar` con `profesionalId` y `sillonId` solo si se eligieron.
- **El turno de la agenda ya trae `prestacionId`** (`TurnoAgenda`), así que no hace falta pedirle nada nuevo al backend para saber qué prestación se reprograma.
- **El backend (`ReprogramarTurno`)** usa el profesional y el sillón del turno original cuando el pedido no los trae, y verifica RN-AG-15 solo si cambia de profesional. La identificación compara el número de reserva sin distinguir mayúsculas pero **exige el guion** (`NumeroReserva.coincideCon`).
- **La disponibilidad pública** (`ConsultarDisponibilidad`) ofrece un horario si el profesional está libre y **algún** sillón está libre, y siempre pasa `ignorarTurnoId = null`: cuenta el propio turno como ocupado.
- **La reserva del paciente (C-12)** ya tiene `paciente/disponibilidad.ts` (consulta tipada) y `paciente/horarios.ts` (grilla de botones, `.chips`), pensados para reutilizarse.
- **Los textos de rechazo** salen de `MENSAJES_POR_CODIGO` en `shared/ui/mensajes.ts`. No tiene `DATOS_DE_RESERVA_INVALIDOS`, así que hoy se muestra el detalle del servidor.
- Los changes de la agenda del personal (C-33, C-35, C-36) modifican specs de C-09; ver D7.

Motivación y alcance: ver `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Que el personal solo vea profesionales y horarios con los que la reprogramación puede funcionar, y entienda el motivo cuando igual se rechaza.
- Reutilizar los componentes y las consultas de C-12, sin duplicarlos ni cambiarlos.
- Encontrar y explicar la causa real de la falla con un horario "válido" antes de tocar el formulario.

**Non-Goals:**
- Cambiar el backend. Lo que resulte ser una causa del backend se avisa a su dueño y se resuelve aparte.
- Ofrecer horarios que pisan al propio turno (D6).

## Decisions

### D1. La reproducción de la falla es la primera tarea, y su resultado manda
Antes de escribir el formulario nuevo se reproduce la falla contra el backend y la base reales, con el seed ficticio y un navegador, y se deja el resultado en `openspec/changes/frontend-reprogramacion-agenda/verify/reproduccion.md` (pasos, pedido, respuesta, causa). Los candidatos, por orden de probabilidad:

1. **Sillón.** La disponibilidad dice "hay algún sillón libre", pero la reprogramación mantiene el sillón original: si está ocupado a esa hora, responde `SOLAPAMIENTO_SILLON` aunque el horario se haya ofrecido. Es la causa más probable.
2. **Número de reserva sin guion** (`DATOS_DE_RESERVA_INVALIDOS`).
3. **Profesional no habilitado** (`PROFESIONAL_NO_HABILITADO`), porque hoy se ofrecen todos.
4. **Texto del rechazo poco claro** (código sin mensaje propio).

Si la causa resulta ser distinta, o está en el backend, el diseño se ajusta con `openspec-update-change` antes del apply y se avisa a su dueño.

### D2. Sillón: se conserva el selector y el rechazo explica qué hacer
El frontend no sabe qué sillón libre usar en un horario dado (la API pública no lo dice). Se mantiene el selector de sillón con "Sin cambios", y `SOLAPAMIENTO_SILLON` al reprogramar muestra "El sillón ya está ocupado en ese horario. Elegí otro sillón." con el formulario abierto y el horario elegido.

*Alternativa descartada*: pedir al backend que, sin `sillonId`, elija un sillón libre (como hace `CrearSobreturno`). Es la mejor solución de fondo pero toca `agenda/application` y `agenda/api` (de Sofía); queda como mejora a proponer si la reproducción confirma la causa 1.

### D3. Profesionales: `GET /public/profesionales?prestacionId=`
Se pide una vez al abrir el formulario, con el `prestacionId` del turno. Las opciones son "Sin cambios" más los devueltos. El profesional actual del turno no se repite. Si la consulta falla, se avisa y queda solo "Sin cambios".

*Alternativa descartada*: filtrar `catalogos.profesionales` en el cliente. El catálogo no trae las habilitaciones (RN-AG-15), y duplicar la regla en el cliente la dejaría desactualizada.

### D4. Horarios: la consulta y la grilla de C-12, sin cambios
`consultarDisponibilidad` de `paciente/disponibilidad.ts` y `crearGrillaHorarios` de `paciente/horarios.ts` se importan tal cual. El módulo nuevo `agenda/reprogramacion.ts` solo arma la consulta (prestación, fecha, profesional elegido o el actual) y maneja el estado del formulario (horario elegido, carga, error). El `inicio` enviado es el del botón (`Horario.inicio`), sin pasar por `aIsoConZona`.

La consulta se repite cuando cambian la fecha o el profesional, y una respuesta tardía de una consulta anterior se descarta para no pisar la actual.

### D5. Número de reserva: normalizar en el cliente
Una función pura `normalizarNumeroReserva` (en `agenda/`) quita espacios y guiones, pasa a mayúsculas y, si quedan 8 símbolos, vuelve a poner el guion en la mitad. Si no quedan 8, envía lo ingresado sin tocar y deja que la API decida. El mensaje de `DATOS_DE_RESERVA_INVALIDOS` no dice cuál de los dos datos falló, igual que el backend.

### D6. Los horarios que pisan al propio turno no se ofrecen (limitación de la v1)
Como la disponibilidad cuenta al turno que se reprograma como ocupado, no se ofrecen horarios que se superponen con él (por ejemplo, correrlo 15 minutos). Se aclara con un texto en el formulario. Quitar la limitación exige que el backend ignore el turno (`BuscadorDeHuecos` ya recibe `ignorarTurnoId`) mediante un parámetro o un endpoint de personal autenticado, sin exponerlo en la API pública. Es un cambio de `reserva`/`agenda/api` que se propone aparte si el consultorio lo necesita.

### D7. Dependencia con el archive de C-09
`frontend-agenda-personal` todavía no está en `openspec/specs/`: la crea el archive de C-09. El delta de este change modifica el requisito "Reprogramar un turno" de esa capacidad, así que `openspec archive` lo rechaza hasta que C-09 esté archivado (`openspec validate` ya lo informa). El apply puede empezar antes, pero **el archive de C-33 espera al de C-09**. El texto modificado se copió del requisito vigente en `openspec/changes/frontend-agenda-personal/specs/frontend-agenda-personal/spec.md`; si C-09 lo cambia antes de archivarse, se vuelve a copiar.

### D8. Mensajes
Se agrega a `MENSAJES_POR_CODIGO`: `DATOS_DE_RESERVA_INVALIDOS` ("El DNI o el número de reserva no son correctos."). El de `SOLAPAMIENTO_SILLON` cambia para la reprogramación, que necesita la indicación de elegir otro sillón: se usa un texto específico del formulario, sin alterar el de la creación de turnos. Un código sin texto sigue mostrando el mensaje genérico de `ErrorNegocio`; se agrega un test de que nunca queda vacío ni muestra el código.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobada el 2026-11-22).
- Decision: yes, 2026-11-23. El modo del proyecto es `system-and-mockups`.
- Mockups: `openspec/changes/frontend-reprogramacion-agenda/mockups/` (pendiente de crear en la etapa de diseño, antes del apply). Pantallas: el diálogo "Reprogramar turno" con los botones de horario, el día sin horarios, el error por sillón, y a 390 × 844 y 1440 × 900.
- Approval: pending.
- Preview: pending.
- Visual verify: pending.

## Risks / Trade-offs

- **[La causa real no es la esperada]** La reproducción puede mostrar un problema distinto, o del backend. → D1 la hace primera tarea y obliga a revisar el diseño antes del apply; lo del backend se avisa a su dueño.
- **[Horarios que pisan el propio turno]** El personal no puede correr un turno unos minutos desde este formulario. → D6: aviso en la pantalla y mejora aparte.
- **[Conflictos con C-35 y C-36]** Tocan la agenda del personal en el mismo directorio. → Se coordina el orden de los PR; este change toca `formularios.ts` solo en `abrirReprogramacion`.
- **[Respuestas tardías]** Dos consultas de horarios seguidas pueden volver desordenadas. → D4: se descarta la respuesta anterior, con un test.
- **[El archive depende de C-09]** → D7.

## Open Questions

- ¿El consultorio necesita correr un turno unos minutos sin cancelarlo (D6)? Si sí, se propone el cambio de backend aparte; no cambia las tareas de este change.
