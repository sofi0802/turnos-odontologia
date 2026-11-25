# Tasks

> Rama `change/C-17-consentimiento-comunicaciones`. Strict TDD: cada escenario GIVEN/WHEN/THEN de las specs tiene su test con el mismo nombre (RED → GREEN → REFACTOR). Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`. Backend con `./gradlew build` en verde; frontend con `cd frontend && npm test` y `npm run build`, TypeScript `strict` sin `any`. Solo datos ficticios ("Paciente Uno", "Paciente Dos", `recepcion`). Governance ALTO: la propuesta y los mockups se aprueban antes del apply.

## 1. Avisos y aprobación

- [x] 1.1 Avisar a Juan Sebastián (`usuarios/`: registro y puerto `ConsentimientoInicial`; `pages/mi-cuenta.ts`, que también toca C-49), a Sofia (C-15: `lista-espera/paciente.ts` y el diálogo "Anotar paciente") y a Enzo (`frontend/`). Verificar dejando el aviso en el PR de la propuesta (hecho: avisos a Juan Sebastián, Sofia y Enzo en la descripción del PR #142)
- [x] 1.2 Mockups de `mockups/` renderizados a 390 × 844 y 1440 × 900, con capturas en `mockups/previews/`, y aprobación de la líder registrada en `design.md` (`Approval: approved (<fecha>, by user)`). No empezar el grupo 6 sin esto (hecho: 12 capturas; aprobada por la líder al mergear el PR #142, 2026-11-25)

## 2. Migración V11

- [x] 2.1 `V11__consentimiento_comunicaciones.sql`: tabla `consentimiento_comunicacion_evento` con sus CHECK (origen, medio y usuario solo con `RECEPCION`), índice por `paciente_id` y trigger que rechaza `UPDATE` y `DELETE` (D1). Verificar con un test de integración que inserta un evento válido, rechaza uno de `RECEPCION` sin medio y rechaza un `DELETE` (hecho: `MigracionConsentimientoIntegrationTest`, 6 tests en verde)

## 3. Consentimiento en el backend

- [x] 3.1 Dominio y caso de uso `CambiarConsentimiento` (idempotente) y `ConsultarConsentimiento`, con el repositorio JDBC que bloquea al paciente e inserta el evento en la misma transacción (D1, D2). Verificar con los tests de "Paciente nuevo sin consentimiento", "Otorgar y revocar deja dos registros", "El historial no se pierde" y "Otorgar dos veces" (hecho: `CambiarConsentimientoTest` y `ConsentimientoRepositoryJdbc`; "Paciente nuevo sin consentimiento" va en `ConsentimientoApiIntegrationTest`)
- [x] 3.2 `GET` y `PUT /api/v1/paciente/consentimiento-comunicaciones`. Verificar con los tests de "El paciente otorga su consentimiento" y "El personal no usa el recurso del paciente" (hecho: `ConsentimientoApiIntegrationTest`)
- [x] 3.3 `GET` y `PUT /api/v1/pacientes/{id}/consentimiento-comunicaciones` con historial, `422 DATOS_INVALIDOS` y `404 RECURSO_NO_ENCONTRADO`. Verificar con los tests de "La recepción registra un consentimiento telefónico", "Falta el medio" y "Paciente inexistente" (hecho: `ConsentimientoApiIntegrationTest`)
- [x] 3.4 Acceso por rol, `401` sin sesión, CSRF en los `PUT` y respuesta sin datos de contacto ni logs con contacto. Verificar con los tests de "El odontólogo no gestiona el consentimiento", "Un paciente no consulta el de otro" y "La respuesta del personal no trae contacto" (hecho: `ConsentimientoApiIntegrationTest`, más 401 y CSRF)

## 4. Registro con consentimiento

- [x] 4.1 Puerto `ConsentimientoInicial` en `usuarios/application`, su adaptador en `notificaciones/infrastructure` y `aceptaMensajes` en `RegistroPacienteRequest` y `RegistrarPaciente` (D3). Verificar con los tests de "Registro aceptando mensajes", "Registro sin indicar mensajes" y "Un registro rechazado no deja consentimiento", más los tests existentes de `cuentas-pacientes` en verde (hecho: `RegistroConConsentimientoIntegrationTest` y `RegistrarPacienteTest`; los 12 tests de `RegistroPacienteIntegrationTest` siguen en verde)

## 5. Los avisos respetan la revocación

- [x] 5.1 Tests de integración de "Revocar corta los recordatorios", "Revocar corta los reintentos" y "El consentimiento registrado por la recepción habilita los avisos". Si un reintento todavía se envía, corregir `reintentosVencidos` o `EnviarRecordatorios` para que lean el consentimiento vigente. Verificar con `./gradlew build` (hecho: `RevocacionConsentimientoIntegrationTest`, 3 en verde sin cambiar código: los reintentos ya leen el consentimiento vigente)

## 6. Frontend

- [x] 6.1 API del consentimiento (`shared/api`) y mensajes de error. Verificar con tests de los pedidos y de las respuestas `422` y `404` (hecho: `consentimiento/api.ts` y `consentimiento-api.test.ts`)
- [x] 6.2 Secciones "Tus datos" y "Mensajes del consultorio" al principio de "Mi cuenta", antes de "Mi cobertura" (D4). Verificar con los tests de "El paciente ve sus datos" y "Falla al cargar el perfil", y que los tests de C-49 sobre "Mi cuenta" siguen pasando (hecho: `paciente/tus-datos.ts`; se actualizó "Paciente con sesión abre su cuenta" de C-49, que esperaba "Mi cobertura" como primer título)
- [x] 6.3 Aceptar y dejar de recibir mensajes. Verificar con los tests de "Mi cuenta sin consentimiento", "Mi cuenta con consentimiento", "Aceptar mensajes", "Dejar de recibir mensajes" y "Cancelar la confirmación" (hecho: `consentimiento/mensajes-paciente.ts`)
- [x] 6.4 Casilla del registro en `#/mi-cuenta`. Verificar con los tests de "Registro con la casilla marcada" y "Registro sin marcar la casilla", y que los tests existentes del registro siguen pasando (hecho: se actualizaron "Registro con datos válidos" y el test de `sesion-api` que comparaban el cuerpo exacto, que ahora suma `aceptaMensajes: false`)
- [x] 6.5 Componente de consentimiento para la recepción (D5) en "Anotar paciente" y "Nuevo turno". Verificar con los tests de "Paciente elegido sin consentimiento", "El odontólogo no ve la línea", "Registrar un consentimiento por teléfono" y "Confirmar sin medio" (hecho: `consentimiento/linea-recepcion.ts`; el buscador de C-36 suma el aviso opcional `alQuitar`)
- [x] 6.6 Rechazo `SIN_CONSENTIMIENTO` en "Anotar paciente" con la acción de registrar (D5) y enlace del aviso al paciente en `lista-espera/paciente.ts` (D6, coordinado con Sofia). Verificar con los tests de "Registrar y volver a guardar" y "Paciente sin consentimiento en la lista de espera" (hecho; el test del aviso va en `lista-espera-paciente.test.ts`)

