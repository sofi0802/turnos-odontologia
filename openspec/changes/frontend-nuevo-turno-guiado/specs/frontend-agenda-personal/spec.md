## MODIFIED Requirements

### Requirement: Crear un turno
El botón "Nuevo turno" SHALL abrir un formulario guiado con paciente (buscador de `frontend-buscador-pacientes`), prestación, profesional, fecha y horario, y el sillón del profesional a la vista. Al enviarlo SHALL pedir `POST /api/v1/turnos` con esos ids, el sillón habitual y el `inicio` del horario elegido tal como lo dio la API. Con `201`, MUST cerrar el formulario, mostrar "Turno creado. Número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción crea un turno
- **GIVEN** la agenda del día abierta, hoy martes 2026-12-01, y la Dra. Ana Ejemplo con el Sillón 1 como sillón habitual
- **WHEN** se elige "Uno, Paciente · DNI 99000001" en "Paciente", "Consulta" en "Prestación", "Ana Ejemplo" en "Profesional", el 2026-12-01 en el calendario y el horario "10:00", y se envía
- **THEN** se pide `POST /api/v1/turnos` con `pacienteId` 1, `prestacionId` 1, `profesionalId` 1, `sillonId` 1 e `inicio` `2026-12-01T10:00:00-03:00`
- **AND** al responder `201` con `numeroReserva` `R-0001` se ve "Turno creado. Número de reserva: R-0001."
- **AND** se vuelve a pedir la agenda del día

#### Scenario: Faltan datos en el formulario
- **GIVEN** el formulario de nuevo turno con paciente, prestación, profesional y fecha elegidos, pero sin horario
- **WHEN** se envía sin elegir un horario
- **THEN** no se envía ningún pedido a `/api/v1/turnos`
- **AND** la zona de horarios muestra "Elegí un horario."

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

## ADDED Requirements

### Requirement: Pasos del nuevo turno en orden
En el formulario de nuevo turno, "Profesional" MUST estar deshabilitado hasta elegir una prestación, el calendario hasta elegir un profesional y los horarios hasta elegir un día. Al cambiar la prestación, el profesional o el día, SHALL vaciarse lo elegido en los pasos siguientes. Las prestaciones son las activas del catálogo.

#### Scenario: Al abrir solo se elige la prestación
- **WHEN** se abre el formulario de nuevo turno
- **THEN** "Prestación" ofrece "Elegí una prestación" y las prestaciones activas
- **AND** "Profesional" está deshabilitado
- **AND** el calendario y los horarios están deshabilitados

#### Scenario: Cambiar la prestación vacía los pasos siguientes
- **GIVEN** el formulario con "Consulta", "Ana Ejemplo", el 2026-12-01 y el horario "10:00" elegidos
- **WHEN** se elige otra prestación
- **THEN** "Profesional" vuelve a "Elegí un profesional", con los habilitados para la prestación nueva
- **AND** no hay día ni horario elegido y el calendario está deshabilitado

#### Scenario: Cambiar el día vacía el horario
- **GIVEN** el formulario con el horario "10:00" del 2026-12-01 elegido
- **WHEN** se elige el 2026-12-02 en el calendario
- **THEN** no hay horario elegido
- **AND** se piden los horarios del 2026-12-02

### Requirement: Profesionales habilitados al crear un turno
Al elegir una prestación, "Profesional" SHALL ofrecer solo los profesionales que devuelve `GET /api/v1/public/profesionales?prestacionId=` (RN-AG-15). Si la consulta falla, MUST mostrarse "No pudimos cargar los profesionales. Probá elegir la prestación de nuevo." y el paso queda deshabilitado.

#### Scenario: Solo los habilitados para la prestación
- **GIVEN** que para "Consulta" la API devuelve solo a "Ana Ejemplo"
- **WHEN** se elige "Consulta" en "Prestación"
- **THEN** se pide `GET /api/v1/public/profesionales?prestacionId=1`
- **AND** "Profesional" ofrece "Elegí un profesional" y "Ana Ejemplo", y no ofrece a "Bruno Prueba"

#### Scenario: No se pueden cargar los profesionales
- **GIVEN** que `GET /api/v1/public/profesionales` responde `500`
- **WHEN** se elige "Consulta" en "Prestación"
- **THEN** se ve "No pudimos cargar los profesionales. Probá elegir la prestación de nuevo."
- **AND** "Profesional" sigue deshabilitado

### Requirement: Calendario desde hoy
La fecha SHALL elegirse en un calendario mensual que empieza en el mes de hoy, con botones para ir al mes anterior y al siguiente. Los días anteriores a hoy MUST estar deshabilitados y no se puede ir a un mes anterior al de hoy. No hay tope hacia adelante. El día elegido queda marcado con `aria-pressed="true"` y cada día se anuncia con su fecha completa.

