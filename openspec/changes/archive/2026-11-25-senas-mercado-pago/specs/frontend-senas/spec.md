## Purpose

Las pantallas de las señas: la sección "Señas" de Configuración para el administrador, la seña al terminar la reserva online y en "Mis turnos" del paciente, y la causa al cancelar desde la agenda (US-202). En los escenarios, con datos ficticios, hoy es el lunes 2026-12-07 a las 10:00; los importes se muestran con el formato de Argentina, por ejemplo "$ 4.500,00".

## ADDED Requirements

### Requirement: Sección "Señas" en Configuración
Con una sesión del `ADMIN`, Configuración SHALL tener la sección "Señas" con "Pedir seña en la reserva online", "Porcentaje del copago" y "Plazo para pagar (minutos)", cargados de `GET /api/v1/senas/configuracion`, y "Guardar", que envía `PUT /api/v1/senas/configuracion`. Un `422` MUST marcar el campo con su mensaje. Con otra sesión, la sección MUST NOT aparecer.

#### Scenario: Ver la configuración de las señas
- **GIVEN** las señas desactivadas con el 30 % y 30 minutos
- **WHEN** el `ADMIN` abre "Señas" en Configuración
- **THEN** "Pedir seña en la reserva online" está sin marcar, "Porcentaje del copago" dice 30 y "Plazo para pagar (minutos)" dice 30

#### Scenario: Guardar las señas
- **WHEN** el `ADMIN` marca "Pedir seña en la reserva online", escribe 25 y 45, y elige "Guardar"
- **THEN** se envía `PUT /api/v1/senas/configuracion` con `activas` `true`, `porcentaje` 25 y `plazoMinutos` 45, y se ve "Configuración de señas guardada."

#### Scenario: Porcentaje inválido en la pantalla
- **GIVEN** la API responde `422` con el campo `porcentaje`
- **WHEN** el `ADMIN` guarda con porcentaje 0
- **THEN** "Porcentaje del copago" muestra el mensaje del error

#### Scenario: La recepción no ve la sección
- **WHEN** la `RECEPCIONISTA` abre Configuración
- **THEN** no hay sección "Señas"

### Requirement: Seña al terminar la reserva
Si la respuesta de la reserva trae una seña pendiente, la confirmación SHALL mostrar el importe y la hora de vencimiento, y el botón "Pagar seña" que abre el link de pago. Sin link, "Pagar seña" MUST pedir uno nuevo. Sin seña, la confirmación MUST quedar como antes de C-23.

#### Scenario: Reserva con seña
- **GIVEN** la reserva responde con una seña pendiente de `4500.00` que vence a las 10:30 y un link de pago
- **WHEN** "Paciente Dos" termina la reserva
- **THEN** ve "Seña: $ 4.500,00. Pagala antes de las 10:30 para no perder el turno." y el botón "Pagar seña"

#### Scenario: Reserva sin seña
- **GIVEN** la reserva responde sin seña
- **WHEN** "Paciente Dos" termina la reserva
- **THEN** no ve "Seña" ni "Pagar seña"

#### Scenario: Seña sin link
- **GIVEN** la reserva responde con una seña pendiente sin link
- **WHEN** "Paciente Dos" elige "Pagar seña"
- **THEN** se envía `POST /api/v1/paciente/reservas/{numeroReserva}/sena/link` y se abre el link que devuelve

### Requirement: Seña en "Mis turnos"
Cada turno con seña SHALL mostrar su estado en "Mis turnos": pendiente ("Seña pendiente: $ 4.500,00. Vence a las 10:30." con "Pagar seña"), pagada ("Seña pagada"), vencida ("Seña vencida: el turno se canceló"), devuelta ("Seña devuelta") o retenida ("Seña retenida por el consultorio"). El estado MUST decirse con texto, no solo con color.

#### Scenario: Seña pendiente en mis turnos
- **GIVEN** un turno con la seña pendiente de `4500.00` que vence a las 10:30
- **WHEN** "Paciente Dos" abre "Mis turnos"
- **THEN** ve "Seña pendiente: $ 4.500,00. Vence a las 10:30." y "Pagar seña"

#### Scenario: Seña pagada en mis turnos
- **GIVEN** un turno con la seña pagada
- **WHEN** "Paciente Dos" abre "Mis turnos"
- **THEN** ve "Seña pagada" y no ve "Pagar seña"

#### Scenario: Seña devuelta en mis turnos
- **GIVEN** un turno cancelado con la seña devuelta
- **WHEN** "Paciente Dos" abre "Mis turnos"
- **THEN** ve "Seña devuelta"

### Requirement: Vuelta desde Mercado Pago
Al volver de Mercado Pago a `#/mis-turnos`, la pantalla SHALL mostrar "Estamos confirmando tu pago. Puede tardar unos minutos." y el estado que informa la API, sin dar la seña por pagada por la vuelta.

#### Scenario: Vuelta antes de la notificación
- **GIVEN** la API todavía informa la seña pendiente
- **WHEN** "Paciente Dos" vuelve de Mercado Pago a `#/mis-turnos?pago=aprobado`
- **THEN** ve "Estamos confirmando tu pago. Puede tardar unos minutos." y "Seña pendiente"

### Requirement: Causa al cancelar desde la agenda
El diálogo "Cancelar turno" de la agenda SHALL ofrecer "Causa" con "A pedido del paciente" y "Por el consultorio", con la ayuda "Obligatoria si el paciente pagó una seña.", y enviarla como `causa` (`PACIENTE` o `CONSULTORIO`) cuando se elige. Un `422` con el campo `causa` MUST marcar ese campo con su mensaje.

#### Scenario: Cancelar con la causa
- **WHEN** la `RECEPCIONISTA` cancela un turno eligiendo "Por el consultorio"
- **THEN** el pedido de cancelación lleva `causa` `CONSULTORIO`

#### Scenario: Falta la causa de un turno con seña
- **GIVEN** la API responde `422` con el campo `causa`
- **WHEN** la `RECEPCIONISTA` cancela sin elegir la causa
- **THEN** "Causa" muestra el mensaje del error y el diálogo sigue abierto
