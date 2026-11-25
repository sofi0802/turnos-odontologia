## Purpose
Definir las pantallas de la lista de espera:
- para la recepción y el administrador, ver la lista en orden, anotar pacientes y darlos de baja;
- para el paciente, anotarse desde la reserva, ver sus inscripciones en Mis turnos y darse de baja;
- la pantalla pública a la que lleva el link de la oferta;
- el plazo para aceptar en Configuración.

En los escenarios, con datos ficticios y salvo que digan otra cosa: el momento actual es el martes 2026-12-01 a las 08:00 en `America/Argentina/Buenos_Aires`; "Limpieza" (id 1) dura 30 minutos y la atienden la Dra. Ana Ejemplo (id 1) y el Dr. Bruno Prueba (id 2).

## ADDED Requirements

### Requirement: Página "Lista de espera" para administración y recepción
La aplicación SHALL ofrecer la página "Lista de espera" en `#/lista-espera`, con su opción en la barra lateral inmediatamente después de "Agenda" y antes de "Pacientes", "Coberturas" (C-49) y "Reportes", solo con sesión `ADMIN` o `RECEPCIONISTA`. La página pide `GET /api/v1/lista-espera` y muestra cada inscripción en orden de llegada con:
- su número de orden;
- el nombre y el DNI del paciente;
- la prestación;
- el profesional o "Sin preferencia";
- el rango de fechas;
- la fecha de inscripción;
- si tiene una oferta pendiente: "Oferta enviada: {día} a las {hora}, vence a las {hora}".

Sin inscripciones, SHALL mostrar "No hay pacientes en la lista de espera." con la acción "Anotar paciente". Con sesión `ODONTOLOGO` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para administración y recepción." sin pedir la lista. Sin sesión, lleva a Mi cuenta.

#### Scenario: Menú de la recepción con Lista de espera
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene el enlace "Lista de espera" inmediatamente después de "Agenda"

#### Scenario: Orden del menú del administrador con Coberturas
- **GIVEN** la sesión de `admin` con rol `ADMIN` y la página "Coberturas" de C-49 disponible
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene los enlaces "Agenda", "Lista de espera", "Pacientes", "Coberturas", "Reportes", "Caja" y "Configuración", en ese orden

#### Scenario: La lista en orden con una oferta pendiente
- **GIVEN** la API responde "Paciente Dos" (inscripto el 2026-11-28) con una oferta del jueves 3 de diciembre a las 10:00 que vence a las 10:00 del 1 de diciembre, y "Paciente Uno" (inscripto el 2026-11-30) sin oferta
- **WHEN** se abre `#/lista-espera`
- **THEN** la primera fila es la 1, de "Paciente Dos", con "Oferta enviada: jueves 3 de diciembre a las 10:00, vence a las 10:00"
- **AND** la segunda fila es la 2, de "Paciente Uno", sin oferta

#### Scenario: Lista vacía
- **WHEN** se abre `#/lista-espera` y la API responde una lista vacía
- **THEN** se muestra "No hay pacientes en la lista de espera." con la acción "Anotar paciente"

#### Scenario: El odontólogo abre la dirección
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/lista-espera`
- **THEN** se muestra "Esta sección es solo para administración y recepción."
- **AND** la barra lateral no tiene el enlace "Lista de espera" y no se pide `GET /api/v1/lista-espera`

### Requirement: Anotar a un paciente desde la recepción
"Anotar paciente" SHALL abrir un diálogo con:
- el buscador de pacientes de C-36;
- la prestación;
- el profesional, con la opción "Sin preferencia" y solo los habilitados para la prestación;
- "Desde" y "Hasta", que por defecto van de hoy a 14 días después.

El diálogo valida antes de enviar: un paciente elegido, "Desde" hoy o después, "Hasta" igual o posterior a "Desde" y 60 días como máximo. Al guardar, envía `POST /api/v1/lista-espera`, y ante `201` cierra el diálogo, agrega la fila al final de la lista y anuncia "Anotaste a {paciente} en la lista de espera.". Los rechazos `YA_EN_LISTA_DE_ESPERA`, `SIN_CONSENTIMIENTO` y `PROFESIONAL_NO_HABILITADO` MUST mostrarse en el diálogo con su mensaje, sin cerrarlo.

#### Scenario: Anotar sin preferencia
- **GIVEN** `#/lista-espera` abierta por `recepcion`
- **WHEN** elige "Anotar paciente", busca y elige "Paciente Uno", elige "Limpieza" y "Sin preferencia" y guarda
- **THEN** se envía `POST /api/v1/lista-espera` con la prestación 1, sin profesional, desde 2026-12-01 hasta 2026-12-15
- **AND** se anuncia "Anotaste a Paciente Uno en la lista de espera."

#### Scenario: Rango de más de 60 días
- **WHEN** en el diálogo pone "Hasta" el 2027-02-15 y guarda
- **THEN** se muestra "El rango puede ser de hasta 60 días." junto a "Hasta"
- **AND** no se envía ningún pedido

