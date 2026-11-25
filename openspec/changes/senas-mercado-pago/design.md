# Design: senas-mercado-pago (C-23)

## Context

- La reserva online (`reserva/`, Juan Sebastián) escribe solo por `AgendaDeTurnos`, y después de crear el turno avisa por el puerto `AvisoReservaOnline`. La respuesta es `TurnoPacienteResponse`, que hoy no tiene datos de pago.
- La agenda (`agenda/`, Sofia) cancela con `CancelarTurnoCommand(turnoId, dni, numeroReserva)` y registra `CANCELAR` en `TurnoAuditoria` sin detalle. Al reprogramar registra `REPROGRAMAR` con el id del turno nuevo como detalle (`ServicioAgenda`, design D10 de C-06).
- La caja (`caja/`, Enzo) genera los cargos con un barrido (`ServicioCobros.generarCargos`, D2 de C-22). `cobro.caja_id` es `NOT NULL` y `medio` admite cuatro valores (V15).
- Seguridad (`shared/security/`, Juan Sebastián): `/api/v1/public/**` es `permitAll`, y CSRF está activo para toda la API con `CookieCsrfTokenRepository` y `SpaCsrfTokenRequestHandler`.
- Las integraciones externas van detrás de puertos y se simulan en el TP (02 de la KB). No hay credenciales de Mercado Pago en el repositorio.

## Goals / Non-Goals

**Goals:**
- Seña de la reserva online con vencimiento, pagada solo por la notificación verificada de Mercado Pago, con reintegro, retención, traslado al reprogramar e imputación al cargo (RN-SE-01 a RN-SE-12).
- Ningún módulo ajeno depende de `pagos/`: `reserva/` y `agenda/` definen puertos que `pagos/` implementa.
- Una pasarela simulada para los tests y el desarrollo, y un adaptador de Mercado Pago en sandbox.
- La excepción de CSRF acotada a una sola ruta (PA-64).

**Non-Goals:**
- La seña en la agenda del personal (PA-65), señas en turnos del personal o en sobreturnos.
- Cobros de caja con Mercado Pago, pagos con tarjeta guardada, credenciales de producción, facturación (C-24).
- Una pantalla del personal para ver o forzar reintegros: son automáticos y quedan en `sena_movimiento`.

## Decisions

### D1. Módulo `pagos/` en capas
- `pagos/domain`: `Sena`, `EstadoSena`, `CalculoSena` (porcentaje del copago, redondeo `HALF_UP` a dos decimales), `ReglaFinal` (decide reintegro o retención según cómo terminó el turno) y `FirmaMercadoPago` (verificación de la firma, sin dependencias de Spring).
- `pagos/application`: `ConfiguracionSenas`, `CrearSena`, `NotificacionDePago`, `RevisionDeSenas`, `NuevoLinkDePago` y los puertos `PasarelaPago`, `Senas` (persistencia) y `AvisoReintegroSena`.
- `pagos/infrastructure`: `SenasJdbc`, `TareaSenas`, `PasarelaSimulada`, `PasarelaMercadoPago` y `PagosConfig`.
- `pagos/api`: `ConfiguracionSenasController`, `NotificacionesMercadoPagoController` y `PagosExceptionHandler`.

Las reglas puras (cálculo, decisión final y firma) se prueban sin base; los casos de uso, con PostgreSQL real.

### D2. Puntos de contacto con otros módulos
| Módulo | Qué cambia | Cómo, sin depender de `pagos/` |
|---|---|---|
| `reserva/` (Juan Sebastián) | La reserva crea la seña y la respuesta la informa; "Mis turnos" informa la seña; endpoint del link nuevo | Puerto `SenasDeReserva` en `reserva/application` (`crearPara(turno, pacienteId)`, `deTurnos(ids)`, `nuevoLink(turnoId)`), implementado en `pagos/`. `TurnoPacienteResponse` suma `sena` |
| `agenda/` (Sofia) | La cancelación del personal acepta `causa` y la guarda en el detalle de la auditoría; es obligatoria con la seña pagada | Puerto `SenasDeAgenda` en `agenda/application` (`tieneSenaPagada(turnoId)`), implementado en `pagos/`. `CancelarTurnoCommand` suma `causa` |
| `caja/` (Enzo) | Cobro `MERCADO_PAGO` sin caja, que no se anula ni se registra a mano | `ServicioCobros.imputarSena(cargoId, importe)` con el cargo bloqueado `FOR UPDATE` |
| `notificaciones/` (Juan Cruz) | Aviso de reintegro sin importe y con consentimiento | Implementa `AvisoReintegroSena` de `pagos/` con el canal y el consentimiento que ya usa (RN-NO-02, RN-NO-03) |
| `shared/security/` (Juan Sebastián) | Excepción de CSRF de una sola ruta | D7 |

