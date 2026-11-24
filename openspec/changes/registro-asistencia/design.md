# Design

## Context

El motivo está en proposal.md (Why) y los requisitos, en los deltas de `specs/`. Las reglas están en la KB aprobada el 2026-11-24 (RN-AG-17 y RN-AG-20, US-042 a US-044, matriz de 03, DD-11).

- `Turno` (dominio) no tiene hora de llegada. `ausente()` devuelve una copia en `AUSENTE` y se pierde si estaba `RESERVADO` o `CONFIRMADO`.
- `MarcarAusencia` (C-13) exige que el turno sea del paciente, del profesional del odontólogo, activo y con el inicio pasado. Lo llama solo `POST /api/v1/pacientes/{id}/ausencias` de la ficha.
- Las dos restricciones `EXCLUDE` (V4) cuentan solo los turnos `RESERVADO` y `CONFIRMADO`, sobre `[inicio, fin_con_margen)`.
- `ConsultaAgenda.turnosActivos` y la consulta JPA filtran por estado activo: hoy la agenda no muestra los `AUSENTE` ni los `ATENDIDO`.
- Ningún caso de uso pasa un turno a `ATENDIDO`. Hay un precedente de tarea programada: `TareaRecordatorios` (C-11), que se apaga por propiedad.
- `AuditoriaTurnos.Accion` tiene `CREAR`, `CANCELAR`, `REPROGRAMAR`, `SOBRETURNO`, `EXTENDER`, `CONFIRMAR` y `AUSENTE`.
- La ficha clínica (`clinica/`, de Emanuel) no tiene pantalla. Lista las ausencias con `AusenciasPacienteJdbc` (turnos `AUSENTE` del paciente).
- El frontend de la agenda (`frontend/src/agenda/`, de Enzo) ya tiene el estilo de la etapa D de C-39.

## Goals / Non-Goals

**Goals:**
- Que la recepción registre la asistencia en el momento, desde la agenda, sin pantallas nuevas.
- Mantener intacta la doble garantía contra solapamientos (regla dura 5).

**Non-Goals:**
- Una pantalla para la ficha clínica.
- "Mis turnos" del paciente: lo resuelve C-42 con la etiqueta "Sin registrar".
- Notificar al paciente cuando se lo marca ausente.
- Una tolerancia configurable: es fija en 15 minutos (decisión de la líder).

## Decisions

1. **La llegada es una columna, no un estado.** `turno.llegada_en timestamptz null` (migración V22). El turno sigue `RESERVADO` o `CONFIRMADO`, así que las `EXCLUDE` lo siguen contando sin tocarlas.
   - *Alternativa*: un estado `PRESENTE`. Se descartó porque obliga a cambiar el `CHECK` y las dos `EXCLUDE`, que son la garantía de la regla dura 5.

2. **El paso a `ATENDIDO` es al terminar el margen, no el turno.** Si pasara a `ATENDIDO` en `fin`, dejaría de contar para las `EXCLUDE` y quedarían libres los minutos de limpieza `[fin, fin_con_margen)`, que todavía están en el futuro. Por eso la tarea toma los turnos activos con `llegada_en` y `fin_con_margen <= ahora`.
   - Tarea `TareaAsistencia` en `agenda/infrastructure`, igual que `TareaRecordatorios`: cada 5 minutos, configurable con `app.agenda.asistencia.intervalo-ms`, y se apaga con `app.agenda.asistencia.activo=false` (los tests de integración la apagan y llaman al caso de uso a mano). Hace un `UPDATE ... WHERE estado IN ('RESERVADO','CONFIRMADO') AND llegada_en IS NOT NULL AND fin_con_margen <= ?` con `RETURNING id`, y registra `ATENDIDO` con el actor `sistema` en la misma transacción. Si dos instancias corren a la vez, solo cambia la fila quien la encuentra activa.
   - La llegada registrada después de `fin_con_margen` (corrección) pasa a `ATENDIDO` en la misma operación.

3. **La tolerancia y la llegada se validan en el dominio.** `Turno` suma `llegadaEn` y los métodos `conLlegada(ahora, zona)`, `ausente(ahora)` y `sinAusencia(estadoPrevio, ahora)`, que lanzan `TurnoRechazadoException` con los códigos nuevos: `AUSENCIA_ANTES_DE_TOLERANCIA`, `LLEGADA_REGISTRADA`, `LLEGADA_ANTICIPADA`, `TURNO_NO_AUSENTE` y `TURNO_TERMINADO`. La constante `TOLERANCIA_AUSENCIA = 15 min` vive en el dominio.
   - `TURNO_NO_INICIADO` deja de usarse para la ausencia: ahora el rechazo es `AUSENCIA_ANTES_DE_TOLERANCIA` también antes del inicio. Es el único cambio que rompe la API de la ficha, y está en el delta.

4. **Un caso de uso por acción, y `MarcarAusencia` se reusa.** `RegistrarLlegada` y `DeshacerAusencia` son nuevos. `MarcarAusencia` recibe el solicitante: el odontólogo del turno (desde la ficha, igual que hoy) o la recepción (desde la agenda, sin el chequeo de profesional ni de paciente). `ServicioAgenda` envuelve cada uno en una transacción con su auditoría, como hoy.

