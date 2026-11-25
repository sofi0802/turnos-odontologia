# frontend-caja Specification

## Purpose

La pantalla "Caja" con la que la recepción abre, cobra y cierra la caja, y el administrador la consulta y anula cobros (US-207). En los escenarios, con datos ficticios, hoy es el lunes 2026-12-07; la sesión es de `recepcion` salvo que se diga otra; los importes se muestran en pesos con el formato de Argentina, por ejemplo "$ 15.000,00".

## Requirements

### Requirement: Estado de la caja
Al abrir `#/caja`, la página SHALL pedir `GET /api/v1/cajas/abierta`. Sin caja abierta MUST mostrar "No hay una caja abierta." y, solo para la `RECEPCIONISTA`, el formulario "Abrir caja" con "Monto inicial en efectivo". Con caja abierta SHALL mostrar desde cuándo y quién la abrió, el monto inicial, los totales por medio de pago y el efectivo esperado.

#### Scenario: Sin caja abierta para la recepción
- **GIVEN** que la API responde `204` a la caja abierta
- **WHEN** se abre `#/caja`
- **THEN** se ve "No hay una caja abierta." y el botón "Abrir caja"

#### Scenario: Sin caja abierta para el admin
- **GIVEN** la sesión de `admin` y que la API responde `204` a la caja abierta
- **WHEN** se abre `#/caja`
- **THEN** se ve "No hay una caja abierta." y no se ve "Abrir caja"

#### Scenario: Caja abierta
- **GIVEN** la caja abierta por `recepcion` con monto inicial `5000.00`, `EFECTIVO` `9000.00`, `DEBITO` `15000.00` y `efectivoEsperado` `14000.00`
- **WHEN** se abre `#/caja`
- **THEN** se ve "Abierta por recepcion", "Monto inicial: $ 5.000,00", "Efectivo: $ 9.000,00", "Débito: $ 15.000,00" y "Efectivo esperado: $ 14.000,00"

### Requirement: Abrir la caja desde la pantalla
"Abrir caja" SHALL pedir `POST /api/v1/cajas` con el monto inicial y, con `201`, mostrar la caja abierta. Un monto vacío o negativo MUST mostrar el error en el campo sin pedir nada. Un `409 CAJA_YA_ABIERTA` MUST mostrar "Ya hay una caja abierta. Cerrala antes de abrir otra." y volver a pedir la caja abierta.

#### Scenario: Abrir con monto inicial
- **WHEN** se escribe `5000` en "Monto inicial en efectivo" y se elige "Abrir caja"
- **THEN** se pide `POST /api/v1/cajas` con `montoInicial` `5000.00`
- **AND** al responder `201` se ve "Monto inicial: $ 5.000,00"

#### Scenario: Monto inicial inválido
- **WHEN** se deja vacío "Monto inicial en efectivo" y se elige "Abrir caja"
- **THEN** el campo muestra "Escribí el monto inicial, aunque sea 0."
- **AND** no se pide `POST /api/v1/cajas`

### Requirement: Pendientes de cobro en la pantalla
Con la caja cargada, la página SHALL listar los pendientes de `GET /api/v1/cargos/pendientes` con el paciente, la fecha del turno, la prestación, el copago y el saldo, y "Cobrar" solo para la `RECEPCIONISTA` con caja abierta. Los turnos sin precio SHALL figurar aparte con "Sin precio: el administrador tiene que cargar el arancel." y sin "Cobrar". Sin pendientes, "No hay cobros pendientes."

#### Scenario: Pendientes con caja abierta
- **GIVEN** la caja abierta y un pendiente de "Uno, Paciente" de "Consulta o control" con saldo `5000.00`
- **WHEN** se abre `#/caja`
- **THEN** la fila dice "Uno, Paciente", "Consulta o control" y "Saldo: $ 5.000,00" y ofrece "Cobrar"

#### Scenario: Sin caja no se cobra
- **GIVEN** que no hay caja abierta y hay pendientes
- **WHEN** se abre `#/caja`
- **THEN** los pendientes se ven sin "Cobrar"

#### Scenario: Turno sin precio
- **GIVEN** un turno atendido sin arancel de "Dos, Paciente"
- **WHEN** se abre `#/caja`
- **THEN** se ve en "Sin precio" con "Sin precio: el administrador tiene que cargar el arancel." y sin "Cobrar"

### Requirement: Cobrar desde la pantalla
"Cobrar" SHALL abrir un formulario con el paciente, la prestación y el saldo, "Importe" (por defecto, el saldo) y "Medio de pago" (Efectivo, Débito, Crédito o Transferencia). Un importe vacío, cero o mayor que el saldo MUST mostrar el error sin pedir nada. Con `201` SHALL cerrar el formulario, mostrar "Cobro registrado. Saldo: <saldo>." y volver a pedir la caja y los pendientes.

#### Scenario: Cobro parcial desde la pantalla
- **GIVEN** el pendiente de "Uno, Paciente" con saldo `9000.00` (cargo 7)
- **WHEN** se elige "Cobrar", se escribe `4000` en "Importe", se elige "Débito" y se confirma
- **THEN** se pide `POST /api/v1/cargos/7/cobros` con `importe` `4000.00` y `medio` `DEBITO`
- **AND** al responder `201` con `saldo` `5000.00` se ve "Cobro registrado. Saldo: $ 5.000,00."