`pagos/` sí depende de `agenda/application` (para cancelar un turno vencido con el actor `sistema`) y de `caja/application` (para imputar). Los dos son contratos públicos de esos módulos.

### D3. La seña al reservar
`ReservarTurno` llama a `SenasDeReserva.crearPara` después de `agenda.crear`, fuera de la transacción de la agenda, como el aviso actual.
- `CrearSena` lee la configuración y calcula el copago con la tarifa de C-22 (`TurnosACobrar`/`Tarifa`) en la fecha del turno. Si las señas están apagadas, el copago es cero o no hay arancel, devuelve "sin seña".
- Inserta la seña `PENDIENTE` en su propia transacción y **después** pide el link a la pasarela. Si la pasarela falla, la seña queda sin link (spec `senas`) y el paciente pide otro con `POST …/sena/link`.
- Si falla el insert de la seña (la base), `ReservarTurno` cancela el turno recién creado con el actor `sistema` y responde `503 SENA_NO_DISPONIBLE`. Así no queda un turno online sin la seña que pedía la configuración.

### D4. Revisión periódica de las señas
`TareaSenas` corre cada `app.pagos.senas.intervalo-ms` (por defecto 60 000; los tests llaman a `RevisionDeSenas.ejecutar()` a mano). Cada seña se procesa en su propia transacción con `SELECT … FOR UPDATE SKIP LOCKED`, así no choca con la notificación ni con otra instancia. En cada pasada:
1. **Vencer**: `PENDIENTE` con `vence_en` pasado → cancela el turno por `agenda/` con el actor `sistema` y la seña pasa a `VENCIDA`. Una `PENDIENTE` cuyo turno ya está `CANCELADO` también pasa a `VENCIDA`.
2. **Reprogramar**: `PENDIENTE` o `PAGADA` cuyo turno tiene `REPROGRAMAR` en la auditoría → `turno_id` pasa al turno nuevo (el detalle), con un movimiento `REPROGRAMACION`.
3. **Terminar**: `PAGADA` con turno `CANCELADO` o `AUSENTE` → `ReglaFinal` con el actor y la fecha de `CANCELAR`, su `causa` y el inicio del turno:
   - actor online (prefijo de `ActorOnline`) → reintegro;
   - personal con 24 h o más → reintegro;
   - personal con menos de 24 h → reintegro si la causa es `CONSULTORIO`, retención si es `PACIENTE`;
   - `AUSENTE` → retención.
4. **Imputar**: `PAGADA` cuyo turno tiene cargo → `imputarSena` por `min(seña, copago)`; el sobrante va a reintegro. Con copago cero no hay cobro y se reintegra todo.
5. **Reintegrar**: toda seña con `importe_a_reintegrar > importe_reintegrado` pide el reintegro a la pasarela con la clave de idempotencia `sena-{id}-{motivo}`. Un error deja un movimiento `REINTEGRO_FALLIDO` y se reintenta en la próxima pasada.

Llamar al barrido de cargos de C-22 antes del paso 4 evita esperar a la tarea de caja.

### D5. Notificación de Mercado Pago
`POST /api/v1/public/pagos/mercadopago/notificaciones`:
1. **Firma**: verifica el encabezado `x-signature` (`ts` y `v1`) contra el HMAC-SHA256 del manifiesto `id:{data.id};request-id:{x-request-id};ts:{ts};` con `MERCADOPAGO_WEBHOOK_SECRET`, comparando en tiempo constante. Sin firma o con firma inválida → `401 FIRMA_INVALIDA`. El formato exacto se confirma contra la documentación vigente de Mercado Pago al implementar (tarea 3.1).
2. **Idempotencia**: inserta el movimiento `NOTIFICACION` con `id_externo` = id del pago, `ON CONFLICT DO NOTHING`. Si ya existía, responde `200` sin más.
3. **Consulta**: pide el pago a la pasarela (estado, `external_reference` e importe). Solo `approved`, con la referencia de una seña y el importe igual pasa a `PAGADA` con `pago_externo_id` y `pagada_en`.
4. **Pago tardío**: si la seña está `VENCIDA` o su turno cancelado, registra el pago y fija el reintegro completo; la revisión lo pide (D4, paso 5).

