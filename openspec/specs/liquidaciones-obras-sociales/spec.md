# liquidaciones-obras-sociales Specification

## Purpose

Permitir que el administrador arme cada mes la liquidación de los turnos atendidos que le pide a cada obra social o prepaga, la revise en borrador, la presente y la descargue en CSV, sin reabrirla ni borrarla. En los escenarios se usan solo datos ficticios del seed de la KB: "Obra Social Ejemplo" (`OSE`) con "Plan Base" y "Plan Integral", "Prepaga Prueba" con "Plan Oro"; los códigos `N-0101` (Consulta o control), `N-0201` (Limpieza (profilaxis)) y `N-0301` (Extracción simple); los aranceles Consulta o control $15.000, Limpieza (profilaxis) $25.000, Tratamiento de conducto $90.000 (sin código) y Extracción simple $30.000; "Plan Base" cubre `N-0101` al 100 % y `N-0201` al 50 %, y "Plan Oro" cubre los tres códigos al 80 %; "Paciente Uno" (DNI `99000001`, "Plan Base", `OSE-000001`, vigente desde 2026-01-01), "Paciente Dos" (`99000002`, particular) y "Paciente Tres" (`99000003`, "Plan Oro", `PP-000003`, vigente desde 2026-01-01); los usuarios `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO`) y `paciente.demo` (`PACIENTE`). Salvo que se diga otra cosa, hoy es 2026-11-24 en `America/Argentina/Buenos_Aires` y las horas de los turnos son de esa zona.

## Requirements

### Requirement: Generar la liquidación de una obra social para un mes cerrado
El sistema SHALL permitir al `ADMIN` generar una liquidación para una obra social y un período (año y mes), que nace en estado `BORRADOR` con sus líneas y su total solicitado. El período MUST estar cerrado: su último día anterior a hoy en `America/Argentina/Buenos_Aires`; si no, `409 PERIODO_NO_CERRADO`. MUST existir una sola liquidación por obra social y período (`409 LIQUIDACION_EXISTENTE`). Una obra social inexistente MUST responder `404`.

#### Scenario: Generar la liquidación de octubre
- **GIVEN** "Paciente Uno" tiene un turno `ATENDIDO` de "Consulta o control" el 2026-10-06 a las 10:00
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para el período `2026-10`
- **THEN** la respuesta es `201` con una liquidación en `BORRADOR`, período `2026-10`, una línea y total solicitado `15000.00`

#### Scenario: El mes en curso no está cerrado
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para el período `2026-11`
- **THEN** la respuesta es `409` con el código `PERIODO_NO_CERRADO` y no se crea ninguna liquidación

#### Scenario: El primer día del mes siguiente el período ya está cerrado
- **GIVEN** hoy es 2026-11-01 a las 00:00 en `America/Argentina/Buenos_Aires`
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para el período `2026-10`
- **THEN** la respuesta es `201`

#### Scenario: Liquidación repetida para la misma obra social y mes
- **GIVEN** existe la liquidación de "Obra Social Ejemplo" para `2026-10`
- **WHEN** `admin` vuelve a generar la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la respuesta es `409` con el código `LIQUIDACION_EXISTENTE` y sigue habiendo una sola

#### Scenario: Otra obra social en el mismo mes
- **GIVEN** existe la liquidación de "Obra Social Ejemplo" para `2026-10`
- **WHEN** `admin` genera la liquidación de "Prepaga Prueba" para `2026-10`
- **THEN** la respuesta es `201`