#### Scenario: Paciente sin consentimiento
- **WHEN** guarda y la API responde `409` con el código `SIN_CONSENTIMIENTO`
- **THEN** el diálogo sigue abierto y muestra "El paciente no aceptó recibir mensajes: no le podemos avisar las ofertas."

### Requirement: Dar de baja desde la recepción
Cada fila de `#/lista-espera` SHALL tener "Dar de baja", que pide confirmación ("¿Dar de baja a {paciente} de la lista de espera?"). Al confirmar, envía `POST /api/v1/lista-espera/{id}/baja`, quita la fila y anuncia "Diste de baja a {paciente}.". Ante `INSCRIPCION_NO_ACTIVA`, SHALL quitar la fila y mostrar "Esta inscripción ya no estaba activa.".

#### Scenario: Baja confirmada
- **WHEN** `recepcion` elige "Dar de baja" en la fila de "Paciente Uno" y confirma
- **THEN** se envía `POST /api/v1/lista-espera/7/baja`
- **AND** la fila ya no está y se anuncia "Diste de baja a Paciente Uno."

#### Scenario: Baja cancelada
- **WHEN** elige "Dar de baja" y después "Volver" en la confirmación
- **THEN** no se envía ningún pedido y la fila sigue

### Requirement: Anotarse desde la reserva
En `#/reserva`, cuando el día elegido no tiene horarios libres, la página SHALL ofrecer, junto a "No hay horarios libres para ese día…", la acción "Anotarme en la lista de espera". Con sesión `PACIENTE`, la acción abre un diálogo con la prestación y el profesional (o "Sin preferencia") ya elegidos y no editables, y "Desde" y "Hasta" por defecto del día elegido a 14 días después. Valida igual que el diálogo de la recepción y envía `POST /api/v1/paciente/lista-espera`. Ante `201`, el diálogo SHALL mostrar "Listo, te anotamos. Si se libera un horario, te avisamos por mensaje." con un enlace a "Mis turnos". Sin sesión, la acción lleva a Mi cuenta. Ante `YA_EN_LISTA_DE_ESPERA`, SHALL mostrar "Ya estás en la lista de espera para esta prestación." con el enlace a "Mis turnos".

#### Scenario: El paciente se anota desde un día sin horarios
- **GIVEN** la sesión de "Paciente Uno" en `#/reserva`, con "Limpieza", la Dra. Ana Ejemplo y el jueves 3 de diciembre sin horarios
- **WHEN** elige "Anotarme en la lista de espera" y confirma con las fechas por defecto
- **THEN** se envía `POST /api/v1/paciente/lista-espera` con la prestación 1, el profesional 1, desde 2026-12-03 hasta 2026-12-17
- **AND** se muestra "Listo, te anotamos. Si se libera un horario, te avisamos por mensaje."

#### Scenario: Un día con horarios no ofrece la lista
- **WHEN** el día elegido tiene horarios libres
- **THEN** no está la acción "Anotarme en la lista de espera"

#### Scenario: Sin sesión
- **GIVEN** un navegador sin sesión en `#/reserva` con un día sin horarios
- **WHEN** elige "Anotarme en la lista de espera"
- **THEN** se muestra "Mi cuenta"

### Requirement: Mi lista de espera en Mis turnos
`#/mis-turnos` SHALL pedir también `GET /api/v1/paciente/lista-espera` y, si hay inscripciones, mostrar después de "Próximos turnos" la sección "Lista de espera". Cada inscripción muestra la prestación, el profesional o "Sin preferencia", y el rango ("del {día} al {día}"). Si tiene una oferta pendiente, muestra además "Te enviamos un horario: {día} a las {hora}. Revisá tu WhatsApp o tu email.". Cada inscripción tiene "Salir de la lista", con confirmación, que envía `POST /api/v1/paciente/lista-espera/{id}/baja` y la quita. Sin inscripciones, la sección no se muestra. Si falla el pedido de la lista de espera, los turnos MUST mostrarse igual, con el aviso "No pudimos cargar tu lista de espera.".

#### Scenario: Una inscripción con oferta pendiente
- **GIVEN** la API responde una inscripción de "Limpieza" sin preferencia del 1 al 15 de diciembre, con una oferta del jueves 3 de diciembre a las 10:00
- **WHEN** se abre `#/mis-turnos`
- **THEN** la sección "Lista de espera" muestra "Limpieza", "Sin preferencia" y "del martes 1 de diciembre al martes 15 de diciembre"
- **AND** muestra "Te enviamos un horario: jueves 3 de diciembre a las 10:00. Revisá tu WhatsApp o tu email."

#### Scenario: Salir de la lista
- **WHEN** elige "Salir de la lista" y confirma
- **THEN** se envía `POST /api/v1/paciente/lista-espera/5/baja`
- **AND** la inscripción ya no se muestra

#### Scenario: Falla la lista de espera
- **WHEN** `GET /api/v1/paciente/lista-espera` responde `500`
- **THEN** los turnos se muestran igual
- **AND** se muestra "No pudimos cargar tu lista de espera."

