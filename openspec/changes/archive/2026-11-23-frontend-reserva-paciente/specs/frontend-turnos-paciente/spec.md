# Spec Delta

## Purpose

Definir la página "Mis turnos" del paciente: ver solo sus turnos, y cancelarlos o cambiarlos de horario online con su número de reserva mientras falten 24 h o más, con el aviso de contactar al consultorio cuando ya no se puede. En los escenarios, con datos ficticios y salvo que digan otra cosa: hay una sesión de "Paciente Uno" (`paciente.uno@example.com`); el momento actual es el martes 2026-12-01 a las 08:00 en `America/Argentina/Buenos_Aires`; "Consulta" (id 1) dura 30 minutos y la atienden la Dra. Ana Ejemplo (id 1) y el Dr. Bruno Prueba (id 2).

## ADDED Requirements

### Requirement: Lista de mis turnos
`#/mis-turnos` SHALL pedir `GET /api/v1/paciente/turnos` y mostrar en "Próximos turnos" los `RESERVADO` o `CONFIRMADO` que no empezaron, del más cercano al más lejano, y el resto en "Turnos anteriores". Cada turno MUST mostrar fecha, hora, prestación, profesional, estado en español y número de reserva, y nada más.

#### Scenario: Próximos y anteriores
- **GIVEN** que la API responde un turno `RESERVADO` el 2026-12-03 a las 10:00 con número `R-0002`, uno `CONFIRMADO` el 2026-12-02 a las 09:00 y uno `CANCELADO` el 2026-11-20 a las 11:00
- **WHEN** se abre `#/mis-turnos`
- **THEN** "Próximos turnos" muestra primero el del miércoles 2 de diciembre a las 09:00 ("Confirmado") y después el del jueves 3 de diciembre a las 10:00 ("Reservado", `R-0002`)
- **AND** "Turnos anteriores" muestra el del 20 de noviembre como "Cancelado"

#### Scenario: Sin turnos
- **WHEN** se abre `#/mis-turnos` y la API responde una lista vacía
- **THEN** se muestra "Todavía no tenés turnos."
- **AND** hay un enlace a "Reservar turno"

### Requirement: Acciones según el plazo de 24 horas
Un próximo turno con `modificableOnline` verdadero SHALL ofrecer "Cancelar turno" y "Cambiar horario". Si es falso, MUST NOT ofrecerlas y SHALL mostrar "Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio."

#### Scenario: Turno dentro y fuera del plazo
- **GIVEN** un turno `RESERVADO` el 2026-12-03 a las 10:00 con `modificableOnline` verdadero y otro el 2026-12-01 a las 18:00 con `modificableOnline` falso
- **WHEN** se abre `#/mis-turnos`
- **THEN** el turno del 3 de diciembre tiene los botones "Cancelar turno" y "Cambiar horario"
- **AND** el del 1 de diciembre no tiene botones y muestra "Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio."

### Requirement: Cancelar un turno
"Cancelar turno" SHALL abrir un diálogo "¿Cancelar el turno?" con la fecha, la hora y el profesional, y los botones "Sí, cancelar turno" y "Volver". Al confirmar, SHALL enviar `POST /api/v1/paciente/reservas/{numeroReserva}/cancelar`; ante `200`, MUST mostrar "Tu turno se canceló." y volver a pedir la lista. Al elegir "Volver", MUST NOT enviar nada.

#### Scenario: Cancelación confirmada
- **GIVEN** el turno `R-0002` del 2026-12-03 a las 10:00 con `modificableOnline` verdadero
- **WHEN** se elige "Cancelar turno" y después "Sí, cancelar turno", y la API responde `200`
- **THEN** se envió `POST /api/v1/paciente/reservas/R-0002/cancelar`
- **AND** se muestra "Tu turno se canceló." y se vuelve a pedir `GET /api/v1/paciente/turnos`

#### Scenario: Cancelación desistida
- **GIVEN** el turno `R-0002` con `modificableOnline` verdadero
- **WHEN** se elige "Cancelar turno" y después "Volver"
- **THEN** no se envía ningún pedido de cancelación
- **AND** el turno sigue en "Próximos turnos"

### Requirement: Cambiar el horario de un turno
"Cambiar horario" SHALL abrir, en el mismo turno, la elección de fecha y profesional (por defecto, el del turno, o "Sin preferencia") con los horarios libres de su prestación de `GET /api/v1/public/disponibilidad`. Al elegir uno y confirmar "Cambiar a las HH:mm", SHALL enviar `POST /api/v1/paciente/reservas/{numeroReserva}/reprogramar` con `inicio` y `profesionalId` si hay uno elegido.

