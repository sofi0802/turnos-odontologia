# cobros-pacientes Specification

## Purpose

Lo que deben los pacientes y lo que pagan: un cargo por el copago de cada turno atendido, los cobros imputados a ese cargo, su anulación y la cuenta corriente (RN-CA-03 a RN-CA-05, RN-CA-07, RN-CA-08, DD-18). En los escenarios, con datos ficticios, hoy es el lunes 2026-12-07; "Consulta o control" tiene un arancel de `15000.00` y el código del nomenclador `01.01`; "Paciente Uno" está afiliado al plan "Plan 40" de la obra social "OS Ficticia", que cubre el 40 % de `01.01`; "Paciente Dos" es particular. Las cuentas son las de la spec `caja`.

## Requirements

### Requirement: Cargo por turno atendido
Cuando un turno queda `ATENDIDO`, el sistema SHALL generar un cargo para su paciente por el copago de su prestación: precio particular menos la cobertura del plan vigente en la fecha del turno, con la fórmula de C-19 (RN-PR-03) y cantidad 1. Un turno `AUSENTE` o `CANCELADO` MUST NOT generar cargo, y un turno MUST generar como máximo un cargo.

#### Scenario: Paciente con cobertura
- **GIVEN** un turno de "Consulta o control" de "Paciente Uno" el 2026-12-07 que queda `ATENDIDO`
- **WHEN** se consulta su cuenta corriente
- **THEN** tiene un cargo con `precio` `15000.00`, `porcentajeCobertura` 40, `cobertura` `6000.00` y `copago` `9000.00`

#### Scenario: Paciente particular
- **GIVEN** un turno de "Consulta o control" de "Paciente Dos" que queda `ATENDIDO`
- **WHEN** se consulta su cuenta corriente
- **THEN** tiene un cargo con `porcentajeCobertura` 0 y `copago` `15000.00`

#### Scenario: Ausentes y cancelados no generan deuda
- **GIVEN** un turno `AUSENTE` y otro `CANCELADO` de "Paciente Dos"
- **WHEN** se consulta su cuenta corriente
- **THEN** no tiene cargos

#### Scenario: Un solo cargo por turno
- **GIVEN** un turno `ATENDIDO` de "Paciente Dos" con su cargo
- **WHEN** se vuelve a consultar su cuenta corriente y corre la tarea de cargos
- **THEN** sigue teniendo un solo cargo de ese turno

### Requirement: El cargo es una foto
El cargo MUST guardar el precio, el porcentaje, la cobertura, el copago y la afiliación del momento en que se generó, y MUST NOT cambiar si después cambian el arancel, la cobertura del plan o la afiliación (RN-CA-03).

#### Scenario: Cambia el arancel después
- **GIVEN** el cargo de `9000.00` del turno atendido de "Paciente Uno"
- **WHEN** el admin cambia el arancel de "Consulta o control" a `18000.00`
- **THEN** el cargo sigue con `precio` `15000.00` y `copago` `9000.00`

### Requirement: Turno sin precio
Si la prestación de un turno `ATENDIDO` no tiene arancel, el sistema MUST NOT generar el cargo y SHALL listar el turno como "sin precio" en los pendientes de cobro. Cuando la prestación tenga arancel, SHALL generar el cargo con el precio y la cobertura de ese momento.

#### Scenario: Atendido sin arancel
- **GIVEN** un turno `ATENDIDO` de "Paciente Dos" de "Limpieza (profilaxis)", que no tiene arancel
- **WHEN** `recepcion` pide `GET /api/v1/cargos/pendientes`
- **THEN** el turno figura en `sinPrecio` y no hay cargo para él

#### Scenario: Se carga el precio después
- **GIVEN** ese turno sin precio
- **WHEN** el admin carga el arancel de "Limpieza (profilaxis)" en `25000.00` y `recepcion` pide los pendientes
- **THEN** hay un cargo de `copago` `25000.00` para ese turno y ya no figura en `sinPrecio`

### Requirement: Cobertura sin código del nomenclador
Si la prestación no tiene ningún código activo del nomenclador, el cargo MUST ser particular (0 %). Si tiene varios, SHALL usar el primero en orden de código.

#### Scenario: Prestación sin código
- **GIVEN** "Extracción simple" con arancel `30000.00` y sin códigos del nomenclador, y un turno atendido de "Paciente Uno"
- **WHEN** se consulta su cuenta corriente
- **THEN** el cargo tiene `porcentajeCobertura` 0 y `copago` `30000.00`

