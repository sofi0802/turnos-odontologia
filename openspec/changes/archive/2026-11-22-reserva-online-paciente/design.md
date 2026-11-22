# Design

## Context

Motivación y alcance: ver `proposal.md`. Comportamiento exigido: ver `specs/`.

Lo que ya existe y condiciona el diseño (leído en `master` a `983d573`):

- **`ServicioAgenda`** (`agenda/infrastructure`, Sofia) es el borde transaccional público: `crear`, `cancelar` y `reprogramar` reciben un comando y un `actor`, abren la transacción, registran la auditoría y reintentan ante deadlock. Debajo, `CrearTurno` aplica pasado, recursos, `ReglasDisponibilidad` (C-07) y `Agenda.verificar`, y la base aplica los dos `EXCLUDE USING gist` (DD-03). Un choque detectado por la base sale como `TurnoRechazadoException` con `SOLAPAMIENTO_*`.
- **`CancelarTurno` y `ReprogramarTurno`** identifican el turno con `turnoId` + DNI + número de reserva (`IdentificacionReserva`). No conocen el plazo de 24 h: el personal puede operar siempre.
- **`CrearTurno` no valida RN-AG-15** (habilitación). C-07 lo dejó anotado como pendiente de C-06; `ReasignarTurno` sí lo valida con el puerto `HabilitacionProfesional` (C-04).
- **Puertos públicos que se pueden inyectar**: `TurnoRepository` (`buscarQueIntersecan` con profesional o sillón nulos, en cualquier estado), `DisponibilidadProfesional` (franjas y períodos por ventana, C-07), `HabilitacionProfesional`, y los beans `Margen`, `Clock` y `ZoneId` de `AgendaConfig`. `SillonesDisponibles.preferidoPara` todavía devuelve vacío (TODO de C-08), aunque la columna `profesional.sillon_habitual_id` existe desde V2.
- **Seguridad** (C-05): `/api/v1/public/**` es `permitAll`, `/api/v1/paciente/**` exige `PACIENTE`, CSRF activo. El principal `UsuarioAutenticado` trae `usuarioId()` y `pacienteId()`.
- **Esquema**: `turno` no tiene columna `origen` (la KB la modela). No hay migración reservada para C-10.
- **C-11** (en curso, Juan Cruz) deja fuera los avisos `CONFIRMACION` y `CANCELACION` y los links de cancelar y reprogramar; C-07 definió el patrón de puerto de aviso con adaptador inactivo y `ObjectProvider`.

## Goals / Non-Goals

**Goals:**
- Que la reserva online cree turnos solo a través de `ServicioAgenda`, para que las dos garantías contra solapamientos y la auditoría sean exactamente las del personal (regla dura 5).
- Que la disponibilidad pública y la reserva usen el mismo cálculo, así un horario ofrecido es uno que la reserva acepta (salvo carreras).
- Que todo el código nuevo viva en `reserva/` (dueño: Juan Sebastián) sin editar archivos de otros módulos.
- Tests deterministas: `Clock` fijo y desempate inyectado.

**Non-Goals:**
- Cambiar `CrearTurno`, `ReprogramarTurno` o la auditoría de `agenda/`.
- Cachear la disponibilidad o precalcular huecos: un consultorio chico tiene decenas de turnos por día (DD-05).
- Disponibilidad de varios días en un pedido.

## Decisions

### D1. Paquete `reserva/` con puertos y adaptadores
- `reserva/domain` (sin Spring): `GrillaHorarios` (inicios candidatos), `CalculadoraHuecos` (decide si un par profesional + sillón está libre en un intervalo), `AsignadorProfesional` (RN-RO-05), `OrdenSillones` (RN-RO-06), `PlazoModificacion` (24 h) y `Desempate` (interfaz).
- `reserva/application`: casos de uso `ConsultarDisponibilidad`, `ReservarTurno`, `ConsultarMisTurnos`, `CancelarReservaOnline` y `ReprogramarReservaOnline`, y los puertos de lectura propios `CatalogoReserva` (prestación con duración y estado, profesionales activos habilitados con sillón habitual, sillones activos), `ReservasPaciente` (turno por número de reserva y paciente, lista de turnos del paciente, DNI del paciente) y `AvisoReservaOnline`.
- `reserva/infrastructure`: adaptadores JDBC de solo lectura para `CatalogoReserva` y `ReservasPaciente` (consultas parametrizadas, sin entidades JPA nuevas), `DesempateAleatorio` y `ReservaConfig`, que arma los casos de uso sin anotaciones en las clases de dominio.
- `reserva/api`: `DisponibilidadPublicaController` y `CatalogoPublicoController` bajo `/api/v1/public`, `ReservasPacienteController` bajo `/api/v1/paciente`, DTOs record y `ReservaExceptionHandler` con `basePackages = "ar.turnos.odontologia.reserva.api"`.