#### Scenario: Horarios para cambiar con el mismo profesional
- **GIVEN** el turno `R-0002` de "Consulta" del 2026-12-03 a las 10:00 con "Ana Ejemplo" y `modificableOnline` verdadero
- **WHEN** se elige "Cambiar horario"
- **THEN** se envía `GET /api/v1/public/disponibilidad?prestacionId=1&fecha=2026-12-03&profesionalId=1`
- **AND** el selector de profesional tiene elegido "Ana Ejemplo" y ofrece "Sin preferencia"

#### Scenario: Cambio confirmado
- **GIVEN** los horarios para cambiar el turno `R-0002` a la vista, con "Ana Ejemplo"
- **WHEN** se elige "11:00", después "Cambiar a las 11:00", y la API responde `200` con el número de reserva `R-0003`
- **THEN** se envió `POST /api/v1/paciente/reservas/R-0002/reprogramar` con `inicio` `2026-12-03T11:00:00-03:00` y `profesionalId` 1
- **AND** se muestra "Cambiamos tu turno al jueves 3 de diciembre a las 11:00. Tu nuevo número de reserva es R-0003."
- **AND** se vuelve a pedir `GET /api/v1/paciente/turnos`

#### Scenario: Cambio sin preferencia
- **GIVEN** los horarios para cambiar el turno `R-0002` a la vista
- **WHEN** se elige "Sin preferencia", después un horario, y se confirma el cambio
- **THEN** el pedido de reprogramación no incluye `profesionalId`

### Requirement: Rechazos al cancelar o cambiar
Ante un rechazo, la página MUST mostrar el mensaje del código y volver a pedir la lista:
- `CANCELACION_FUERA_DE_PLAZO`: "Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio."
- `RESERVA_INEXISTENTE`: "No encontramos ese turno."
- `TURNO_NO_ACTIVO`: "El turno ya no está activo."

Para `SIN_DISPONIBILIDAD` al cambiar, MUST ofrecer las `alternativas` y conservar el turno original.

#### Scenario: Plazo vencido mientras la página estaba abierta
- **GIVEN** el turno `R-0002` con `modificableOnline` verdadero
- **WHEN** se confirma la cancelación y la API responde `409` con `CANCELACION_FUERA_DE_PLAZO`
- **THEN** se muestra "Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio."
- **AND** se vuelve a pedir `GET /api/v1/paciente/turnos`

#### Scenario: Turno que ya no existe
- **GIVEN** el turno `R-0002` con `modificableOnline` verdadero
- **WHEN** se confirma la cancelación y la API responde `404` con `RESERVA_INEXISTENTE`
- **THEN** se muestra "No encontramos ese turno."

#### Scenario: Nuevo horario ocupado
- **GIVEN** los horarios para cambiar el turno `R-0002` a la vista
- **WHEN** se confirma el cambio a las 11:00 y la API responde `409` con `SIN_DISPONIBILIDAD` y una alternativa a las 11:30
- **THEN** se muestra "Ese horario se ocupó recién. Elegí otro." con el botón "11:30"
- **AND** no se vuelve a pedir la lista, y el turno sigue a las 10:00

### Requirement: Entrada desde los links de los avisos
Los avisos de C-11 llevan links a `/mis-turnos/cancelar` y `/mis-turnos/reprogramar`, sin número de reserva. Al abrir la aplicación en cualquiera de esas direcciones, SHALL reemplazar la dirección por `/#/mis-turnos`, sin sumar una entrada al historial, y mostrar "Mis turnos" con la misma guarda de sesión que esa página.

#### Scenario: Link de cancelar sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `/mis-turnos/cancelar`
- **THEN** la dirección pasa a ser `/#/mis-turnos` y después `/#/mi-cuenta`
- **AND** al ingresar como "Paciente Uno" se muestra "Mis turnos"

#### Scenario: Link de reprogramar con sesión
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** se abre la aplicación en la dirección `/mis-turnos/reprogramar`
- **THEN** se muestra "Mis turnos" en la dirección `/#/mis-turnos`

### Requirement: Mis turnos en el celular
En una pantalla de 390 × 844 px, cada turno SHALL verse como una tarjeta a lo ancho con sus botones de al menos 44 px de alto, y la página MUST NOT tener desplazamiento horizontal.

#### Scenario: Mis turnos en el celular
- **GIVEN** la página `#/mis-turnos` con próximos turnos en una pantalla de 390 × 844 px
- **WHEN** se mide la página
- **THEN** el ancho del documento no supera el de la pantalla
- **AND** los botones "Cancelar turno" y "Cambiar horario" miden al menos 44 px de alto