#### Scenario: Obra social inexistente o período mal formado
- **WHEN** `admin` genera una liquidación para una obra social que no existe
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`
- **AND** si el período es `2026-13` o falta, la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `periodo`

#### Scenario: Mes sin turnos para liquidar
- **GIVEN** no hay turnos atendidos de afiliados a "Prepaga Prueba" hasta el 2026-09-30
- **WHEN** `admin` genera la liquidación de "Prepaga Prueba" para `2026-09`
- **THEN** la respuesta es `201` con una liquidación en `BORRADOR`, sin líneas y total solicitado `0.00`

### Requirement: Turnos que entran en la liquidación
Una liquidación SHALL tener una línea por cada turno `ATENDIDO` cuyo paciente tenía, en la fecha del turno, una afiliación vigente a un plan de esa obra social, cuya prestación tiene un código activo del nomenclador que el plan cubre con más de 0 %, con fecha hasta el último día del período y que no está en otra liquidación. La fecha del turno MUST ser la de su inicio en `America/Argentina/Buenos_Aires`.

#### Scenario: Turnos atendidos de un afiliado
- **GIVEN** "Paciente Uno" tiene turnos `ATENDIDO` de "Consulta o control" el 2026-10-06 y de "Limpieza (profilaxis)" el 2026-10-20
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la liquidación tiene dos líneas de "Paciente Uno" con "Plan Base", los códigos `N-0101` y `N-0201`

#### Scenario: Paciente particular
- **GIVEN** "Paciente Dos" (particular) tiene un turno `ATENDIDO` de "Consulta o control" el 2026-10-08
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la liquidación no tiene ninguna línea de "Paciente Dos"

#### Scenario: Código que el plan no cubre y prestación sin código
- **GIVEN** "Paciente Uno" tiene turnos `ATENDIDO` de "Extracción simple" (`N-0301`, sin cobertura en "Plan Base") el 2026-10-09 y de "Tratamiento de conducto" (sin código) el 2026-10-10
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** ninguno de los dos turnos está en la liquidación

#### Scenario: Turnos no atendidos
- **GIVEN** "Paciente Uno" tiene un turno `AUSENTE` el 2026-10-13, uno `CANCELADO` el 2026-10-14 y uno `CONFIRMADO` el 2026-10-15, todos de "Consulta o control"
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** ninguno de esos turnos está en la liquidación

#### Scenario: Afiliado a otra obra social
- **GIVEN** "Paciente Tres" ("Plan Oro" de "Prepaga Prueba") tiene un turno `ATENDIDO` de "Consulta o control" el 2026-10-07
- **WHEN** `admin` genera las liquidaciones de `2026-10` de "Obra Social Ejemplo" y de "Prepaga Prueba"
- **THEN** el turno no está en la de "Obra Social Ejemplo" y está en la de "Prepaga Prueba" con "Plan Oro" y el código `N-0101`

#### Scenario: Afiliación vigente en la fecha del turno
- **GIVEN** "Paciente Dos" tiene un turno `ATENDIDO` de "Consulta o control" el 2026-10-02 y una afiliación a "Plan Base" vigente desde 2026-10-05
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** el turno del 2026-10-02 no está en la liquidación

#### Scenario: Turno del último instante del mes
- **GIVEN** "Paciente Uno" tiene turnos `ATENDIDO` de "Consulta o control" el 2026-10-31 a las 23:30 y el 2026-11-01 a las 00:00
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** el turno del 2026-10-31 está en la liquidación y el del 2026-11-01 no

### Requirement: Un turno se liquida una sola vez y los atrasados entran después
Un turno MUST estar como máximo en una liquidación. Un turno `ATENDIDO` con fecha anterior al período que todavía no está en ninguna liquidación SHALL entrar en la siguiente que se genere para esa obra social, con su fecha real.

#### Scenario: Turno ya liquidado
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-09` tiene el turno de "Paciente Uno" del 2026-09-15
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** ese turno no está en la de `2026-10`

