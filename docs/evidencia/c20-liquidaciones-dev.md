# C-20: liquidaciones a obras sociales, evidencia de desarrollo

Fecha: 2026-11-24. Rama `change/C-20-liquidaciones-apply`, desde `master` con el propose (PR #122). Gobernanza ALTO (dinero). Solo datos ficticios del seed de la KB: "Obra Social Ejemplo" con "Plan Base", "Paciente Uno" (DNI `99000001`, afiliado `OSE-000001`), los códigos `N-0101` y `N-0201` y los aranceles del seed.

## 1. Cómo se probó

- App en el puerto 8081 (`./gradlew bootRun`, perfil `dev` con el seed ficticio) contra una base aparte, `turnos_c20_dev`, de un contenedor PostgreSQL 16 descartable en el puerto 5433. Se aplicaron todas las migraciones, incluida `V14`.
- Cuenta ADMIN ficticia creada con `APP_ADMIN_USERNAME` y `APP_ADMIN_PASSWORD` generada para la corrida y no escrita en ningún archivo.
- Dos turnos `ATENDIDO` de "Paciente Uno" cargados por SQL: "Consulta o control" el 2026-10-06 y "Limpieza (profilaxis)" el 2026-10-20.
- Pedidos con `curl` (sesión por cookie `HttpOnly` y token CSRF), hoy 2026-11-24 en Buenos Aires.

## 2. Flujo de punta a punta

| Paso | Pedido | Resultado |
|---|---|---|
| Generar el mes en curso | `POST /api/v1/liquidaciones` con `2026-11` | `409 PERIODO_NO_CERRADO` |
| Generar octubre | `POST /api/v1/liquidaciones` con `2026-10` | `201`, `BORRADOR`, 2 líneas, total solicitado `27500.00`, `turnosSinArancel` `0` |
| Generar octubre otra vez | igual | `409 LIQUIDACION_EXISTENTE` |
| CSV en borrador | `GET /{id}/csv` | `409 LIQUIDACION_NO_PRESENTADA` |
| Presentar | `POST /{id}/presentar` | `200`, `PRESENTADA`, total solicitado `27500.00`, 2 líneas pendientes |
| Conciliar la consulta | `PUT /{id}/lineas/{linea}/conciliacion` con `15000.00` | `200`, `COBRADA`, total cobrado `15000.00`, 1 pendiente |
| Rechazar la limpieza | con `0.00` y un motivo | `200`, `RECHAZADA` con su motivo, total cobrado `15000.00`, diferencia `12500.00` |
| Corregir la limpieza | con `10000.00` | `200`, `PARCIAL`, total cobrado `25000.00`, diferencia `2500.00`, 0 pendientes |
| Monto mayor que el solicitado | con `15000.01` en la consulta | `422 MONTO_COBRADO_INVALIDO` |
| Cerrar | `POST /{id}/cerrar` | `200`, `CERRADA`, total cobrado `25000.00`, diferencia `2500.00` |
| Listar | `GET /api/v1/liquidaciones?obraSocialId=1` | `200`, una fila con totales y sin datos de pacientes |

La respuesta de generar trae el detalle agrupado por plan: el grupo "Plan Base" con subtotal `27500.00` y las dos líneas (consulta `15000.00` al 100 %, limpieza `12500.00` al 50 %, origen `ARANCEL`).

## 3. CSV de la liquidación (Obra Social Ejemplo, 2026-10)

Encabezados HTTP: `200`, `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="liquidacion-1-2026-10.csv"`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.

Archivo (`cat -A`: `M-oM-;M-?` es el BOM UTF-8, `^M$` es el fin de línea `\r\n`):

```
M-oM-;M-?fecha;paciente;dni;numero_afiliado;plan;codigo;prestacion;monto_solicitado^M$
2026-10-06;Paciente Uno;99000001;OSE-000001;Plan Base;N-0101;Consulta o control;15000.00^M$
2026-10-20;Paciente Uno;99000001;OSE-000001;Plan Base;N-0201;Limpieza (profilaxis);12500.00^M$
Total;;;;;;;27500.00^M$
```

El formato también se verifica byte por byte en `ExportarCsvIntegrationTest` y en `CsvLiquidacionTest`.

## 4. La base protege lo que la API protege

Con la liquidación `CERRADA`, por SQL directo (sin pasar por la API):

```
UPDATE liquidacion_linea SET monto_cobrado = 12500.00, resultado = 'COBRADA' WHERE id = 2
ERROR:  Una liquidación cerrada no cambia
DELETE FROM liquidacion WHERE id = 1
ERROR:  Una liquidación no se borra
```

En la base: `CERRADA | 27500.00 | 25000.00`, y la suma de las líneas coincide (`27500.00` solicitado, `25000.00` cobrado).

## 5. Privacidad (RN-LQ-08)

En el log de la app durante todo el flujo hay 0 coincidencias con `99000001`, `OSE-000001`, `Paciente Uno`, `Consulta o control` o `15000`. Los errores `409` y `422` traen mensajes fijos sin datos del paciente ni importes.

## 6. Suite de tests (antes y después)

| Suite | Antes (master) | Después |
|---|---|---|
| `./gradlew clean build` (unitarias) | 837 | 946 |
| `./gradlew integrationTest` (PostgreSQL 16 real) | 929 | 1077 |

Sin fallas ni omitidos. Los 72 escenarios de las dos specs (51 de `liquidaciones-obras-sociales` y 21 de `conciliacion-liquidaciones`) tienen su test con el `@DisplayName` igual al nombre del escenario.

## 7. Lo que queda para después

- El adaptador del cargo de caja de C-22 (`CargosDeTurnoJdbc`): hoy todas las líneas usan el arancel (`origen_precio = 'ARANCEL'`); el puerto `CargosDeTurno` ya está probado con un adaptador de prueba.
- Las pantallas de liquidaciones (ficha de frontend aparte, con su gate de mockups).
