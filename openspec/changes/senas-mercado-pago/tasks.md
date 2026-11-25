# Tasks

Acceptance criteria: los escenarios de los deltas `senas`, `frontend-senas`, `cobros-pacientes`, `agenda-api`, `seguridad-autorizacion`, `turnos-paciente` y `reserva-online`, y el mockup aprobado en `mockups/` (ver `design.md`, sección UI Mockups). Governance CRÍTICO: el grupo 1 empieza solo con la aprobación explícita de la propuesta y de los mockups, y con V23 confirmada por la líder.

## 0. Antes del apply

- [x] 0.1 Mockups con los tokens de la Revision 5, a 390 y 1440 px, con capturas en `mockups/previews/`: sección "Señas" de Configuración (normal y con error), confirmación de la reserva con seña (sin link se ve igual), "Mis turnos" con los cinco estados de la seña y el aviso de vuelta de Mercado Pago, y el diálogo "Cancelar turno" con "Causa" (normal y con el error de la causa)
- [x] 0.2 Aprobación explícita de la propuesta y de los mockups registrada en `design.md` (`Approval: approved`)
- [x] 0.3 La líder confirma la migración V23 en la tabla de dueños de `CHANGES.md`

## 1. Migración, dominio y configuración (backend)

- [x] 1.1 `V23__senas.sql` con `sena`, `sena_movimiento` y el cambio de `cobro` (design D8); test de integración de la migración: CHECK, índices únicos y `caja_id` nulo solo con `MERCADO_PAGO`
- [x] 1.2 Test primero: `CalculoSena` (particular, con cobertura del 40 %, redondeo, copago cero y sin arancel)
- [x] 1.3 Test primero: `ReglaFinal` (online, personal con 24 h o más, personal con menos de 24 h por causa del paciente y del consultorio, ausente)
- [x] 1.4 `ConfiguracionSenas` y `ConfiguracionSenasController` con `@PreAuthorize`; un `@Test` de integración por escenario de "Configurar las señas" y "Permisos de las señas"

## 2. Pasarela de pagos (backend)

- [x] 2.1 Puerto `PasarelaPago` y `PasarelaSimulada` con fallos programables (design D6)
- [x] 2.2 `PasarelaMercadoPago` con `RestClient`, probada con `MockRestServiceServer`: la preferencia sin datos del pagador ni la prestación, la consulta del pago y el reintegro con `X-Idempotency-Key`
- [ ] 2.3 Variables `PAGOS_PASARELA`, `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` y `PAGOS_URL_PUBLICA` en `.env.example` sin valores reales; el arranque falla con `mercadopago` sin credenciales

## 3. Notificación de Mercado Pago (backend)

- [x] 3.1 Confirmar con la documentación vigente de Mercado Pago (context7) el formato de `x-signature` y su manifiesto; test primero de `FirmaMercadoPago` (válida, alterada, vacía y con otra clave)
- [ ] 3.2 `NotificacionesMercadoPagoController` y `NotificacionDePago` con idempotencia y pago tardío (design D5); un `@Test` por escenario de "Pago confirmado por la notificación de Mercado Pago" y "Notificaciones inválidas o repetidas"
- [ ] 3.3 Excepción de CSRF de la ruta exacta en `SecurityConfig` (avisa a Juan Sebastián, design D7); un `@Test` por escenario del delta `seguridad-autorizacion`

## 4. Reserva y "Mis turnos" (backend, `reserva/` de Juan Sebastián)

- [ ] 4.1 Puerto `SenasDeReserva` y `CrearSena` (design D3); `ReservarTurno` crea la seña y `TurnoPacienteResponse` suma `sena`; un `@Test` por escenario de "Seña al reservar online", "Turnos que no piden seña", "Link de pago y datos para Mercado Pago" y del delta `reserva-online`, incluida la reserva simultánea
- [ ] 4.2 `GET /api/v1/paciente/turnos` con `sena` y `POST /api/v1/paciente/reservas/{numeroReserva}/sena/link`; un `@Test` por escenario del delta `turnos-paciente`

## 5. Ciclo de vida de la seña (backend)

- [ ] 5.1 `RevisionDeSenas` y `TareaSenas` con `FOR UPDATE SKIP LOCKED` (design D4): vencimiento y reprogramación; un `@Test` por escenario de "Vencimiento de la seña" y "Reprogramación de un turno con seña"
- [ ] 5.2 Causa de la cancelación en `agenda/` (avisa a Sofia): puerto `SenasDeAgenda`, `causa` en `CancelarTurnoCommand` y en el detalle de la auditoría; un `@Test` por escenario del delta `agenda-api`
- [ ] 5.3 Reintegro y retención según cómo terminó el turno; un `@Test` por escenario de "Cancelación de un turno con seña pagada"
- [ ] 5.4 Imputación al cargo: `ServicioCobros.imputarSena`, cobro `MERCADO_PAGO` sin caja, rechazo del medio en el cobro manual y `COBRO_SIN_CAJA` al anular; un `@Test` por escenario de "Imputación de la seña al cargo" y del delta `cobros-pacientes`
- [ ] 5.5 Reintegros con reintento e idempotencia; un `@Test` por escenario de "Reintegros sin duplicar"
- [ ] 5.6 `AvisoReintegroSena` en `notificaciones/` (avisa a Juan Cruz) y logs sin datos (design D10); un `@Test` por escenario de "Privacidad de las señas"
- [ ] 5.7 `./gradlew build integrationTest` en verde, con `cleanTest cleanIntegrationTest`; evidencia en `docs/evidencia/c23-backend.txt`

## 6. Frontend

- [ ] 6.1 Test primero, un `it` por escenario de "Sección Señas en Configuración"; `configuracion/senas.ts` y la pestaña solo para el `ADMIN`
- [ ] 6.2 Test primero, un `it` por escenario de "Seña al terminar la reserva"; bloque de la seña en `pages/reserva.ts`
- [ ] 6.3 Test primero, un `it` por escenario de "Seña en Mis turnos" y "Vuelta desde Mercado Pago"; estado y "Pagar seña" en `pages/mis-turnos.ts`
- [ ] 6.4 Test primero, un `it` por escenario de "Causa al cancelar desde la agenda"; "Causa" en el diálogo de cancelación
- [ ] 6.5 Estilos en `src/styles/23-senas.css`, registrado en `test/estilos.ts` y `test/estilos-modulos.test.ts`, solo con tokens del design system

## 7. Verificación

- [ ] 7.1 Cada escenario de los siete deltas tiene exactamente un test con su nombre (`grep`)
- [ ] 7.2 `npx tsc --noEmit`, `npm test` y `npm run build` en verde; la cantidad de tests no baja
- [ ] 7.3 Verificación visual contra el mockup aprobado, a 390 y 1440 px, con el backend de desarrollo, la pasarela simulada y datos ficticios; resultado en `design.md`
- [ ] 7.4 `openspec validate senas-mercado-pago --strict` en verde, C-23 marcado en `CHANGES.md` y, en el PR, aviso a Juan Sebastián (`reserva/`, `shared/security/`), a Sofia (`agenda/`) y a Juan Cruz (`notificaciones/`)

## Workflow follow-up

- Archive del change (`openspec archive senas-mercado-pago`) después del merge, en un PR aparte.
