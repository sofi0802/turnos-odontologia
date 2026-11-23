## MODIFIED Requirements

### Requirement: Crear un turno
El botón "Nuevo turno" SHALL abrir un formulario con paciente, profesional, sillón, prestación, fecha y hora. El paciente se elige con el buscador de pacientes (capacidad `frontend-buscador-pacientes`), no escribiendo su id. Al enviarlo SHALL pedir `POST /api/v1/turnos` con el id del paciente elegido, los demás ids e `inicio` en ISO-8601 con zona horaria. Con `201`, MUST cerrar el formulario, mostrar "Turno creado. Número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción crea un turno
- **GIVEN** la agenda del día abierta
- **WHEN** se busca "99000001" en el campo "Paciente", se elige "Uno, Paciente · DNI 99000001", se completan "Ana Ejemplo", "Sillón 1", "Consulta", 2026-12-01 a las 10:00 y se envía
- **THEN** se pide `POST /api/v1/turnos` con `pacienteId` 1, `profesionalId` 1, `sillonId` 1, `prestacionId` 1 e `inicio` `2026-12-01T10:00:00-03:00`
- **AND** al responder `201` con `numeroReserva` `R-0001` se ve "Turno creado. Número de reserva: R-0001."
- **AND** se vuelve a pedir la agenda del día

#### Scenario: Faltan datos en el formulario
- **GIVEN** el formulario de nuevo turno abierto
- **WHEN** se envía sin elegir el sillón
- **THEN** no se envía ningún pedido
- **AND** el campo "Sillón" muestra "Elegí un sillón."

#### Scenario: Paciente escrito pero no elegido
- **GIVEN** el formulario de nuevo turno completo, con "fict" escrito en el campo "Paciente" y ninguna opción elegida
- **WHEN** se envía
- **THEN** no se envía ningún pedido a `/api/v1/turnos`
- **AND** el campo "Paciente" muestra "Elegí un paciente de la lista."

#### Scenario: Ya no se pide el id del paciente
- **WHEN** se abre el formulario de nuevo turno
- **THEN** no hay ningún campo "Id del paciente"
- **AND** el primer campo es "Paciente", con el foco y `role="combobox"`

#### Scenario: Error de la API en el paciente
- **GIVEN** el formulario de nuevo turno completo con "Carla Ficticia" elegida
- **WHEN** se envía y la API responde `422` con el código `DATOS_INVALIDOS` y un error del campo `pacienteId` con el mensaje "no existe"
- **THEN** el campo "Paciente" muestra "no existe"
- **AND** el formulario sigue abierto
