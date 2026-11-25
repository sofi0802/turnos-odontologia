# presupuestos Specification

## Purpose

Que el odontólogo arme el presupuesto de un paciente a partir de su odontograma y que el paciente sepa cuánto cubre su obra social y cuánto paga él: cálculo de cobertura y copago, foto inmutable del cálculo, estados y vencimiento, y consulta por rol cuidando la privacidad. En los escenarios se usan solo datos ficticios del seed de la KB y del de C-19:

- Pacientes: "Paciente Uno" (DNI `99000001`, cuenta `paciente.uno`) con "Plan Base" de "Obra Social Ejemplo" vigente desde 2026-01-01 y el número `OSE-000001`; "Paciente Dos" (DNI `99000002`, cuenta `paciente.dos`) sin afiliaciones (particular); "Paciente Tres" (DNI `99000003`) con "Plan Oro" de "Prepaga Prueba" vigente desde 2026-01-01 y el número `PP-000003`.
- Aranceles: "Consulta o control" 15000.00, "Limpieza (profilaxis)" 25000.00, "Tratamiento de conducto" 90000.00 y "Extracción simple" 30000.00.
- Nomenclador: `N-0101` vinculado a "Consulta o control", `N-0201` a "Limpieza (profilaxis)" y `N-0301` a "Extracción simple"; "Tratamiento de conducto" no tiene código.
- Cobertura: "Plan Base" cubre `N-0101` al 100 % y `N-0201` al 50 %; "Plan Oro" cubre `N-0101`, `N-0201` y `N-0301` al 80 %.
- Usuarios: `admin`, `recepcion`, `ana.ejemplo` (`ODONTOLOGO`, "Dra. Ana Ejemplo") y `bruno.prueba` (`ODONTOLOGO`, "Dr. Bruno Prueba").

Salvo que el escenario diga otra cosa, hoy es 2026-11-24 en `America/Argentina/Buenos_Aires`. Los importes están en pesos con dos decimales.

## Requirements

### Requirement: Emitir un presupuesto
El `ODONTOLOGO` SHALL emitir el presupuesto de un paciente con uno o más ítems. El presupuesto MUST nacer `PENDIENTE`, con la fecha de hoy en Buenos Aires, el vencimiento a los 30 días y el profesional de la sesión, nunca uno enviado en el pedido. La respuesta `201` MUST devolver el presupuesto con sus ítems y totales. Un paciente inexistente MUST responder `404` con `RECURSO_NO_ENCONTRADO`.

#### Scenario: Presupuesto de un paciente con obra social
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con un ítem "Consulta o control" y un ítem "Limpieza (profilaxis)", cantidad 1 cada uno
- **THEN** la respuesta es `201` con estado `PENDIENTE`, fecha 2026-11-24, vencimiento 2026-12-24 y profesional "Dra. Ana Ejemplo"
- **AND** el ítem "Consulta o control" tiene importe 15000.00, porcentaje 100, cobertura 15000.00 y copago 0.00
- **AND** el ítem "Limpieza (profilaxis)" tiene importe 25000.00, porcentaje 50, cobertura 12500.00 y copago 12500.00
- **AND** los totales son precio 40000.00, cobertura 27500.00 y copago 12500.00, con "Obra Social Ejemplo", "Plan Base" y `OSE-000001` como cobertura usada

#### Scenario: El profesional sale de la sesión
- **WHEN** `bruno.prueba` emite un presupuesto para "Paciente Uno" con un ítem "Consulta o control" e incluye en el pedido el id de "Dra. Ana Ejemplo" como profesional
- **THEN** la respuesta es `201` con profesional "Dr. Bruno Prueba"

#### Scenario: Paciente inexistente
- **WHEN** `ana.ejemplo` emite un presupuesto para el paciente con id `999999` con un ítem "Consulta o control"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Cálculo de cobertura y copago de cada ítem
Para cada ítem el sistema MUST calcular el importe como precio particular por cantidad; la cobertura como importe por porcentaje dividido 100, redondeada a centavos *half up*; y el copago como importe menos cobertura. El porcentaje MUST salir del plan de la afiliación vigente en la fecha del presupuesto y del código del ítem, y MUST ser 0 sin cobertura cargada. Los totales MUST ser la suma de los ítems.