#### Scenario: Turno marcado atendido tarde
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-09` ya se generó
- **AND** después se marcó `ATENDIDO` el turno de "Consulta o control" de "Paciente Uno" del 2026-09-30
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la de `2026-10` tiene ese turno con fecha 2026-09-30 y monto `15000.00`

### Requirement: Monto solicitado de cada línea
El monto solicitado de cada línea SHALL ser el precio de la prestación por el porcentaje del plan para el código de la línea dividido 100, redondeado a centavos *half up*. Si el turno no tiene cargo de caja, MUST usarse el arancel y la cobertura del plan vigentes al generar. El total solicitado MUST ser la suma de los montos de las líneas, y el detalle MUST agrupar las líneas por plan con su subtotal.

#### Scenario: Liquidación de "Obra Social Ejemplo" de octubre
- **GIVEN** "Paciente Uno" tiene turnos `ATENDIDO` de "Consulta o control" el 2026-10-06 y de "Limpieza (profilaxis)" el 2026-10-20
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la línea de "Consulta o control" tiene precio `15000.00`, porcentaje `100` y monto `15000.00`
- **AND** la de "Limpieza (profilaxis)" tiene precio `25000.00`, porcentaje `50` y monto `12500.00`
- **AND** el grupo "Plan Base" tiene subtotal `27500.00` y el total solicitado es `27500.00`

#### Scenario: Liquidación de "Prepaga Prueba" con cobertura del 80 %
- **GIVEN** "Paciente Tres" tiene turnos `ATENDIDO` de "Consulta o control" el 2026-10-07, de "Limpieza (profilaxis)" el 2026-10-14 y de "Extracción simple" el 2026-10-21
- **WHEN** `admin` genera la liquidación de "Prepaga Prueba" para `2026-10`
- **THEN** los montos son `12000.00`, `20000.00` y `24000.00`, y el total solicitado es `56000.00`

#### Scenario: Redondeo half up
- **GIVEN** el arancel de "Limpieza (profilaxis)" es `25000.15` y "Paciente Uno" tiene un turno `ATENDIDO` de "Limpieza (profilaxis)" el 2026-10-20
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la línea tiene monto `12500.08`

#### Scenario: Varios planes de la misma obra social
- **GIVEN** "Paciente Uno" ("Plan Base") y "Paciente Dos", afiliado a "Plan Integral" desde 2026-10-01, tienen cada uno un turno `ATENDIDO` de "Consulta o control" en octubre
- **WHEN** `admin` consulta la liquidación de "Obra Social Ejemplo" de `2026-10`
- **THEN** el detalle tiene un grupo "Plan Base" con subtotal `15000.00`, otro "Plan Integral" con subtotal `15000.00` y total `30000.00`

### Requirement: Precio del cargo de caja del turno
Si el turno ya tiene su cargo de caja (C-22), la línea SHALL usar el precio y el porcentaje de cobertura de ese cargo, de modo que el copago del paciente más el monto solicitado den el precio. Si el porcentaje del cargo es 0, el turno MUST NOT entrar en la liquidación.

#### Scenario: Turno con cargo de caja
- **GIVEN** el turno `ATENDIDO` de "Consulta o control" de "Paciente Uno" del 2026-10-06 tiene un cargo con precio `14000.00`, porcentaje `100`, cobertura `14000.00` y copago `0.00`
- **AND** hoy el arancel de "Consulta o control" es `15000.00`
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la línea tiene precio `14000.00`, porcentaje `100` y monto `14000.00`

#### Scenario: Cargo sin cobertura
- **GIVEN** el turno `ATENDIDO` de "Limpieza (profilaxis)" de "Paciente Uno" del 2026-10-20 tiene un cargo con porcentaje `0`
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** ese turno no está en la liquidación

### Requirement: Turnos sin arancel
Un turno que cumple las condiciones para entrar pero cuya prestación no tiene arancel ni cargo de caja MUST NOT generar una línea. La respuesta de generar y de regenerar SHALL informar cuántos turnos quedaron afuera por falta de arancel, sin identificarlos en logs; esos turnos siguen libres para una regeneración o una liquidación posterior.

#### Scenario: Prestación sin arancel
- **GIVEN** "Limpieza (profilaxis)" no tiene arancel y "Paciente Uno" tiene un turno `ATENDIDO` de "Limpieza (profilaxis)" el 2026-10-20
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la liquidación no tiene ese turno y la respuesta informa `1` turno sin arancel

#### Scenario: Se carga el arancel y se regenera
- **GIVEN** la liquidación anterior está en `BORRADOR` y después se carga el arancel `25000.00` de "Limpieza (profilaxis)"
- **WHEN** `admin` regenera la liquidación
- **THEN** la liquidación tiene la línea de "Limpieza (profilaxis)" con monto `12500.00` y la respuesta informa `0` turnos sin arancel

### Requirement: Código del nomenclador de cada línea
Si la prestación tiene un solo código activo, la línea SHALL usarlo. Si tiene varios y el plan cubre al menos uno con más de 0 %, la línea MUST quedar sin código, porcentaje ni monto, y el `ADMIN` SHALL elegir en el borrador uno de los códigos de la prestación que el plan cubre con más de 0 %; elegir otro MUST responder `422 CODIGO_NOMENCLADOR_INVALIDO`.

#### Scenario: Prestación con varios códigos
- **GIVEN** "Consulta o control" tiene además el código ficticio `N-0102`, que "Plan Base" cubre al 80 %
- **AND** "Paciente Uno" tiene un turno `ATENDIDO` de "Consulta o control" el 2026-10-06
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la línea de ese turno no tiene código, porcentaje ni monto, informa los códigos `N-0101` y `N-0102` para elegir y no suma al total solicitado

#### Scenario: Elegir el código de una línea
- **GIVEN** la línea anterior sin código en una liquidación en `BORRADOR`
- **WHEN** `admin` elige `N-0102` para esa línea
- **THEN** la respuesta es `200`, la línea tiene código `N-0102`, porcentaje `80` y monto `12000.00`, y el total solicitado suma `12000.00`

#### Scenario: Código que no es de la prestación
- **GIVEN** la línea anterior sin código
- **WHEN** `admin` elige `N-0301` para esa línea
- **THEN** la respuesta es `422` con el código `CODIGO_NOMENCLADOR_INVALIDO` y la línea sigue sin código

### Requirement: Regenerar una liquidación en borrador
El `ADMIN` SHALL poder regenerar una liquidación en `BORRADOR`: agrega los turnos que ahora cumplen las condiciones, recalcula precio, porcentaje y monto de las líneas sin cargo con los valores vigentes, saca las líneas cuyo turno ya no cumple las condiciones y conserva el código elegido por el `ADMIN` y los turnos quitados a mano. Regenerar una presentada o cerrada MUST responder `409`.

#### Scenario: Regenerar agrega turnos y recalcula
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-10` en `BORRADOR` con la "Consulta o control" de "Paciente Uno" en `15000.00`
- **AND** después el arancel de "Consulta o control" pasa a `16000.00` y se marca `ATENDIDO` el turno de "Limpieza (profilaxis)" de "Paciente Uno" del 2026-10-20
- **WHEN** `admin` regenera la liquidación
- **THEN** la consulta tiene monto `16000.00`, aparece la limpieza con `12500.00` y el total solicitado es `28500.00`

