## ADDED Requirements

### Requirement: Aviso de la oferta de la lista de espera
El sistema SHALL avisar al paciente cada oferta de la lista de espera con las mismas reglas de consentimiento y canal que el recordatorio: por WhatsApp si lo tiene, y si no o si WhatsApp falla, por email. El aviso lleva la fecha, la hora, el consultorio, hasta cuándo puede aceptar y el link a la pantalla de la oferta con su token. El aviso MUST NOT incluir la prestación, el nombre del paciente, el del profesional, el DNI ni el número de reserva. El token MUST NOT guardarse en claro: el aviso no deja una fila en `notificacion`, y la oferta guarda solo si el aviso salió. Un aviso que falla SHALL reintentarse desde la tarea de la lista de espera, hasta 3 veces mientras la oferta siga pendiente, cada vez con un token nuevo.

#### Scenario: Oferta con consentimiento y WhatsApp
- **GIVEN** "Paciente Uno" con consentimiento y WhatsApp
- **WHEN** se crea una oferta del 2026-12-03 a las 10:00 que vence a las 10:00 del 2026-12-02
- **THEN** se envía un mensaje por WhatsApp con la fecha, la hora, el consultorio, el vencimiento y el link de la oferta
- **AND** el mensaje no contiene la prestación, el nombre del paciente ni el del profesional

#### Scenario: Oferta por email
- **GIVEN** "Paciente Dos" con consentimiento, sin WhatsApp y con email
- **WHEN** se crea una oferta para "Paciente Dos"
- **THEN** el mensaje se envía por email

#### Scenario: Si WhatsApp falla, sale por email
- **GIVEN** "Paciente Uno" con consentimiento, WhatsApp y email
- **WHEN** se crea una oferta y el envío por WhatsApp falla
- **THEN** el mensaje se envía por email

#### Scenario: La notificación no guarda el token
- **WHEN** se envía el aviso de una oferta
- **THEN** no queda ninguna fila en `notificacion` con el token de la oferta

#### Scenario: El aviso fallido se reintenta con un token nuevo
- **GIVEN** una oferta `PENDIENTE` cuyo aviso falló
- **WHEN** corre la tarea de la lista de espera
- **THEN** se envía el aviso con un token nuevo y la oferta guarda solo el hash de ese token