#### Scenario: Paciente con prepaga
- **GIVEN** "Paciente Tres" tiene "Plan Oro" vigente
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Consulta o control" y "Limpieza (profilaxis)", cantidad 1 cada uno
- **THEN** "Consulta o control" tiene cobertura 12000.00 y copago 3000.00
- **AND** "Limpieza (profilaxis)" tiene cobertura 20000.00 y copago 5000.00
- **AND** los totales son precio 40000.00, cobertura 32000.00 y copago 8000.00

#### Scenario: Cantidad mayor que uno
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Extracción simple", cantidad 2
- **THEN** el ítem tiene precio unitario 30000.00, importe 60000.00, porcentaje 80, cobertura 48000.00 y copago 12000.00

#### Scenario: Código sin cobertura en el plan
- **GIVEN** "Plan Base" no tiene cobertura cargada para `N-0301`
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Extracción simple"
- **THEN** el ítem tiene porcentaje 0, cobertura 0.00 y copago 30000.00

#### Scenario: Redondeo half up a centavos
- **GIVEN** `admin` cambió el precio de "Limpieza (profilaxis)" a 25000.15
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Limpieza (profilaxis)"
- **THEN** el ítem tiene importe 25000.15, porcentaje 50, cobertura 12500.08 y copago 12500.07

### Requirement: Paciente particular
Un paciente sin afiliación vigente en la fecha del presupuesto MUST presupuestarse como particular: cada ítem con porcentaje 0, cobertura 0.00 y copago igual al importe, y el presupuesto sin afiliación y marcado como particular.

#### Scenario: Paciente sin obra social paga todo
- **GIVEN** "Paciente Dos" no tiene afiliaciones
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Dos" con "Consulta o control" y "Limpieza (profilaxis)"
- **THEN** la respuesta es `201` marcada como particular y sin afiliación
- **AND** los totales son precio 40000.00, cobertura 0.00 y copago 40000.00

#### Scenario: Afiliación cerrada antes de la fecha del presupuesto
- **GIVEN** la afiliación de "Paciente Uno" a "Plan Base" se cerró el 2026-11-23
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control"
- **THEN** el presupuesto es particular, con cobertura 0.00 y copago 15000.00

### Requirement: Código del nomenclador de cada ítem
Si la prestación tiene un solo código activo, el ítem MUST usarlo. Si tiene varios, el ítem MUST indicar cuál o responder `422` con `CODIGO_NOMENCLADOR_REQUERIDO`. Si no tiene ninguno, el ítem MUST ser particular (0 %). Un código indicado que no esté activo o no esté vinculado a esa prestación MUST responder `422` con `CODIGO_NOMENCLADOR_INVALIDO`.

#### Scenario: Prestación sin código
- **GIVEN** "Tratamiento de conducto" no tiene código del nomenclador
- **WHEN** `bruno.prueba` emite un presupuesto para "Paciente Tres" con "Tratamiento de conducto" en la pieza 36
- **THEN** el ítem no tiene código, con porcentaje 0, cobertura 0.00 y copago 90000.00

#### Scenario: Prestación con varios códigos sin indicar cuál
- **GIVEN** `admin` agregó el código `N-0102` "Consulta de urgencia", vinculado a "Consulta o control"
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control" sin indicar el código
- **THEN** la respuesta es `422` con el código `CODIGO_NOMENCLADOR_REQUERIDO`
- **AND** no se crea ningún presupuesto

#### Scenario: Prestación con varios códigos indicando uno
- **GIVEN** `admin` agregó el código `N-0102` vinculado a "Consulta o control" y "Plan Base" no lo cubre
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control" y el código `N-0102`
- **THEN** el ítem tiene el código `N-0102`, porcentaje 0 y copago 15000.00

#### Scenario: Código de otra prestación
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control" y el código `N-0201`
- **THEN** la respuesta es `422` con el código `CODIGO_NOMENCLADOR_INVALIDO`

#### Scenario: Código inactivo
- **GIVEN** `admin` desactivó `N-0301`
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Extracción simple" y el código `N-0301`
- **THEN** la respuesta es `422` con el código `CODIGO_NOMENCLADOR_INVALIDO`

### Requirement: Prestaciones presupuestables
Cada ítem MUST referirse a una prestación existente (o `422` `PRESTACION_INEXISTENTE`), activa (o `409` `PRESTACION_INACTIVA`) y con precio particular (o `409` `ARANCEL_INEXISTENTE`). Un ítem inválido MUST rechazar todo el presupuesto, sin crear nada.