5. **Deshacer reusa la validación de superposición, sin las reglas de creación.** `DeshacerAusencia` toma el turno con bloqueo (`buscarParaModificar`), busca los turnos activos del mismo profesional y sillón que se cruzan con `[inicio, fin_con_margen)` excluyéndose a sí mismo, y aplica las reglas RN-AG-02 a RN-AG-05 que ya usa la creación. No aplica RN-AG-09 (en el pasado), ni horario de trabajo, ni ausencias o bloqueos del profesional: el turno ya era válido y se está restaurando. Si dos operaciones compiten por el horario, la `EXCLUDE` rechaza el `UPDATE` y se traduce al mismo código, como en reprogramar (C-06).
   - Si era sobreturno, se aplica la misma excepción de profesional que tienen los sobreturnos.

6. **El estado previo sale de la auditoría.** Al deshacer, el turno vuelve a `CONFIRMADO` si tiene un registro `CONFIRMAR`, y si no, a `RESERVADO`. No hace falta guardar el estado anterior en otra columna.
   - *Alternativa*: una columna `estado_previo`. Se descartó porque duplica lo que ya está en la auditoría.

7. **La agenda devuelve `ATENDIDO` y `AUSENTE`.** `turnosActivos` pasa a `turnosDeAgenda`: estado `<> 'CANCELADO'`, y `TurnoEnAgenda` suma `llegadaEn`. Los demás usos del repositorio, como disponibilidad y superposición, siguen filtrando por activos.

8. **Endpoints de la recepción en `TurnosController`.** `POST /turnos/{id}/llegada`, `/ausencia` y `/deshacer-ausencia`, con `@PreAuthorize("hasRole('RECEPCIONISTA')")`. El de la ficha sigue en `FichaClinicaController` para el odontólogo.

9. **La ficha arma las ausencias deshechas con la auditoría.** `AusenciasPacienteJdbc` suma los turnos del paciente con registro `DESHACER_AUSENCIA`, con el momento y el actor. Es un cambio de lectura en `clinica/`; se avisa a Emanuel.

10. **Frontend: el rol y el momento deciden los botones.** En `agenda/vista.ts`, una función pura `accionesDeAsistencia(turno, rol, ahora)` devuelve qué botones mostrar y si "Ausente" está habilitado. Otra, `etiquetaDeAsistencia(turno, ahora)`, devuelve "Atendido", "Ausente", "Llegó HH:mm" o "Sin registrar". Los botones usan los estilos de botón de la etapa D de C-39. No hay pantallas nuevas.

## Risks / Trade-offs

- [El paciente ve "Sin registrar" hasta 15 minutos después de terminar, aunque haya llegado: el margen más el intervalo de la tarea] → Es una demora corta. C-42 calcula la etiqueta con `fin`, y la tarea corre cada 5 minutos.
- [La recepción se olvida de registrar] → El turno se ve "Sin registrar" en la agenda y en Mis turnos, y se corrige después.
- [Deshacer la ausencia mientras otro usuario crea un turno en ese horario] → La `EXCLUDE` decide y el perdedor recibe el código de superposición.
- [Cambio del código de rechazo en la ficha] → No hay pantalla de la ficha; solo cambian los tests de C-13 que esperaban `TURNO_NO_INICIADO`, y su delta los reemplaza.

## Migration Plan

- `V22__llegada_turno.sql`: `ALTER TABLE turno ADD COLUMN llegada_en timestamptz` y un índice parcial para la tarea (`WHERE llegada_en IS NOT NULL AND estado IN ('RESERVADO','CONFIRMADO')`). No toca el `CHECK` ni las `EXCLUDE`.
- La columna admite nulos: los turnos existentes no cambian. Para volver atrás, se revierte el PR y una migración nueva borra la columna (una migración mergeada no se edita).

## UI Mockups

- Design system revision: 3 (ver `design-system/README.md`)
- Decision: yes (2026-11-24, política `system-and-mockups`)
- Mockups: `openspec/changes/registro-asistencia/mockups/` (ver su README): la agenda de la recepción con un turno en cada estado de asistencia, el rechazo al deshacer con el horario ocupado y la agenda de la odontóloga
- Approval: approved (2026-11-24, by user: Sofia Indovino aprobó los mockups de la revisión 1)
- Preview: rendered (2026-11-24, `mockups/previews/` a 390x844 y 1440x900)
- Visual verify: passed (2026-11-24, app real con el backend de desarrollo, V22 aplicada y datos ficticios; `mockups/verify/` a 390x844 y 1440x900 como recepción y como odontólogo. Se probaron en vivo "Deshacer ausencia" con el horario ocupado (409 y mensaje) y "Llegó" en un turno terminado (pasa a Atendido con auditoría LLEGADA y ATENDIDO). Sin errores de consola salvo el 409 esperado. Diferencia corregida: `.btn-llego` pasó a `06-componentes.css` para ganarle a `.btn`)
