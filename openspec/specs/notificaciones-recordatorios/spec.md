# notificaciones-recordatorios Specification

## Purpose

Avisar a los pacientes de sus turnos con un recordatorio 24 horas antes, por WhatsApp o por email, solo con su consentimiento y sin que viaje ningún dato de salud (RN-NO-01 a RN-NO-04, Ley 25.326). En los escenarios, con datos ficticios, "Paciente Uno" (DNI `99000001`) tiene turnos con la Dra. A en el Sillón 1, y el momento actual es el 2026-12-01 a las 08:00 salvo que se indique otro.

## Requirements

### Requirement: Recordatorio 24 horas antes del turno
El sistema SHALL enviar un recordatorio a cada paciente con un turno activo (`RESERVADO` o `CONFIRMADO`) que empieza dentro de las próximas 24 horas, una sola vez por turno.

#### Scenario: Turno dentro de las próximas 24 horas
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
- **AND** "Paciente Uno" con consentimiento, WhatsApp y teléfono
- **WHEN** corre la tarea de recordatorios
- **THEN** se envía un recordatorio a "Paciente Uno" por WhatsApp
- **AND** queda una notificación `RECORDATORIO` en estado `ENVIADA` para ese turno

#### Scenario: Turno que todavía no está dentro de las 24 horas
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" el 2026-12-02 a las 09:00, reservado el 2026-11-28
- **WHEN** corre la tarea de recordatorios
- **THEN** no se envía ningún recordatorio y no se crea ninguna notificación

#### Scenario: Un turno confirmado también recibe recordatorio
- **GIVEN** un turno `CONFIRMADO` de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
- **AND** "Paciente Uno" con consentimiento y email
- **WHEN** corre la tarea de recordatorios
- **THEN** se envía un recordatorio por email

#### Scenario: Un turno cancelado no recibe recordatorio
- **GIVEN** un turno `CANCELADO` de "Paciente Uno" el 2026-12-02 a las 07:30
- **WHEN** corre la tarea de recordatorios
- **THEN** no se envía ningún recordatorio

#### Scenario: No se envía dos veces el recordatorio de un turno
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" el 2026-12-02 a las 07:30 que ya tiene un recordatorio enviado
- **WHEN** la tarea de recordatorios corre otra vez
- **THEN** no se envía un segundo recordatorio y sigue existiendo una sola notificación `RECORDATORIO` para ese turno

### Requirement: Reservas con menos de 24 horas de anticipación
El sistema SHALL NOT enviar recordatorio de un turno que se reservó con menos de 24 horas de anticipación respecto de su inicio.

#### Scenario: Turno reservado el mismo día
- **GIVEN** un turno de "Paciente Uno" el 2026-12-02 a las 07:30 creado el 2026-12-01 a las 09:00, con 22 horas y 30 minutos de anticipación
- **WHEN** corre la tarea de recordatorios el 2026-12-01 a las 09:30
- **THEN** no se envía ningún recordatorio para ese turno

#### Scenario: Turno reservado con exactamente 24 horas de anticipación
- **GIVEN** un turno de "Paciente Uno" el 2026-12-02 a las 09:00 creado el 2026-12-01 a las 09:00
- **WHEN** corre la tarea de recordatorios el 2026-12-01 a las 09:00
- **THEN** se envía el recordatorio

### Requirement: Solo con consentimiento
El sistema SHALL enviar comunicaciones únicamente a pacientes con el consentimiento de comunicaciones otorgado.

#### Scenario: Paciente sin consentimiento
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" el 2026-12-02 a las 07:30, reservado el 2026-11-28
- **AND** "Paciente Uno" sin consentimiento de comunicaciones
- **WHEN** corre la tarea de recordatorios
- **THEN** no se envía ningún mensaje y no se crea ninguna notificación

#### Scenario: El consentimiento otorgado después se respeta
- **GIVEN** el mismo turno y "Paciente Uno" sin consentimiento en la primera corrida
- **WHEN** "Paciente Uno" otorga el consentimiento y la tarea corre otra vez dentro de las 24 horas
- **THEN** se envía el recordatorio

### Requirement: Elección del canal
El sistema SHALL enviar por WhatsApp si el paciente tiene WhatsApp y teléfono, y por email en cualquier otro caso en que tenga email. Si no tiene ninguno de los dos, MUST NOT enviar nada.

#### Scenario: Paciente con WhatsApp
- **GIVEN** "Paciente Uno" con consentimiento, WhatsApp, teléfono y email
- **WHEN** se envía su recordatorio
- **THEN** el canal es `WHATSAPP`