*Alternativa descartada*: agregar los casos de uso en `agenda/application` para usar `CrearTurno.preparar`. Tocaba el módulo de Sofia en cada archivo y mezclaba la política online (plazo, asignación, anonimato de errores) con la agenda del personal.

### D2. Cálculo de huecos reutilizando las reglas de la agenda
Para una fecha local `F` y una prestación de duración `d`:
1. Ventana del día: `[F 00:00, F+1 00:00)` en el `ZoneId` de la agenda.
2. Candidatos: profesionales activos habilitados (o el elegido, si cumple). Por cada uno, `DisponibilidadProfesional.periodos(id, ventana)` y `TurnoRepository.buscarQueIntersecan(id, null, ventana ampliada por el margen)`. Por cada sillón activo, `buscarQueIntersecan(null, sillonId, ventana ampliada)`. Son `P + S + P` lecturas por pedido, no una por horario.
3. Inicios: para cada franja del día de la semana, desde `desde` cada 15 minutos mientras `inicio + d <= hasta` (`app.reserva.paso-minutos`, por defecto 15, *propuesta*), descartando los anteriores a `Clock.instant()`.
4. Un intervalo es libre para el profesional `p` si `ReglasDisponibilidad.verificar(intervalo, periodos, zona, true)` no lanza, y existe un sillón `s` (en el orden de D4) tal que `Agenda.verificar(candidato, turnosDe(p) ∪ turnosDe(s))` no lanza, donde `candidato = Turno.reservar(...)` con un número de reserva descartable.
5. Sin preferencia: unión de inicios en un `TreeSet`, así cada horario aparece una vez y ordenado.

Se reutilizan `ReglasDisponibilidad` y `Agenda` tal cual: no se reescribe ninguna comparación de intervalos ni de margen. `TurnoRechazadoException` se usa como "no libre" dentro del cálculo; el costo es aceptable para decenas de candidatos.

*Alternativa descartada*: una consulta SQL con `tstzrange` para encontrar huecos. Duplicaba en SQL las reglas de franja, ausencia y margen que hoy viven en el dominio.

### D3. Asignación del profesional (RN-RO-05) y desempate inyectado
Entre los candidatos libres en el intervalo pedido (D2), se cuenta para cada uno los turnos activos (`RESERVADO`, `CONFIRMADO`, incluidos sobreturnos) que empiezan en el día local, con los turnos ya leídos en D2. En la reprogramación no se cuenta el turno original. Se queda el mínimo; si hay varios, `Desempate.elegir(empatadosOrdenadosPorId)`. En producción `DesempateAleatorio` usa `java.util.random.RandomGenerator` (no hace falta `SecureRandom`: no es un secreto). En los tests, un desempate fijo (primero o último).

### D4. Asignación del sillón (RN-RO-06)
Orden: el sillón habitual del profesional si está activo, y después los demás activos por id ascendente. Se elige el primero libre con margen. El habitual sale de `CatalogoReserva` (columna `sillon_habitual_id`). No se modifica `SillonesDisponibles` de `agenda/`: queda como mejora posible para el sobreturno, fuera de este change.

### D5. Reserva: crear por `ServicioAgenda` y reintentar con otra combinación
`ReservarTurno`:
1. Valida existencia (422) y estado: prestación o profesional elegido inactivos → `RECURSO_INACTIVO`; profesional elegido no habilitado → `PROFESIONAL_NO_HABILITADO`; inicio pasado → `INICIO_EN_EL_PASADO`. Estos códigos ya existen en `CodigoRechazo`.
2. Calcula la lista ordenada de combinaciones libres (profesional por D3, sillón por D4). Si está vacía → `SIN_DISPONIBILIDAD` con alternativas (D7).
3. Prueba la primera con `ServicioAgenda.crear(new CrearTurnoCommand(pacienteSesion, p, s, prestacion, inicio), actor)`. Si responde `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON` o `MARGEN_INSUFICIENTE` (alguien ganó la carrera), descarta esa combinación, recalcula con datos frescos y prueba la siguiente; si se agotan, `SIN_DISPONIBILIDAD`. Cualquier otro rechazo de la agenda en este paso (horario, ausencia, bloqueo cargados en el medio) también se traduce a `SIN_DISPONIBILIDAD`.
4. Cada intento es una transacción propia de `ServicioAgenda`; `ReservarTurno` no abre una transacción externa, para no anular el reintento por deadlock de `ServicioAgenda` (una transacción interna que participa de otra la marca *rollback-only*).

La validación de RN-AG-15 vive en la reserva porque `CrearTurno` todavía no la hace (C-07, Risks).

