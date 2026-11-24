# frontend-agenda-personal Specification

## Purpose

Definir la agenda del personal en la aplicación web: cómo se ven los turnos del día y de la semana, cómo se filtran por profesional o sillón, y cómo se crean, cancelan y reprograman turnos con mensajes claros para cada rechazo de la API. En los escenarios, con datos ficticios, hay una sesión de `recepcion`, la Dra. Ana Ejemplo (id 1), el Dr. Bruno Prueba (id 2), el Sillón 1 (id 1), el Sillón 2 (id 2), la prestación "Consulta" (id 1, 30 minutos) y "Paciente Uno" (id 1, DNI `99000001`). La hora es la de `America/Argentina/Buenos_Aires` (`-03:00`) y hoy es el martes 2026-12-01.

## Requirements

### Requirement: Agenda del día
Al abrirse, la agenda SHALL mostrar el día de hoy pidiendo `GET /api/v1/turnos` con `desde` a las 00:00 de ese día y `hasta` a las 00:00 del día siguiente, en ISO-8601 con zona horaria. Cada turno SHALL mostrar la hora de inicio y de fin, el nombre y apellido del paciente, el profesional, el sillón y la prestación, ordenados por inicio.

#### Scenario: Ver los turnos de hoy
- **GIVEN** que la API tiene un turno de "Paciente Uno" de 10:00 a 10:30 con la Dra. Ana Ejemplo en el Sillón 1 para "Consulta"
- **WHEN** se abre la agenda
- **THEN** se pide `GET /api/v1/turnos?desde=2026-12-01T00:00:00-03:00&hasta=2026-12-02T00:00:00-03:00`
- **AND** se ve un turno "10:00 – 10:30" de "Paciente Uno" con "Ana Ejemplo", "Sillón 1" y "Consulta"

#### Scenario: Día sin turnos
- **GIVEN** que la API responde `200` con una lista vacía
- **WHEN** se abre la agenda
- **THEN** se ve el mensaje "No hay turnos para este día."

#### Scenario: Hora mostrada en la zona del consultorio
- **GIVEN** que la API devuelve un turno con `inicio` `2026-12-01T13:00:00Z`
- **WHEN** se abre la agenda
- **THEN** el turno se muestra a las "10:00"

### Requirement: Agenda de la semana
La agenda SHALL permitir cambiar entre la vista del día y la de la semana. La semana va de lunes 00:00 al lunes siguiente 00:00 y muestra los turnos agrupados por día. Los botones "Anterior", "Hoy" y "Siguiente" SHALL mover el período mostrado un día o una semana según la vista. "Anterior" y "Siguiente" SHALL mostrarse como flechas hacia la izquierda y hacia la derecha, sin texto visible, y MUST conservar "Anterior" y "Siguiente" como nombre accesible y como texto de ayuda al pasar el mouse, para todos los roles del personal.

#### Scenario: Cambiar a la vista semanal
- **GIVEN** la agenda del martes 2026-12-01 abierta
- **WHEN** se elige la vista "Semana"
- **THEN** se pide `GET /api/v1/turnos?desde=2026-11-30T00:00:00-03:00&hasta=2026-12-07T00:00:00-03:00`
- **AND** los turnos se ven agrupados bajo cada día de lunes a domingo

#### Scenario: Ir al día siguiente
- **GIVEN** la agenda del día 2026-12-01 abierta
- **WHEN** se usa "Siguiente"
- **THEN** se pide la agenda con `desde=2026-12-02T00:00:00-03:00` y `hasta=2026-12-03T00:00:00-03:00`

#### Scenario: Volver a hoy
- **GIVEN** la agenda de la semana del 2026-12-14 abierta
- **WHEN** se usa "Hoy"
- **THEN** se muestra la semana que contiene el 2026-12-01

#### Scenario: Anterior y Siguiente como flechas
- **GIVEN** la agenda del día abierta con cualquier rol del personal
- **WHEN** se mira la barra del período
- **THEN** "Anterior" se ve como una flecha hacia la izquierda y "Siguiente" como una flecha hacia la derecha, sin texto visible
- **AND** los dos botones tienen el nombre accesible "Anterior" y "Siguiente"

### Requirement: Filtros por profesional y por sillón
La agenda SHALL ofrecer los filtros "Profesional" y "Sillón" con las opciones de `GET /api/v1/profesionales` y `GET /api/v1/sillones`, más la opción "Todos". Al elegir una opción SHALL volver a pedir la agenda con `profesionalId` o `sillonId`. Si esas listas no se pueden obtener, los filtros SHALL quedar solo con "Todos" y un aviso. Con sesión `ODONTOLOGO` (RN-AG-18), la agenda MUST NOT ofrecer el filtro "Profesional": SHALL mostrar el título "Mi agenda" con el nombre de su profesional debajo, pedir siempre `GET /api/v1/turnos` con el `profesionalId` de la sesión y conservar el filtro "Sillón".

