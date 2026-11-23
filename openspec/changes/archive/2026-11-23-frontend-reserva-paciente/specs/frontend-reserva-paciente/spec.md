# Spec Delta

## Purpose

Definir la página pública "Reservar turno", pensada para el celular y con un paso por pantalla: el paciente elige la prestación, después el profesional o "Sin preferencia", la fecha y el horario, y confirma con su sesión de paciente. Al confirmar recibe su número de reserva, u otras opciones si el horario se ocupó. En los escenarios, con datos ficticios y salvo que digan otra cosa: hoy es el martes 2026-12-01 en `America/Argentina/Buenos_Aires`; "Consulta" (id 1) dura 30 minutos; la Dra. Ana Ejemplo (id 1) y el Dr. Bruno Prueba (id 2) están habilitados para "Consulta"; y "Paciente Uno" (`paciente.uno@example.com`) es un paciente con cuenta.

## ADDED Requirements

### Requirement: Reserva en tres pasos
`#/reserva` SHALL abrir sin sesión, con el título "Reservar turno", y mostrar un paso por vez con su indicador ("Paso N de 3"):
1. "¿Qué necesitás?"
2. "Elegí día y horario"
3. "Confirmá tu turno"

"Continuar" pasa al paso siguiente solo con lo del paso completo, y "Volver" regresa al anterior sin perder lo elegido.

#### Scenario: Pasar del paso 1 al 2
- **GIVEN** la página `#/reserva` abierta sin sesión, en el paso "¿Qué necesitás?"
- **WHEN** se elige "Consulta" y después "Continuar"
- **THEN** se muestra el paso "Elegí día y horario", con el indicador "Paso 2 de 3"

#### Scenario: Continuar sin elegir
- **GIVEN** la página `#/reserva` en el paso "¿Qué necesitás?" sin prestación elegida
- **WHEN** se elige "Continuar"
- **THEN** se sigue en el paso 1 con el mensaje "Elegí una prestación."

#### Scenario: Volver sin perder lo elegido
- **GIVEN** el paso "Elegí día y horario" de "Consulta" con "Bruno Prueba" elegido
- **WHEN** se elige "Volver" y después "Continuar"
- **THEN** "Consulta" sigue elegida en el paso 1
- **AND** "Bruno Prueba" sigue elegido en el paso 2

### Requirement: Elección de la prestación
El paso 1 SHALL cargar las prestaciones de `GET /api/v1/public/prestaciones` y mostrarlas como opciones con su nombre y su duración ("Consulta · 30 minutos"). Si no hay prestaciones o el pedido falla, MUST mostrar "No pudimos cargar las prestaciones. Probá de nuevo en unos minutos." sin el botón "Continuar".

#### Scenario: Prestaciones a elegir
- **GIVEN** la página `#/reserva` abierta sin sesión
- **WHEN** la API de prestaciones responde "Consulta" con 30 minutos
- **THEN** el paso 1 ofrece la opción "Consulta · 30 minutos"

### Requirement: Elección del profesional y la fecha
Al pasar al paso 2, SHALL pedir `GET /api/v1/public/profesionales?prestacionId=` y ofrecer "Sin preferencia" ("Te asignamos quien esté libre"), elegido de entrada, seguido de esos profesionales. La fecha empieza en hoy y MUST NOT aceptar días anteriores.

#### Scenario: Profesionales de la prestación elegida
- **GIVEN** "Consulta" elegida en el paso 1
- **WHEN** se pasa al paso 2
- **THEN** se envía `GET /api/v1/public/profesionales?prestacionId=1`
- **AND** las opciones de profesional son "Sin preferencia", "Ana Ejemplo" y "Bruno Prueba", con "Sin preferencia" elegida

#### Scenario: Fecha inicial y días pasados
- **GIVEN** el paso 2 de "Consulta"
- **WHEN** se mira el campo de fecha
- **THEN** su valor es `2026-12-01`
- **AND** no permite elegir el `2026-11-30`

### Requirement: Horarios libres del día
En el paso 2, la página SHALL pedir `GET /api/v1/public/disponibilidad` con `prestacionId`, `fecha` y, si hay un profesional elegido, `profesionalId`, y mostrar cada horario como un botón con su hora de inicio (`HH:mm`) en la zona del consultorio. Al cambiar el profesional o la fecha, SHALL volver a pedirlos y descartar el horario elegido. "Continuar" requiere un horario.