### Requirement: Cobrar un cargo
`POST /api/v1/cargos/{id}/cobros` con `importe` y `medio` (`EFECTIVO`, `DEBITO`, `CREDITO` o `TRANSFERENCIA`) SHALL registrar el cobro en la caja abierta y responder `201` con el cobro y el `saldo` que queda del cargo. El importe MUST ser mayor que cero y con hasta dos decimales; si no, o si el medio no es válido, `422` con el campo.

#### Scenario: Cobro total
- **GIVEN** la caja abierta y el cargo de `9000.00` de "Paciente Uno" sin cobros
- **WHEN** `recepcion` lo cobra con `importe` `9000.00` y `medio` `EFECTIVO`
- **THEN** la respuesta es `201` con `saldo` `0.00`

#### Scenario: Cobro parcial
- **GIVEN** la caja abierta y el cargo de `9000.00` de "Paciente Uno" sin cobros
- **WHEN** `recepcion` lo cobra con `importe` `4000.00` y `medio` `DEBITO`
- **THEN** la respuesta es `201` con `saldo` `5000.00`
- **AND** la cuenta corriente de "Paciente Uno" tiene saldo `5000.00`

#### Scenario: Importe inválido
- **WHEN** `recepcion` cobra un cargo con `importe` `0.00` o `10.005`
- **THEN** la respuesta es `422` con el campo `importe`

### Requirement: Rechazos del cobro
Un cobro mayor que el saldo del cargo MUST responder `409 IMPORTE_SUPERA_SALDO`, y uno sin caja abierta, `409 SIN_CAJA_ABIERTA`. Un cargo inexistente MUST responder `404`. La garantía de no superar el saldo MUST valer también con cobros simultáneos.

#### Scenario: Importe mayor que el saldo
- **GIVEN** la caja abierta y el cargo de `9000.00` con un cobro de `4000.00`
- **WHEN** `recepcion` lo cobra con `importe` `6000.00`
- **THEN** la respuesta es `409` con el código `IMPORTE_SUPERA_SALDO`

#### Scenario: Cobro sin caja abierta
- **GIVEN** que no hay ninguna caja abierta
- **WHEN** `recepcion` cobra un cargo
- **THEN** la respuesta es `409` con el código `SIN_CAJA_ABIERTA`

#### Scenario: Cobros simultáneos
- **GIVEN** la caja abierta y el cargo de `9000.00` sin cobros
- **WHEN** llegan a la vez dos cobros de `6000.00`
- **THEN** uno responde `201` y el otro `409` con el código `IMPORTE_SUPERA_SALDO`

### Requirement: Pendientes de cobro
`GET /api/v1/cargos/pendientes` SHALL listar los cargos con saldo mayor que cero, del turno más viejo al más nuevo, con el paciente, la fecha del turno, la prestación, el copago, lo pagado y el saldo, y en `sinPrecio` los turnos atendidos sin arancel.

#### Scenario: Pendientes del consultorio
- **GIVEN** el cargo de `9000.00` de "Paciente Uno" con un cobro de `4000.00` y el cargo de `15000.00` de "Paciente Dos" pagado completo
- **WHEN** `recepcion` pide los pendientes
- **THEN** la lista tiene solo el cargo de "Paciente Uno" con `pagado` `4000.00` y `saldo` `5000.00`

### Requirement: Cuenta corriente del paciente
`GET /api/v1/pacientes/{id}/cuenta-corriente` SHALL responder con los cargos del paciente, del más nuevo al más viejo, cada uno con sus cobros (incluidos los anulados, marcados) y su saldo, y el `saldoTotal`. Un paciente inexistente MUST responder `404`.

#### Scenario: Cuenta con deuda
- **GIVEN** el cargo de `9000.00` de "Paciente Uno" con un cobro de `4000.00` y otro anulado de `1000.00`
- **WHEN** `admin` pide su cuenta corriente
- **THEN** el cargo tiene los dos cobros, el anulado marcado, y `saldo` `5000.00`
- **AND** `saldoTotal` es `5000.00`