#### Scenario: Filtrar por profesional
- **GIVEN** la agenda del día abierta
- **WHEN** se elige "Ana Ejemplo" en el filtro "Profesional"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1`

#### Scenario: Filtrar por sillón y profesional a la vez
- **GIVEN** la agenda filtrada por "Ana Ejemplo"
- **WHEN** se elige "Sillón 2" en el filtro "Sillón"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1` y `sillonId=2`

#### Scenario: Catálogos no disponibles
- **GIVEN** que `GET /api/v1/profesionales` responde `404`
- **WHEN** se abre la agenda
- **THEN** el filtro "Profesional" solo tiene la opción "Todos"
- **AND** se ve el aviso "No pudimos cargar la lista de profesionales."
- **AND** los turnos del día se muestran igual

#### Scenario: El odontólogo ve solo su agenda
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO` y `profesionalId=1` ("Ana Ejemplo")
- **WHEN** se abre la agenda del día
- **THEN** no se ve el filtro "Profesional"
- **AND** se ve el título "Mi agenda" con el nombre "Ana Ejemplo"
- **AND** se pide `GET /api/v1/turnos` del día con `profesionalId=1`

#### Scenario: El odontólogo filtra por sillón
- **GIVEN** la agenda del día abierta con la sesión de `ana.ejemplo` (`profesionalId=1`)
- **WHEN** se elige "Sillón 2" en el filtro "Sillón"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1` y `sillonId=2`

#### Scenario: Recepción conserva el filtro por profesional
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la agenda del día
- **THEN** se ven los filtros "Profesional" y "Sillón"

### Requirement: Indicadores de confirmado y sobreturno
Un turno con `estado` `CONFIRMADO` SHALL mostrar la etiqueta "Confirmado" y un turno con `esSobreturno` `true` SHALL mostrar la etiqueta "Sobreturno". La diferencia MUST NOT depender solo del color. Un turno `RESERVADO` con `esSobreturno` `false` no lleva etiquetas.

#### Scenario: Turno confirmado
- **GIVEN** que la API devuelve un turno con `estado` `CONFIRMADO`
- **WHEN** se abre la agenda
- **THEN** el turno muestra la etiqueta de texto "Confirmado"

#### Scenario: Sobreturno
- **GIVEN** que la API devuelve un turno con `esSobreturno` `true`
- **WHEN** se abre la agenda
- **THEN** el turno muestra la etiqueta de texto "Sobreturno"

#### Scenario: Turno común sin etiquetas
- **GIVEN** que la API devuelve un turno `RESERVADO` con `esSobreturno` `false`
- **WHEN** se abre la agenda
- **THEN** el turno no muestra las etiquetas "Sobreturno" ni "Confirmado"

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

### Requirement: Mensajes por código de rechazo
Ante un `409`, el formulario MUST quedar abierto con lo cargado y mostrar un mensaje en español propio de cada código conocido (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `INICIO_EN_EL_PASADO`, `RECURSO_INACTIVO`, `TURNO_NO_ACTIVO`, `FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`). Con otro código, SHALL mostrar el `detail` de la API.

#### Scenario: Solapamiento con el profesional
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `SOLAPAMIENTO_PROFESIONAL`
- **THEN** se ve "El profesional ya tiene un turno en ese horario."
- **AND** el formulario sigue abierto con los datos cargados

#### Scenario: Margen insuficiente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `MARGEN_INSUFICIENTE`
- **THEN** se ve "No queda el margen mínimo entre turnos."

#### Scenario: Sillón ocupado
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El sillón ya está ocupado en ese horario."

#### Scenario: Inicio en el pasado
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `INICIO_EN_EL_PASADO`
- **THEN** se ve "El horario elegido ya pasó."

#### Scenario: Recurso inactivo
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `RECURSO_INACTIVO`
- **THEN** se ve "El profesional, el sillón o la prestación está inactivo."

#### Scenario: Fuera del horario del profesional
- **GIVEN** el formulario de nuevo turno con la Dra. Ana Ejemplo el 2026-12-01 a las 15:00
- **WHEN** se envía y la API responde `409` con el código `FUERA_DE_HORARIO`
- **THEN** se ve "El profesional no atiende en ese horario."
- **AND** el formulario sigue abierto con los datos cargados

#### Scenario: Profesional ausente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `PROFESIONAL_AUSENTE`
- **THEN** se ve "El profesional tiene registrada una ausencia en ese horario."

#### Scenario: Agenda bloqueada
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `AGENDA_BLOQUEADA`
- **THEN** se ve "La agenda del profesional está bloqueada en ese horario."

#### Scenario: Código sin mensaje propio
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `OTRO_CODIGO` y el `detail` "Regla ficticia"
- **THEN** se ve "Regla ficticia"