#### Scenario: Horarios sin preferencia
- **GIVEN** el paso 2 de "Consulta" con la fecha `2026-12-01` y "Sin preferencia"
- **WHEN** la API responde horarios que empiezan a las 10:00 y a las 10:30 (`-03:00`)
- **THEN** el pedido fue `GET /api/v1/public/disponibilidad?prestacionId=1&fecha=2026-12-01`, sin `profesionalId`
- **AND** se muestran los botones "10:00" y "10:30"

#### Scenario: Horarios de un profesional elegido
- **GIVEN** el paso 2 de "Consulta" con la fecha `2026-12-01`
- **WHEN** se elige "Bruno Prueba"
- **THEN** se envía `GET /api/v1/public/disponibilidad?prestacionId=1&fecha=2026-12-01&profesionalId=2`

#### Scenario: Horario elegido
- **GIVEN** los horarios "10:00" y "10:30" a la vista
- **WHEN** se elige "10:30"
- **THEN** el botón "10:30" queda marcado como elegido (`aria-pressed="true"`) y "10:00" no

### Requirement: Día sin horarios libres
Si la disponibilidad responde una lista vacía, el paso 2 MUST mostrar "No hay horarios libres para ese día. Probá con otra fecha o con otro profesional." y no permitir continuar.

#### Scenario: Día completo
- **GIVEN** el paso 2 de "Consulta" con la fecha `2026-12-01`
- **WHEN** la API de disponibilidad responde una lista vacía
- **THEN** se muestra "No hay horarios libres para ese día. Probá con otra fecha o con otro profesional."
- **AND** el botón "Continuar" está deshabilitado

### Requirement: Resumen antes de confirmar
El paso 3 SHALL mostrar la prestación, el profesional o "Sin preferencia (te asignamos uno)", la fecha y la hora, con el botón "Confirmar reserva". MUST NOT pedir datos del paciente: salen de su cuenta.

#### Scenario: Resumen sin preferencia
- **GIVEN** "Consulta", "Sin preferencia" y el horario de las 10:30 del `2026-12-01` elegidos
- **WHEN** se pasa al paso 3
- **THEN** el resumen dice "Consulta", "Sin preferencia (te asignamos uno)", "martes 1 de diciembre" y "10:30"
- **AND** se muestra el botón "Confirmar reserva" y ningún campo de datos personales

### Requirement: Confirmar sin sesión de paciente
Si se elige "Confirmar reserva" sin sesión, la aplicación MUST NOT enviar la reserva. SHALL conservar la prestación, el profesional, la fecha y el horario elegidos en esa pestaña y mostrar `#/mi-cuenta` con el aviso "Ingresá o creá tu cuenta para confirmar la reserva." Después de ingresar o registrarse, SHALL volver al paso 3 de `#/reserva` con el mismo resumen, sin confirmar sola.

#### Scenario: Confirmar sin sesión
- **GIVEN** el resumen de "Consulta" del `2026-12-01` a las 10:30 sin sesión
- **WHEN** se elige "Confirmar reserva"
- **THEN** no se envía ningún pedido a `/api/v1/paciente/reservas`
- **AND** se muestra `#/mi-cuenta` con el aviso "Ingresá o creá tu cuenta para confirmar la reserva."

#### Scenario: Vuelta a la reserva después de ingresar
- **GIVEN** una reserva pendiente de "Consulta" del `2026-12-01` a las 10:30
- **WHEN** "Paciente Uno" ingresa correctamente desde `#/mi-cuenta`
- **THEN** se muestra el paso 3 de `#/reserva` con el resumen de "Consulta" del martes 1 de diciembre a las 10:30
- **AND** no se envió ningún pedido a `/api/v1/paciente/reservas`

### Requirement: Reserva confirmada
Con sesión de paciente, "Confirmar reserva" SHALL enviar `POST /api/v1/paciente/reservas` con `prestacionId`, el `inicio` tal como lo dio la disponibilidad y `profesionalId` solo si se eligió uno. Ante `201` MUST:
- Mostrar "¡Turno reservado!" con el número de reserva, la fecha, la hora, la prestación y el profesional asignado.
- Recordar que el número sirve para cancelar o reprogramar.
- Ofrecer un enlace a "Mis turnos" y borrar la reserva pendiente.

