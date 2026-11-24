# Tasks

Acceptance criteria: los escenarios de los deltas de `specs/` (un test por escenario) y los mockups aprobados en `mockups/` (ver `design.md`, sección UI Mockups). El apply no empieza sin la aprobación de la propuesta y de los mockups por la líder (governance ALTO).

## 1. Base de datos y dominio

- [ ] 1.1 `V22__llegada_turno.sql`: columna `turno.llegada_en` e índice parcial para la tarea; sin tocar el `CHECK` ni las `EXCLUDE`
- [ ] 1.2 Test primero, unitarios de `Turno`: tolerancia de 15 minutos (antes del inicio, 10:14, 10:15), ausencia con llegada, llegada anticipada, segunda llegada, llegada a un turno no activo, deshacer un turno no ausente y después del fin
- [ ] 1.3 `Turno`: campo `llegadaEn`, `conLlegada`, `ausente(ahora)` con la tolerancia, `sinAusencia`, y los códigos nuevos en `CodigoRechazo`

## 2. Casos de uso y persistencia

- [ ] 2.1 `TurnoRepositoryJpa` y la entidad guardan y leen `llegada_en`
- [ ] 2.2 `RegistrarLlegada` (con el paso inmediato a `ATENDIDO` si ya pasó `fin_con_margen`) y su auditoría `LLEGADA`
- [ ] 2.3 `MarcarAusencia` con solicitante recepción u odontólogo del turno; auditoría `AUSENTE` igual en los dos
- [ ] 2.4 `DeshacerAusencia`: validación de superposición y margen sin las reglas de creación, estado previo desde la auditoría `CONFIRMAR`, traducción de la `EXCLUDE`, y auditoría `DESHACER_AUSENCIA`
- [ ] 2.5 Caso de uso del paso automático a `ATENDIDO` (`UPDATE ... RETURNING`) con auditoría `ATENDIDO` y actor `sistema`, y `TareaAsistencia` programada que se apaga por propiedad
- [ ] 2.6 `ConsultaAgenda`: devolver los turnos no cancelados con `llegadaEn`

## 3. API y ficha

- [ ] 3.1 Tests de integración (`@Tag("integration")`, PostgreSQL real), uno por escenario de los deltas de `agenda-turnos`, `agenda-api` y `auditoria-turnos`, incluidos los permisos de admin, odontólogo y recepción
- [ ] 3.2 `TurnosController`: `POST /{id}/llegada`, `/ausencia` y `/deshacer-ausencia` solo para `RECEPCIONISTA`; `GET /turnos` con `estado` y `llegadaEn`
- [ ] 3.3 `FichaClinicaController`: la ausencia desde la ficha aplica la tolerancia; actualizar los tests de C-13 que esperaban `TURNO_NO_INICIADO` según el delta
- [ ] 3.4 Ficha: ausencias deshechas con momento y actor (`AusenciasPacienteJdbc`), con su test de integración (avisar a Emanuel)

## 4. Frontend

- [ ] 4.1 Tests primero (Vitest), uno por escenario del delta de `frontend-agenda-personal`: etiquetas, botones por rol y momento, y mensajes de cada código
- [ ] 4.2 `etiquetaDeAsistencia` y `accionesDeAsistencia` puras en `frontend/src/agenda/`, y los botones y etiquetas en la vista; sin "Cancelar turno" ni "Reprogramar" en los turnos atendidos y ausentes
- [ ] 4.3 Pedidos `llegada`, `ausencia` y `deshacer-ausencia` en el cliente de la agenda y mensajes en `shared/ui/mensajes.ts`

## 5. Verificación

- [ ] 5.1 `./gradlew build integrationTest` en verde, con la evidencia en `docs/evidencia/c43-backend.txt`; los tests de C-06, C-11, C-13 y C-35 siguen pasando
- [ ] 5.2 `npx tsc --noEmit`, `npm test` y `npm run build` en verde
- [ ] 5.3 Verificación visual contra los mockups aprobados con el backend de desarrollo, a 390 y 1440 px, como recepción y como odontólogo; capturas en `mockups/verify/` y resultado en `design.md`
- [ ] 5.4 `openspec validate registro-asistencia --strict` en verde, C-43 marcado en `CHANGES.md` y aviso a Emanuel y a Enzo en el PR