#### Scenario: Importe mayor que el saldo en la pantalla
- **GIVEN** el formulario de cobro de un pendiente con saldo `9000.00`
- **WHEN** se escribe `9500` en "Importe" y se confirma
- **THEN** el campo muestra "El importe no puede superar el saldo ($ 9.000,00)."
- **AND** no se pide ningún cobro

### Requirement: Cobros de la caja y anulación
La página SHALL listar los cobros de la caja abierta con hora, paciente, medio e importe, los anulados tachados y con "Anulado". Solo el `ADMIN` SHALL ver "Anular" en los cobros no anulados; "Anular" pide un motivo obligatorio y `POST /api/v1/cobros/{id}/anulacion`, y con `200` vuelve a pedir la caja.

#### Scenario: El admin anula desde la pantalla
- **GIVEN** la sesión de `admin`, la caja abierta y el cobro 21 de `4000.00`
- **WHEN** elige "Anular" en ese cobro, escribe "Cargado al paciente equivocado" y confirma
- **THEN** se pide `POST /api/v1/cobros/21/anulacion` con ese motivo

#### Scenario: La recepción no ve "Anular"
- **GIVEN** la caja abierta con cobros
- **WHEN** se abre `#/caja` con la sesión de `recepcion`
- **THEN** ningún cobro ofrece "Anular"

### Requirement: Cerrar la caja desde la pantalla
Solo la `RECEPCIONISTA` SHALL ver "Cerrar caja". El formulario SHALL mostrar el efectivo esperado, pedir "Efectivo contado" y mostrar la diferencia mientras se escribe. Con diferencia distinta de cero, "Motivo de la diferencia" MUST ser obligatorio. Con `200` SHALL mostrar el arqueo de la caja cerrada y el estado sin caja abierta.

#### Scenario: Cierre cuadrado
- **GIVEN** la caja 3 abierta con efectivo esperado `14000.00`
- **WHEN** se elige "Cerrar caja", se escribe `14000` en "Efectivo contado" y se confirma
- **THEN** se pide `POST /api/v1/cajas/3/cierre` con `efectivoContado` `14000.00` y sin motivo
- **AND** al responder `200` se ve "Caja cerrada. Diferencia: $ 0,00."

#### Scenario: Cierre con diferencia sin motivo
- **GIVEN** el formulario de cierre con efectivo esperado `14000.00`
- **WHEN** se escribe `13500` en "Efectivo contado"
- **THEN** se ve "Diferencia: -$ 500,00"
- **AND** al confirmar sin motivo, "Motivo de la diferencia" muestra "Escribí el motivo de la diferencia." y no se pide nada

### Requirement: Cuenta corriente de un paciente
La sección "Cuenta corriente" SHALL buscar un paciente con el buscador de `frontend-buscador-pacientes` y mostrar sus cargos con la fecha, la prestación, el copago, lo pagado y el saldo, y el saldo total. Sin cargos, "El paciente no tiene cargos."

#### Scenario: Ver la cuenta corriente
- **GIVEN** "Uno, Paciente" con un cargo de `9000.00` y `4000.00` pagados
- **WHEN** se elige "Uno, Paciente · DNI 99000001" en "Paciente" de "Cuenta corriente"
- **THEN** se pide `GET /api/v1/pacientes/1/cuenta-corriente`
- **AND** se ve "Pagado: $ 4.000,00", "Saldo: $ 5.000,00" y "Saldo total: $ 5.000,00"

### Requirement: Historial de cajas en la pantalla
La sección "Cajas anteriores" SHALL mostrar las cajas de los últimos 30 días de `GET /api/v1/cajas` con la fecha, quién abrió y cerró, el efectivo esperado, el contado y la diferencia con su motivo; una diferencia distinta de cero MUST destacarse con texto, no solo con color.

#### Scenario: Caja descuadrada en el historial
- **GIVEN** la caja del 2026-12-04 cerrada con `diferencia` `-500.00` y el motivo "Faltante sin explicar"
- **WHEN** se abre `#/caja`
- **THEN** su fila dice "Diferencia: -$ 500,00 (Faltante sin explicar)"

### Requirement: Mensajes de los rechazos de la caja
Ante un `409`, la pantalla MUST mostrar el mensaje del código: `SIN_CAJA_ABIERTA` → "No hay una caja abierta. Abrí la caja para cobrar."; `IMPORTE_SUPERA_SALDO` → "El importe supera el saldo del cargo."; `CAJA_CERRADA` → "La caja ya está cerrada."; `COBRO_ANULADO` → "El cobro ya estaba anulado."; `CAJA_YA_ABIERTA` → "Ya hay una caja abierta. Cerrala antes de abrir otra.". Después SHALL volver a pedir la caja y los pendientes.

#### Scenario: Otro puesto cobró antes
- **GIVEN** el formulario de cobro de un pendiente
- **WHEN** se confirma y la API responde `409` con el código `IMPORTE_SUPERA_SALDO`
- **THEN** se ve "El importe supera el saldo del cargo."
- **AND** se vuelven a pedir los pendientes