### D6. Cancelar y reprogramar: componer los casos de uso del personal
1. `ReservasPaciente.buscar(numeroReserva normalizado, pacienteId de la sesión)` busca el turno con `upper(trim(codigo_reserva)) = ?` **y** `paciente_id = ?`. Si no hay fila → `404 RESERVA_INEXISTENTE`. Así un número ajeno y uno inexistente son indistinguibles.
2. Si no está activo → `409 TURNO_NO_ACTIVO`.
3. `PlazoModificacion`: permitido si `inicio - ahora >= 24 h`; si no, `409 CANCELACION_FUERA_DE_PLAZO`. El plazo es una constante de 24 h hasta C-16.
4. Cancelar: `ServicioAgenda.cancelar(new CancelarTurnoCommand(turnoId, dniDelPaciente, numeroReserva), actor)`. El DNI se lee del paciente de la sesión, nunca del pedido, y la doble verificación de `IdentificacionReserva` se mantiene.
5. Reprogramar: calcula la combinación como en D5 ignorando el turno original como conflicto y en el conteo, y llama a `ServicioAgenda.reprogramar(new ReprogramarTurnoCommand(turnoId, dni, numero, inicio, p, s), actor)`, con el mismo reintento por carrera. `ReprogramarTurno` ya ignora al original y cancela y crea en una transacción.

*Por qué 404 y no 403*: un 403 confirmaría que el número de reserva existe y es de otra persona. El número es aleatorio de 8 caracteres (≈ 30^8), así que la enumeración no es práctica, pero la respuesta igual no debe dar pistas (RN-RO-01, DD-10).

*Carrera conocida*: entre el paso 3 y la cancelación el turno no cambia de inicio (la reprogramación crea otro turno), así que el plazo no puede quedar mal evaluado; dos cancelaciones simultáneas las serializa el bloqueo de `buscarParaModificar` y la segunda recibe `TURNO_NO_ACTIVO`.

### D7. Errores y anonimato
`ReservaExceptionHandler` produce `ProblemDetail` con la propiedad `codigo`, como C-05 y C-06:
- `409`: `SIN_DISPONIBILIDAD` (con `alternativas`: lista de `{inicio, fin}`, hasta 3 del mismo día posteriores al pedido, con el mismo criterio), `CANCELACION_FUERA_DE_PLAZO`, `TURNO_NO_ACTIVO`, `INICIO_EN_EL_PASADO`, `PROFESIONAL_NO_HABILITADO`, `RECURSO_INACTIVO`.
- `404`: `RESERVA_INEXISTENTE`.
- `422`: `DATOS_INVALIDOS` con la lista de campos, incluidas fechas sin zona y parámetros mal formados.

Los códigos de disponibilidad de la agenda (`PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`, `FUERA_DE_HORARIO`, `SOLAPAMIENTO_*`, `MARGEN_INSUFICIENTE`) nunca salen por la API online: se traducen a `SIN_DISPONIBILIDAD`. Una ausencia puede ser por enfermedad del profesional y no se informa al público.

Los códigos nuevos (`SIN_DISPONIBILIDAD`, `CANCELACION_FUERA_DE_PLAZO`, `RESERVA_INEXISTENTE`) son de `reserva/domain` (`CodigoReserva`) y no se agregan a `CodigoRechazo` de `agenda/`.

### D8. Auditoría y origen online sin migración
El actor que se pasa a `ServicioAgenda` es `paciente-online:{usuarioId}` (máximo 30 caracteres, cabe en `actor VARCHAR(120)`). Identifica la cuenta sin datos personales y deja trazado el origen `ONLINE` sin columna nueva. *(Propuesta, a confirmar)*: si la líder quiere la columna `turno.origen` de la KB, necesita reservar un número de migración; el cambio sería aditivo y no altera estos specs.

### D9. Aviso de reserva y cancelación
Puerto `AvisoReservaOnline` en `reserva/application` con `reservado(turnoId)`, `cancelado(turnoId)` y `reprogramado(turnoIdNuevo)`. Se llama después de que `ServicioAgenda` confirma la transacción; cualquier excepción se registra con el id del turno, sin datos del paciente, y se descarta. Si no hay un bean que lo implemente, se usa `AvisoReservaOnlineInactivo` vía `ObjectProvider` (mismo patrón que C-07 D10). El adaptador real lo escribe quien tenga el servicio de `notificaciones` con los tipos `CONFIRMACION`/`CANCELACION` (C-11 o un follow-up, Juan Cruz). Ningún dato de salud viaja por el puerto: solo el id del turno (regla dura 6).