#### Scenario: Paciente sin WhatsApp
- **GIVEN** "Paciente Uno" con consentimiento, sin WhatsApp y con email
- **WHEN** se envía su recordatorio
- **THEN** el canal es `EMAIL`

#### Scenario: Paciente sin ningún contacto
- **GIVEN** "Paciente Uno" con consentimiento, sin WhatsApp y sin email
- **WHEN** corre la tarea de recordatorios
- **THEN** no se envía ningún mensaje y no se crea ninguna notificación

### Requirement: Mensaje sin datos de salud
El mensaje MUST incluir solo la fecha, la hora, el consultorio (nombre y dirección) y los links de confirmar, cancelar y reprogramar (regla dura 6, RN-NO-01). MUST NOT incluir el nombre del paciente ni del profesional, la prestación, el motivo de un sobreturno ni ningún dato clínico.

#### Scenario: Contenido del recordatorio
- **GIVEN** un turno de "Paciente Uno" de "Consulta" con la Dra. A el 2026-12-02 a las 07:30
- **WHEN** se arma el recordatorio
- **THEN** el mensaje contiene la fecha, la hora, el consultorio, su dirección y los links de confirmar, cancelar y reprogramar
- **AND** no contiene "Consulta" ni ningún otro nombre de prestación
- **AND** no contiene el nombre de "Paciente Uno" ni el de la Dra. A

#### Scenario: Un sobreturno no filtra su motivo
- **GIVEN** un sobreturno de "Paciente Uno" con un motivo administrativo
- **WHEN** se arma el recordatorio
- **THEN** el mensaje no contiene el motivo

### Requirement: Reintentos y respaldo por email
Si el proveedor falla, el sistema SHALL marcar la notificación como `FALLIDA`, reintentarla con espera creciente y, tras 3 fallas por WhatsApp, enviarla por email si el paciente tiene.

#### Scenario: Falla y reintento
- **GIVEN** un recordatorio por WhatsApp cuyo envío falla
- **WHEN** corre la tarea de recordatorios
- **THEN** la notificación queda `FALLIDA` con un reintento programado más adelante
- **AND** el turno no recibe un segundo recordatorio por otro canal todavía

#### Scenario: Tres fallas por WhatsApp con email disponible
- **GIVEN** un recordatorio por WhatsApp que falló 3 veces y "Paciente Uno" con email
- **WHEN** corre la tarea de recordatorios
- **THEN** se envía el recordatorio por email

#### Scenario: Tres fallas sin email
- **GIVEN** un recordatorio por WhatsApp que falló 3 veces y "Paciente Uno" sin email
- **WHEN** corre la tarea de recordatorios
- **THEN** la notificación queda `FALLIDA` sin nuevos reintentos

### Requirement: Recuperación de un envío interrumpido
El sistema SHALL reprocesar una notificación que quedó `PENDIENTE` sin enviarse después de 10 minutos, de modo que un corte entre el registro y el envío no deje al paciente sin su recordatorio. Dos corridas simultáneas MUST NOT enviar la misma notificación dos veces.

#### Scenario: Notificación pendiente que quedó sin enviar
- **GIVEN** una notificación `RECORDATORIO` en estado `PENDIENTE` creada hace más de 10 minutos, para un turno que sigue activo y con consentimiento
- **WHEN** corre la tarea de recordatorios
- **THEN** se envía el recordatorio y la notificación queda `ENVIADA`

#### Scenario: Notificación pendiente reciente
- **GIVEN** una notificación `RECORDATORIO` en estado `PENDIENTE` creada hace menos de 10 minutos
- **WHEN** corre la tarea de recordatorios
- **THEN** no se envía nada todavía

#### Scenario: Dos corridas simultáneas sobre la misma notificación
- **GIVEN** una notificación `FALLIDA` con reintento vencido
- **WHEN** dos corridas de la tarea la procesan al mismo tiempo
- **THEN** se envía una sola vez

### Requirement: Aviso de reasignación por ausencia
El sistema SHALL poder avisar a un paciente que su turno fue reasignado por la ausencia del profesional, con las mismas reglas de consentimiento, canal y contenido que el recordatorio (RN-AG-16).

#### Scenario: Aviso con consentimiento
- **GIVEN** un turno de "Paciente Uno" reasignado al 2026-12-03 a las 10:00 con otro profesional
- **AND** "Paciente Uno" con consentimiento y email
- **WHEN** se pide avisarle la reasignación
- **THEN** se envía un aviso por email con la nueva fecha y la nueva hora, sin nombres ni datos de salud
- **AND** la notificación no guarda ningún token

