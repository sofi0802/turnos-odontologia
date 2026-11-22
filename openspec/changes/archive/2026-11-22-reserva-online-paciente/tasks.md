# Tasks

> Strict TDD: en cada tarea primero el test en rojo (RED), después el mínimo código para verde (GREEN) y al final el refactor con todo en verde (REFACTOR). Un test por escenario, con `@DisplayName` igual al nombre del escenario. Solo datos ficticios (Dra. A, Dr. B, "Paciente Uno" `99000001`, "Paciente Dos" `99000002`, emails `@example.com`). Todo el código nuevo va en `backend/.../reserva/` (Juan Sebastián). No se editan archivos de `agenda/`, `configuracion/`, `notificaciones/` ni `shared/persistence/`; si hiciera falta, se frena y se avisa a su dueño en el PR.
>
> Comandos en Windows (Git Bash), desde `backend/`: `export JAVA_TOOL_OPTIONS=-Duser.timezone=America/Argentina/Buenos_Aires` y `DB_URL`, `DB_USER`, `DB_PASSWORD` apuntando al PostgreSQL local (valores solo en el entorno, nunca en el repo). Unitarios: `./gradlew test`. Integración: `./gradlew integrationTest`.

## 1. Red de seguridad

- [x] 1.1 Con la rama `change/C-10-reserva-online-paciente` al día con `master`, correr `./gradlew test` y `./gradlew integrationTest` sin cambios y anotar en el PR la cantidad de tests en verde. Verificar que los dos terminan en verde; si algo falla antes de tocar código, frenar y avisar a la líder (no es de este change).
- [x] 1.2 Crear la estructura `reserva/{domain,application,infrastructure,api}` y `ReservaConfig` vacía, y verificar con `./gradlew build` que compila y que `reserva/domain` y `reserva/application` no importan `org.springframework` (`grep -r "org.springframework" backend/src/main/java/ar/turnos/odontologia/reserva/domain backend/src/main/java/ar/turnos/odontologia/reserva/application` sin resultados).

## 2. Dominio puro de la reserva (unitarios, sin base)

- [x] 2.1 `GrillaHorarios` (design D2): inicios cada 15 minutos desde el comienzo de cada franja del día, con el turno completo dentro de la franja y sin inicios anteriores al reloj. Verificar con tests de unidad: franja de 10:00 a 11:00 con 30 minutos da 10:00, 10:15 y 10:30; día sin franja da vacío; reloj a las 10:20 deja solo 10:30; inicio igual al reloj se incluye; paso configurable.
- [x] 2.2 `CalculadoraHuecos` (design D2) usando `ReglasDisponibilidad.verificar` y `Agenda.verificar` sin reescribir comparaciones de intervalos. Verificar con tests de unidad: turno cancelado no ocupa; margen de 10 minutos excluye 10:30 tras un turno de 10:00 a 10:30; bloqueo de 10:00 a 10:30 excluye 10:00 y 10:15 y deja 10:30; licencia excluye todo; profesional libre sin sillón libre no aporta el horario; turno original ignorado en la reprogramación.
- [x] 2.3 `OrdenSillones` (design D4): habitual activo primero, después los activos por id. Verificar con tests de unidad: habitual libre, habitual ocupado, habitual inactivo, sin habitual.
- [x] 2.4 `AsignadorProfesional` y la interfaz `Desempate` (design D3): menos turnos activos del día local, desempate inyectado, cancelados no cuentan, original excluido en la reprogramación. Verificar con tests de unidad con un `Desempate` fijo (primero y último).
- [x] 2.5 `PlazoModificacion` (design D6): permitido con 24 h o más. Verificar con tests de unidad: exactamente 24 h permitido, 23 h 59 min rechazado, turno ya iniciado rechazado.

## 3. Disponibilidad pública (`disponibilidad-online`)

