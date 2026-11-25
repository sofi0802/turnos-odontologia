# caja Specification

## Purpose

La caja diaria del consultorio: la recepción la abre con un monto inicial en efectivo, cobra en ella y la cierra con el arqueo del efectivo (RN-CA-01, RN-CA-02, RN-CA-06). En los escenarios, con datos ficticios, hoy es el lunes 2026-12-07 en el consultorio; las cuentas son `recepcion` (`RECEPCIONISTA`), `admin` (`ADMIN`), `ana.ejemplo` (`ODONTOLOGO`) y `paciente.uno@example.com` (`PACIENTE`). Los importes son pesos con dos decimales.

## Requirements

### Requirement: Abrir la caja
`POST /api/v1/cajas` con `montoInicial` SHALL abrir la caja del día con ese efectivo y responder `201` con `id`, `fecha`, `montoInicial`, `abiertaPor` y `abiertaEn`. El monto MUST ser mayor o igual a cero, con hasta dos decimales; si no, `422 DATOS_INVALIDOS` con el campo `montoInicial`.

#### Scenario: La recepción abre la caja
- **GIVEN** que no hay ninguna caja abierta
- **WHEN** `recepcion` pide `POST /api/v1/cajas` con `montoInicial` `5000.00`
- **THEN** la respuesta es `201` con `fecha` `2026-12-07`, `montoInicial` `5000.00` y `abiertaPor` `recepcion`

#### Scenario: Monto inicial negativo
- **WHEN** `recepcion` pide abrir la caja con `montoInicial` `-1.00`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `montoInicial`

### Requirement: Una sola caja abierta
MUST haber como máximo una caja abierta en el consultorio. Abrir otra mientras haya una abierta, sea de hoy o de un día anterior, MUST responder `409` con el código `CAJA_YA_ABIERTA` y el `id` de la caja abierta. La garantía MUST valer también con pedidos simultáneos.

#### Scenario: Ya hay una caja abierta
- **GIVEN** la caja de hoy abierta
- **WHEN** `recepcion` pide abrir otra caja
- **THEN** la respuesta es `409` con el código `CAJA_YA_ABIERTA`

#### Scenario: Caja de un día anterior sin cerrar
- **GIVEN** la caja del viernes 2026-12-04 abierta
- **WHEN** `recepcion` pide abrir la caja de hoy
- **THEN** la respuesta es `409` con el código `CAJA_YA_ABIERTA` y el `id` de la caja del 2026-12-04

#### Scenario: Dos aperturas simultáneas
- **GIVEN** que no hay ninguna caja abierta
- **WHEN** llegan a la vez dos pedidos de abrir la caja
- **THEN** uno responde `201` y el otro `409` con el código `CAJA_YA_ABIERTA`

### Requirement: Consultar la caja abierta
`GET /api/v1/cajas/abierta` SHALL responder `200` con la caja abierta, sus totales de cobros no anulados por medio de pago y el `efectivoEsperado` (monto inicial más los cobros en efectivo no anulados), o `204` si no hay ninguna.

#### Scenario: Caja abierta con cobros
- **GIVEN** la caja de hoy abierta con `montoInicial` `5000.00`, un cobro de `9000.00` en efectivo, uno de `15000.00` con débito y uno anulado de `2000.00` en efectivo
- **WHEN** `recepcion` pide `GET /api/v1/cajas/abierta`
- **THEN** la respuesta es `200` con los totales `EFECTIVO` `9000.00`, `DEBITO` `15000.00`, `CREDITO` `0.00` y `TRANSFERENCIA` `0.00`
- **AND** `efectivoEsperado` es `14000.00`

#### Scenario: Sin caja abierta
- **GIVEN** que no hay ninguna caja abierta
- **WHEN** `recepcion` pide `GET /api/v1/cajas/abierta`
- **THEN** la respuesta es `204`

### Requirement: Cerrar la caja con arqueo
`POST /api/v1/cajas/{id}/cierre` con `efectivoContado` y un `motivoDiferencia` opcional SHALL cerrar la caja y responder `200` con `efectivoEsperado`, `efectivoContado`, `diferencia` (contado menos esperado) y los totales por medio. Si la diferencia no es cero, el motivo MUST ser obligatorio; si falta, `422` con el campo `motivoDiferencia`. Una caja cerrada MUST responder `409 CAJA_CERRADA`.

#### Scenario: Arqueo cuadrado
- **GIVEN** la caja abierta con `efectivoEsperado` `14000.00`
- **WHEN** `recepcion` la cierra con `efectivoContado` `14000.00`
- **THEN** la respuesta es `200` con `diferencia` `0.00`
- **AND** `GET /api/v1/cajas/abierta` responde `204`

#### Scenario: Arqueo descuadrado con motivo
- **GIVEN** la caja abierta con `efectivoEsperado` `14000.00`
- **WHEN** `recepcion` la cierra con `efectivoContado` `13500.00` y el motivo "Faltante sin explicar"
- **THEN** la respuesta es `200` con `diferencia` `-500.00` y ese motivo

#### Scenario: Arqueo descuadrado sin motivo
- **GIVEN** la caja abierta con `efectivoEsperado` `14000.00`
- **WHEN** `recepcion` la cierra con `efectivoContado` `14500.00` y sin motivo
- **THEN** la respuesta es `422` con el campo `motivoDiferencia`
- **AND** la caja sigue abierta

#### Scenario: Caja ya cerrada
- **GIVEN** la caja del viernes 2026-12-04 cerrada
- **WHEN** `recepcion` pide cerrarla otra vez
- **THEN** la respuesta es `409` con el código `CAJA_CERRADA`

### Requirement: Historial de cajas
`GET /api/v1/cajas?desde=&hasta=` SHALL listar las cajas abiertas entre esas fechas, de la más nueva a la más vieja, con quién y cuándo se abrieron y cerraron, el monto inicial y, si están cerradas, el efectivo esperado, el contado, la diferencia y su motivo. El período MUST tener como máximo 92 días; si no, `422` con el campo `hasta`.

#### Scenario: Historial de la semana
- **GIVEN** la caja del 2026-12-04 cerrada con `diferencia` `-500.00` y la de hoy abierta
- **WHEN** `admin` pide `GET /api/v1/cajas?desde=2026-12-01&hasta=2026-12-07`
- **THEN** la respuesta es `200` con la caja de hoy primero, sin arqueo, y la del 2026-12-04 con `diferencia` `-500.00`

### Requirement: Permisos de la caja
Solo la `RECEPCIONISTA` SHALL abrir y cerrar la caja. El `ADMIN` y la `RECEPCIONISTA` SHALL consultar la caja abierta y el historial. El `ADMIN` que intenta abrir o cerrar, y el `ODONTOLOGO` o el `PACIENTE` en cualquier pedido de la caja, MUST recibir `403`; sin sesión, `401`.

#### Scenario: El admin no abre la caja
- **WHEN** `admin` pide abrir la caja
- **THEN** la respuesta es `403`

#### Scenario: El odontólogo no ve la caja
- **WHEN** `ana.ejemplo` pide `GET /api/v1/cajas/abierta`
- **THEN** la respuesta es `403`

#### Scenario: El paciente no ve la caja
- **WHEN** `paciente.uno@example.com` pide `GET /api/v1/cajas/abierta`
- **THEN** la respuesta es `403`

#### Scenario: Sin sesión en la caja
- **WHEN** un navegador sin sesión pide `GET /api/v1/cajas/abierta`
- **THEN** la respuesta es `401`