#### Scenario: Aviso sin consentimiento
- **GIVEN** el mismo turno reasignado y "Paciente Uno" sin consentimiento
- **WHEN** se pide avisarle la reasignación
- **THEN** no se envía ningún mensaje

### Requirement: Aviso de la reserva online
El sistema SHALL avisar al paciente que reservó online, con las mismas reglas de consentimiento y canal que el recordatorio, con la fecha, la hora, el consultorio y los links de cancelar y reprogramar. El aviso MUST NOT llevar link de confirmar ni nombres.

#### Scenario: Reserva online con consentimiento
- **GIVEN** "Paciente Uno" con consentimiento y email que reservó online un turno el 2026-12-02 a las 10:00
- **WHEN** la reserva online pide el aviso
- **THEN** se envía un mensaje por email con la fecha, la hora, el consultorio y los links de cancelar y reprogramar
- **AND** el mensaje no contiene el número de reserva, el DNI, el nombre del paciente ni el del profesional

#### Scenario: Reserva online sin consentimiento
- **GIVEN** "Paciente Uno" sin consentimiento de comunicaciones que reservó online un turno
- **WHEN** la reserva online pide el aviso
- **THEN** no se envía ningún mensaje

### Requirement: Aviso de la cancelación online
El sistema SHALL avisar al paciente que canceló online, con las mismas reglas de consentimiento y canal, con la fecha, la hora y el consultorio del turno cancelado. El aviso MUST NOT llevar links.

#### Scenario: Cancelación online con consentimiento
- **GIVEN** "Paciente Uno" con consentimiento y WhatsApp que canceló online su turno del 2026-12-02 a las 10:00
- **WHEN** la cancelación online pide el aviso
- **THEN** se envía un mensaje por WhatsApp que informa la cancelación con la fecha, la hora y el consultorio
- **AND** el mensaje no contiene links ni datos de salud

#### Scenario: Cancelación online sin consentimiento
- **GIVEN** "Paciente Uno" sin consentimiento que canceló online su turno
- **WHEN** la cancelación online pide el aviso
- **THEN** no se envía ningún mensaje

### Requirement: Aviso de la reprogramación online
El sistema SHALL avisar al paciente que reprogramó online, con la fecha y la hora del turno nuevo y los links de cancelar y reprogramar, con las mismas reglas de consentimiento y canal.

#### Scenario: Reprogramación online con consentimiento
- **GIVEN** "Paciente Uno" con consentimiento y email que reprogramó online su turno al 2026-12-03 a las 11:00
- **WHEN** la reprogramación online pide el aviso para el turno nuevo
- **THEN** se envía un mensaje con la fecha y la hora nuevas y los links de cancelar y reprogramar
- **AND** el mensaje no menciona la fecha del turno anterior ni datos de salud

### Requirement: Los links no llevan credenciales
Los links de cancelar y reprogramar SHALL apuntar a las pantallas del paciente configuradas para el consultorio y MUST NOT incluir el número de reserva, el DNI ni ningún dato del paciente: para cancelar o reprogramar, el paciente inicia sesión e ingresa su número de reserva (RN-AG-07).

#### Scenario: El link de cancelar no incluye credenciales
- **GIVEN** un aviso de reserva online con links de cancelar y reprogramar
- **WHEN** se arma el mensaje
- **THEN** los links empiezan con la dirección base configurada
- **AND** no contienen el número de reserva, el DNI, el email, el teléfono ni el id del turno

### Requirement: Un aviso online fallido queda para reintento
Si el envío de un aviso online falla, el sistema SHALL dejar la notificación `FALLIDA` y reintentarla con la misma política que el recordatorio, y MUST NOT deshacer ni demorar la operación de reserva, cancelación o reprogramación.

#### Scenario: Falla el envío del aviso de una reserva
- **GIVEN** "Paciente Uno" con consentimiento y email, y un proveedor que falla
- **WHEN** la reserva online pide el aviso
- **THEN** la reserva queda confirmada
- **AND** la notificación queda `FALLIDA` con un reintento programado

### Requirement: Los adaptadores simulados no exponen datos
Los adaptadores simulados de WhatsApp y email MUST NOT escribir en el log el cuerpo del mensaje, el token ni el teléfono o el email completos.

#### Scenario: Registro de un envío simulado
- **WHEN** un adaptador simulado envía un mensaje
- **THEN** el log registra solo el canal, el id de la notificación y el resultado
- **AND** no contiene el cuerpo del mensaje, el token, el teléfono ni el email

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
