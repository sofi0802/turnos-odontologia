# Spec Delta

## ADDED Requirements

### Requirement: Revocar el consentimiento corta los avisos
Desde que el paciente o la recepción revocan el consentimiento, el sistema MUST NOT crear notificaciones nuevas para ese paciente ni reintentar sus notificaciones fallidas, aunque su próximo intento ya haya vencido. Las notificaciones ya enviadas MUST conservarse sin cambios.

#### Scenario: Revocar corta los recordatorios
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28, y "Paciente Uno" con consentimiento
- **WHEN** "Paciente Uno" revoca el consentimiento el 2026-12-01 a las 06:00 y la tarea de recordatorios corre a las 08:00
- **THEN** no se envía ningún mensaje y no se crea ninguna notificación para ese turno

#### Scenario: Revocar corta los reintentos
- **GIVEN** un recordatorio de "Paciente Uno" en estado `FALLIDA` con el próximo intento vencido
- **WHEN** "Paciente Uno" revoca el consentimiento y la tarea de recordatorios corre
- **THEN** no se reintenta el envío y la notificación sigue `FALLIDA` sin sumar intentos

#### Scenario: El consentimiento registrado por la recepción habilita los avisos
- **GIVEN** "Paciente Dos", sin cuenta, con email y sin consentimiento
- **WHEN** `recepcion` registra su consentimiento por teléfono y "Paciente Dos" tiene un turno que entra en las próximas 24 horas
- **THEN** la tarea de recordatorios le envía el recordatorio por email
