# Tasks

Acceptance criteria: el mockup aprobado en `mockups/` (ver `design.md`, sección UI Mockups) y los escenarios del delta de `frontend-agenda-personal`.

## 0. Mockups (antes del apply)

- [x] 0.1 Mockups en `mockups/` con los tokens del sistema visual: agenda con el botón "Sobreturno", formulario de sobreturno (vacío y con `SIN_SILLON_DISPONIBLE`), formulario de extender y choque al extender, a 390 y 1440 px; capturas en `mockups/previews/`
- [x] 0.2 Aprobación de los mockups registrada en `design.md` (`Approval: approved`) antes de empezar el grupo 1

## 1. Base compartida

- [x] 1.1 Test primero: `consultarAgenda` lee `pacienteId` de cada turno (en `frontend/test/agenda.test.ts`)
- [x] 1.2 Sumar `pacienteId` a `TurnoAgenda` en `frontend/src/agenda/consulta.ts`
- [x] 1.3 Test primero en `frontend/test/mensajes.test.ts`: textos de `SIN_SILLON_DISPONIBLE` y `TURNO_NO_EXTENSIBLE`
- [x] 1.4 Sumar los dos códigos a `MENSAJES_POR_CODIGO` en `frontend/src/shared/ui/mensajes.ts`
- [x] 1.5 Exportar de `frontend/src/agenda/formularios.ts` los helpers que reusa el módulo nuevo (`selector`, `entrada`, `validarObligatorios`, `manejarEnvio`, `leerNumeroReserva`, `opcionesActivas` y el diálogo), sin cambiar su comportamiento; los tests de `formularios.test.ts` y `nuevo-turno-formulario.test.ts` siguen pasando
- [x] 1.6 Sumar a `ControlAgenda` `turnosCargados()` y el `profesionalFijo` de la sesión, y conectarlos en `frontend/src/pages/agenda.ts`

## 2. Sobreturno

- [x] 2.1 Test primero, un `it` por escenario de "Cargar un sobreturno" en `frontend/test/sobreturno.test.ts` (recepción carga, no pide sillón, odontólogo con su profesional, motivo obligatorio, faltan datos, ayuda del motivo, paciente no ve el botón)
- [x] 2.2 Implementar `abrirSobreturno(control, precarga?)` en `frontend/src/agenda/sobreturno.ts` (design D1 a D4, D8): buscador o paciente fijo, prestación, profesional habilitado o fijo, fecha, hora con paso de 5, motivo con ayuda y `maxlength` 500, y mensaje de éxito con el nombre del sillón
- [x] 2.3 Botón "Sobreturno" (`boton-secundario`) antes de "Nuevo turno" en `frontend/src/pages/agenda.ts`, también en "Mi agenda" del odontólogo (design D10)

## 3. Extensión

- [x] 3.1 Test primero, un `it` por escenario de "Extender un turno" en `frontend/test/sobreturno.test.ts` y en `agenda-semana.test.ts` para "Extender desde la semana"
- [x] 3.2 Sumar `extender` a `AccionesTurno` y el botón "Extender" en `frontend/src/agenda/vista.ts` (solo `RESERVADO` o `CONFIRMADO` y no sobreturno) y en el detalle de `frontend/src/agenda/semana.ts` con `cerrandoAntes` (design D5)
- [x] 3.3 Implementar `abrirExtension(control, turno)` en `frontend/src/agenda/sobreturno.ts`: resumen del horario, "Nuevo fin" con fin + 15 min, validación de fin posterior, motivo y `POST /turnos/{id}/extender` (design D6)

## 4. Choque al extender y rechazos

- [x] 4.1 Test primero, un `it` por escenario de "Choque al extender un turno" y de "Mensajes de los rechazos de sobreturno y extensión", y tests unitarios de `turnoQueChoca` (mismo sillón, mismo profesional, fuera de la ventana, turno cancelado, el propio turno)
- [x] 4.2 Implementar `turnoQueChoca` y el aviso con "Reprogramar el turno de las HH:mm" y "Cargar como sobreturno" (design D7), y el mapa de campos de los `422` (design D9)

## 5. Estilos

- [x] 5.1 Estilos de la barra con los dos botones (390 px: una fila, mitad y mitad) y del aviso de choque en `frontend/src/styles/`, con los tokens del sistema visual y sin colores sueltos

## 6. Verificación

- [x] 6.1 Verificar que cada escenario del delta tiene un `it` con su nombre (`grep` de los nombres), sin duplicados
- [x] 6.2 `npx tsc --noEmit`, `npm test` y `npm run build` en verde; los tests de C-09, C-36, C-40 y C-43 siguen pasando y la cantidad de tests no baja
- [x] 6.3 Verificación visual contra el mockup aprobado, a 390 y 1440 px, con el backend de desarrollo: cargar un sobreturno, extender un turno y provocar un choque, con datos ficticios; capturas en `mockups/verify/` y resultado en `design.md`. Hecha a mano por Enzo el 2026-11-24 (sin capturas automatizadas; ver `Visual verify` en `design.md`)
- [x] 6.4 `openspec validate frontend-sobreturno-y-extension --strict` en verde, C-45 marcado en `CHANGES.md` y aviso a Juan Cruz en el PR

## Workflow follow-up

- Archive del change (`openspec archive frontend-sobreturno-y-extension`) después del merge, en un PR aparte.
