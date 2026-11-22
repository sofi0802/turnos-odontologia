# Tasks

## 1. Migración y seed

- [x] 1.1 Crear `db/migration/V5__horarios_ausencias_bloqueos.sql` (design D5, D7 y D9) con:
  - `horario_trabajo`, con `CHECK` de día de 1 a 7 y `hora_hasta > hora_desde`, y el `EXCLUDE` de franjas superpuestas;
  - `ausencia_profesional`, con `CHECK` de tipo y `fin > inicio`;
  - `bloqueo_agenda`, con `CHECK fin > inicio`;
  - los índices por profesional.

  Verificar con `./gradlew integrationTest` que Flyway migra y que los tests existentes siguen en verde. Hasta el grupo 3, las reglas no están conectadas.
- [x] 1.2 Crear `db/seed/V5_1__seed_horarios_dev.sql` con los horarios ficticios de la KB, buscando a cada profesional por matrícula. Verificar levantando la app con `dev` que la Dra. Ana Ejemplo tiene franjas de lunes a viernes de 08:00 a 13:00, que el Dr. Bruno Prueba las tiene de 10:00 a 18:00, y que el perfil `test` no las carga.
- [x] 1.3 Dejar como test de integración del esquema que la base rechaza dos franjas superpuestas del mismo profesional y día, y acepta dos contiguas.

## 2. Reglas puras

- [x] 2.1 Crear `FranjaHoraria`, `HorarioSemanal`, `PeriodoSinAtencion` y `ReglasDisponibilidad` en `agenda/domain/reglas/`, sin Spring ni JPA, y los códigos `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA` y `FUERA_DE_HORARIO` en `CodigoRechazo`. Verificar con tests de unidad:
  - franja inválida;
  - franjas superpuestas y contiguas;
  - turno que termina justo en el fin de la franja;
  - turno que cruza dos franjas;
  - día sin franjas;
  - cruce exacto con un período;
  - prioridad entre ausencia, bloqueo y horario;
  - `exigirHorario = false`.

## 3. Integración con la agenda (revisión de Sofia y Juan Cruz)

- [x] 3.1 Agregar el puerto `DisponibilidadProfesional` y los constructores nuevos de `CrearTurno` y `CrearSobreturno`, conservando los actuales (design D2). Verificar con tests de unidad, usando una disponibilidad en memoria, estos escenarios de `agenda-turnos`:
  - "Turno dentro de la franja";
  - "Turno que termina después de la franja";
  - "Turno en un día sin franjas";
  - "Turno que cruza el corte entre dos franjas";
  - "Turno en la segunda franja del día";
  - "Profesional sin horarios cargados";
  - "Reprogramar fuera de horario";
  - "Turno durante una licencia";
  - "Turno que termina justo cuando empieza la ausencia";
  - "Sobreturno durante una ausencia";
  - "Turno durante un bloqueo";
  - "Extensión que entra en un bloqueo";
  - "Sobreturno fuera de horario";
  - "Extensión que pasa el fin de la franja";
  - "Choque por profesional y por sillón a la vez";
  - "Ausencia y solapamiento a la vez";
  - "Fuera de horario y solapamiento a la vez".

  Los tests unitarios de C-01, C-06 y C-08 tienen que seguir en verde sin cambios.
- [x] 3.2 Implementar el adaptador `DisponibilidadProfesionalJpa`, cablear los constructores nuevos en `AgendaConfig` y cargar franjas de 00:00 a 23:59 todos los días en `IntegracionAgendaBase`. Verificar que todos los tests de integración de C-06 y C-08 siguen en verde. Dejar este grupo en un commit aparte para la revisión de Sofia.

## 4. Horarios de trabajo

- [x] 4.1 Implementar el caso de uso y el controlador de `GET` y `PUT /profesionales/{id}/horarios` con reemplazo transaccional, DTOs `record`, `@PreAuthorize` y el control de "propio profesional" (design D7 y D8). Verificar que compila.
- [x] 4.2 Crear la base de integración de C-07 con los usuarios `admin`, `recepcion`, `ana.ejemplo` y `bruno.prueba`, y escribir los tests de estos escenarios:
  - "Dos franjas el mismo día";
  - "Redefinir reemplaza las franjas anteriores";
  - "Quitar todos los horarios";
  - "Franja con el fin antes del inicio";
  - "Franjas superpuestas en el mismo día";
  - "Franjas contiguas";
  - "La recepción consulta los horarios";
  - "La recepción intenta modificar los horarios";
  - "Un odontólogo consulta los horarios de otro";
  - "Horarios de un profesional inexistente".

  Verificar que pasan.

## 5. Ausencias y bloqueos

- [x] 5.1 Implementar los casos de uso y controladores de ausencias y bloqueos (`POST`, `GET` y `DELETE`), con la lista de turnos afectados sin datos del paciente y sin el motivo en logs (design D5, D6 y D8). Verificar que compila.
- [x] 5.2 Escribir los tests de integración de estos escenarios y verificar que pasan:
  - "Registrar una licencia";
  - "Período con el fin antes del inicio";
  - "Borrar una ausencia";
  - "El odontólogo consulta sus ausencias";
  - "El odontólogo intenta registrar una ausencia";
  - "El odontólogo bloquea su agenda";
  - "Borrar un bloqueo";
  - "Un odontólogo bloquea la agenda de otro";
  - "La recepción consulta los bloqueos";
  - "Licencia con turnos dados";
  - "Bloqueo sin turnos dados";
  - "Los turnos afectados no muestran al paciente".