- [x] 3.1 Puerto `CatalogoReserva` y su adaptador JDBC de solo lectura (design D1, D10): prestación con duración y estado, profesionales activos habilitados con sillón habitual, sillones activos. Verificar con un test de integración `@Tag("integration")` del adaptador contra PostgreSQL real con datos ficticios.
- [x] 3.2 Caso de uso `ConsultarDisponibilidad` con fakes en memoria y `Clock.fixed` en 2026-12-01 08:00 (Buenos Aires). Verificar con tests de unidad la unión sin repetidos, la exclusión de no habilitados e inactivos, y la lista vacía para prestación inactiva y profesional elegido no habilitado.
- [x] 3.3 `DisponibilidadPublicaController` (`GET /api/v1/public/disponibilidad`), DTOs record y `ReservaExceptionHandler` (422 `DATOS_INVALIDOS` con campos). Crear `IntegracionReservaBase` en los tests de `reserva/` (extiende `IntegracionSeguridadBase`, reloj fijo, franjas y especialidades por escenario; no se toca `IntegracionAgendaBase`). Verificar con un test de integración por escenario de `specs/disponibilidad-online` en los requisitos "Horarios libres de un profesional elegido" (4), "Unión de horarios sin preferencia" (3), "Un horario necesita un sillón libre" (1), "Ausencias, bloqueos y horarios pasados" (4), "Disponibilidad pública sin datos de terceros" (1), "Validación de la consulta de disponibilidad" (3) y "Combinaciones que no se ofrecen" (2).

## 4. Catálogo público (`disponibilidad-online`, propuesta a confirmar)

- [x] 4.1 `CatalogoPublicoController` con `GET /api/v1/public/prestaciones` y `GET /api/v1/public/profesionales?prestacionId`, devolviendo solo `id`, `nombre` (y `duracionMinutos` en prestaciones). Verificar con un test de integración por escenario del requisito "Catálogo público para reservar" (3). Si la líder rechaza el catálogo público, quitar este grupo y el requisito del spec antes de seguir.

## 5. Reserva online (`reserva-online`)

- [x] 5.1 Caso de uso `ReservarTurno` (design D5) con un doble de la agenda que registra los comandos y puede simular un rechazo `SOLAPAMIENTO_SILLON` por carrera. Verificar con tests de unidad: "Sin preferencia con dos profesionales libres", "Empate resuelto al azar" (desempate primero y último), "Profesional no habilitado excluido de la asignación", "Solo se asigna un profesional libre", "Los turnos cancelados no cuentan", reintento con la siguiente combinación tras la carrera, y `SIN_DISPONIBILIDAD` con hasta 3 alternativas cuando se agotan.
- [x] 5.2 Traducción de rechazos (design D7): los códigos de disponibilidad de la agenda salen como `SIN_DISPONIBILIDAD`; `INICIO_EN_EL_PASADO`, `PROFESIONAL_NO_HABILITADO` y `RECURSO_INACTIVO` salen tal cual. Verificar con tests de unidad del caso de uso y del `ReservaExceptionHandler` (409 con `codigo` y `alternativas`).
- [x] 5.3 `ReservasPacienteController` con `POST /api/v1/paciente/reservas`: paciente tomado solo de `UsuarioAutenticado.pacienteId()`, sin campo de sillón ni de paciente en el request. Verificar con un test de integración por escenario de `specs/reserva-online`: "Reservar un turno online" (1), "El paciente de la reserva es el de la sesión" (1), "Asignación del profesional sin preferencia" (5, con el bean `Desempate` reemplazado por uno fijo en el test), "Asignación del sillón" (4), "Horario no disponible" (5), "Rechazos de negocio de la reserva" (3), "Validación de la reserva" (3) y "Solo el paciente con sesión reserva" (3).
- [x] 5.4 Carreras (design D5): verificar con dos tests de integración con dos hilos y `CountDownLatch`, sin `500`, los escenarios "Dos pacientes piden el mismo profesional a la misma hora" y "Dos pacientes sin preferencia a la misma hora", comprobando en la base que no quedan turnos superpuestos.

