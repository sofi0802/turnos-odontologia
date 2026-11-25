## ADDED Requirements

### Requirement: Aviso de la oferta de la lista de espera
El sistema SHALL avisar al paciente cada oferta de la lista de espera con las mismas reglas de consentimiento, canal y reintentos que el recordatorio. El aviso lleva la fecha, la hora, el consultorio, hasta cuándo puede aceptar y el link a la pantalla de la oferta con su token. El aviso MUST NOT incluir la prestación, el nombre del paciente, el del profesional, el DNI ni el número de reserva. La notificación MUST NOT guardar el token en claro.

#### Scenario: Oferta con consentimiento y WhatsApp
- **GIVEN** "Paciente Uno" con consentimiento y WhatsApp
- **WHEN** se crea una oferta del 2026-12-03 a las 10:00 que vence a las 10:00 del 2026-12-02
- **THEN** se envía un mensaje por WhatsApp con la fecha, la hora, el consultorio, el vencimiento y el link de la oferta
- **AND** el mensaje no contiene la prestación, el nombre del paciente ni el del profesional

#### Scenario: Oferta por email
- **GIVEN** "Paciente Dos" con consentimiento, sin WhatsApp y con email
- **WHEN** se crea una oferta para "Paciente Dos"
- **THEN** el mensaje se envía por email

#### Scenario: La notificación no guarda el token
- **WHEN** se envía el aviso de una oferta
- **THEN** la notificación guardada no contiene el token de la oferta
