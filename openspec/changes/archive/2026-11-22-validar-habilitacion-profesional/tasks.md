# Tasks

Convención **Strict TDD** para cada tarea:
1. **RED**: escribir el test con un `@DisplayName` igual al nombre del escenario y verlo fallar.
2. **GREEN**: escribir lo mínimo para que pase.
3. **TRIANGULATE**: sumar variantes sin debilitar aserciones.
4. **REFACTOR**: limpiar con todo en verde.

Nunca se deshabilita, borra ni debilita un test que falla (regla dura 7).

Hay **8 escenarios nuevos** y cada uno tiene su test:
- 7 en `agenda-turnos`: los 6 de "Profesional habilitado para la prestación" y "Profesional no habilitado y ausente a la vez";
- 1 en `agenda-api`: "Turno con un profesional no habilitado".

Los escenarios que ya existían y se copian en los requisitos MODIFIED siguen cubiertos por sus tests actuales.

**Dónde quedaron los tests**: los 7 de `agenda-turnos`, más 3 de triangulación, están juntos en `HabilitacionEnLaAgendaTest`, con el doble `HabilitacionesEnMemoria`. Así crear, reprogramar y sobreturno comparten el mismo fixture. El de `agenda-api` está en `TurnosApiIntegrationTest`.

Red de seguridad, que debe seguir en verde sin cambiar sus aserciones:
- unitarios: `CrearTurnoTest`, `CrearSobreturnoTest`, `ReprogramarTurnoTest`, `ReasignarTurnoTest`, `ExtenderTurnoTest` y `DisponibilidadEnLaAgendaTest`;
- integración: toda la suite `integrationTest`.

## 0. Línea base

- [x] 0.1 Con Postgres levantado y las variables de entorno de test, ejecutar `./gradlew build integrationTest` sin cambios de código. Verificar que pasa (referencia: 876 tests, 0 fallas en `master` 3fdccd7) y anotar el total.

## 1. Crear turno (`CrearTurno`)

- [x] 1.1 Cablear la habilitación en `CrearTurno` (design D1 y D4).
  - **RED**: en `CrearTurnoTest`, escribir "Crear un turno con un profesional no habilitado" y "Crear un turno con un profesional habilitado", con un fake de `HabilitacionProfesional` en memoria, y verlos fallar.
  - **GREEN**: sumar el constructor con `HabilitacionProfesional`, hacer que los constructores actuales deleguen con "todos habilitados" y agregar el parámetro `exigirHabilitacion` a `armar`, verificando después de `verificarRecursos`. `ejecutar` exige.
  - **TRIANGULATE**: el rechazo no guarda ningún turno en el repositorio en memoria.
  - Verificar con `./gradlew test --tests '*CrearTurnoTest'`.
- [x] 1.2 Prioridad del error.
  - **RED**: escribir "Profesional inactivo y no habilitado" (espera `RECURSO_INACTIVO`) y "Profesional no habilitado y ausente a la vez" (espera `PROFESIONAL_NO_HABILITADO`, con `DisponibilidadEnMemoria`), y verlos fallar si el orden no es el correcto.
  - **GREEN**: ajustar el orden.
  - **TRIANGULATE**: un caso no habilitado con solapamiento también responde `PROFESIONAL_NO_HABILITADO`.
  - Verificar con `./gradlew test --tests '*CrearTurnoTest' --tests '*DisponibilidadEnLaAgendaTest'`.

## 2. Reprogramar (`ReprogramarTurno`)

- [x] 2.1 Exigir la habilitación solo al cambiar de profesional (design D1 y D2).
  - **RED**: en `ReprogramarTurnoTest`, escribir "Reprogramar a un profesional no habilitado" (el original queda `RESERVADO` y no hay turno nuevo) y "Reprogramar con el mismo profesional" (sin habilitación vigente, se reprograma igual), y verlos fallar.
  - **GREEN**: agregar `preparar(comando, reemplazaA, exigirHabilitacion)` en `CrearTurno`. `ReprogramarTurno` pasa `true` solo si el profesional cambia. La firma de 2 parámetros queda sin exigir, para que `ReasignarTurno` no cambie.
  - **TRIANGULATE**: reprogramar a otro profesional **habilitado** se acepta.
  - Verificar con `./gradlew test --tests '*ReprogramarTurnoTest' --tests '*ReasignarTurnoTest' --tests '*ExtenderTurnoTest'`, todos en verde.

## 3. Sobreturno (`CrearSobreturno`)

- [x] 3.1 Exigir la habilitación en el sobreturno (design D3 y D4).
  - **RED**: en `CrearSobreturnoTest`, escribir "Sobreturno con un profesional no habilitado" y verlo fallar.
  - **GREEN**: sumar el constructor con `HabilitacionProfesional`, delegar los actuales con "todos habilitados" y verificar después de `verificarRecursos` y antes de `ReglasDisponibilidad`.
  - **TRIANGULATE**: un motivo vacío sigue respondiendo `DATOS_INVALIDOS` aunque el profesional no esté habilitado, porque la validación del pedido va primero; el rechazo no ocupa ningún sillón.
  - Verificar con `./gradlew test --tests '*CrearSobreturnoTest'`.

## 4. Cableado, API y fixtures de integración

- [x] 4.1 Cableado en `AgendaConfig`.
  - Pasar `HabilitacionProfesional` a los beans `crearTurno` y `crearSobreturno`.
  - Verificar que la aplicación arranca con `./gradlew integrationTest --tests '*AgendaConfigIntegrationTest'`.
- [x] 4.2 Escenario por la API.
  - **RED**: en el test de integración de la API de turnos, escribir "Turno con un profesional no habilitado" (`POST /api/v1/turnos` → `409` con `codigo` `PROFESIONAL_NO_HABILITADO` y sin filas nuevas en `turno`) y verlo fallar.
  - **GREEN**: en `IntegracionAgendaBase`, cargar las habilitaciones que usan sus tests en `profesional_prestacion` y la prestación "Tratamiento de conducto" sin habilitar a la Dra. A.
  - Verificar con `./gradlew integrationTest --tests 'ar.turnos.odontologia.agenda.*'`.
- [x] 4.3 Fixtures de otros módulos.
  - Correr toda la suite `./gradlew build integrationTest`.
  - Si un fixture de otro módulo falla con `PROFESIONAL_NO_HABILITADO` (por ejemplo `IntegracionClinicaBase`, `IntegracionSeguridadBase` o los de `notificaciones`), corregirlo **solo** con los `INSERT INTO profesional_prestacion` necesarios, sin tocar aserciones.
  - Anotar cada archivo y su dueño para el PR.
  - Verificar que la suite completa pasa, con el total de la tarea 0.1 más los tests nuevos.

## 5. Documentación y PR

- [x] 5.1 Actualizar `CHANGES.md` con una línea de seguimiento de C-06 que mencione RN-AG-15, el change y la rama.
  - Verificar con `git diff CHANGES.md`.
- [x] 5.2 Preparar la descripción del PR, que debe incluir:
  - los archivos de fixtures ajenos tocados, con su dueño (regla dura 3);
  - el aviso a Enzo para que agregue `PROFESIONAL_NO_HABILITADO` a `MENSAJES_POR_CODIGO`;
  - el aviso de que `ReasignarTurno` y `ExtenderTurno` no cambian.
  - Verificar que la lista de archivos coincide con `git diff --stat master`.

## Workflow follow-up

- Verificar el change contra specs y design, archivarlo con `openspec archive validar-habilitacion-profesional` y sincronizar las specs `agenda-turnos` y `agenda-api`.
- Marcar el seguimiento como completo en `CHANGES.md` después del merge.