Responde `200` a toda notificación con firma válida, para que Mercado Pago no reintente sin fin.

### D6. Puerto `PasarelaPago` y adaptadores
- `crearLink(referencia, importe, descripcion, vence)`, `consultarPago(idPago)` y `reintegrar(idPago, importe, claveIdempotencia)`.
- **`PasarelaSimulada`** (`PAGOS_PASARELA=simulada`, por defecto): guarda en memoria los links, pagos y reintegros; los tests le programan fallos y aprobaciones. Devuelve links a `#/mis-turnos?pago=simulado`.
- **`PasarelaMercadoPago`** (`PAGOS_PASARELA=mercadopago`): `RestClient` contra la API de Checkout Pro (preferencias, pagos y reintegros) con `MERCADOPAGO_ACCESS_TOKEN` de sandbox, `notification_url` en `PAGOS_URL_PUBLICA`, vuelta a `#/mis-turnos?pago=…` y el encabezado `X-Idempotency-Key` en los reintegros. Sin SDK, para no sumar una dependencia. La preferencia lleva un solo ítem "Seña de turno", `external_reference` = `sena.referencia` y `date_of_expiration` = `vence_en`. No lleva datos del pagador (RN-SE-11).
- El arranque falla si `PAGOS_PASARELA=mercadopago` y falta alguna credencial.

### D7. Excepción de CSRF de una sola ruta
En `SecurityConfig`, `csrf.ignoringRequestMatchers(PathPatternRequestMatcher` de `POST /api/v1/public/pagos/mercadopago/notificaciones`), coincidencia exacta, sin comodines. El resto de la configuración de CSRF no cambia. Un test de integración prueba que esa ruta entra sin token y que `POST /api/v1/public/confirmaciones/{token}` y otra ruta bajo `/public/pagos/` siguen pidiéndolo (spec `seguridad-autorizacion`).

### D8. Migración V23
`V23__senas.sql` (número propuesto en la KB, a confirmar por la líder antes del apply):
- `sena` con los campos de la KB (04) más `importe_a_reintegrar numeric(12,2) NOT NULL DEFAULT 0` (lo que hay que devolver; `importe_reintegrado` es lo devuelto) y `link_pago varchar(500)` nullable.
  - Índice único parcial `sena_vigente_por_turno` sobre `turno_id` con `estado IN ('PENDIENTE','PAGADA')`.
  - Únicos `referencia` y `pago_externo_id`.
  - CHECK de la KB y `importe_reintegrado ≤ importe_a_reintegrar ≤ importe`.
- `sena_movimiento` con un índice único parcial `(tipo, id_externo) WHERE id_externo IS NOT NULL`.
- `cobro`:
  - reemplaza `cobro_medio_valido` por la versión con `MERCADO_PAGO`;
  - `caja_id` pasa a `DROP NOT NULL`;
  - se agrega el CHECK `(caja_id IS NULL) = (medio = 'MERCADO_PAGO')`.
  - No se edita V15.
- Sin `ON DELETE CASCADE`.

### D9. API, dinero y errores
- `GET|PUT /api/v1/senas/configuracion` con `@PreAuthorize("hasRole('ADMIN')")`. La configuración se guarda en `configuracion` (claves de la KB). Los límites `porcentaje` 1–100 y `plazoMinutos` 5–1440 son de la spec; el mínimo de 5 minutos evita que la seña venza antes de que el paciente llegue a Mercado Pago.
- `POST /api/v1/paciente/reservas/{numeroReserva}/sena/link` en `ReservasPacienteController` (rol `PACIENTE`, turno propio como el resto de la reserva).
- `sena` en `TurnoPacienteResponse`: `{ estado, importe, venceEn, linkPago }`.
- El dinero sigue D6 de C-22: `BigDecimal` con escala 2, número en JSON.
- Códigos de error:
  - `409`: `SENA_NO_PENDIENTE`, `COBRO_SIN_CAJA`;
  - `401`: `FIRMA_INVALIDA`;
  - `503`: `PASARELA_NO_DISPONIBLE`, `SENA_NO_DISPONIBLE`;
  - `422 DATOS_INVALIDOS`: con el campo `causa`, `porcentaje`, `plazoMinutos` o `medio`.