## 7. Verificación

- [x] 7.1 Recorrer los deltas y verificar que cada escenario tiene un test con su nombre, con un `grep` de los nombres (hecho el 2026-11-25: los 33 escenarios tienen exactamente un test con su nombre en los archivos de C-17)
- [x] 7.2 Verificación visual contra los mockups a 390 × 844 y 1440 × 900, con el backend de desarrollo y datos ficticios, sin desplazamiento horizontal ni errores en la consola; capturas en `mockups/verify/` y `Preview` y `Visual verify` completos en `design.md` (hecho el 2026-11-25: sin blockers ni majors, dos menores corregidas; ver `mockups/verify/README.md`)
- [x] 7.3 Recorrido completo: la recepción registra el consentimiento de "Paciente Dos" por teléfono y lo anota en la lista de espera, "Paciente Uno" lo acepta desde "Mi cuenta", reserva y aparece la línea `Envío simulado` en el log. Al revocarlo, ya no se crean notificaciones (hecho el 2026-11-25: notificación 4 por email al reservar con consentimiento; ninguna después de revocar; Paciente Dos anotado con consentimiento de la recepción por teléfono)
- [ ] 7.4 `./gradlew build`, `npm test`, `npm run build` y `openspec validate consentimiento-comunicaciones --strict` en verde; marcar C-17 en `CHANGES.md` y abrir el PR a `master` (verificaciones hechas el 2026-11-25: backend 2358 tests en verde con `build integrationTest`, frontend 1386 y `npm run build` sin errores, `openspec validate --strict` válido y C-17 marcado en `CHANGES.md`; falta abrir el PR)

## Workflow follow-up

- Archivar después del merge. Si C-49 todavía no se archivó, su delta de "Cuenta del paciente con sesión iniciada" sigue siendo de C-49: los requirements de C-17 son ADDED y no lo pisan.
