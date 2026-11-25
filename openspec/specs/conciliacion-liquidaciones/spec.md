# conciliacion-liquidaciones Specification

## Purpose

Permitir que el administrador registre, línea por línea, lo que pagó cada obra social por una liquidación presentada, vea cuánto cobró y cuánto falta o le rechazaron, y cierre la liquidación cuando todo está conciliado. En los escenarios se usan solo datos ficticios del seed de la KB: la liquidación de "Obra Social Ejemplo" del período `2026-10`, `PRESENTADA`, con dos líneas de "Paciente Uno" ("Plan Base", `OSE-000001`): "Consulta o control" (`N-0101`) con monto solicitado `15000.00` y "Limpieza (profilaxis)" (`N-0201`) con `12500.00`, total solicitado `27500.00`; y los usuarios `admin` (`ADMIN`) y `recepcion` (`RECEPCIONISTA`).

## Requirements

### Requirement: Registrar lo cobrado de cada línea
El `ADMIN` SHALL poder registrar en cada línea de una liquidación `PRESENTADA` el monto cobrado, y el sistema MUST derivar el resultado: `COBRADA` si es igual al solicitado, `PARCIAL` si es mayor que 0 y menor, y `RECHAZADA` si es 0. Cada conciliación MUST guardar quién y cuándo la registró. Una línea que no es de esa liquidación MUST responder `404`.

#### Scenario: Línea cobrada completa
- **WHEN** `admin` registra `15000.00` cobrados en la línea de "Consulta o control"
- **THEN** la respuesta es `200` y la línea tiene monto cobrado `15000.00` y resultado `COBRADA`

#### Scenario: Línea cobrada en parte
- **WHEN** `admin` registra `10000.00` cobrados en la línea de "Limpieza (profilaxis)"
- **THEN** la línea tiene monto cobrado `10000.00` y resultado `PARCIAL`

#### Scenario: Línea rechazada con motivo
- **WHEN** `admin` registra `0.00` cobrados en la línea de "Limpieza (profilaxis)" con el motivo "Falta la orden de la práctica"
- **THEN** la línea tiene monto cobrado `0.00`, resultado `RECHAZADA` y ese motivo