#### Scenario: Regenerar conserva el código elegido
- **GIVEN** una línea de "Consulta o control" en `BORRADOR` con el código `N-0102` elegido por `admin`
- **WHEN** `admin` regenera la liquidación
- **THEN** la línea conserva `N-0102` con porcentaje `80`

#### Scenario: Regenerar una liquidación presentada
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-10` está `PRESENTADA`
- **WHEN** `admin` la regenera
- **THEN** la respuesta es `409` con el código `LIQUIDACION_PRESENTADA` y las líneas no cambian

### Requirement: Quitar líneas de una liquidación en borrador
El `ADMIN` SHALL poder quitar una línea de una liquidación en `BORRADOR`. El turno quitado MUST NOT volver a entrar al regenerar esa misma liquidación y MUST quedar libre para cualquier otra liquidación. Quitar una línea de una presentada o cerrada MUST responder `409`; una línea que no es de esa liquidación MUST responder `404`.

#### Scenario: Quitar una línea
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-10` en `BORRADOR` con la consulta (`15000.00`) y la limpieza (`12500.00`) de "Paciente Uno"
- **WHEN** `admin` quita la línea de la limpieza
- **THEN** la respuesta es `204` y la liquidación tiene una línea y total solicitado `15000.00`

#### Scenario: El turno quitado no vuelve al regenerar
- **GIVEN** la línea de la limpieza se quitó de la liquidación de `2026-10`
- **WHEN** `admin` regenera la liquidación de `2026-10`
- **THEN** la limpieza no vuelve a esa liquidación

#### Scenario: El turno quitado entra en el mes siguiente
- **GIVEN** la línea de la limpieza del 2026-10-20 se quitó de la liquidación de `2026-10`
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" para `2026-11` con hoy 2026-12-02
- **THEN** la de `2026-11` tiene la limpieza con fecha 2026-10-20

