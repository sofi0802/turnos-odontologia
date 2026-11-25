# Design: caja-y-cobros (C-22)

## Context

- **KB aprobada** (PR #118): RN-CA-01 a RN-CA-08, US-207, DD-18 y PA-32 a PA-37. Ver proposal.md (Why). Governance CRÍTICO: el apply empieza solo con la aprobación explícita de esta propuesta.
- **Paso a `ATENDIDO`**: lo hace `agenda/` (Sofia). Sucede de dos formas:
  - automáticamente, en `PasarAAtendidos` (`TareaAsistencia`, cada 5 minutos), con los turnos que tienen la llegada registrada y ya terminaron;
  - en `RegistrarLlegada`, cuando la recepción corrige un turno sin registrar.
  `agenda/` no publica eventos.
- **Copago en C-19** (`coberturas/`, Juan Sebastián):
  - `CalculoItem.de(Precio, cantidad, Porcentaje)` calcula importe, cobertura y copago con `HALF_UP` a centavos.
  - El precio sale de `arancel`, el porcentaje de `cobertura_plan` (plan y código del nomenclador) y el plan de la afiliación vigente en una fecha (`afiliacion_paciente`, con `vigente_desde` y `vigente_hasta`).
- **Migraciones**: V15 está reservada para C-22. Flyway corre con `out-of-order: true`, así que V15 se aplica aunque ya exista V22.
- **Infraestructura disponible**:
  - Los beans `Clock reloj` y `ZoneId zonaHoraria` (`AgendaConfig`).
  - `@EnableScheduling` (ya lo usan `TareaAsistencia` y `TareaRecordatorios`).
  - `@EnableMethodSecurity`: cada módulo restringe con `@PreAuthorize`.
- **Frontend**:
  - `Pagina.roles` y `Pagina.avisoSinRol` (C-44, C-32).
  - El buscador de pacientes de C-36 (`agenda/buscador-pacientes.ts`).
  - El diálogo uniforme de C-39.

## Goals / Non-Goals

**Goals:**
- Que la deuda de un turno atendido aparezca sola, sin cambiar `agenda/` ni depender de que alguien la cargue.
- Que las tres garantías de dinero valgan también con pedidos simultáneos:
  - una sola caja abierta;
  - no cobrar más que el saldo;
  - no cobrar en una caja cerrada.
- Que el copago del cargo sea el mismo que daría un presupuesto de C-19 para esa prestación.

**Non-Goals:**
- Señas y pagos en línea (C-23), facturas (C-24), liquidaciones (C-20), saldo a favor, cobros sin turno y anulación de cargos.
- Que el paciente vea su cuenta corriente.

## Decisions

### D1. Módulo `caja/` en capas
- `caja/domain`: `Dinero` (importe con escala 2, mayor que cero o mayor o igual a cero según el uso), `MedioPago`, `Arqueo` (esperado, contado, diferencia y motivo obligatorio si la diferencia no es cero), `Tarifa` (precio, porcentaje y afiliación) y `CalculoCargo`, que arma el cargo con `CalculoItem` de C-19.
- `caja/application`: los casos de uso `AbrirCaja`, `CerrarCaja`, `ConsultaCaja`, `GeneradorCargos`, `Cobrar`, `AnularCobro` y `CuentaCorriente`; los puertos `Cajas`, `Cargos`, `Cobros` y `TurnosACobrar`; y las excepciones con código.
- `caja/infrastructure`: implementaciones con `JdbcTemplate`, `TareaCargos` (`@Scheduled`) y `CajaConfig`.
- `caja/api`: controladores, records de pedido y respuesta, y `CajaExceptionHandler`.

### D2. Los cargos nacen por barrido, sin tocar `agenda/`
`GeneradorCargos.generarPendientes()` busca los turnos `ATENDIDO` sin cargo cuya prestación tiene arancel y les crea el cargo. Lo llama:
- `TareaCargos`, cada `app.caja.cargos.intervalo-ms` (por defecto 5 minutos; los tests de integración lo llaman a mano, como C-43 con la asistencia);
- los casos de uso de pendientes, cuenta corriente y cobro, al empezar. Así la recepción ve la deuda apenas el turno queda atendido.

El insert usa `ON CONFLICT (turno_id) DO NOTHING`. Si el barrido y una consulta corren a la vez, cada turno conserva un solo cargo.

Los turnos atendidos sin arancel no se insertan. Se listan como `sinPrecio` y el barrido los toma cuando se carga el arancel (RN-CA-03).

*Alternativas descartadas*:
1. Que `agenda/` publique un evento al pasar a `ATENDIDO`. Obliga a cambiar el módulo de Sofia en dos casos de uso. Además, el cargo de un turno sin precio igual necesitaría un barrido posterior.
2. Un trigger en la base. Deja la regla del copago en SQL, lejos del cálculo de C-19 y difícil de probar.

### D3. Tarifa del turno: una consulta que reproduce C-19
`TurnosACobrarJdbc` lee, por cada turno atendido sin cargo, los datos de la tarifa:
- el arancel de la prestación;
- la afiliación del paciente vigente en la fecha del turno (en la zona del consultorio);
- el primer código activo del nomenclador de la prestación, en orden de código;
- el porcentaje de `cobertura_plan` para ese plan y ese código, o 0 si no hay fila.

`CalculoCargo` llama a `CalculoItem.de(precio, 1, porcentaje)` de `coberturas/domain`, que es puro, y guarda la foto (DD-18).

Con varios códigos activos se toma el primero en orden de código, porque nadie lo elige al atender. Es una regla nueva respecto de la KB y queda en la spec ("Cobertura sin código del nomenclador").

### D4. Concurrencia
- **Una caja abierta**: índice único parcial `caja_una_abierta` sobre `((true)) WHERE cerrada_en IS NULL`. `AbrirCaja` primero busca la abierta, para responder con su `id`. Si dos aperturas compiten, la violación del índice se traduce a `409 CAJA_YA_ABIERTA`.
- **Cobro**:
  - En una transacción, `SELECT … FROM caja WHERE cerrada_en IS NULL FOR SHARE`; si no hay, `SIN_CAJA_ABIERTA`.
  - Después, `SELECT … FROM cargo WHERE id = ? FOR UPDATE` y la suma de sus cobros no anulados. Si el importe supera el saldo, `IMPORTE_SUPERA_SALDO`. Si no, se inserta el cobro.
  - El bloqueo del cargo serializa los cobros de un mismo cargo.
- **Cierre**: `SELECT … FROM caja WHERE id = ? FOR UPDATE`. Espera a los cobros en curso, que tienen la caja con `FOR SHARE`, y un cobro que llega después ya no la encuentra abierta.
- **Anulación**: bloquea la caja del cobro con `FOR SHARE` y el cobro con `FOR UPDATE`. Si la caja está cerrada responde `CAJA_CERRADA`; si el cobro ya está anulado, `COBRO_ANULADO`.

### D5. Migración V15
Archivo `V15__caja_y_cobros.sql`:
- **Tablas**: `caja`, `cargo` (con `turno_id` único y FK a `turno`, `paciente`, `prestacion` y `afiliacion_paciente`) y `cobro` (con FK a `cargo` y `caja`), con los CHECK de la KB (04).
- **Índices**: `cargo(paciente_id)`, `cobro(cargo_id)` y `cobro(caja_id)`.
- **Sin borrado**: nada tiene `ON DELETE CASCADE`. No se borra nada (RN-CA-07).

### D6. Dinero en la API
- Los importes son `BigDecimal` con escala 2.
- Las respuestas los serializan como número JSON (`5000.00`).
- Los pedidos aceptan número o texto (`"4000.00"`) y se validan con `@DecimalMin` y `@Digits(fraction = 2)`. El frontend los manda como texto con dos decimales para no perder precisión.

### D7. Permisos y errores
- `@PreAuthorize` por método:
  - abrir, cerrar y cobrar: `hasRole('RECEPCIONISTA')`;
  - anular: `hasRole('ADMIN')`;
  - consultas: `hasAnyRole('ADMIN','RECEPCIONISTA')`.
- `CajaExceptionHandler` (paquete `caja`) traduce:
  - las reglas a `409` con código estable (`CAJA_YA_ABIERTA`, `CAJA_CERRADA`, `SIN_CAJA_ABIERTA`, `IMPORTE_SUPERA_SALDO`, `COBRO_ANULADO`);
  - las validaciones a `422 DATOS_INVALIDOS` con `errores` por campo;
  - lo inexistente a `404`.

### D8. Logs sin datos personales
Los casos de uso registran solo la acción, ids, el medio de pago y el usuario del personal (por ejemplo, "Cobro 21 registrado en la caja 3 por recepcion, medio DEBITO"). Nunca registran importes, deudas ni datos del paciente (RN-CA-08). Un test de integración captura el log.

### D9. Respuestas
- **Caja**: `{ id, fecha, montoInicial, abiertaPor, abiertaEn, cerradaPor, cerradaEn, totales: { EFECTIVO, DEBITO, CREDITO, TRANSFERENCIA }, efectivoEsperado, efectivoContado, diferencia, motivoDiferencia, cobros: [...] }`. Los campos del cierre son `null` mientras está abierta. `cobros` viene solo en la caja abierta y en el detalle.
- **Cobro**: `{ id, cargoId, paciente: "Uno, Paciente", hora, medio, importe, anulado, anuladoPor, motivoAnulacion }`.
- **Pendientes**: `{ cargos: [{ id, pacienteId, paciente, fecha, prestacion, copago, pagado, saldo }], sinPrecio: [{ turnoId, paciente, fecha, prestacion }] }`.
- **Cuenta corriente**: `{ pacienteId, paciente, cargos: [{ id, fecha, prestacion, precio, porcentajeCobertura, cobertura, copago, pagado, saldo, cobros: [...] }], saldoTotal }`.

Son records de `caja/api`, nunca entidades.

### D10. Frontend
- **Página**: `pages/caja.ts` (`ruta: 'caja'`, `roles: ['ADMIN', 'RECEPCIONISTA']`, `avisoSinRol` como Reportes, ícono nuevo `'caja'`), registrada después de Reportes.
- **Módulo `frontend/src/caja/`**:
  - `api.ts`: lectura de las respuestas sin `any`.
  - `dinero.ts`: `formatearPesos` con `Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })`, y `leerImporte`, que acepta "4000", "4000,50" o "4.000,50" y devuelve `"4000.50"`.
  - `estado.ts`: el bloque de la caja, la apertura y el cierre.
  - `pendientes.ts`: los pendientes y el cobro.
  - `cuenta-corriente.ts` e `historial.ts`.
- **Roles**: lo que solo hace un rol ("Abrir caja", "Cobrar" y "Cerrar caja" para la recepción; "Anular" para el admin) no se dibuja para el otro. La API igual rechaza con `403`.
- **Recarga**: después de cada acción y de cada `409`, se vuelven a pedir la caja abierta y los pendientes.

### D11. Tests
- **Unitarios del dominio**: `Dinero`, `Arqueo` y `CalculoCargo` (cobertura, particular, sin código, 100 %).
- **Integración** (`@Tag("integration")`, PostgreSQL real): un `@Test` por escenario de `caja` y `cobros-pacientes`. Los de concurrencia usan dos hilos con un `CountDownLatch`, como `CarreraSobreturnoIntegrationTest`. El de logs usa `OutputCaptureExtension`.
- **Frontend**: Vitest con `ServidorFalso`, un `it` por escenario de `frontend-caja` y de `frontend-navegacion`.

## Risks / Trade-offs

- [Hasta 5 minutos sin cargo si nadie consulta] → Pendientes, cuenta corriente y cobro barren antes de responder, así que la recepción nunca ve un estado viejo. La tarea solo cubre a quien no consulta.
- [`CalculoItem` cambia en C-19] → C-22 lo reusa a propósito para que el copago coincida. Un test de integración compara el cargo con un presupuesto de la misma prestación.
- [Elegir el primer código con varios códigos activos puede no ser el que hubiera elegido el odontólogo] → Queda en la spec y en "Para revisar" del PR. Si hace falta, un change posterior deja elegir el código al atender.
- [Un cobro de una caja cerrada no se puede anular] → Lo pide la KB (RN-CA-07). Si después hace falta, se corrige con un change sobre cargos o con notas de crédito (C-24).
- [Lectura de tablas de otros módulos] → Solo lectura por JDBC, sin mapearlas con JPA. Se avisa a Sofia y a Juan Sebastián en el PR.

## Migration Plan

- Se despliega con V15 y el build del frontend.
- Para volver atrás antes de usar la caja, se revierte el PR y se agrega una migración nueva que borra las tres tablas. Nunca se edita V15 ya mergeada.
- Con datos cargados no se borra nada: se apaga la pantalla y se conservan los registros.

## UI Mockups

- Design system revision: 5 (aprobada el 2026-11-24 por Sofia Indovino)
- Decision: yes (2026-11-24, política `system-and-mockups`)
- Mockups: `openspec/changes/caja-y-cobros/mockups/` (ver su README)
- Approval: approved (2026-11-24, by user: Enzo aprobó explícitamente la propuesta y los 6 mockups de la revisión 1; governance CRÍTICO)
- Preview: rendered (2026-11-24, 6 pantallas en `mockups/previews/` a 390x844 y 1440x900, sin desplazamiento horizontal ni paneles desbordados)
- Visual verify: passed (2026-11-24, después de corregir dos diferencias; capturas en `mockups/verify/`)

### Verificación visual (tarea 5.3)

Backend de desarrollo con el código de C-22 (puerto 8081, V15 aplicada), Vite en el 5173 y solo datos ficticios
(Paciente Dos particular, Paciente Tres con cobertura; turnos `ATENDIDO` cargados a mano en la base local). Se
recorrieron las 6 pantallas con las sesiones de recepción y de admin, a 1440x900 y 390x844; la consola y la red no
mostraron errores de la app.

| Pantalla | 1440 | 390 | Resultado |
|---|---|---|---|
| Sin caja (recepción) | `caja-sin-abrir-1440.jpg` | `caja-sin-abrir-390.jpg` | Coincide; pendientes sin "Cobrar" |
| Sin caja (admin) | `caja-sin-abrir-admin-1440.jpg` | `caja-sin-abrir-admin-390.jpg` | Coincide; sin "Abrir caja" |
| Caja abierta (recepción) | `caja-abierta-1440.jpg` | `caja-abierta-390.jpg` | Coincide; totales, efectivo esperado y "Cobrar" |
| Caja abierta (admin) y cuenta corriente | `caja-abierta-admin-1440.jpg` | `caja-abierta-admin-390.jpg` | Coincide tras las correcciones; "Anular", cobro anulado tachado |
| Cobrar (parcial, débito) | `caja-cobrar-1440.jpg` | — | Coincide tras las correcciones |
| Cerrar con diferencia e historial | `caja-cerrar-1440.jpg`, `caja-historial-1440.jpg` | — | Coincide; motivo obligatorio y "Diferencia: -$ 500,00 (Faltante sin explicar)" |

Diferencias encontradas:

- **Mayor, corregida**: a 390 px la tabla de la cuenta corriente ensanchaba la página (485 px) en vez de desplazarse
  dentro de su panel. `.caja-grilla` y `.caja-grilla .panel` usaban una columna implícita `auto`; ahora usan
  `minmax(0, 1fr)` (`21-caja.css`). Venía del `delta.css` del mockup y no se veía con los datos cortos de las capturas.
- **Menor, corregida**: faltaban las ayudas "El efectivo con el que arranca el día." y "Podés cobrar una parte; lo que
  falta queda como deuda." (ahora enlazadas con `aria-describedby`).
- **Menor, corregida**: la columna "Turno" de la cuenta corriente mostraba "martes 24/11 09:00"; ahora "24/11".
- **Menor, aceptada**: la búsqueda de la cuenta corriente reusa el buscador de pacientes de C-36 (lupa y ayuda "Buscá
  por DNI o por nombre y apellido.") en vez del placeholder del mockup.
- **Menor, aceptada**: el historial dice "recepcionista abrió · recepcionista cerró" y "martes 24/11" donde el mockup
  dice "recepcion abrió y cerró" y "Viernes 04/12"; el spec no fija esos textos.

No verificado en el navegador: el grupo "Sin precio" (todas las prestaciones de la base local tienen arancel); lo
cubre el test del escenario "Turno sin precio".
