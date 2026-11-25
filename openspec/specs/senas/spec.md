# senas Specification

## Purpose
La seña que paga el paciente con Mercado Pago al reservar online: su configuración, su creación, el pago confirmado por la notificación verificada, el vencimiento, lo que pasa al cancelar, reprogramar o atender el turno, los reintegros y la privacidad de lo que se le manda a Mercado Pago (RN-SE-01 a RN-SE-12, DD-21). En los escenarios, con datos ficticios, hoy es el lunes 2026-12-07 a las 10:00 en el consultorio; "Consulta o control" tiene un arancel de `15000.00`; "Paciente Uno" tiene una cobertura del 40 % ("OS Ficticia", "Plan 40") y "Paciente Dos" es particular; las señas están activas con el 30 % y un plazo de 30 minutos, salvo que se diga otra cosa; la pasarela es la simulada.

## Requirements
### Requirement: Configurar las señas
`GET /api/v1/senas/configuracion` SHALL devolver `activas`, `porcentaje` y `plazoMinutos`, y `PUT /api/v1/senas/configuracion` SHALL guardarlos. Sin configuración guardada, las señas MUST estar desactivadas, con `porcentaje` 30 y `plazoMinutos` 30. El `porcentaje` MUST ser un entero de 1 a 100 y `plazoMinutos` un entero de 5 a 1440; si no, la respuesta es `422` con `DATOS_INVALIDOS`. Un cambio MUST valer solo para las reservas nuevas.

#### Scenario: Configuración inicial
- **GIVEN** que nadie guardó la configuración de las señas
- **WHEN** el `ADMIN` pide `GET /api/v1/senas/configuracion`
- **THEN** la respuesta es `200` con `activas` `false`, `porcentaje` 30 y `plazoMinutos` 30

#### Scenario: Activar las señas
- **WHEN** el `ADMIN` envía `PUT /api/v1/senas/configuracion` con `activas` `true`, `porcentaje` 25 y `plazoMinutos` 45
- **THEN** la respuesta es `200` y un `GET` posterior devuelve esos valores

#### Scenario: Porcentaje fuera de rango
- **WHEN** el `ADMIN` envía `porcentaje` 0
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `porcentaje`

#### Scenario: Un cambio no toca las señas creadas
- **GIVEN** una seña pendiente de `4500.00` creada con el 30 %
- **WHEN** el `ADMIN` cambia el porcentaje a 50
- **THEN** esa seña sigue en `4500.00`

### Requirement: Permisos de las señas
Solo el `ADMIN` SHALL leer y cambiar la configuración de las señas. La `RECEPCIONISTA`, el `ODONTOLOGO` y el `PACIENTE` MUST recibir `403`, y un pedido sin sesión `401`.

#### Scenario: La recepción no configura las señas
- **WHEN** la `RECEPCIONISTA` pide `GET /api/v1/senas/configuracion`
- **THEN** la respuesta es `403`

#### Scenario: El paciente no configura las señas
- **WHEN** "Paciente Uno" envía `PUT /api/v1/senas/configuracion`
- **THEN** la respuesta es `403`

### Requirement: Seña al reservar online
Con las señas activas, la reserva online de un paciente SHALL crear una seña **pendiente** de su turno por el porcentaje configurado del copago, calculado como el cargo de C-22 (precio particular menos la cobertura del plan vigente en la fecha del turno) y redondeado a dos decimales, que vence en el momento de la reserva más el plazo. La seña MUST guardar el porcentaje y el copago usados.

#### Scenario: Paciente particular
- **WHEN** "Paciente Dos" reserva online "Consulta o control"
- **THEN** su turno tiene una seña pendiente de `4500.00` que vence a las 10:30

#### Scenario: Paciente con cobertura
- **WHEN** "Paciente Uno" reserva online "Consulta o control"
- **THEN** su turno tiene una seña pendiente de `2700.00` (el 30 % del copago de `9000.00`)

### Requirement: Turnos que no piden seña
MUST NOT crearse una seña si las señas están desactivadas, si el copago es cero o si la prestación no tiene arancel, ni en los turnos que crea el personal ni en los sobreturnos.

#### Scenario: Señas desactivadas
- **GIVEN** las señas desactivadas
- **WHEN** "Paciente Dos" reserva online
- **THEN** su turno no tiene seña

#### Scenario: Copago cero
- **GIVEN** un plan que cubre el 100 % de "Consulta o control" para "Paciente Uno"
- **WHEN** "Paciente Uno" reserva online "Consulta o control"
- **THEN** su turno no tiene seña

#### Scenario: Prestación sin arancel
- **GIVEN** "Limpieza (profilaxis)" sin arancel
- **WHEN** "Paciente Dos" reserva online "Limpieza (profilaxis)"
- **THEN** su turno no tiene seña

#### Scenario: Turno del personal sin seña
- **WHEN** la `RECEPCIONISTA` crea un turno para "Paciente Dos" desde la agenda
- **THEN** el turno no tiene seña

