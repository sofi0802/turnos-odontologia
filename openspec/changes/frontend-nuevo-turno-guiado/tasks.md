# Tasks

Cada escenario de la spec tiene un test con el mismo nombre. Comandos desde `frontend/`.

## 0. Línea de base y mockups (antes del apply)

- [x] 0.1 Correr `npm test` y `npm run build` sobre la rama y registrar la cantidad de tests; verificar que todo pasa antes de tocar código *(2026-11-23: sobre `master` d8cc8fa fallaba "Abrir Configuración sin sesión" y el build no compilaba por el choque entre los PR #59 y #60. Se arregló en `master` 40f67d2 (PR #67, C-37); ahí la línea de base da 345 tests en verde en 22 archivos y `npm run build` compila.)*
- [x] 0.2 Crear `mockups/` con el diálogo "Nuevo turno" guiado en sus estados (al abrir, profesional elegido con su sillón, calendario con días pasados deshabilitados, horarios del día, día sin horarios, profesional sin sillón habitual), con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`; renderizarlos a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/previews/` y completar su `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal
- [x] 0.3 Presentar los mockups y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)`, `Preview: rendered`). Verificar que la sección UI Mockups lo dice; no empezar el grupo 1 sin esto

## 1. Piezas reutilizables

- [x] 1.1 RED: tests de `catalogos.ts` que lean `sillonHabitualId` (número, `null` y ausente) y fallen; GREEN: sumarlo a `ItemCatalogo` de los profesionales sin romper sillones y prestaciones; verificar con `npm test -- agenda catalogos`
- [x] 1.2 RED: tests unitarios de `horariosSinChoqueDeSillon` (D4) con los bordes de RN-AG-04: contiguo sin margen se oculta, a 10 minutos se muestra, turno cancelado no cuenta, lista vacía de turnos; GREEN: implementar la función pura en `frontend/src/agenda/`; verificar con `npm test -- nuevo-turno`
- [x] 1.3 RED: tests del calendario (D3) para "Los días pasados no se pueden elegir", "Ir al mes siguiente" y "Elegir un día" con `vi.setSystemTime` en 2026-12-01, más un mes que empieza en domingo y el paso de diciembre a enero; GREEN: crear `frontend/src/shared/ui/calendario.ts`; verificar también con `TZ=UTC npm test -- calendario`
- [x] 1.4 Parametrizar `crearZonaHorarios` (D5) con la ayuda y los textos de "sin horarios" y "error", con los de C-33 por defecto; verificar que los tests de C-33 (`formularios`, `reprogramacion`) siguen pasando sin cambios

## 2. Formulario guiado

- [x] 2.1 RED: escribir en `test/nuevo-turno.test.ts` "Al abrir solo se elige la prestación", "Solo los habilitados para la prestación" y "No se pueden cargar los profesionales"; GREEN: rehacer `abrirNuevoTurno` con el paciente primero, prestación del catálogo y profesionales de `GET /public/profesionales?prestacionId=` (D1, D2); verificar que pasan
- [x] 2.2 RED: "Se muestra el sillón habitual sin poder cambiarlo" y "Profesional sin sillón habitual"; GREEN: el párrafo del sillón (D6) y el bloqueo del calendario; verificar que pasan
- [x] 2.3 RED: "Horarios del día como botones", "Se ocultan los horarios que pisan el sillón habitual", "Día sin horarios" y "No se pueden cargar los horarios", más el caso en que falla la consulta de turnos (se muestran sin filtrar); GREEN: calendario y zona de horarios con el filtro (D4); verificar que pasan
- [x] 2.4 RED: "Cambiar la prestación vacía los pasos siguientes" y "Cambiar el día vacía el horario"; GREEN: `habilitarPasos()` (D1); verificar que pasan
- [x] 2.5 RED: "Recepción crea un turno" y "Faltan datos en el formulario" con los pasos nuevos; GREEN: el envío con `sillonId` del habitual y el `inicio` del horario elegido, y el mapa de errores de campo (D7); verificar que pasan