#### Scenario: Prestación sin precio
- **GIVEN** "Tratamiento de conducto" no tiene precio particular
- **WHEN** `bruno.prueba` emite un presupuesto para "Paciente Uno" con "Consulta o control" y "Tratamiento de conducto"
- **THEN** la respuesta es `409` con el código `ARANCEL_INEXISTENTE`
- **AND** "Paciente Uno" no tiene presupuestos

#### Scenario: Prestación inexistente
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con la prestación de id `999999`
- **THEN** la respuesta es `422` con el código `PRESTACION_INEXISTENTE`

#### Scenario: Prestación inactiva
- **GIVEN** `admin` desactivó "Extracción simple"
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Extracción simple"
- **THEN** la respuesta es `409` con el código `PRESTACION_INACTIVA`

### Requirement: Ítems válidos
Un presupuesto MUST tener de 1 a 50 ítems. Cada ítem MUST tener cantidad entera de 1 a 99 y, opcionalmente, una pieza FDI y una cara (`O`, `M`, `D`, `V`, `L` o `P`) con las mismas reglas del odontograma; una cara MUST ir con pieza. Si no, MUST responder `422` con `DATOS_INVALIDOS` y el campo, sin repetir el valor.

#### Scenario: Presupuesto sin ítems
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" sin ítems
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `items`

#### Scenario: Cantidad cero
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Limpieza (profilaxis)", cantidad 0
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `items[0].cantidad`

#### Scenario: Pieza fuera de la notación FDI
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Extracción simple" en la pieza 19
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `items[0].pieza`

#### Scenario: Cara palatina en una pieza inferior
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control" en la pieza 36, cara `P`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `items[0].cara`

#### Scenario: Cara sin pieza
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Uno" con "Consulta o control", cara `O` y sin pieza
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `items[0].cara`

#### Scenario: Pieza y cara válidas
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Extracción simple" en la pieza 36 y "Consulta o control" en la pieza 16, cara `O`
- **THEN** la respuesta es `201` con las piezas 36 y 16, y la cara `O` en la 16

### Requirement: El presupuesto es una foto del cálculo
Un presupuesto emitido MUST conservar el precio unitario, el porcentaje, los importes, la afiliación usada y los nombres de la prestación, del código, del plan y de la obra social del momento de emitirlo. Cambiar después aranceles, coberturas, el catálogo o la afiliación MUST NOT cambiar un presupuesto emitido.

#### Scenario: Cambia el precio después de emitir
- **GIVEN** `ana.ejemplo` emitió para "Paciente Uno" un presupuesto con "Limpieza (profilaxis)" a 25000.00
- **WHEN** `admin` cambia el precio de "Limpieza (profilaxis)" a 30000.00 y `ana.ejemplo` consulta el presupuesto
- **THEN** el ítem sigue con precio unitario 25000.00, cobertura 12500.00 y copago 12500.00

#### Scenario: Cambia la cobertura del plan después de emitir
- **GIVEN** `ana.ejemplo` emitió para "Paciente Uno" un presupuesto con "Limpieza (profilaxis)" al 50 %
- **WHEN** `admin` carga para "Plan Base" el código `N-0201` al 100 % y `ana.ejemplo` consulta el presupuesto
- **THEN** el ítem sigue con porcentaje 50 y copago 12500.00

#### Scenario: Se corrige la afiliación después de emitir
- **GIVEN** `ana.ejemplo` emitió para "Paciente Uno" un presupuesto con "Plan Base"
- **WHEN** `recepcion` corrige la afiliación de "Paciente Uno" a "Plan Integral" y `ana.ejemplo` consulta el presupuesto
- **THEN** el presupuesto sigue mostrando "Plan Base" y los mismos importes

#### Scenario: La base rechaza modificar un ítem emitido
- **GIVEN** un presupuesto emitido para "Paciente Uno"
- **WHEN** se intenta cambiar el precio unitario de uno de sus ítems directamente en la base
- **THEN** la base rechaza el cambio y el ítem queda igual

### Requirement: Estados del presupuesto
El `ODONTOLOGO` SHALL aceptar o rechazar un presupuesto `PENDIENTE`. Uno `ACEPTADO` o `RECHAZADO` MUST NOT cambiar de estado (`409` `PRESUPUESTO_CERRADO`). Ningún presupuesto MUST poder editarse ni borrarse. Un presupuesto que no es de ese paciente MUST responder `404` con `RECURSO_NO_ENCONTRADO`.

#### Scenario: Aceptar un presupuesto pendiente
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno" emitido por `ana.ejemplo`
- **WHEN** `bruno.prueba` lo acepta
- **THEN** la respuesta es `200` con estado `ACEPTADO`

