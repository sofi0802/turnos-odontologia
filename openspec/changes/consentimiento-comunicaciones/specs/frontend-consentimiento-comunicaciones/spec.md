# Spec Delta

## Purpose

Definir cómo el paciente y la recepción ven, otorgan y revocan el consentimiento de comunicaciones en la aplicación web, y a dónde llevan los avisos de falta de consentimiento. En los escenarios, con datos ficticios, "Paciente Uno" tiene cuenta (`paciente.uno@example.com`), "Paciente Dos" no tiene cuenta y `recepcion` es `RECEPCIONISTA`.

## ADDED Requirements

### Requirement: Sección de mensajes en la cuenta del paciente
La página "Mi cuenta" del paciente SHALL tener la sección "Mensajes del consultorio", que explica que los mensajes solo traen fecha, hora, consultorio y links para confirmar, cancelar o reprogramar, sin datos de salud. MUST mostrar el estado vigente ("Aceptás recibir mensajes" o "No recibís mensajes") y, si hubo algún cambio, su fecha.

#### Scenario: Paciente sin consentimiento
- **GIVEN** una sesión de `paciente.uno@example.com` sin consentimiento
- **WHEN** abre `#/mi-cuenta`
- **THEN** la sección "Mensajes del consultorio" dice "No recibís mensajes" y ofrece "Aceptar mensajes"

#### Scenario: Paciente con consentimiento
- **GIVEN** una sesión de `paciente.uno@example.com` con el consentimiento otorgado el 2026-12-01
- **WHEN** abre `#/mi-cuenta`
- **THEN** la sección dice "Aceptás recibir mensajes" y "Desde el 01/12/2026"
- **AND** ofrece "Dejar de recibir mensajes"

### Requirement: El paciente acepta o deja de recibir mensajes
"Aceptar mensajes" SHALL enviar `PUT /api/v1/paciente/consentimiento-comunicaciones` con `otorgado` en `true` y, ante `200`, actualizar la sección y anunciarlo. "Dejar de recibir mensajes" MUST pedir confirmación antes de enviar `otorgado` en `false`. Mientras el pedido está en curso, el botón MUST quedar deshabilitado; si falla, MUST mostrarse el error sin cambiar el estado.

#### Scenario: Aceptar mensajes
- **GIVEN** `#/mi-cuenta` abierta por "Paciente Uno" sin consentimiento
- **WHEN** elige "Aceptar mensajes"
- **THEN** se envía `PUT /api/v1/paciente/consentimiento-comunicaciones` con `otorgado` en `true`
- **AND** la sección pasa a "Aceptás recibir mensajes" y se anuncia "Listo, vas a recibir mensajes del consultorio."

#### Scenario: Dejar de recibir mensajes
- **GIVEN** `#/mi-cuenta` abierta por "Paciente Uno" con consentimiento
- **WHEN** elige "Dejar de recibir mensajes" y confirma
- **THEN** se envía el `PUT` con `otorgado` en `false`
- **AND** la sección pasa a "No recibís mensajes" y avisa que no le vamos a recordar sus turnos ni ofrecer horarios de la lista de espera

#### Scenario: Cancelar la confirmación
- **GIVEN** `#/mi-cuenta` abierta por "Paciente Uno" con consentimiento
- **WHEN** elige "Dejar de recibir mensajes" y cancela la confirmación
- **THEN** no se envía ningún pedido y el estado no cambia

### Requirement: La recepción ve el consentimiento del paciente elegido
Al elegir un paciente con el buscador en "Anotar paciente" de la lista de espera y en "Nuevo turno" de la agenda, la aplicación SHALL pedir `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones` y mostrar debajo del paciente "Acepta mensajes" o "No acepta mensajes", con la acción "Registrar consentimiento" o "Revocar". Solo `ADMIN` y `RECEPCIONISTA` MUST ver esta línea.

#### Scenario: Paciente elegido sin consentimiento
- **GIVEN** `#/lista-espera` abierta por `recepcion` y el diálogo "Anotar paciente" abierto
- **WHEN** busca y elige "Paciente Dos"
- **THEN** se pide `GET /api/v1/pacientes/{id}/consentimiento-comunicaciones` de "Paciente Dos"
- **AND** debajo del paciente se ve "No acepta mensajes" y "Registrar consentimiento"

#### Scenario: El odontólogo no ve la línea
- **GIVEN** una sesión de `ODONTOLOGO` en un diálogo que usa el buscador
- **WHEN** elige un paciente
- **THEN** no se pide el consentimiento y no se muestra la línea de mensajes

### Requirement: La recepción registra o revoca el consentimiento
"Registrar consentimiento" y "Revocar" SHALL abrir un diálogo que pide el medio ("Por teléfono" o "En persona") y confirma que el paciente lo pidió. Al confirmar, MUST enviar `PUT /api/v1/pacientes/{id}/consentimiento-comunicaciones` con `otorgado` y `medio`, y ante `200` cerrar el diálogo y actualizar la línea, sin cerrar ni perder el formulario de abajo. Sin medio elegido MUST NOT enviar el pedido.

#### Scenario: Registrar un consentimiento por teléfono
- **GIVEN** "Paciente Dos" elegido en "Anotar paciente", con "No acepta mensajes"
- **WHEN** `recepcion` elige "Registrar consentimiento", marca "Por teléfono" y confirma
- **THEN** se envía el `PUT` con `otorgado` en `true` y `medio` `TELEFONO`
- **AND** la línea pasa a "Acepta mensajes" y el diálogo "Anotar paciente" conserva la prestación y las fechas elegidas

#### Scenario: Confirmar sin medio
- **GIVEN** el diálogo de consentimiento abierto
- **WHEN** confirma sin elegir el medio
- **THEN** no se envía ningún pedido y se muestra "Elegí cómo lo pidió el paciente." junto al medio

### Requirement: El rechazo por falta de consentimiento ofrece registrarlo
Cuando "Anotar paciente" recibe `409 SIN_CONSENTIMIENTO`, el diálogo SHALL mostrar su mensaje junto con la acción "Registrar consentimiento" para el paciente elegido. Después de registrarlo, MUST poder guardar de nuevo sin volver a cargar los datos.

#### Scenario: Registrar y volver a guardar
- **GIVEN** "Anotar paciente" con "Paciente Dos" elegido y la respuesta `409 SIN_CONSENTIMIENTO`
- **WHEN** `recepcion` elige "Registrar consentimiento", marca "Por teléfono", confirma y vuelve a guardar
- **THEN** se envía otra vez `POST /api/v1/lista-espera` con los mismos datos

### Requirement: El aviso al paciente lleva a Mi cuenta
Cuando el paciente no puede anotarse en la lista de espera por `SIN_CONSENTIMIENTO`, el aviso SHALL incluir el enlace "Aceptar mensajes en Mi cuenta" a `#/mi-cuenta`.

#### Scenario: Paciente sin consentimiento en la lista de espera
- **GIVEN** "Paciente Uno" sin consentimiento intentando anotarse en la lista de espera
- **WHEN** la API responde `409 SIN_CONSENTIMIENTO`
- **THEN** se muestra el aviso de que tiene que aceptar recibir mensajes
- **AND** el aviso tiene el enlace "Aceptar mensajes en Mi cuenta" a `#/mi-cuenta`