#### Scenario: Línea de otra liquidación
- **WHEN** `admin` registra lo cobrado indicando la liquidación de `2026-10` y una línea de otra liquidación
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO` y ninguna línea cambia

### Requirement: Monto cobrado y motivo válidos
El monto cobrado MUST ser un importe en pesos con hasta dos decimales, entre 0 y el monto solicitado de la línea; si no, `422 MONTO_COBRADO_INVALIDO`. Una línea `RECHAZADA` MUST tener un motivo de 1 a 200 caracteres sin contar los espacios de los extremos (`422 MOTIVO_REQUERIDO` si falta); un motivo en una línea que no queda rechazada MUST responder `422 DATOS_INVALIDOS`.

#### Scenario: Monto mayor que el solicitado
- **WHEN** `admin` registra `15000.01` cobrados en la línea de "Consulta o control"
- **THEN** la respuesta es `422` con el código `MONTO_COBRADO_INVALIDO` y la línea sigue sin conciliar

#### Scenario: Monto negativo o con tres decimales
- **WHEN** `admin` registra `-1.00` o `100.005` cobrados en la línea de "Limpieza (profilaxis)"
- **THEN** cada respuesta es `422` con el código `MONTO_COBRADO_INVALIDO`

#### Scenario: Rechazo sin motivo
- **WHEN** `admin` registra `0.00` cobrados en la línea de "Limpieza (profilaxis)" con el motivo vacío o solo con espacios
- **THEN** la respuesta es `422` con el código `MOTIVO_REQUERIDO` y la línea sigue sin conciliar

#### Scenario: Motivo en una línea cobrada
- **WHEN** `admin` registra `15000.00` cobrados en la línea de "Consulta o control" con un motivo
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `motivo`

### Requirement: Solo se concilia una liquidación presentada
Registrar lo cobrado MUST estar permitido solo en una liquidación `PRESENTADA`. En una en `BORRADOR` MUST responder `409 LIQUIDACION_NO_PRESENTADA` y en una `CERRADA` `409 LIQUIDACION_CERRADA`. Mientras la liquidación siga `PRESENTADA`, el `ADMIN` SHALL poder corregir lo registrado en una línea ya conciliada.

#### Scenario: Conciliar un borrador
- **GIVEN** la liquidación de "Prepaga Prueba" de `2026-10` está en `BORRADOR`
- **WHEN** `admin` registra lo cobrado en una de sus líneas
- **THEN** la respuesta es `409` con el código `LIQUIDACION_NO_PRESENTADA`

#### Scenario: Corregir una línea conciliada
- **GIVEN** la línea de "Limpieza (profilaxis)" tiene `10000.00` cobrados y resultado `PARCIAL`
- **WHEN** `admin` registra `12500.00` cobrados en esa línea
- **THEN** la línea queda con `12500.00` y resultado `COBRADA`

#### Scenario: Conciliar una liquidación cerrada
- **GIVEN** la liquidación de `2026-10` está `CERRADA`
- **WHEN** `admin` registra lo cobrado en una de sus líneas
- **THEN** la respuesta es `409` con el código `LIQUIDACION_CERRADA` y la línea no cambia

### Requirement: Conciliación parcial y totales
La liquidación SHALL poder conciliarse por partes: las líneas sin resultado quedan pendientes. El detalle MUST informar el total cobrado (suma de lo cobrado en las líneas conciliadas), la diferencia (lo solicitado de las líneas conciliadas menos lo cobrado) y la cantidad de líneas pendientes. Los montos solicitados MUST NOT cambiar al conciliar.

#### Scenario: Una línea conciliada y otra pendiente
- **GIVEN** `admin` registró `15000.00` cobrados en la línea de "Consulta o control"
- **WHEN** `admin` consulta la liquidación
- **THEN** el total solicitado es `27500.00`, el total cobrado `15000.00`, la diferencia `0.00` y hay `1` línea pendiente

#### Scenario: Una línea parcial
- **GIVEN** `admin` registró `15000.00` en la consulta y `10000.00` en la limpieza
- **WHEN** `admin` consulta la liquidación
- **THEN** el total cobrado es `25000.00`, la diferencia `2500.00` y no hay líneas pendientes

#### Scenario: Una línea rechazada
- **GIVEN** `admin` registró `15000.00` en la consulta y `0.00` en la limpieza con un motivo
- **WHEN** `admin` consulta la liquidación
- **THEN** el total cobrado es `15000.00` y la diferencia `12500.00`

### Requirement: Cerrar la liquidación conciliada
El `ADMIN` SHALL poder pasar una liquidación de `PRESENTADA` a `CERRADA`, con la fecha y hora de cierre, cuando todas sus líneas tienen resultado; si queda alguna pendiente MUST responder `409 CONCILIACION_INCOMPLETA`. Una liquidación cerrada MUST NOT cambiar más: ni sus líneas, ni su conciliación, ni su estado.

#### Scenario: Cerrar con todo conciliado
- **GIVEN** las dos líneas tienen resultado (`COBRADA` y `PARCIAL`)
- **WHEN** `admin` cierra la liquidación
- **THEN** la respuesta es `200` con estado `CERRADA`, la fecha de cierre, total cobrado `25000.00` y diferencia `2500.00`

#### Scenario: Cerrar con una línea pendiente
- **GIVEN** solo la línea de "Consulta o control" tiene resultado
- **WHEN** `admin` cierra la liquidación
- **THEN** la respuesta es `409` con el código `CONCILIACION_INCOMPLETA` y sigue `PRESENTADA`

#### Scenario: Cerrar un borrador o una cerrada
- **WHEN** `admin` cierra una liquidación en `BORRADOR` o una ya `CERRADA`
- **THEN** la respuesta es `409` con el código `LIQUIDACION_NO_PRESENTADA` o `LIQUIDACION_CERRADA`

#### Scenario: La base rechaza cambiar una liquidación cerrada
- **GIVEN** la liquidación de `2026-10` está `CERRADA`
- **WHEN** se intenta por SQL cambiar el monto cobrado de una línea o volver la liquidación a `PRESENTADA`
- **THEN** la base rechaza cada operación y los datos no cambian

### Requirement: Lo no cobrado no se le cobra al paciente
La conciliación MUST NOT generar cargos, cobros ni deudas del paciente ni avisos a nadie: la diferencia entre lo solicitado y lo cobrado solo se informa en la liquidación (RN-LQ-07).

#### Scenario: Línea rechazada sin efectos sobre el paciente
- **WHEN** `admin` registra `0.00` cobrados en la línea de "Limpieza (profilaxis)" con un motivo
- **THEN** no se crea ninguna notificación para "Paciente Uno" y la liquidación informa la diferencia de `12500.00`

### Requirement: Solo el administrador concilia
Registrar lo cobrado y cerrar la liquidación SHALL estar permitido solo al `ADMIN`; los demás roles MUST recibir `403` sin cambios.

#### Scenario: La recepción intenta conciliar
- **WHEN** `recepcion` registra `15000.00` cobrados en la línea de "Consulta o control" o cierra la liquidación
- **THEN** la respuesta es `403` y la liquidación no cambia

### Requirement: Conciliaciones simultáneas
Las operaciones sobre una misma liquidación MUST aplicarse de a una: si se cierra la liquidación mientras se registra lo cobrado de una línea, el resultado final MUST ser coherente con el orden en que se aplicaron.

#### Scenario: Cerrar mientras se concilia la última línea
- **GIVEN** solo la línea de "Consulta o control" tiene resultado
- **WHEN** al mismo tiempo `admin` registra `12500.00` en la limpieza y cierra la liquidación
- **THEN** o la conciliación se aplica primero y el cierre responde `200`, o el cierre responde `409 CONCILIACION_INCOMPLETA` y la conciliación se aplica; nunca queda `CERRADA` con una línea pendiente
