# Tasks

Acceptance criteria: el mockup aprobado en `mockups/` (ver `design.md`, sección UI Mockups) y los escenarios del delta de `frontend-turnos-paciente`.

## 1. Estado visible del turno

- [x] 1.1 Test primero: `estadoVisible` devuelve "Sin registrar" (`e-sin-registrar`) para un turno `RESERVADO` o `CONFIRMADO` con `fin` pasado, conserva el estado de un turno en curso y el de los `ATENDIDO`, `AUSENTE` y `CANCELADO`
- [x] 1.2 Agregar `estadoVisible(turno, ahora)` en `frontend/src/paciente/turnos.ts`

## 2. Mis turnos

- [x] 2.1 Test primero, un `it` por escenario nuevo del delta en `frontend/test/turnos-paciente.test.ts`: "Turno pasado sin registrar", "Turno en curso" y "Turnos registrados se muestran igual"
- [x] 2.2 En `frontend/src/pages/mis-turnos.ts`, usar `estadoVisible` en la tarjeta, con el mismo momento con que se separan los turnos, y sumar el texto "El consultorio todavía no registró la asistencia." en los turnos sin registrar
- [x] 2.3 Agregar `.e-sin-registrar` en `frontend/src/styles/06-componentes.css` con los tokens de "Cancelado"

## 3. Verificación

- [x] 3.1 `npx tsc --noEmit`, `npm test` y `npm run build` en verde; los escenarios de C-12 siguen pasando
- [x] 3.2 Verificación visual contra el mockup aprobado, a 390 y 1440 px, con el backend de desarrollo y un turno pasado sin registrar; capturas en `mockups/verify/` y resultado en `design.md`
- [x] 3.3 `openspec validate frontend-turnos-sin-registrar --strict` en verde, C-42 marcado en `CHANGES.md` y aviso a Enzo en el PR