### Requirement: Link de pago y datos para Mercado Pago
Al crear la seña, el sistema SHALL pedir a la pasarela un link de pago por el importe de la seña. A la pasarela MUST llegar solo el importe, la descripción "Seña de turno" y una referencia opaca de la seña: ni la prestación, ni el nombre, el DNI o el email del paciente. Si la pasarela no responde, la reserva MUST quedar igual, con la seña pendiente y sin link, y el paciente MUST poder pedir un link nuevo mientras la seña siga pendiente (spec `turnos-paciente`).

#### Scenario: Datos que recibe Mercado Pago
- **WHEN** "Paciente Dos" reserva online "Consulta o control"
- **THEN** la pasarela recibe el importe `4500.00`, la descripción "Seña de turno" y una referencia que no contiene "Consulta", "Paciente" ni su DNI

#### Scenario: Pasarela caída al reservar
- **GIVEN** la pasarela simulada responde con error
- **WHEN** "Paciente Dos" reserva online
- **THEN** la reserva responde `201`, el turno queda `RESERVADO` y su seña pendiente no tiene link

### Requirement: Vencimiento de la seña
El sistema SHALL revisar las señas pendientes al menos una vez por minuto. Una seña pendiente vencida MUST pasar a **vencida** y su turno a `CANCELADO`, con el actor `sistema` en la auditoría, y el horario MUST quedar libre. Una seña pendiente de un turno que se cancela por otro motivo MUST quedar vencida, sin reintegro.

#### Scenario: Seña sin pagar a tiempo
- **GIVEN** la seña pendiente de "Paciente Dos" que vence a las 10:30
- **WHEN** son las 10:31 y corre la revisión de las señas
- **THEN** la seña está vencida, el turno está `CANCELADO` con el actor `sistema` y el horario aparece libre en la disponibilidad

#### Scenario: Seña pagada no vence
- **GIVEN** la seña de "Paciente Dos" pagada a las 10:10
- **WHEN** son las 10:31 y corre la revisión de las señas
- **THEN** la seña sigue pagada y el turno sigue `RESERVADO`

### Requirement: Pago confirmado por la notificación de Mercado Pago
Una seña SHALL pasar a **pagada** solo cuando llega a `POST /api/v1/public/pagos/mercadopago/notificaciones` una notificación con firma válida y el pago, consultado a la pasarela, está aprobado, es de esa referencia y tiene el importe de la seña. El turno MUST seguir `RESERVADO` (PA-62). Una notificación válida MUST recibir `200` aunque el pago no esté aprobado.

#### Scenario: Pago aprobado
- **GIVEN** la seña pendiente de `4500.00` de "Paciente Dos"
- **WHEN** llega la notificación firmada de un pago aprobado de `4500.00` con su referencia
- **THEN** la respuesta es `200`, la seña está pagada y el turno sigue `RESERVADO`

#### Scenario: Pago no aprobado
- **WHEN** llega la notificación firmada de un pago rechazado de la seña de "Paciente Dos"
- **THEN** la respuesta es `200` y la seña sigue pendiente

#### Scenario: Importe distinto
- **WHEN** llega la notificación firmada de un pago aprobado de `100.00` con la referencia de la seña de `4500.00`
- **THEN** la seña sigue pendiente y la notificación queda registrada

#### Scenario: Pago tardío
- **GIVEN** la seña de "Paciente Dos" vencida y su turno cancelado
- **WHEN** llega la notificación firmada de un pago aprobado de `4500.00` con su referencia
- **THEN** el turno sigue `CANCELADO`, la pasarela recibe un reintegro de `4500.00` y la seña está devuelta

### Requirement: Notificaciones inválidas o repetidas
Una notificación con firma inválida o sin firma SHALL recibir `401` con `FIRMA_INVALIDA` y MUST NOT cambiar ninguna seña. La misma notificación MUST NOT aplicarse dos veces: si se repite, recibe `200` sin cambios.

#### Scenario: Firma inválida
- **WHEN** llega una notificación con una firma que no corresponde a la clave configurada
- **THEN** la respuesta es `401` con `FIRMA_INVALIDA` y ninguna seña cambia

#### Scenario: Notificación repetida
- **GIVEN** la seña de "Paciente Dos" ya pagada por un pago
- **WHEN** llega otra vez la notificación de ese pago
- **THEN** la respuesta es `200` y la seña tiene un solo pago registrado

### Requirement: Cancelación de un turno con seña pagada
Cuando se cancela un turno con seña pagada, el sistema SHALL **reintegrar** la seña completa si la canceló el paciente online, si el personal la canceló con 24 horas o más de anticipación, o si el personal la canceló con la causa `CONSULTORIO`. El consultorio MUST **retener** la seña si el personal canceló con menos de 24 horas y la causa `PACIENTE`, o si el turno quedó `AUSENTE`.