### Requirement: Pantalla de la oferta
Al abrir la aplicación en `/oferta/{token}`, la aplicación SHALL guardar el token solo en memoria, pasar a `#/oferta` y pedir, sin sesión, `GET /api/v1/public/ofertas/{token}`. El token MUST NOT aparecer en la dirección ni en el texto de la página. Con una oferta `PENDIENTE`, la página "Turno disponible" muestra "Se liberó un turno el {fecha larga} a las {hora} con {profesional}." y "Podés aceptarlo hasta el {día} a las {hora}.", con los botones "Aceptar el turno" y "No me interesa". Ningún botón envía nada hasta que se elige.

#### Scenario: Abrir el link de la oferta
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `/oferta/xyz789` y la API responde una oferta `PENDIENTE` del 2026-12-03 a las 10:00 con "Ana Ejemplo", que vence el 2026-12-01 a las 10:00
- **THEN** se muestra "Se liberó un turno el jueves 3 de diciembre a las 10:00 con Ana Ejemplo."
- **AND** se muestra "Podés aceptarlo hasta el martes 1 de diciembre a las 10:00."
- **AND** la dirección es `#/oferta` y el texto de la página no contiene `xyz789`
- **AND** no se envió ningún `POST`

#### Scenario: Link inválido
- **WHEN** la API responde `404` con el código `OFERTA_INEXISTENTE`
- **THEN** se muestra "El link no es válido. Revisá que esté completo." sin botones

#### Scenario: Oferta que ya no está pendiente
- **WHEN** la API responde una oferta `VENCIDA`
- **THEN** se muestra "Esta oferta venció. Seguís en la lista de espera." sin botones

### Requirement: Responder la oferta
"Aceptar el turno" SHALL pedir el token CSRF con `GET /api/v1/public/sesion` si todavía no existe y enviar `POST /api/v1/public/ofertas/{token}/aceptar` una sola vez. Ante `200` MUST mostrar "Listo, tu turno quedó reservado para el {fecha larga} a las {hora} con {profesional}. Tu número de reserva es {número}." y quitar los botones. "No me interesa" envía `POST /api/v1/public/ofertas/{token}/rechazar` y muestra "Listo, se lo ofrecemos a otra persona. Seguís en la lista de espera.". Los rechazos MUST mostrar su mensaje y quitar los botones:
- `OFERTA_NO_DISPONIBLE`: "Ese horario ya se ocupó. Seguís en la lista de espera.";
- `OFERTA_VENCIDA`: "Esta oferta venció. Seguís en la lista de espera.";
- `OFERTA_YA_RESPONDIDA`: "Ya respondiste esta oferta.".

#### Scenario: Aceptar
- **GIVEN** la pantalla de la oferta abierta desde `/oferta/xyz789`
- **WHEN** elige "Aceptar el turno" y la API responde `200` con `fecha` `2026-12-03`, `hora` `10:00`, `profesional` "Ana Ejemplo" y `numeroReserva` `R-0042`
- **THEN** se envió una sola vez `POST /api/v1/public/ofertas/xyz789/aceptar`
- **AND** se muestra "Listo, tu turno quedó reservado para el jueves 3 de diciembre a las 10:00 con Ana Ejemplo. Tu número de reserva es R-0042."
- **AND** ya no están los botones

#### Scenario: El horario ya se ocupó
- **WHEN** elige "Aceptar el turno" y la API responde `409` con el código `OFERTA_NO_DISPONIBLE`
- **THEN** se muestra "Ese horario ya se ocupó. Seguís en la lista de espera."

#### Scenario: No me interesa
- **WHEN** elige "No me interesa" y la API responde `200`
- **THEN** se envió `POST /api/v1/public/ofertas/xyz789/rechazar`
- **AND** se muestra "Listo, se lo ofrecemos a otra persona. Seguís en la lista de espera."

### Requirement: Plazo para aceptar en Configuración
La pestaña "Margen" de `#/configuracion` SHALL mostrar también "Plazo para aceptar una oferta de la lista de espera", en horas, con lo que responde `GET /api/v1/configuracion/lista-espera`. Con sesión `ADMIN`, el campo es editable y "Guardar plazo" envía `PUT /api/v1/configuracion/lista-espera`, después de validar un entero entre 1 y 24 ("Ingresá un número entero de 1 a 24."). Con sesión `RECEPCIONISTA` se muestra en solo lectura. Con `ODONTOLOGO` no se muestra.

#### Scenario: El administrador cambia el plazo
- **GIVEN** la sesión de `admin` en la pestaña "Margen" con el plazo 2
- **WHEN** pone 4 y elige "Guardar plazo"
- **THEN** se envía `PUT /api/v1/configuracion/lista-espera` con el plazo 4
- **AND** se anuncia "Guardaste el plazo: 4 horas."

#### Scenario: Plazo fuera de rango
- **WHEN** el administrador pone 30 y elige "Guardar plazo"
- **THEN** se muestra "Ingresá un número entero de 1 a 24." y no se envía ningún pedido

#### Scenario: La recepción ve el plazo sin editarlo
- **GIVEN** la sesión de `recepcion` en la pestaña "Margen"
- **THEN** se muestra el plazo 2 sin el botón "Guardar plazo"