#### Scenario: Reserva sin preferencia confirmada
- **GIVEN** una sesión de `paciente.uno@example.com` y el resumen de "Consulta" del `2026-12-01` a las 10:30 con "Sin preferencia"
- **WHEN** se elige "Confirmar reserva" y la API responde `201` con el número de reserva `R-0001` y el profesional "Bruno Prueba"
- **THEN** el pedido tuvo `prestacionId` 1 e `inicio` `2026-12-01T10:30:00-03:00`, sin `profesionalId`
- **AND** se muestra "¡Turno reservado!" con `R-0001`, "martes 1 de diciembre", "10:30", "Consulta" y "Bruno Prueba"
- **AND** hay un enlace a "Mis turnos"

#### Scenario: Doble envío
- **GIVEN** una sesión de paciente y el resumen de una reserva
- **WHEN** se elige "Confirmar reserva" dos veces seguidas antes de la respuesta
- **THEN** se envía un solo pedido a `/api/v1/paciente/reservas`

### Requirement: Horario ocupado al confirmar
Si la reserva responde `409` con `SIN_DISPONIBILIDAD`, el paso 3 MUST mostrar "Ese horario se ocupó recién. Elegí otro." y ofrecer como botones los horarios de `alternativas`. Al elegir uno, SHALL actualizar el resumen sin confirmarlo. Sin alternativas, MUST volver al paso 2 con "No quedan horarios libres ese día. Elegí otra fecha." y volver a pedir los horarios.

#### Scenario: Horario tomado con alternativas
- **GIVEN** una sesión de paciente y el resumen de "Consulta" del `2026-12-01` a las 10:30
- **WHEN** se confirma y la API responde `409` con `SIN_DISPONIBILIDAD` y alternativas a las 11:00 y a las 11:30
- **THEN** se muestra "Ese horario se ocupó recién. Elegí otro."
- **AND** se ofrecen los botones "11:00" y "11:30"

#### Scenario: Elegir una alternativa
- **GIVEN** las alternativas "11:00" y "11:30" a la vista
- **WHEN** se elige "11:00"
- **THEN** el resumen muestra "11:00" con el botón "Confirmar reserva"
- **AND** no se envió una reserva nueva

#### Scenario: Horario tomado sin alternativas
- **GIVEN** una sesión de paciente y el resumen de una reserva
- **WHEN** se confirma y la API responde `409` con `SIN_DISPONIBILIDAD` y `alternativas` vacía
- **THEN** se muestra el paso 2 con "No quedan horarios libres ese día. Elegí otra fecha."
- **AND** se vuelve a pedir `GET /api/v1/public/disponibilidad` para esa fecha

### Requirement: Otros rechazos de la reserva
Si la reserva responde `409` con `INICIO_EN_EL_PASADO`, `PROFESIONAL_NO_HABILITADO` o `RECURSO_INACTIVO`, la página MUST volver al paso 2 con el mensaje en español de ese código, sin detalles internos, descartar el horario elegido y volver a pedir los horarios.

#### Scenario: El horario pasó mientras se elegía
- **GIVEN** una sesión de paciente y el resumen de una reserva a las 10:30
- **WHEN** se confirma y la API responde `409` con `INICIO_EN_EL_PASADO`
- **THEN** se muestra el paso 2 con "El horario elegido ya pasó."
- **AND** se vuelve a pedir la disponibilidad y no queda ningún horario elegido

### Requirement: Reserva con sesión del personal
Con una sesión del personal, `#/reserva` MUST mostrar "La reserva online es para pacientes. Para dar un turno, usá la Agenda." sin pedir prestaciones ni horarios.

#### Scenario: Recepción abre la reserva
- **GIVEN** una sesión de `recepcion`
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** se muestra "La reserva online es para pacientes. Para dar un turno, usá la Agenda."
- **AND** no se envía ningún pedido a `/api/v1/public/prestaciones`

### Requirement: Reserva cómoda en el celular
En una pantalla de 390 × 844 px, la página "Reservar turno" SHALL mostrar los controles uno debajo del otro y los horarios como botones de al menos 44 px de alto, y MUST NOT tener desplazamiento horizontal.

#### Scenario: Reserva en el celular
- **GIVEN** el paso 2 de `#/reserva` con horarios a la vista en una pantalla de 390 × 844 px
- **WHEN** se mide la página
- **THEN** el ancho del documento no supera el de la pantalla
- **AND** cada botón de horario mide al menos 44 px de alto
