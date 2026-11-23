# Spec Delta

## MODIFIED Requirements

### Requirement: Reprogramar un turno
Al elegir un turno SHALL ofrecerse "Reprogramar", que pide DNI y número de reserva, deja elegir el profesional y el horario libre y envía `POST /api/v1/turnos/{id}/reprogramar`. Con `200`, MUST mostrar "Turno reprogramado. Nuevo número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción reprograma un turno
- **GIVEN** la agenda con el turno 7 de "Paciente Uno" de "Consulta" a las 10:00 con la Dra. Ana Ejemplo
- **WHEN** se elige "Reprogramar", se ingresa el DNI `99000001` y el número de reserva `R-0001`, se elige el botón de las 11:00 del mismo día sin cambiar profesional, y se confirma
- **THEN** se pide `POST /api/v1/turnos/7/reprogramar` con `dni`, `numeroReserva` e `inicio` `2026-12-01T11:00:00-03:00`, sin `profesionalId` ni `sillonId`
- **AND** al responder `200` con `numeroReserva` `R-0002` se ve "Turno reprogramado. Nuevo número de reserva: R-0002."

#### Scenario: Reprogramación rechazada por el sillón
- **GIVEN** el formulario de reprogramación del turno 7 completo
- **WHEN** se confirma y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El sillón ya está ocupado en ese horario. Elegí otro sillón."
- **AND** el formulario sigue abierto con el horario elegido

## ADDED Requirements

### Requirement: Profesionales habilitados al reprogramar
El selector de profesional de "Reprogramar" SHALL ofrecer solo los profesionales habilitados para la prestación del turno, obtenidos con `GET /api/v1/public/profesionales?prestacionId=`, más la opción de dejar el profesional actual.

#### Scenario: Solo los habilitados para la prestación
- **GIVEN** el turno 7 de "Consulta" (prestación 2) y la API que responde, para `prestacionId=2`, solo a la Dra. Ana Ejemplo y al Dr. Bruno Prueba
- **WHEN** se abre "Reprogramar"
- **THEN** el selector ofrece "Sin cambios", "Ana Ejemplo" y "Bruno Prueba"
- **AND** no ofrece a ningún otro profesional activo del consultorio

#### Scenario: No se pudo cargar la lista de habilitados
- **GIVEN** el turno 7 y la API de profesionales que responde con error
- **WHEN** se abre "Reprogramar"
- **THEN** se ve un aviso de que no se pudieron cargar los profesionales
- **AND** el selector ofrece solo "Sin cambios" y el formulario sigue usable con el profesional actual

### Requirement: Horarios libres al reprogramar
El formulario SHALL pedir la fecha y mostrar, como botones, los horarios libres del profesional elegido ese día, obtenidos con `GET /api/v1/public/disponibilidad`. La confirmación MUST exigir un horario elegido y MUST enviar su `inicio` tal como lo dio la API.

#### Scenario: Se muestran los horarios del profesional
- **GIVEN** el formulario del turno 7 abierto, con "Sin cambios" y la fecha 2026-12-01
- **WHEN** la API de disponibilidad, pedida con `prestacionId=2`, `fecha=2026-12-01` y `profesionalId` de la Dra. Ana Ejemplo, responde horarios a las 11:00 y a las 11:30
- **THEN** se ven los botones "11:00" y "11:30"
- **AND** ninguno está elegido

#### Scenario: Cambiar de profesional o de fecha vuelve a pedir los horarios
- **GIVEN** el formulario con un horario elegido
- **WHEN** se elige al Dr. Bruno Prueba o se cambia la fecha
- **THEN** se vuelven a pedir los horarios con el profesional y la fecha nuevos
- **AND** el horario elegido se descarta

#### Scenario: Día sin horarios libres
- **GIVEN** el formulario del turno 7 abierto
- **WHEN** la API de disponibilidad responde una lista vacía
- **THEN** se ve "No hay horarios libres ese día."
- **AND** el botón de confirmar no envía ningún pedido

#### Scenario: Confirmar sin elegir horario
- **GIVEN** el formulario con DNI y número de reserva completos y horarios a la vista
- **WHEN** se confirma sin elegir ningún botón
- **THEN** se ve "Elegí un horario." y no se envía `POST /api/v1/turnos/7/reprogramar`