#### Scenario: Rechazar un presupuesto pendiente
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Dos"
- **WHEN** `ana.ejemplo` lo rechaza
- **THEN** la respuesta es `200` con estado `RECHAZADO`

#### Scenario: Un presupuesto aceptado no se rechaza
- **GIVEN** un presupuesto `ACEPTADO` de "Paciente Uno"
- **WHEN** `ana.ejemplo` lo rechaza
- **THEN** la respuesta es `409` con el código `PRESUPUESTO_CERRADO`
- **AND** el presupuesto sigue `ACEPTADO`

#### Scenario: Aceptar y rechazar a la vez
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno"
- **WHEN** `ana.ejemplo` lo acepta y `bruno.prueba` lo rechaza al mismo tiempo
- **THEN** uno responde `200` y el otro `409` con el código `PRESUPUESTO_CERRADO`
- **AND** el estado final es el del pedido que respondió `200`

#### Scenario: Presupuesto de otro paciente en la ruta
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno"
- **WHEN** `ana.ejemplo` lo acepta en la ruta de "Paciente Dos"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`
- **AND** el presupuesto sigue `PENDIENTE`

#### Scenario: No hay borrado
- **GIVEN** un presupuesto `RECHAZADO` de "Paciente Dos"
- **WHEN** `admin` pide `DELETE` sobre ese presupuesto
- **THEN** la respuesta no es exitosa y el presupuesto sigue existiendo

### Requirement: Vencimiento del presupuesto
Un presupuesto `PENDIENTE` cuyo vencimiento es anterior a hoy en Buenos Aires MUST informarse como vencido y MUST NOT aceptarse (`409` `PRESUPUESTO_VENCIDO`); sí MUST poder rechazarse. El día del vencimiento todavía se puede aceptar. Un presupuesto aceptado o rechazado MUST NOT informarse como vencido.

#### Scenario: Se acepta el día del vencimiento
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno" con fecha 2026-11-24 y vencimiento 2026-12-24
- **WHEN** el 2026-12-24 a las 23:59 de Buenos Aires `ana.ejemplo` lo acepta
- **THEN** la respuesta es `200` con estado `ACEPTADO`

#### Scenario: No se acepta un presupuesto vencido
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno" con vencimiento 2026-12-24
- **WHEN** el 2026-12-25 a las 00:00 de Buenos Aires `ana.ejemplo` lo acepta
- **THEN** la respuesta es `409` con el código `PRESUPUESTO_VENCIDO`
- **AND** el presupuesto sigue `PENDIENTE` y se informa vencido

#### Scenario: Se rechaza un presupuesto vencido
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno" con vencimiento 2026-12-24
- **WHEN** el 2026-12-30 `ana.ejemplo` lo rechaza
- **THEN** la respuesta es `200` con estado `RECHAZADO` y no se informa vencido

### Requirement: Sugerencias desde el odontograma
El `ODONTOLOGO` SHALL consultar las piezas y caras del paciente cuyo último estado en el odontograma es `A_TRATAR` o `CARIES`, ordenadas por pieza y cara, para elegir la prestación de cada una. Consultarlas MUST NOT crear un presupuesto ni cambiar el odontograma.

#### Scenario: Sugerir caries y piezas a tratar
- **GIVEN** el odontograma de "Paciente Uno" tiene la pieza 16 cara `O` en `CARIES`, la pieza 36 en `A_TRATAR` y la pieza 21 en `SANO`
- **WHEN** `ana.ejemplo` pide las sugerencias de presupuesto de "Paciente Uno"
- **THEN** la respuesta es `200` con la pieza 16 cara `O` (`CARIES`) y la pieza 36 sin cara (`A_TRATAR`), en ese orden

#### Scenario: Solo cuenta el último estado
- **GIVEN** la pieza 16 cara `O` de "Paciente Uno" se registró `CARIES` y después `OBTURADO`
- **WHEN** `ana.ejemplo` pide las sugerencias de presupuesto de "Paciente Uno"
- **THEN** la respuesta no incluye la pieza 16 cara `O`

#### Scenario: Paciente sin odontograma
- **GIVEN** "Paciente Dos" no tiene registros en el odontograma
- **WHEN** `ana.ejemplo` pide las sugerencias de presupuesto de "Paciente Dos"
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Consulta de presupuestos por el personal
`ADMIN`, `ODONTOLOGO` y `RECEPCIONISTA` SHALL listar los presupuestos de un paciente, del más reciente al más antiguo, y consultar uno, con estado, vencido o no, fechas, profesional, cobertura usada o particular, ítems y totales. La respuesta a la `RECEPCIONISTA` MUST NOT incluir pieza ni cara.

#### Scenario: El odontólogo ve pieza y cara
- **GIVEN** un presupuesto de "Paciente Tres" con "Extracción simple" en la pieza 36
- **WHEN** `ana.ejemplo` consulta ese presupuesto
- **THEN** la respuesta es `200` con el ítem "Extracción simple" en la pieza 36

#### Scenario: La recepción no ve pieza ni cara
- **GIVEN** un presupuesto de "Paciente Uno" con "Consulta o control" en la pieza 16, cara `O`
- **WHEN** `recepcion` lista los presupuestos de "Paciente Uno" y consulta ese presupuesto
- **THEN** ambas respuestas son `200` con "Consulta o control", el importe, la cobertura y el copago
- **AND** ninguna de las dos contiene los campos `pieza` ni `cara`

#### Scenario: El administrador consulta los presupuestos
- **GIVEN** "Paciente Uno" tiene un presupuesto del 2026-11-20 y otro del 2026-11-24
- **WHEN** `admin` lista los presupuestos de "Paciente Uno"
- **THEN** la respuesta es `200` con el del 2026-11-24 primero

### Requirement: El paciente consulta sus presupuestos
Una sesión `PACIENTE` SHALL listar y consultar solo sus propios presupuestos, identificada por la sesión y nunca por un id del pedido, sin poder cambiarlos. Un presupuesto de otro paciente MUST responder `404` con `RECURSO_NO_ENCONTRADO`.

#### Scenario: El paciente ve los suyos
- **GIVEN** "Paciente Uno" tiene un presupuesto y "Paciente Dos" tiene otro
- **WHEN** `paciente.uno` lista sus presupuestos
- **THEN** la respuesta es `200` con solo el presupuesto de "Paciente Uno", con sus ítems y totales

#### Scenario: Presupuesto de otro paciente
- **GIVEN** "Paciente Dos" tiene un presupuesto
- **WHEN** `paciente.uno` consulta ese presupuesto por su id
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: El paciente no acepta su presupuesto
- **GIVEN** "Paciente Uno" tiene un presupuesto `PENDIENTE`
- **WHEN** `paciente.uno` pide aceptarlo por la ruta del personal
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el presupuesto sigue `PENDIENTE`

### Requirement: Permisos sobre los presupuestos
Solo el `ODONTOLOGO` SHALL emitir, aceptar o rechazar presupuestos y pedir sugerencias. `ADMIN` y `RECEPCIONISTA` SHALL solo consultarlos. El sistema MUST responder `403` con `ACCESO_DENEGADO`, sin cambiar nada, a cualquier otra operación de esos roles y a toda ruta de presupuestos del personal pedida por un `PACIENTE`.

#### Scenario: La recepción no emite presupuestos
- **WHEN** `recepcion` emite un presupuesto para "Paciente Uno" con "Consulta o control"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene presupuestos

#### Scenario: El administrador no acepta presupuestos
- **GIVEN** un presupuesto `PENDIENTE` de "Paciente Uno"
- **WHEN** `admin` lo acepta
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el presupuesto sigue `PENDIENTE`

#### Scenario: La recepción no pide sugerencias
- **WHEN** `recepcion` pide las sugerencias de presupuesto de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Privacidad del presupuesto
Las piezas, las caras, los importes y la cobertura de un presupuesto MUST NOT aparecer en logs, recordatorios, mensajes de WhatsApp ni emails (RN-NO-01, RN-PR-07). Las respuestas de error MUST NOT repetir piezas, caras ni importes recibidos.

#### Scenario: El recordatorio no lleva el presupuesto
- **GIVEN** "Paciente Uno" tiene un presupuesto con "Consulta o control" en la pieza 16 y un turno mañana
- **WHEN** se genera el recordatorio del turno
- **THEN** el mensaje no contiene "presupuesto", la pieza 16 ni los importes

#### Scenario: Los logs no llevan el presupuesto
- **WHEN** `ana.ejemplo` emite un presupuesto para "Paciente Tres" con "Extracción simple" en la pieza 36 y después un pedido rechazado por `ARANCEL_INEXISTENTE`
- **THEN** la salida de logs de ambos pedidos no contiene la pieza 36, 30000.00, 24000.00 ni 6000.00