- [x] 5.3 Escribir tests de integración de punta a punta: crear un turno por la API durante una licencia y durante un bloqueo registrados por la API. Verificar `409 PROFESIONAL_AUSENTE` y `409 AGENDA_BLOQUEADA`.

## 6. Reasignación de turnos afectados (revisión de Sofia; aviso de C-11)

- [x] 6.1 Agregar `PROFESIONAL_NO_HABILITADO` a `CodigoRechazo` y el caso de uso `ReasignarTurno` con su comando en `agenda/application`, y `ServicioAgenda.reasignar` con auditoría `REPROGRAMAR` y `CREAR` (design D10). Verificar con tests de unidad:
  - mismo horario con otro profesional;
  - otro horario;
  - profesional no habilitado;
  - turno no activo;
  - nuevo horario en ausencia;
  - que el original solo se cancela si el nuevo es válido.

  Los tests de C-06 y C-08 tienen que seguir en verde. Va en el commit aparte para la revisión de Sofia.
- [x] 6.2 Agregar el puerto `AvisoReasignacion`, el caso de uso de C-07 (la ausencia es del profesional, el turno está afectado, se reasigna y se pide el aviso después de confirmar) y el endpoint `POST /profesionales/{id}/ausencias/{ausenciaId}/turnos/{turnoId}/reasignar` para `ADMIN` y `RECEPCIONISTA`. Agregar también el adaptador `AvisoReasignacionInactivo`, que se usa por medio de un `ObjectProvider` cuando no hay otro adaptador (design D10, cambio en el apply). `agenda` no importa nada de `notificaciones`. Verificar que compila y que, sin otro adaptador, el contexto usa el inactivo.
- [x] 6.3 Escribir los tests de integración, con un doble del aviso, de estos escenarios y verificar que pasan:
  - "Reasignar a otro profesional en el mismo horario";
  - "Reprogramar con el mismo profesional después de la licencia";
  - "Profesional no habilitado para la prestación";
  - "El nuevo horario también cae en la licencia";
  - "Turno que no está afectado por la ausencia";
  - "El odontólogo intenta reasignar";
  - "Se pide el aviso para el turno nuevo";
  - "Un aviso que falla no deshace la reasignación".
- [x] 6.4 Solo si C-11 ya está en master al hacer el apply: agregar `AvisoReasignacionNotificaciones` en `notificaciones/infrastructure`, con aviso a Juan Cruz. Delega en `ServicioNotificaciones.avisarReasignacion`. Verificar con un test de integración que una reasignación de un paciente con consentimiento deja una notificación `REASIGNACION` para el turno nuevo. Si C-11 todavía no está, marcar esta tarea como hecha anotando que el adaptador lo suma Juan Cruz en C-11, como se acordó.
  - **Resultado del apply (2026-11-22)**: C-11 entró en master durante el apply (PR #30). C-07 agregó `AvisoReasignacionNotificaciones` y su test de integración, con aviso a Juan Cruz. La prueba del aviso inactivo pasó a un test de unidad (`ServicioReasignacionTest`), porque el contexto completo ya tiene el adaptador real, y el doble de `ReasignacionIntegrationTest` es `@Primary`.

## 7. Verificación integral

- [x] 7.1 Correr `./gradlew test integrationTest` completo y verificar:
  - que los 47 escenarios (30 de `disponibilidad-profesional` y 17 de `agenda-turnos`, incluido el escenario existente "Choque por profesional y por sillón a la vez", que se vuelve a probar con las reglas conectadas) tienen un test con `@DisplayName` igual a su nombre, todos en verde;
  - que los tests de C-01, C-03, C-04, C-05, C-06 y C-08 siguen pasando.
- [x] 7.2 Verificar con `grep` que `agenda/domain/reglas` no importa `org.springframework` ni `jakarta.persistence`, y que ningún controlador devuelve una entidad JPA.
- [x] 7.3 Correr `openspec validate horarios-ausencias-bloqueos --strict` y verificar que es válido.

## Workflow follow-up

- Abrir el PR de `change/C-07-horarios-ausencias-bloqueos` a `master` con estos avisos:
  - a **Sofia**, por `CrearTurno`, `CodigoRechazo`, `AgendaConfig`, `IntegracionAgendaBase`, `ReasignarTurno` y `ServicioAgenda.reasignar`;
  - a **Juan Cruz**, por `CrearSobreturno` y por el puerto `AvisoReasignacion`, que implementa su módulo;
  - si C-11 no estaba en master, aclarar que el aviso de reasignación queda inactivo hasta que C-11 sume `AvisoReasignacionNotificaciones`;
  - a **todo el equipo**: sin horarios cargados no se crean turnos.
- Marcar `[x]` en C-07 de `CHANGES.md`.
- Al archivar, actualizar el `## Purpose` de `openspec/specs/agenda-turnos/spec.md` para decir que en sus escenarios los profesionales trabajan todo el día, salvo que el escenario diga otra cosa.
- Archivar con `openspec archive horarios-ausencias-bloqueos` y verificar los specs `disponibilidad-profesional` y `agenda-turnos`.