#### Scenario: Falla la consulta de horarios
- **GIVEN** el formulario del turno 7 abierto
- **WHEN** la API de disponibilidad responde con error
- **THEN** se ve un aviso de que no se pudieron cargar los horarios
- **AND** el formulario sigue abierto y se puede volver a intentar al cambiar la fecha

#### Scenario: Aviso de los horarios que pisan el turno actual
- **GIVEN** el formulario del turno 7 abierto con horarios a la vista
- **WHEN** se muestran los botones
- **THEN** se ve un texto que aclara que los horarios que se superponen con el turno actual no aparecen y que, para esos, hay que cancelar y crear el turno de nuevo

#### Scenario: Horarios en el celular
- **WHEN** se abre "Reprogramar" en una pantalla de 390 × 844 px
- **THEN** los botones de horario se ven en filas que se ajustan al ancho, con al menos 44 × 44 px cada uno
- **AND** el diálogo no tiene desplazamiento horizontal

### Requirement: Sillón al reprogramar
El formulario SHALL mantener el selector de sillón con la opción "Sin cambios". Si el horario elegido solo está libre en otro sillón, el rechazo por sillón MUST explicar que hay que elegir otro sillón.

#### Scenario: Elegir otro sillón después de un rechazo
- **GIVEN** el formulario con el horario de las 11:00 elegido y "Sin cambios" en el sillón
- **WHEN** la API responde `409` con el código `SOLAPAMIENTO_SILLON` y luego se elige el "Sillón 2" y se confirma otra vez
- **THEN** se pide otra vez `POST /api/v1/turnos/7/reprogramar` con `sillonId` del "Sillón 2"
- **AND** el DNI y el número de reserva siguen cargados

### Requirement: Identificación al reprogramar
El formulario SHALL aceptar el número de reserva escrito en minúsculas, con espacios o sin el guion, y enviarlo en el formato `XXXX-XXXX`. Si la API lo rechaza con `DATOS_DE_RESERVA_INVALIDOS`, MUST mostrar un mensaje que no diga cuál de los dos datos es el incorrecto.

#### Scenario: Número de reserva sin guion
- **GIVEN** el formulario del turno 7 con el DNI `99000001` y un horario elegido
- **WHEN** se ingresa el número de reserva ` abcd2345 ` y se confirma
- **THEN** el pedido lleva `numeroReserva` `ABCD-2345`
- **AND** no se muestra ningún error de formato

#### Scenario: DNI o número de reserva incorrectos
- **GIVEN** el formulario del turno 7 completo
- **WHEN** se confirma y la API responde `409` con el código `DATOS_DE_RESERVA_INVALIDOS`
- **THEN** se ve "El DNI o el número de reserva no son correctos."
- **AND** el formulario sigue abierto con el horario elegido

### Requirement: Mensajes de rechazo de la reprogramación
Cada código de rechazo que la reprogramación puede devolver MUST tener un texto en español propio, y un código sin texto MUST mostrar un mensaje genérico, nunca vacío ni el código interno.

#### Scenario: Profesional no habilitado
- **GIVEN** el formulario completo con otro profesional elegido
- **WHEN** la API responde `409` con el código `PROFESIONAL_NO_HABILITADO`
- **THEN** se ve "Ese profesional no atiende esa prestación. Elegí otro."

#### Scenario: Profesional ausente o fuera de horario
- **GIVEN** el formulario completo
- **WHEN** la API responde `409` con el código `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA` o `FUERA_DE_HORARIO`
- **THEN** se ve el texto propio de cada código
- **AND** el formulario sigue abierto

#### Scenario: Código desconocido
- **GIVEN** el formulario completo
- **WHEN** la API responde `409` con un código que el frontend no conoce
- **THEN** se ve el mensaje genérico de error y no el código

### Requirement: Un horario ofrecido se puede reprogramar
Un horario que la disponibilidad ofrece al profesional elegido MUST poder reprogramarse sin que el formulario lo estorbe: el pedido lleva exactamente lo que el personal eligió, y los rechazos del backend llegan al personal con su motivo.

#### Scenario: Reprogramar a un horario ofrecido
- **GIVEN** el formulario del turno 7 con el botón de las 11:00 ofrecido por la API
- **WHEN** se elige ese botón, se completa la identificación y se confirma
- **THEN** el pedido lleva el `inicio` que dio la API, sin ajustarlo ni recalcularlo
- **AND** al responder `200` se cierra el formulario y se recarga la agenda