#### Scenario: Los días pasados no se pueden elegir
- **GIVEN** hoy es martes 2026-12-01 y el formulario tiene un profesional elegido
- **WHEN** se mira el calendario
- **THEN** se ve diciembre de 2026, con el 1 de diciembre habilitado
- **AND** los días de noviembre que completan la primera semana están deshabilitados
- **AND** el botón del mes anterior está deshabilitado

#### Scenario: Ir al mes siguiente
- **GIVEN** el calendario en diciembre de 2026
- **WHEN** se usa el botón del mes siguiente
- **THEN** se ve enero de 2027 con todos sus días habilitados
- **AND** el botón del mes anterior está habilitado

#### Scenario: Elegir un día
- **GIVEN** el calendario en diciembre de 2026 con "Ana Ejemplo" y "Consulta" elegidos
- **WHEN** se elige el 3 de diciembre
- **THEN** ese día queda marcado como elegido
- **AND** se pide `GET /api/v1/public/disponibilidad?prestacionId=1&fecha=2026-12-03&profesionalId=1`

### Requirement: Horarios libres al crear un turno
Al elegir un día SHALL mostrarse como botones los horarios libres del profesional que devuelve `GET /api/v1/public/disponibilidad`, sin los que pisan otro turno activo del sillón habitual ese día, contando el margen vigente (`GET /api/v1/configuracion/margen`). Para eso SHALL consultarse `GET /api/v1/turnos` de ese día con el `sillonId` del sillón habitual. Sin horarios, MUST mostrarse "No hay horarios libres ese día. Probá con otro día.".

#### Scenario: Horarios del día como botones
- **GIVEN** que la disponibilidad del 2026-12-01 ofrece 10:00, 10:30 y 11:00, y el Sillón 1 no tiene turnos ese día
- **WHEN** se elige el 2026-12-01 en el calendario
- **THEN** se ven los botones "10:00", "10:30" y "11:00"

#### Scenario: Se ocultan los horarios que pisan el sillón habitual
- **GIVEN** la Dra. Ana Ejemplo con el Sillón 1 como habitual, un margen de 10 minutos, la disponibilidad del 2026-12-01 con 10:00, 10:30, 11:00 y 11:30 para "Consulta" de 30 minutos, y un turno activo del Dr. Bruno Prueba en el Sillón 1 de 11:00 a 11:30
- **WHEN** se elige el 2026-12-01 en el calendario
- **THEN** se pide `GET /api/v1/turnos` del 2026-12-01 con `sillonId=1`
- **AND** se ven los botones "10:00" y "11:30" (con el margen, 10:30 y 11:00 chocan con el turno de las 11:00)

#### Scenario: Día sin horarios
- **GIVEN** que la disponibilidad del 2026-12-05 responde una lista vacía
- **WHEN** se elige el 2026-12-05 en el calendario
- **THEN** se ve "No hay horarios libres ese día. Probá con otro día."
- **AND** se puede elegir otro día

#### Scenario: No se pueden cargar los horarios
- **GIVEN** que `GET /api/v1/public/disponibilidad` responde `500`
- **WHEN** se elige un día en el calendario
- **THEN** se ve "No pudimos cargar los horarios. Elegí el día de nuevo para volver a intentar."

### Requirement: Sillón habitual del profesional
Al elegir un profesional, el formulario SHALL mostrar como texto, sin poder cambiarlo, el nombre de su sillón habitual (campo `sillonHabitualId` de `GET /api/v1/profesionales`), y es el `sillonId` que se envía. Si el profesional no tiene sillón habitual, MUST mostrarse "Ana Ejemplo no tiene un sillón asignado. Pedile al administrador que lo asigne en Configuración." y el turno no se puede crear.

#### Scenario: Se muestra el sillón habitual sin poder cambiarlo
- **GIVEN** la Dra. Ana Ejemplo con el Sillón 1 como sillón habitual
- **WHEN** se elige "Ana Ejemplo" en "Profesional"
- **THEN** se ve "Sillón: Sillón 1"
- **AND** no hay ningún selector ni campo para elegir el sillón

#### Scenario: Profesional sin sillón habitual
- **GIVEN** la Dra. Ana Ejemplo sin sillón habitual
- **WHEN** se elige "Ana Ejemplo" en "Profesional"
- **THEN** se ve "Ana Ejemplo no tiene un sillón asignado. Pedile al administrador que lo asigne en Configuración."
- **AND** el calendario sigue deshabilitado y "Crear turno" no envía ningún pedido