### Requirement: Errores de campo ante un 422
Ante un `422` con `DATOS_INVALIDOS`, el formulario MUST mostrar cada mensaje de la lista `errores` junto al campo que nombra, sin cerrarse. Un error de un campo que el formulario no muestra SHALL aparecer como mensaje general.

#### Scenario: Profesional inexistente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `422` con el código `DATOS_INVALIDOS` y un error del campo `profesionalId` con el mensaje "no existe"
- **THEN** el campo "Profesional" muestra "no existe"
- **AND** el formulario sigue abierto

### Requirement: Cancelar un turno
Al elegir un turno de la agenda SHALL ofrecerse "Cancelar turno", que pide el DNI del paciente y el número de reserva y envía `POST /api/v1/turnos/{id}/cancelar` con `dni` y `numeroReserva`. Con `200`, MUST mostrar "Turno cancelado." y recargar la agenda; ante un rechazo, mostrar su mensaje sin cerrar el formulario.

#### Scenario: Recepción cancela un turno
- **GIVEN** la agenda con el turno 7 de "Paciente Uno" a las 10:00
- **WHEN** se elige ese turno, "Cancelar turno", se ingresa el DNI `99000001` y el número de reserva `R-0001` y se confirma
- **THEN** se pide `POST /api/v1/turnos/7/cancelar` con `dni` `99000001` y `numeroReserva` `R-0001`
- **AND** al responder `200` se ve "Turno cancelado." y se vuelve a pedir la agenda

#### Scenario: Turno ya cancelado
- **GIVEN** el formulario de cancelación del turno 7 completo
- **WHEN** se confirma y la API responde `409` con el código `TURNO_NO_ACTIVO`
- **THEN** se ve "El turno ya no está activo."

#### Scenario: Turno que ya no existe
- **GIVEN** el formulario de cancelación del turno 7 completo
- **WHEN** se confirma y la API responde `404` con el código `TURNO_INEXISTENTE`
- **THEN** se ve "El turno ya no existe." y se vuelve a pedir la agenda

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

### Requirement: Agenda usable en el celular
En pantallas de menos de 768 px la agenda SHALL mostrar los turnos en una sola columna, con filtros y formularios a todo el ancho y controles de al menos 44 × 44 px. En ningún ancho desde 320 px MUST NOT haber desplazamiento horizontal.

#### Scenario: Agenda en el celular
- **WHEN** se abre la agenda de la semana en una pantalla de 390 × 844 px
- **THEN** los turnos se ven en una sola columna
- **AND** la página no tiene desplazamiento horizontal

#### Scenario: Agenda en la computadora
- **WHEN** se abre la agenda de la semana en una pantalla de 1440 × 900 px
- **THEN** los filtros y los turnos se ven sin desplazamiento horizontal

### Requirement: Etiquetas de asistencia
La agenda SHALL mostrar en cada turno su asistencia con una etiqueta de texto, para todos los roles del personal: "Atendido" para `ATENDIDO`, "Ausente" para `AUSENTE`, "Llegó HH:mm" para un turno activo con `llegadaEn`, y "Sin registrar" para un turno activo sin llegada cuyo fin ya pasó. La diferencia MUST NOT depender solo del color. Los turnos `ATENDIDO` y `AUSENTE` MUST NOT ofrecer "Cancelar turno" ni "Reprogramar".

#### Scenario: Turnos con asistencia registrada
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** la API devuelve un turno `ATENDIDO` de 09:00 a 09:30, uno `AUSENTE` de 09:30 a 10:00 y uno `RESERVADO` de 10:40 a 11:10 con `llegadaEn` a las 10:35
- **WHEN** se abre la agenda del día
- **THEN** los turnos muestran "Atendido", "Ausente" y "Llegó 10:35"
- **AND** los turnos "Atendido" y "Ausente" no ofrecen "Cancelar turno" ni "Reprogramar"

#### Scenario: Turno sin registrar en la agenda
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** la API devuelve un turno `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se abre la agenda del día
- **THEN** el turno muestra "Sin registrar"

### Requirement: Acciones de asistencia de la recepción
Con sesión `RECEPCIONISTA`, la agenda SHALL ofrecer "Llegó" en un turno activo sin llegada desde el día del turno, "Ausente" en un turno activo sin llegada (habilitado 15 minutos después del inicio; antes, deshabilitado con "Desde las HH:mm") y "Deshacer ausencia" en un turno `AUSENTE` que no terminó. Cada botón envía su `POST` y con `200` recarga la agenda. Con sesión `ADMIN` u `ODONTOLOGO` MUST NOT ofrecerlos.

#### Scenario: Registrar la llegada
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:05
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se elige "Llegó" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/llegada`
- **AND** al responder `200` se vuelve a pedir la agenda