### Requirement: Anular un cobro
`POST /api/v1/cobros/{id}/anulacion` con `motivo` SHALL marcar el cobro como anulado, con quién, cuándo y el motivo, sin borrarlo; el importe vuelve a ser deuda del cargo y deja de contar en la caja. Solo el `ADMIN` SHALL anular, con la caja del cobro abierta; si está cerrada, `409 CAJA_CERRADA`; si ya está anulado, `409 COBRO_ANULADO`; sin motivo, `422`.

#### Scenario: El admin anula un cobro
- **GIVEN** la caja abierta y un cobro de `4000.00` del cargo de `9000.00`
- **WHEN** `admin` lo anula con el motivo "Cargado al paciente equivocado"
- **THEN** la respuesta es `200` y el saldo del cargo vuelve a `9000.00`

#### Scenario: Anular con la caja cerrada
- **GIVEN** un cobro de la caja del 2026-12-04, ya cerrada
- **WHEN** `admin` lo anula con un motivo
- **THEN** la respuesta es `409` con el código `CAJA_CERRADA`

#### Scenario: Anular sin motivo
- **WHEN** `admin` anula un cobro con el motivo vacío
- **THEN** la respuesta es `422` con el campo `motivo`

### Requirement: Permisos de los cobros
Solo la `RECEPCIONISTA` SHALL cobrar y solo el `ADMIN` SHALL anular. Los dos SHALL consultar los pendientes y la cuenta corriente. El `ODONTOLOGO` y el `PACIENTE` MUST recibir `403` en cualquier pedido de cargos, cobros o cuenta corriente.

#### Scenario: El admin no cobra
- **WHEN** `admin` cobra un cargo
- **THEN** la respuesta es `403`

#### Scenario: La recepción no anula
- **WHEN** `recepcion` anula un cobro
- **THEN** la respuesta es `403`

#### Scenario: El odontólogo no ve la cuenta corriente
- **WHEN** `ana.ejemplo` pide la cuenta corriente de "Paciente Uno"
- **THEN** la respuesta es `403`

### Requirement: Importes fuera de los logs
Los logs MUST NOT contener importes, deudas ni nombres o DNI de pacientes al cobrar, anular, abrir o cerrar la caja (RN-CA-08).

#### Scenario: Log de un cobro
- **WHEN** `recepcion` cobra `9000.00` del cargo de "Paciente Uno"
- **THEN** ninguna línea del log contiene "9000", "Paciente" ni "99000001"

### Requirement: Cobro de la seña sin caja
Un cobro con el medio `MERCADO_PAGO` SHALL registrarse solo desde la imputación de una seña (spec `senas`), sin caja: MUST NOT exigir una caja abierta, MUST NOT entrar en los totales ni en el efectivo esperado de ninguna caja, y MUST NOT poder registrarse desde `POST /api/v1/cargos/{id}/cobros` ni anularse desde `POST /api/v1/cobros/{id}/anulacion` (`409` con `COBRO_SIN_CAJA`). La cuenta corriente y los pendientes MUST contarlo como cualquier cobro no anulado.

#### Scenario: Seña en la cuenta corriente
- **GIVEN** un cargo de `15000.00` de "Paciente Dos" con la seña de `4500.00` imputada
- **WHEN** el `ADMIN` consulta su cuenta corriente
- **THEN** el cargo tiene un cobro `MERCADO_PAGO` de `4500.00`, `pagado` `4500.00` y `saldo` `10500.00`

#### Scenario: La seña no entra en la caja
- **GIVEN** la caja abierta con monto inicial `5000.00` y un cargo con la seña de `4500.00` imputada
- **WHEN** la `RECEPCIONISTA` consulta la caja abierta
- **THEN** los totales no incluyen el cobro `MERCADO_PAGO` y el efectivo esperado es `5000.00`

#### Scenario: Imputar sin caja abierta
- **GIVEN** que no hay caja abierta
- **WHEN** se imputa la seña de `4500.00` al cargo de "Paciente Dos"
- **THEN** el cargo tiene el cobro `MERCADO_PAGO` de `4500.00`

#### Scenario: La recepción no cobra con Mercado Pago
- **WHEN** la `RECEPCIONISTA` envía `POST /api/v1/cargos/7/cobros` con `medio` `MERCADO_PAGO`
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `medio`

#### Scenario: El cobro de la seña no se anula desde la caja
- **WHEN** el `ADMIN` envía `POST /api/v1/cobros/{id}/anulacion` para el cobro `MERCADO_PAGO` de una seña
- **THEN** la respuesta es `409` con `COBRO_SIN_CAJA` y el cobro sigue vigente
