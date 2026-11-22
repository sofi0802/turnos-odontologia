# Design

## Context

Ver `proposal.md` (Why). Estado actual:
- **Puerto que ya existe**: `HabilitacionProfesional` (`configuracion/application`, C-04) consulta la tabla `profesional_prestacion`. Hoy lo usan `ReasignarTurno` (C-07) y la reserva online (C-10).
- **Validaciones de `CrearTurno` en `preparar` y `armar`**, en este orden:
  1. pasado;
  2. recursos existentes y activos;
  3. fin del turno;
  4. disponibilidad del profesional;
  5. solapamientos.
- **Quién usa `CrearTurno`**:
  - `ejecutar`: crear desde el panel y la reserva online, a través de `ServicioAgenda`;
  - `preparar(comando, reemplazaA)`: `ReprogramarTurno`, y también `ReasignarTurno`, que ya controla la habilitación por su cuenta;
  - `prepararExtension`: `ExtenderTurno`.
- **`CrearSobreturno`** tiene su propio flujo:
  1. validar;
  2. recursos;
  3. disponibilidad sin exigir horario;
  4. elección de sillón.
- **Seeds**: el seed de desarrollo `V2_1` carga habilitaciones. Los fixtures de integración `IntegracionAgendaBase`, `IntegracionClinicaBase` e `IntegracionSeguridadBase` crean profesionales sin `profesional_prestacion`.

## Goals / Non-Goals

**Goals**
- Un único lugar por caso de uso donde se exige RN-AG-15, en el orden de prioridad del spec.
- No cambiar el comportamiento de `ReasignarTurno` ni de `ExtenderTurno`.
- No romper los tests unitarios existentes.

**Non-Goals**
- Garantía en la base de datos. RN-AG-15 no es parte de la doble garantía contra solapamientos (DD-03, regla dura 5), y una restricción con subconsulta no es viable en PostgreSQL.
- Revalidar turnos ya dados cuando se quita una habilitación.
- Textos del frontend para el código nuevo (Enzo).

## Decisions

### D1. El control vive en `CrearTurno` y se activa con un parámetro explícito

`armar` recibe `exigirHabilitacion` y, cuando es verdadero, llama al puerto justo después de `verificarRecursos` y antes de `ReglasDisponibilidad`. Así queda `PROFESIONAL_NO_HABILITADO` después de `RECURSO_INACTIVO` y antes de `PROFESIONAL_AUSENTE`.

| Camino | `exigirHabilitacion` |
|---|---|
| `ejecutar` (crear desde el panel y la reserva online) | `true` |
| Nueva sobrecarga `preparar(comando, reemplazaA, exigirHabilitacion)`, que usa `ReprogramarTurno` | `true` solo si el profesional nuevo es distinto del original |
| `preparar(comando, reemplazaA)` (firma actual, la usa `ReasignarTurno`) | `false`: Reasignar ya lo controla y su criterio no cambia |
| `prepararExtension` | `false`: conserva el profesional y la prestación |

*Alternativas descartadas:*
- **Controlar siempre en `armar`.** Cambiaría `ReasignarTurno` (C-07, de Emanuel), que exime a propósito al mismo profesional, y a la extensión.
- **Controlar en `ServicioAgenda`.** Es un borde transaccional, no un caso de uso, y los tests unitarios del dominio no lo cubrirían.

### D2. Reprogramar con el mismo profesional no vuelve a exigir la habilitación

Es el mismo criterio que `ReasignarTurno`: el turno ya estaba dado. Si la habilitación se quitó después, reprogramar no debería trabar al paciente. Queda explícito en el spec, en el escenario "Reprogramar con el mismo profesional".

### D3. `CrearSobreturno` controla igual que crear

El control va después de `verificarRecursos` y antes de `ReglasDisponibilidad`, así se respeta la misma prioridad. Se exige siempre, porque RN-AG-06 no exceptúa las especialidades.

### D4. Constructores compatibles

`CrearTurno` y `CrearSobreturno` suman un constructor con `HabilitacionProfesional`. Los constructores actuales delegan en el nuevo con `HabilitacionProfesional.TODOS` (una constante `(p, q) -> true`, definida como constante local en `agenda/application` para no tocar el puerto de C-04). Es el mismo patrón que `DisponibilidadProfesional.SIN_RESTRICCIONES` de C-07, así que los tests unitarios actuales no cambian. `AgendaConfig` usa el constructor nuevo con el adaptador JPA.

### D5. Los fixtures de integración cargan habilitaciones reales

`IntegracionAgendaBase` inserta `profesional_prestacion` para la Dra. A y el Dr. B en las prestaciones que usan sus tests. Los fixtures de otros módulos que creen turnos por la API y fallen se ajustan solo con esos `INSERT`, avisando a su dueño en el PR.

*Alternativa descartada:* un bean de test que habilite a todos. Escondería la regla justo en los tests que deben probarla.

## Risks / Trade-offs

- [Fixtures ajenos fallan con `409`] → Correr todo `integrationTest`. Arreglar solo con `INSERT INTO profesional_prestacion` y listar los archivos en el PR para Emanuel, Juan Sebastián y Juan Cruz (regla dura 3).
- [El frontend muestra un mensaje genérico ante el código nuevo] → Avisar a Enzo para que agregue `PROFESIONAL_NO_HABILITADO` a `MENSAJES_POR_CODIGO`.
- [Turnos ya dados con un profesional que perdió la habilitación] → Siguen válidos y se pueden reprogramar con el mismo profesional (D2). Revalidarlos queda fuera de alcance.

## Migration Plan

Sin migración de datos ni de esquema. Para volver atrás, alcanza con revertir el PR: el código de error ya existía.