### D10. Consultas de lectura propias
`ReservasPaciente` y `CatalogoReserva` usan `JdbcTemplate`/`NamedParameterJdbcTemplate` con SQL parametrizado sobre `turno`, `paciente`, `prestacion`, `profesional`, `profesional_prestacion` y `sillon`, en transacciones de solo lectura. Devuelven records con lo justo (sin entidades JPA). Es el mismo enfoque de `TurnosARecordarJdbc` (C-11) y evita depender de las entidades de `shared/persistence`.

### D11. Tests
- **Unitarios (sin base)**: `GrillaHorarios`, `CalculadoraHuecos`, `AsignadorProfesional`, `OrdenSillones` y `PlazoModificacion`, y los casos de uso con fakes en memoria propios de `reserva` (catálogo, reservas del paciente, `TurnoRepository`, `DisponibilidadProfesional`) y un doble de "agenda" que simula `ServicioAgenda` (incluido un rechazo `SOLAPAMIENTO_SILLON` para la carrera). `Clock.fixed` en 2026-12-01 08:00 de Buenos Aires y `Desempate` fijo.
- **Integración** `@Tag("integration")`, PostgreSQL real: una base `IntegracionReservaBase` en `reserva/` que extiende `IntegracionSeguridadBase`, fija el reloj (como `IntegracionAgendaBase`), carga la Dra. A, el Dr. B, dos sillones con sillón habitual, "Consulta" y "Tratamiento de conducto" con especialidades, dos pacientes con cuenta y franjas por escenario. `@DisplayName` igual al nombre del escenario. Los de carreras usan dos hilos con `CountDownLatch`, como `CarreraSobreturnoIntegrationTest`.
- No se modifica `IntegracionAgendaBase` (Sofia): la base de C-10 es propia.

## Risks / Trade-offs

- **[`CrearTurno` no valida la habilitación]** → La reserva la valida antes de crear (D5). Si C-06 la agrega después, la reserva sigue igual; el doble chequeo no cambia el resultado.
- **[Disponibilidad pública sin límite de pedidos]** → Cada pedido hace pocas lecturas indexadas (D2). Un rate limit (429) recomendado por `springboot-security` queda como pregunta abierta para C-14.
- **[La grilla de 15 minutos deja huecos sin ofrecer]** → Es una simplificación de UX; el personal puede dar turnos en cualquier minuto. El paso es configurable.
- **[El horario ofrecido puede ocuparse antes de reservar]** → D5 reintenta con otra combinación y, si no hay, responde alternativas. La base garantiza que nunca queden dos turnos superpuestos.
- **[Sin aviso real de la reserva hasta que exista el adaptador de C-11]** → US-021 CA-1 (código de reserva) se cumple con la respuesta; el aviso por canal queda pendiente y dicho en el PR.
- **[Base sin franjas]** → Desde C-07, un profesional sin franjas no ofrece horarios. En dev lo cubre el seed `V5_1`.
- **[Zona horaria en Windows]** → Los tests corren con `-Duser.timezone=America/Argentina/Buenos_Aires` (ya configurado en `build.gradle`); la fecha se interpreta siempre con el `ZoneId` de `AgendaConfig`.

## Migration Plan

Sin migración de base: no hay número reservado para C-10 y el diseño no lo necesita. El despliegue es solo código nuevo en `reserva/`. Rollback: revertir el commit; no quedan datos huérfanos (los turnos creados online son turnos comunes).

## Open Questions

- ¿Hay que limitar cuántos días hacia adelante se puede consultar o reservar (por ejemplo 60)? Se puede agregar como validación `422` sin cambiar el resto del diseño.
- ¿El paso de la grilla (15 min) y la cantidad de alternativas (3) son los que quiere el consultorio? Son propiedades.

## Resolución de decisiones (2026-11-22)

Juan Sebastián (responsable de C-10) aceptó todas las propuestas por defecto. La líder (Sofia) las revisa en el PR; si cambia alguna, se actualizan los artefactos con `openspec-update-change` antes de mergear.

- Sin migración en este change; el origen online queda en el actor de auditoría `paciente-online:{usuarioId}`. Si la líder quiere `turno.origen`, reserva un número y se hace en un change aparte.
- Endpoints públicos `GET /public/prestaciones` y `GET /public/profesionales?prestacionId`: **aceptados** (los necesita C-12).
- Grilla de 15 minutos, 3 alternativas y "sin profesional" en la reprogramación = sin preferencia: **aceptados**.
- Límite de días hacia adelante: **sin límite en este change**; queda como mejora futura.
- Límite de pedidos (`429`) en los endpoints públicos: **fuera de este change** (candidato para C-14).
- Aviso real al paciente (adaptador de `AvisoReservaOnline` y links de cancelar/reprogramar): **fuera de este change**; se acuerda con Juan Cruz (C-11). C-10 deja el puerto con la implementación que no hace nada.