## 6. Mis turnos (`turnos-paciente`)

- [x] 6.1 Puerto `ReservasPaciente` y adaptador JDBC (design D10): turnos del paciente con prestación y profesional, búsqueda por número de reserva normalizado **y** paciente, DNI del paciente. Verificar con un test de integración del adaptador que un número de otro paciente devuelve vacío.
- [x] 6.2 Caso de uso `ConsultarMisTurnos` y `GET /api/v1/paciente/turnos` con `modificableOnline`. Verificar con un test de integración por escenario: "Solo los turnos propios", "Intento de ver los turnos de otro paciente", "Turnos dentro y fuera del plazo" y "Mis turnos sin sesión".

## 7. Cancelar y reprogramar online (`turnos-paciente`)

- [x] 7.1 Caso de uso `CancelarReservaOnline` (design D6) que compone `ServicioAgenda.cancelar` con el DNI leído del paciente de la sesión. Verificar con tests de unidad el orden 404 → `TURNO_NO_ACTIVO` → plazo, y con un test de integración por escenario: "Cancelación con anticipación", "Cancelación exactamente 24 h antes", "Cancelación a 23 h rechazada", "Número de reserva de otro paciente", "Cancelar un turno ya cancelado" y "El personal intenta cancelar por la ruta del paciente".
- [x] 7.2 Caso de uso `ReprogramarReservaOnline` (design D6) que reutiliza la asignación de la reserva ignorando el original y compone `ServicioAgenda.reprogramar`. Verificar con un test de integración por escenario: "Reprogramación a 23 h rechazada", "Número de reserva inexistente", "Reprogramación a otro horario", "Reprogramación sobre el propio horario", "Reprogramación sin preferencia", "Reprogramación a un horario no disponible" y "Reprogramación sin inicio".

## 8. Auditoría online (`auditoria-turnos`)

- [x] 8.1 Pasar el actor `paciente-online:{usuarioId}` en todas las llamadas a `ServicioAgenda` (design D8). Verificar con un test de integración por escenario del requisito "Actor de las operaciones online del paciente" (3), comprobando en `turno_auditoria` que no aparecen el email, el DNI ni el número de reserva.

## 9. Aviso de reserva y cancelación (puerto, sin adaptador real)

- [x] 9.1 Puerto `AvisoReservaOnline`, `AvisoReservaOnlineInactivo` y su uso por `ObjectProvider` después de cada operación confirmada (design D9). Verificar con tests de unidad que se llama con el id del turno solo cuando la operación se confirma, que una excepción del aviso no deshace la reserva, y que el log del adaptador inactivo no contiene nombre, DNI, email ni número de reserva.

## 10. Verificación final

- [x] 10.1 Correr `./gradlew build` y `./gradlew integrationTest` y verificar que todo está en verde, con la misma cantidad de tests previos de la tarea 1.1 más los nuevos, sin tests deshabilitados.
- [x] 10.2 Revisar el diff y verificar: ningún archivo fuera de `reserva/` y de los tests de `reserva/` (si hay alguno, listarlo en el PR con su dueño); ninguna migración nueva; ninguna entidad JPA en las respuestas; ningún dato de salud ni DNI en logs; `openspec validate reserva-online-paciente --strict` en verde.
- [ ] 10.3 Abrir el PR a `master` con: preguntas abiertas pendientes, coordinación con C-11 (adaptador de `AvisoReservaOnline` y links de cancelar/reprogramar) y con C-12 (contrato de los endpoints), y marcar `[x]` en CHANGES.md solo cuando la líder apruebe la verificación.

## Workflow follow-up

- Archivar el change con `openspec archive reserva-online-paciente` después del merge y de la revisión de la líder.
- Verificar que los specs nuevos (`disponibilidad-online`, `reserva-online`, `turnos-paciente`) y el delta de `auditoria-turnos` quedan en `openspec/specs/`.