#### Scenario: Quitar una línea de una liquidación presentada
- **GIVEN** la liquidación de `2026-10` está `PRESENTADA`
- **WHEN** `admin` quita una de sus líneas
- **THEN** la respuesta es `409` con el código `LIQUIDACION_PRESENTADA` y la línea sigue

### Requirement: Presentar la liquidación
El `ADMIN` SHALL poder pasar una liquidación de `BORRADOR` a `PRESENTADA`, con la fecha y hora de presentación. MUST rechazarse con `409 LINEA_SIN_CODIGO` si alguna línea no tiene código y con `409 LIQUIDACION_VACIA` si no tiene líneas. Una liquidación presentada o cerrada MUST NOT volver a `BORRADOR`.

#### Scenario: Presentar una liquidación completa
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-10` en `BORRADOR` con total solicitado `27500.00`
- **WHEN** `admin` la presenta
- **THEN** la respuesta es `200` con estado `PRESENTADA`, la fecha de presentación y total solicitado `27500.00`

#### Scenario: Presentar con una línea sin código
- **GIVEN** una liquidación en `BORRADOR` con una línea de "Consulta o control" sin código
- **WHEN** `admin` la presenta
- **THEN** la respuesta es `409` con el código `LINEA_SIN_CODIGO` y sigue en `BORRADOR`

#### Scenario: Presentar una liquidación vacía
- **GIVEN** la liquidación de "Prepaga Prueba" de `2026-09` en `BORRADOR` sin líneas
- **WHEN** `admin` la presenta
- **THEN** la respuesta es `409` con el código `LIQUIDACION_VACIA`

#### Scenario: Presentar dos veces
- **GIVEN** la liquidación de `2026-10` está `PRESENTADA`
- **WHEN** `admin` la presenta otra vez
- **THEN** la respuesta es `409` con el código `LIQUIDACION_PRESENTADA`

### Requirement: Una liquidación presentada no cambia y ninguna se borra
Una vez `PRESENTADA`, las líneas, los montos solicitados y la foto de cada línea MUST NOT cambiar, aunque cambien los aranceles, la cobertura del plan, la afiliación o los nombres; solo se registra la conciliación. Las liquidaciones MUST NOT borrarse. La base de datos MUST rechazar esos cambios aunque no pasen por la API.

#### Scenario: Cambios en el catálogo después de presentar
- **GIVEN** la liquidación de `2026-10` está `PRESENTADA` con la consulta de "Paciente Uno" en `15000.00` y el plan "Plan Base"
- **WHEN** después cambian el arancel de "Consulta o control" a `16000.00` y el nombre del plan a "Plan Base 2027"
- **THEN** la consulta de la liquidación sigue mostrando `15000.00` y "Plan Base"

#### Scenario: La base rechaza modificar una línea presentada
- **GIVEN** la liquidación de `2026-10` está `PRESENTADA`
- **WHEN** se intenta por SQL cambiar el monto solicitado de una línea, insertar o borrar una línea, o borrar la liquidación
- **THEN** la base rechaza cada operación y los datos no cambian

#### Scenario: Sin borrado por la API
- **WHEN** `admin` envía `DELETE` a una liquidación
- **THEN** la respuesta es `405` y la liquidación sigue existiendo

### Requirement: Exportar la liquidación en CSV
El `ADMIN` SHALL poder descargar en CSV (UTF-8) una liquidación `PRESENTADA` o `CERRADA`: un encabezado, una fila por línea ordenada por plan, fecha y turno con fecha, paciente, DNI, número de afiliado, plan, código, prestación y monto solicitado, y una fila final con el total. La respuesta MUST ser un adjunto sin caché. En `BORRADOR` MUST responder `409 LIQUIDACION_NO_PRESENTADA`.

#### Scenario: Descargar una liquidación presentada
- **GIVEN** la liquidación de "Obra Social Ejemplo" de `2026-10` está `PRESENTADA` con la consulta y la limpieza de "Paciente Uno"
- **WHEN** `admin` descarga su CSV
- **THEN** la respuesta es `200` con `text/csv; charset=UTF-8`, `Content-Disposition: attachment` y `Cache-Control: no-store`
- **AND** el archivo tiene el encabezado, la fila `2026-10-06;Paciente Uno;99000001;OSE-000001;Plan Base;N-0101;Consulta o control;15000.00`, la de la limpieza con `12500.00` y la fila final con el total `27500.00`

#### Scenario: Descargar un borrador
- **GIVEN** la liquidación de `2026-10` está en `BORRADOR`
- **WHEN** `admin` descarga su CSV
- **THEN** la respuesta es `409` con el código `LIQUIDACION_NO_PRESENTADA`

#### Scenario: Celdas que empiezan con un signo de fórmula
- **GIVEN** una liquidación presentada con una prestación ficticia llamada "=Control"
- **WHEN** `admin` descarga su CSV
- **THEN** la celda de la prestación no empieza con `=` y la planilla no la interpreta como fórmula

### Requirement: Consulta de las liquidaciones
El `ADMIN` SHALL poder listar las liquidaciones, filtrando por obra social y por estado, con período, estado, cantidad de líneas y totales, más recientes primero, y ver el detalle de una con sus líneas agrupadas por plan, subtotales, totales, fechas de generación, presentación y cierre y los datos de conciliación de cada línea. Una liquidación inexistente MUST responder `404`.

#### Scenario: Listar las liquidaciones de una obra social
- **GIVEN** existen las liquidaciones de "Obra Social Ejemplo" de `2026-09` y `2026-10` y la de "Prepaga Prueba" de `2026-10`
- **WHEN** `admin` lista las liquidaciones de "Obra Social Ejemplo"
- **THEN** la respuesta tiene dos liquidaciones, primero la de `2026-10`, sin nombres ni DNI de pacientes

#### Scenario: Liquidación inexistente
- **WHEN** `admin` consulta una liquidación que no existe
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Solo el administrador maneja las liquidaciones
Todas las operaciones de liquidaciones y de su conciliación SHALL estar permitidas solo al `ADMIN`. La `RECEPCIONISTA`, el `ODONTOLOGO` y el `PACIENTE` MUST recibir `403` sin que se cree ni cambie nada; un pedido sin sesión MUST recibir `401`.

#### Scenario: La recepción intenta generar una liquidación
- **WHEN** `recepcion` genera la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** la respuesta es `403` y no se crea ninguna liquidación

#### Scenario: Otros roles intentan consultar o descargar
- **WHEN** `ana.ejemplo` o `paciente.demo` consultan una liquidación o descargan su CSV
- **THEN** la respuesta es `403`

#### Scenario: Sin sesión
- **WHEN** un pedido sin sesión lista las liquidaciones
- **THEN** la respuesta es `401`

### Requirement: Privacidad de la liquidación
Los nombres, DNI y números de afiliado de los pacientes, las prestaciones y los importes de una liquidación MUST NOT incluirse en logs, mensajes ni en el detalle de los errores. Los mensajes de error MUST ser fijos por código.

#### Scenario: Generar y fallar sin datos en los logs
- **WHEN** `admin` genera la liquidación de "Obra Social Ejemplo" de `2026-10` con la consulta de "Paciente Uno" y después la genera otra vez
- **THEN** los logs no contienen `99000001`, `OSE-000001`, `Paciente Uno`, `Consulta o control` ni `15000`
- **AND** el `409 LIQUIDACION_EXISTENTE` no menciona pacientes ni importes

### Requirement: Generaciones simultáneas
Si dos pedidos generan a la vez la misma liquidación, MUST crearse una sola y el otro MUST recibir `409 LIQUIDACION_EXISTENTE`. Un turno MUST NOT quedar en dos liquidaciones aunque se generen a la vez.

#### Scenario: Dos generaciones a la vez
- **WHEN** dos pedidos de `admin` generan al mismo tiempo la liquidación de "Obra Social Ejemplo" para `2026-10`
- **THEN** uno recibe `201`, el otro `409` con el código `LIQUIDACION_EXISTENTE` y hay una sola liquidación con sus líneas
