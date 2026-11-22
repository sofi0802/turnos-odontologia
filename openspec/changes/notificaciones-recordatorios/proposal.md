# Proposal

## Why

Hoy un turno `RESERVADO` nunca pasa a `CONFIRMADO` y el consultorio no tiene forma de saber si el paciente va a ir: no existe ningún recordatorio ni confirmación (RN-NO-04, US-030, US-031). Además el sistema no tiene dónde guardar el consentimiento de comunicaciones ni el canal preferido del paciente, así que ni siquiera se puede cumplir RN-NO-02 y RN-NO-03 (Ley 25.326). C-11 agrega los recordatorios 24 h antes y la confirmación por token, con WhatsApp y email simulados (DD-04), y sin que viaje ningún dato de salud (RN-NO-01, regla dura 6).

## What Changes

- **Puerto `NotificacionPort`** (en `notificaciones/application`) con dos adaptadores simulados, `WhatsAppAdapter` y `EmailAdapter`, que solo escriben en el log un registro sin cuerpo del mensaje ni datos de contacto completos (DD-04).
- **Recordatorio a las 24 h**: una tarea `@Scheduled` busca turnos activos (`RESERVADO` o `CONFIRMADO`) que empiezan dentro de las próximas 24 h, de pacientes con consentimiento, que no tienen recordatorio y que fueron reservados con 24 h o más de anticipación (RN-NO-04). Elige WhatsApp si `tiene_whatsapp`, si no email (RN-NO-02), y arma un mensaje con nombre del paciente, fecha, hora, profesional, dirección del consultorio y link de confirmar (RN-NO-01).
- **Sin consentimiento no se envía nada** (RN-NO-03): el paciente sin consentimiento no genera notificación ni se registra su canal en el log.
- **Confirmación por token de un solo uso** (US-031): `POST /api/v1/public/confirmaciones/{token}` pasa el turno a `CONFIRMADO`. El token se guarda hasheado, se consume una sola vez y deja un registro de auditoría `CONFIRMAR`.
- **Fallas y reintentos**: una falla del proveedor deja la notificación `FALLIDA` con reintento y backoff; después de 3 fallas por WhatsApp se prueba por email si el paciente tiene.
- **Aviso por reasignación (RN-AG-16)**: el puerto y su caso de uso quedan listos y probados con un doble de prueba; conectarlo a las ausencias es trabajo de C-07 (Emanuel).
- **Migración V7** (archivo nuevo): tabla `notificacion`; columnas `tiene_whatsapp`, `consentimiento_comunicaciones` y `consentimiento_fecha` en `paciente` (todas con valor por defecto `FALSE`/`NULL`, sin datos reales); ampliación del `CHECK` de `turno_auditoria.accion` con `CONFIRMAR`. No se edita V1, V3 ni V4.
- **Avisos de la reserva online (C-10) y links de cancelar y reprogramar**: C-10 ya está en `master` y dejó el puerto `AvisoReservaOnline` (reservado, cancelado, reprogramado) con una implementación inactiva. Este change agrega el adaptador real en `notificaciones`: aviso de reserva y de reprogramación (con links de cancelar y reprogramar), y de cancelación (sin links), con las mismas reglas de consentimiento, canal, contenido y reintentos. Los links apuntan a pantallas del paciente configurables y nunca llevan número de reserva ni DNI. El recordatorio también suma esos dos links (RN-NO-01). Se acordó con Juan Sebastián (C-10); el frontend de esas pantallas es de C-12 (Enzo).
- **Gestión del consentimiento fuera de scope**: no hay endpoints para otorgar o revocar el consentimiento; eso es C-17 (V11). C-11 solo lo lee.

## Capabilities

### New Capabilities

- `notificaciones-recordatorios`: recordatorio a las 24 h con consentimiento, elección de canal, mensaje sin datos de salud, no duplicación, reintentos y fallback a email, y aviso de reasignación detrás del puerto.
- `confirmacion-asistencia`: confirmación de un turno por token de un solo uso, endpoint público, y rechazos por token inexistente, usado, o turno que ya no está `RESERVADO`.

### Modified Capabilities

- `auditoria-turnos`: nueva acción `CONFIRMAR`, con el actor `paciente-token` y sin guardar el token.
- `agenda-turnos`: un turno `RESERVADO` puede pasar a `CONFIRMADO`; un turno cancelado, atendido o ausente no.

## Impact

- **Módulo nuevo `notificaciones/`** (dueño: Juan Cruz, V7): `domain`, `application`, `infrastructure`, `api`.
- **`shared/persistence`** y `usuarios`: sin cambios. `notificaciones` lee las columnas nuevas de `paciente` con su propia consulta.
- **Módulos ajenos que se tocan y requieren aviso y aprobación en el PR**:
  - Sofía (`agenda/domain`, `agenda/application`, `agenda/infrastructure`): `Turno.confirmado()`, un caso de uso `ConfirmarTurno` nuevo, la acción `CONFIRMAR` de `AuditoriaTurnos` y `ServicioAgenda.confirmar`. Todo aditivo; la consulta de turnos a recordar es propia de `notificaciones`.
  - Juan Sebastián (`shared/security`): **ningún cambio**; `/api/v1/public/**` ya es `permitAll` y CSRF sigue activo.
- **Configuración**: variables `NOTIFICACIONES_BASE_URL`, `CONSULTORIO_NOMBRE`, `CONSULTORIO_DIRECCION`; `.env.example` solo con valores ficticios. Se activa `@EnableScheduling` y la tarea se puede apagar por propiedad.
- **Orden de merge**: C-08 (V6) ya está en `master`. Las dos migraciones reescriben el mismo `CHECK` de `turno_auditoria`, y V7 lo deja con las siete acciones (incluida `AUSENTE`, que usa C-13).
- **Tests**: unitarios por escenario (Strict TDD) e integración `@Tag("integration")` con PostgreSQL real y `Clock` fijo 2026-12-01 08:00.
- **Dependencia nueva**: `notificaciones/infrastructure` implementa el puerto `reserva.application.AvisoReservaOnline`; `reserva` no conoce a `notificaciones`.
- **Fuera de scope**: C-07 (conectar ausencias), C-10 (links de cancelar/reprogramar y reserva online), C-17 (consentimiento), C-09 y C-12 (pantallas), envío real por WhatsApp o SMTP, las pantallas del paciente a las que apuntan los links (C-12).