### D10. Logs y privacidad
Los logs registran solo la acción, el id y la referencia de la seña, y el estado del pago (por ejemplo, "Seña 12 pagada, referencia S-8K2P…"). Nunca registran importes, nombres, DNI ni la prestación. Los cuerpos de Mercado Pago no se loguean. Un test de integración captura los logs del pago y del reintegro (spec `senas`).

### D11. Frontend
- **`configuracion/senas.ts`**: pestaña "Señas" solo para el `ADMIN` (se suma en `pages/configuracion.ts` como "Usuarios"), con casilla, dos números y "Guardar".
- **`pages/reserva.ts`**: la confirmación muestra el bloque de la seña con "Pagar seña". Si hay `linkPago`, es un enlace que abre Mercado Pago en la misma pestaña; si no, un botón que pide el link nuevo.
- **`pages/mis-turnos.ts`**: el estado de la seña por turno, el aviso de vuelta (`?pago=` en el hash) y "Pagar seña" en las pendientes.
- **`agenda/`**: el diálogo de cancelación suma "Causa" como un grupo de radio opcional con su ayuda, y marca el `422` del campo `causa`.
- **Estilos**: módulo `23-senas.css` (el siguiente libre después de `22-caja.css`), registrado en `test/estilos.ts`, solo con los tokens del design system.
- Textos y montos con `caja/dinero.ts` (formato "$ 4.500,00").

### D12. Tests
- Un test por escenario de los siete deltas, con el nombre del escenario.
- Unitarios para `CalculoSena`, `ReglaFinal` y `FirmaMercadoPago`, incluidos casos de firma vieja, alterada y vacía.
- Integración con PostgreSQL real y `@Tag("integration")`, la pasarela simulada y un `Clock` fijo.
- `PasarelaMercadoPago` se prueba contra un servidor HTTP falso (`MockRestServiceServer`), nunca contra Mercado Pago.

## Risks / Trade-offs

- **Hasta un minuto de demora** en vencer una seña o reaccionar a una cancelación → el hueco se libera un poco después del plazo. La notificación sí es inmediata. El intervalo es configurable.
- **Mercado Pago cambia el formato de la firma** → la verificación está aislada en `FirmaMercadoPago`, y la tarea 3.1 la confirma contra la documentación vigente antes de implementarla.
- **Ruta pública sin CSRF** → aceptada por la líder (PA-64), con coincidencia exacta, firma, consulta del pago e idempotencia. Se revisa en el PR con Juan Sebastián, dueño de `shared/security/`.
- **Cambios en módulos ajenos** (reserva, agenda, notificaciones, seguridad) → son puertos y un campo nuevo, sin lógica de pagos en esos módulos. Se avisa a cada dueño en el PR.
- **Lectura de la auditoría de la agenda para decidir reintegros** → solo lectura por JDBC, como C-22. Si la agenda cambia el detalle de `REPROGRAMAR`, falla un test de integración de C-23.
- **Números de módulo de estilos sin reservar** (C-22 y C-47 chocaron en el 21) → se usa el 23 y, si otro change lo toma antes, se renumera al mergear.
- **V23 sin confirmar** → el apply no empieza hasta que la líder confirma el número en `CHANGES.md`.

## Migration Plan

- Se despliega con V23 y el build del frontend. Las señas quedan desactivadas, así que nada cambia hasta que el `ADMIN` las active.
- Para volver atrás, alcanza con desactivarlas en Configuración. Si hay que revertir el código, una migración nueva restaura la constraint de `cobro` solo si no hay cobros `MERCADO_PAGO`. Nunca se edita V23 ya mergeada.
- Las variables de `.env.example` se agregan sin valores; `PAGOS_PASARELA=simulada` por defecto.

## UI Mockups

- Design system revision: 5 (aprobada el 2026-11-24 por Sofia Indovino)
- Decision: yes (2026-11-25, política `system-and-mockups`)
- Mockups: `openspec/changes/senas-mercado-pago/mockups/` (ver su README)
- Approval: approved (2026-11-25, by user: Enzo aprobó explícitamente la propuesta y los 7 mockups de la revisión 1; governance CRÍTICO)
- Preview: rendered (2026-11-25, 7 pantallas en `mockups/previews/` a 390x844 y 1440x900, sin desplazamiento horizontal)
