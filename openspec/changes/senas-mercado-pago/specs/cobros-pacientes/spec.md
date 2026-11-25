## ADDED Requirements

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