## 3. Tests existentes y responsivo

- [x] 3.1 Reescribir los tests de C-09 y C-36 que llenan el formulario viejo ("Recepción crea un turno", los de "Mensajes por código de rechazo", "Profesional inexistente", "Paciente escrito pero no elegido", "Ya no se pide el id del paciente", "Error de la API en el paciente") para que recorran los pasos nuevos, sin cambiar su nombre ni lo que comprueban; verificar con `npm test` que todos pasan
- [x] 3.2 Estilos del calendario y del sillón en `styles.css` con tokens `--ds-*`: días de al menos 44 px, grilla de 7 columnas que entra en 320 px, foco visible y estado elegido que no depende solo del color; sumar el test en `responsivo.test.ts`; verificar que pasa

## 4. Verificación integral

- [x] 4.1 Correr `npm run build` y `npm test` con captura de evidencia y verificar que pasan todos, con un test por escenario de la spec (19) con su mismo nombre
- [x] 4.2 Probar contra el backend y la base de desarrollo (seed ficticio): crear un turno guiado de punta a punta, un día sin horarios, un profesional sin sillón habitual y un horario que el filtro oculta; capturar a 390 × 844 y 1440 × 900 en `mockups/verify/`, comparar con los mockups aprobados y anotar las diferencias por prioridad
- [x] 4.3 Verificar que `grep -rnE ":\s*any\b|as any" frontend/src frontend/test` no devuelve resultados, que no hay frameworks en `package.json` y que tests y capturas usan solo datos ficticios
- [x] 4.4 Correr `openspec validate frontend-nuevo-turno-guiado --strict` y verificar que es válido

## Notas de implementación

- **Escenario corregido en la spec** ("Se ocultan los horarios que pisan el sillón habitual"): decía que con un turno de 11:00 a 11:30 y un margen de 10 minutos se veía el horario de las 11:30. Empezar justo cuando termina el otro turno no deja margen (RN-AG-04), así que se cambió por un horario a las 11:45.
- **Orden de TDD**: los tests de C-09, C-33 y C-36 de la tarea 3.1 se adaptaron después de escribir el formulario (recorren los pasos nuevos con los helpers `responderPasosDelNuevoTurno` y `completarPasosDelNuevoTurno` de `test/apoyo.ts`, sin cambiar su nombre ni lo que comprueban). Para comprobar que no son triviales se rompió a propósito el filtro por sillón y el sillón enviado: fallaron "Se ocultan los horarios que pisan el sillón habitual", "sin el margen de la configuración…" y "Recepción crea un turno", y se restauró el código.
- **Test reemplazado**: "el formulario solo ofrece profesionales activos y propone el día mostrado" (de C-09, sin escenario en la spec) pasó a "el formulario solo ofrece prestaciones activas y el calendario empieza en el mes de hoy", porque la spec de C-40 cambia ese comportamiento.
- **Estilos**: la sección de C-40 va antes de la de C-34 y termina con `/* Fin del nuevo turno guiado (C-40) */`, porque un test de C-34 lee el último `@media` del archivo.
- **Sillón**: el detalle usa la clase `sillon-detalle` y no `ayuda`, porque `.campo .ayuda::first-letter` (de C-12) lo ponía en mayúscula; se vio en la verificación contra el backend.
- **Verificación**: contra el backend de desarrollo con datos ficticios (`mockups/verify/README.md`). Quedan sin probar contra el backend el profesional sin sillón habitual y la sesión de odontólogo (cubiertos por tests o documentados como riesgo).

## Workflow follow-up

- Avisar a Juan Cruz en el PR que C-40 se mergea antes de la etapa D de C-39.
- Archivar C-36 (`buscador-pacientes`) antes que este change, porque los dos modifican "Crear un turno".
- Abrir el PR de `change/C-40-frontend-nuevo-turno-guiado` a `master`, marcar `[x]` en C-40 de `CHANGES.md` y archivar después del merge.