#### Scenario: El paciente cancela online a tiempo
- **GIVEN** la seña pagada de `4500.00` de un turno de "Paciente Dos" del miércoles 2026-12-09
- **WHEN** "Paciente Dos" lo cancela online
- **THEN** la pasarela recibe un reintegro de `4500.00` y la seña está devuelta

#### Scenario: El personal cancela con anticipación
- **GIVEN** la seña pagada de un turno de "Paciente Dos" del miércoles 2026-12-09
- **WHEN** la `RECEPCIONISTA` lo cancela con la causa `PACIENTE`
- **THEN** la seña está devuelta

#### Scenario: El consultorio cancela tarde
- **GIVEN** la seña pagada de un turno de "Paciente Dos" de hoy a las 16:00
- **WHEN** la `RECEPCIONISTA` lo cancela con la causa `CONSULTORIO`
- **THEN** la seña está devuelta

#### Scenario: El paciente pide cancelar tarde
- **GIVEN** la seña pagada de un turno de "Paciente Dos" de hoy a las 16:00
- **WHEN** la `RECEPCIONISTA` lo cancela con la causa `PACIENTE`
- **THEN** la seña está retenida y la pasarela no recibe reintegro

#### Scenario: El paciente no viene
- **GIVEN** la seña pagada de un turno de "Paciente Dos" que queda `AUSENTE`
- **WHEN** corre la revisión de las señas
- **THEN** la seña está retenida

### Requirement: Reprogramación de un turno con seña
Cuando se reprograma un turno con seña pagada o pendiente, la seña SHALL pasar al turno nuevo con su estado, su importe y su vencimiento, sin pedir un pago nuevo ni una diferencia.

#### Scenario: Reprogramar con la seña pagada
- **GIVEN** la seña pagada de `4500.00` de un turno de "Paciente Dos" del miércoles 2026-12-09
- **WHEN** "Paciente Dos" lo reprograma online al jueves 2026-12-10
- **THEN** el turno del jueves tiene la seña pagada de `4500.00` y el del miércoles no tiene seña

### Requirement: Imputación de la seña al cargo
Cuando un turno con seña pagada tiene su cargo (spec `cobros-pacientes`), el sistema SHALL registrar un cobro de ese cargo con el medio `MERCADO_PAGO` por el menor entre la seña y el copago, y la seña MUST pasar a **imputada**. Si la seña es mayor que el copago, la diferencia MUST reintegrarse. Si el copago es cero, la seña MUST reintegrarse completa, sin cobro.

#### Scenario: Seña imputada
- **GIVEN** la seña pagada de `4500.00` de un turno de "Paciente Dos" que queda `ATENDIDO` con un cargo de `15000.00`
- **WHEN** corre la revisión de las señas
- **THEN** el cargo tiene un cobro `MERCADO_PAGO` de `4500.00` y saldo `10500.00`, y la seña está imputada

#### Scenario: Seña mayor que el copago
- **GIVEN** la seña pagada de `4500.00` de un turno cuyo cargo queda con un copago de `3000.00`
- **WHEN** corre la revisión de las señas
- **THEN** el cargo tiene un cobro `MERCADO_PAGO` de `3000.00`, la pasarela recibe un reintegro de `1500.00` y la seña está imputada

#### Scenario: Copago final cero
- **GIVEN** la seña pagada de `2700.00` de un turno cuyo cargo queda con copago `0.00`
- **WHEN** corre la revisión de las señas
- **THEN** el cargo no tiene cobros, la pasarela recibe un reintegro de `2700.00` y la seña está devuelta

### Requirement: Reintegros sin duplicar
Cada reintegro SHALL pedirse a la pasarela una sola vez por seña y motivo. Si la pasarela lo rechaza, el sistema MUST reintentarlo en la próxima revisión y registrar el intento fallido, y MUST NOT reintegrar más que lo pagado.

#### Scenario: Reintegro rechazado
- **GIVEN** la pasarela simulada rechaza el primer reintegro de la seña cancelada de "Paciente Dos"
- **WHEN** corren dos revisiones de las señas
- **THEN** hay un intento fallido registrado, la pasarela recibe un solo reintegro aceptado de `4500.00` y la seña está devuelta

### Requirement: Privacidad de las señas
Los logs de las señas y de la notificación SHALL identificar la seña solo por su id y su referencia: MUST NOT incluir importes, nombres, DNI ni la prestación. El aviso de reintegro al paciente MUST decir solo que se reintegró la seña del turno de esa fecha, sin el importe, y MUST enviarse solo con consentimiento de comunicaciones (RN-NO-03).

#### Scenario: Logs sin importes
- **WHEN** se procesa el pago y después el reintegro de la seña de "Paciente Dos"
- **THEN** los logs del proceso no contienen "4500", "Paciente", "Dos" ni "Consulta"

#### Scenario: Aviso de reintegro sin importe
- **GIVEN** "Paciente Dos" con consentimiento de comunicaciones
- **WHEN** se reintegra la seña de su turno del miércoles 2026-12-09
- **THEN** recibe un aviso con la fecha del turno y sin el importe
