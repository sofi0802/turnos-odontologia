# Proposal

## Why

Hoy solo el personal puede dar, cancelar y reprogramar turnos (`/api/v1/turnos`). El paciente tiene cuenta (C-05), pero no puede ver horarios libres ni reservar, y cada turno sigue requiriendo una llamada al consultorio. C-10 abre la reserva online (Épica 3: US-020 a US-024; RN-RO-01 a RN-RO-08; RN-AG-07) sobre lo que ya existe: `CrearTurno` con las dos garantías contra solapamientos (C-01/C-06), las reglas de horario, ausencias y bloqueos (C-07), las especialidades y el sillón habitual (C-04), y la sesión del paciente (C-05). Es el paso del camino crítico que desbloquea la pantalla de reserva (C-12).

## What Changes

- **Disponibilidad pública**: `GET /api/v1/public/disponibilidad?prestacionId&fecha[&profesionalId]` devuelve los horarios libres de un día. Con profesional, solo los de ese profesional; sin él ("sin preferencia"), la unión sin repetidos de los profesionales activos habilitados para la prestación que trabajan ese día. Un horario aparece solo si existe al menos un par profesional + sillón libre con margen (RN-RO-07). La respuesta tiene solo horarios: ni pacientes, ni profesionales, ni sillones (RN-RO-01).
- **Catálogo público mínimo** *(propuesta, a confirmar)*: `GET /api/v1/public/prestaciones` (activas) y `GET /api/v1/public/profesionales?prestacionId` (activos y habilitados, con id y nombre). Sin esto, la pantalla de C-12 no puede ofrecer "elegir profesional o sin preferencia" (RN-RO-04), porque el catálogo de C-04 es solo para el personal.
- **Reserva**: `POST /api/v1/paciente/reservas` con `prestacionId`, `inicio` y `profesionalId` opcional. El servidor asigna el profesional si no hay preferencia (libre, habilitado, con menos turnos ese día y, si hay empate, al azar; RN-RO-05) y siempre el sillón (primero el habitual; RN-RO-06). Crea el turno con el mismo caso de uso del personal, sin sobreturno (RN-RO-03), y devuelve `201` con el número de reserva y el profesional asignado. Si el horario se ocupa entre la consulta y la reserva, prueba otra combinación libre; si no queda ninguna, `409 SIN_DISPONIBILIDAD` con alternativas del mismo día.
- **Mis turnos**: `GET /api/v1/paciente/turnos` lista solo los turnos del paciente de la sesión.
- **Cancelar y reprogramar online**: `POST /api/v1/paciente/reservas/{numeroReserva}/cancelar` y `/reprogramar`, hasta 24 h antes del inicio (RN-AG-07). Con menos plazo, `409 CANCELACION_FUERA_DE_PLAZO`. Un número de reserva inexistente o de otro paciente responde igual: `404 RESERVA_INEXISTENTE`, sin revelar si existe.
- **Auditoría**: las operaciones online se registran con el actor `paciente-online:{usuarioId}`, sin email, DNI ni número de reserva.
- **Sin migración** *(propuesta, a confirmar)*: no hay número reservado para C-10 en CHANGES.md. El origen `ONLINE` del modelo de la KB no se persiste en una columna: queda en el actor de la auditoría. Si la líder quiere la columna `origen`, hace falta que reserve un número de migración (pregunta abierta).
- **Aviso de reserva y cancelación**: se define un puerto `AvisoReservaOnline` en `reserva/application` con un adaptador inactivo por defecto, igual que `AvisoReasignacion` de C-07. Enviar la confirmación o la cancelación por WhatsApp o email requiere un adaptador de `notificaciones` (C-11, Juan Cruz), que hoy deja fuera los tipos `CONFIRMACION` y `CANCELACION`.

## Capabilities

### New Capabilities

- `disponibilidad-online`: cálculo de horarios libres de un día para una prestación, con profesional o sin preferencia, con las mismas reglas que la agenda del personal y sin datos de terceros; catálogo público mínimo de prestaciones y profesionales habilitados.
- `reserva-online`: reserva del paciente con sesión, asignación de profesional (RN-RO-05) y de sillón (RN-RO-06), reintento ante carreras y rechazos con códigos estables.
- `turnos-paciente`: consulta de los turnos propios, y cancelación y reprogramación online con número de reserva, plazo de 24 h y protección contra el acceso a reservas ajenas.

### Modified Capabilities

- `auditoria-turnos`: nuevo requisito sobre el actor de las operaciones online del paciente, sin datos personales.

## Impact

- **Módulo nuevo `backend/.../reserva/`** (dueño: Juan Sebastián; sin migración): `domain` (cálculo de huecos y asignación, sin Spring), `application` (casos de uso y puertos), `infrastructure` (consultas de lectura propias y adaptadores) y `api` (dos controllers públicos, uno del paciente y un `@RestControllerAdvice` limitado al paquete).
- **Reutiliza sin modificar**: `ServicioAgenda.crear/cancelar/reprogramar` (transacción, auditoría, reintento por deadlock), `CrearTurno` y las dos restricciones `EXCLUDE`, `ReglasDisponibilidad` y el puerto `DisponibilidadProfesional` (C-07), `Agenda.verificar`, el bean `Margen`, el `Clock` y el `ZoneId` de `AgendaConfig`, y `HabilitacionProfesional` (C-04).
- **Módulos ajenos**: el plan no necesita editar archivos de otros dueños. Si en el apply hiciera falta exponer algo de `agenda/` (Sofia) o de `configuracion/` (Emanuel), se avisa en el PR. `shared/security/` es del mismo dueño y no cambia: `/api/v1/public/**` ya es público y `/api/v1/paciente/**` ya exige `PACIENTE`.
- **Tests**: unitarios por escenario con `Clock` fijo y desempate inyectado, e integración `@Tag("integration")` con PostgreSQL real, con franjas y especialidades cargadas por escenario.
- **Fuera de scope**: pantallas de reserva y de "mis turnos" (C-12, Enzo); envío de confirmación, cancelación y links de cancelar o reprogramar en los mensajes (C-11, Juan Cruz); edición de datos de contacto y consentimiento al reservar (C-17); lista de espera (C-15); plazo de cancelación configurable (C-16); límite de pedidos por IP en los endpoints públicos (pregunta abierta); vincular una cuenta a un paciente cargado por el consultorio (C-05).