#### Scenario: Ausente antes de la tolerancia
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:10
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se abre la agenda del día
- **THEN** el botón "Ausente" del turno 7 está deshabilitado y dice "Desde las 10:15"

#### Scenario: Marcar ausente
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:20
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se elige "Ausente" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/ausencia`

#### Scenario: Deshacer la ausencia
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:22
- **AND** el turno 7 `AUSENTE` de 10:00 a 10:30
- **WHEN** se elige "Deshacer ausencia" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/deshacer-ausencia`

#### Scenario: El odontólogo no ve las acciones de asistencia
- **GIVEN** la sesión de `ana.ejemplo` y el momento actual el 2026-12-01 a las 10:20
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 con la Dra. Ana Ejemplo
- **WHEN** se abre la agenda del día
- **THEN** el turno 7 no ofrece "Llegó", "Ausente" ni "Deshacer ausencia"

### Requirement: Mensajes de los rechazos de asistencia
Ante un rechazo de "Llegó", "Ausente" o "Deshacer ausencia", la agenda MUST mostrar el mensaje en español de su código y volver a pedir la agenda. Al deshacer, cualquier rechazo por superposición o margen muestra el mismo mensaje.

#### Scenario: Deshacer con el horario ocupado
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` de 10:00 a 10:30
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El horario ya se ocupó. Dale otro turno al paciente."
- **AND** se vuelve a pedir la agenda

#### Scenario: Llegada ya registrada en otra pantalla
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` sin `llegadaEn` en la agenda
- **WHEN** se elige "Llegó" y la API responde `409` con el código `LLEGADA_REGISTRADA`
- **THEN** se ve "El paciente ya tiene la llegada registrada."

#### Scenario: Rechazo AUSENCIA_ANTES_DE_TOLERANCIA
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` en la agenda
- **WHEN** se elige "Ausente" y la API responde `409` con el código `AUSENCIA_ANTES_DE_TOLERANCIA`
- **THEN** se ve "Todavía no pasaron 15 minutos desde el inicio del turno."

#### Scenario: Rechazo LLEGADA_ANTICIPADA
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` en la agenda
- **WHEN** se elige "Llegó" y la API responde `409` con el código `LLEGADA_ANTICIPADA`
- **THEN** se ve "La llegada se registra el día del turno."

#### Scenario: Rechazo TURNO_NO_AUSENTE
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` en la agenda
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `TURNO_NO_AUSENTE`
- **THEN** se ve "El turno no está marcado como ausente."

#### Scenario: Rechazo TURNO_TERMINADO
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` en la agenda
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `TURNO_TERMINADO`
- **THEN** se ve "El turno ya terminó."

### Requirement: Ver la ficha desde un turno
Con sesión `ADMIN` u `ODONTOLOGO`, cada turno de la agenda del día y de la semana SHALL ofrecer "Ver ficha", que abre la ficha clínica de su paciente en "Pacientes". Con sesión `RECEPCIONISTA` MUST NOT ofrecerlo.

#### Scenario: El odontólogo abre la ficha
- **GIVEN** la sesión de `ana.ejemplo` y el turno 7 de "Paciente Uno" en su agenda del día
- **WHEN** elige "Ver ficha" en el turno 7
- **THEN** se muestra la página "Pacientes" con la ficha de "Paciente Uno"

#### Scenario: El administrador ve el acceso
- **GIVEN** la sesión de `admin`
- **WHEN** se abre la agenda de la semana con el turno 7
- **THEN** el turno 7 ofrece "Ver ficha"

#### Scenario: La recepción no ve el acceso
- **GIVEN** la sesión de `recepcion`
- **WHEN** se abre la agenda del día con el turno 7
- **THEN** el turno 7 no ofrece "Ver ficha"

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
Cada código de rechazo que la reprogramación puede devolver MUST tener un texto en español propio, y un código sin texto MUST mostrar el detalle del servidor o un mensaje genérico, nunca vacío ni el código interno.

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
- **THEN** se ve el detalle que informó el servidor o, si no trae detalle, el mensaje genérico de error
- **AND** nunca se ve el código ni un mensaje vacío

### Requirement: Un horario ofrecido se puede reprogramar
Un horario que la disponibilidad ofrece al profesional elegido MUST poder reprogramarse sin que el formulario lo estorbe: el pedido lleva exactamente lo que el personal eligió, y los rechazos del backend llegan al personal con su motivo.

#### Scenario: Reprogramar a un horario ofrecido
- **GIVEN** el formulario del turno 7 con el botón de las 11:00 ofrecido por la API
- **WHEN** se elige ese botón, se completa la identificación y se confirma
- **THEN** el pedido lleva el `inicio` que dio la API, sin ajustarlo ni recalcularlo
- **AND** al responder `200` se cierra el formulario y se recarga la agenda
