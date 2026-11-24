## MODIFIED Requirements

### Requirement: Lista de mis turnos
`#/mis-turnos` SHALL pedir `GET /api/v1/paciente/turnos` y mostrar en "Próximos turnos" los `RESERVADO` o `CONFIRMADO` que no empezaron, del más cercano al más lejano, y el resto en "Turnos anteriores". Cada turno MUST mostrar fecha, hora, prestación, profesional, estado en español y número de reserva, y nada más. Un turno `RESERVADO` o `CONFIRMADO` cuyo `fin` ya pasó SHALL mostrar como estado "Sin registrar", con el texto "El consultorio todavía no registró la asistencia.", en lugar de "Reservado" o "Confirmado". "Sin registrar" es solo lo que se muestra: la página MUST NOT enviar ningún pedido por ese motivo. Un turno que empezó y todavía no terminó SHALL conservar su estado.

#### Scenario: Próximos y anteriores
- **GIVEN** que la API responde un turno `RESERVADO` el 2026-12-03 a las 10:00 con número `R-0002`, uno `CONFIRMADO` el 2026-12-02 a las 09:00 y uno `CANCELADO` el 2026-11-20 a las 11:00
- **WHEN** se abre `#/mis-turnos`
- **THEN** "Próximos turnos" muestra primero el del miércoles 2 de diciembre a las 09:00 ("Confirmado") y después el del jueves 3 de diciembre a las 10:00 ("Reservado", `R-0002`)
- **AND** "Turnos anteriores" muestra el del 20 de noviembre como "Cancelado"

#### Scenario: Sin turnos
- **WHEN** se abre `#/mis-turnos` y la API responde una lista vacía
- **THEN** se muestra "Todavía no tenés turnos."
- **AND** hay un enlace a "Reservar turno"

#### Scenario: Turno pasado sin registrar
- **GIVEN** que la API responde un turno `RESERVADO` el 2026-11-28 de 10:00 a 10:30 con número `R-0001` y uno `CONFIRMADO` el 2026-11-27 de 09:00 a 09:30
- **WHEN** se abre `#/mis-turnos`
- **THEN** "Turnos anteriores" muestra los dos turnos con el estado "Sin registrar" y el texto "El consultorio todavía no registró la asistencia."
- **AND** ninguno muestra "Reservado" ni "Confirmado"
- **AND** el único pedido a la API es `GET /api/v1/paciente/turnos`

#### Scenario: Turno en curso
- **GIVEN** que la API responde un turno `CONFIRMADO` el 2026-12-01 de 07:45 a 08:15
- **WHEN** se abre `#/mis-turnos`
- **THEN** "Turnos anteriores" muestra el turno con el estado "Confirmado"
- **AND** no se muestra "Sin registrar"

#### Scenario: Turnos registrados se muestran igual
- **GIVEN** que la API responde un turno `ATENDIDO` el 2026-11-25 a las 10:00, uno `AUSENTE` el 2026-11-26 a las 10:00 y uno `CANCELADO` el 2026-11-20 a las 11:00
- **WHEN** se abre `#/mis-turnos`
- **THEN** "Turnos anteriores" los muestra como "Atendido", "Ausente" y "Cancelado"
- **AND** no se muestra "Sin registrar"
