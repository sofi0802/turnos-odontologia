# Tasks

> Strict TDD en cada tarea de código: RED (el test falla por la razón correcta) → GREEN → TRIANGULATE → REFACTOR. Un test por escenario del spec, con el nombre del escenario. TypeScript con `strict`, sin `any` y sin frameworks. Los tests corren con `cd frontend && npm test` (Vitest) y el chequeo de tipos con `npm run build`. Los tests de C-09, C-12 y C-33 a C-37 siguen pasando, salvo los que eligen la prestación como botón de opción: se actualizan al selector y se informa cada uno. Datos ficticios (Paciente Uno, "Consulta", "Limpieza dental", "Extracción").

## 0. Línea base

- [x] 0.1 Verificar que el PR de C-40 (Enzo) está mergeado y traer `master` a la rama; si no lo está, no empezar el apply (la propuesta y los mockups pueden seguir) (hecho: mergeado)
- [x] 0.2 Correr `cd frontend && npm test` y `npm run build` sobre la rama y registrar la cantidad de tests; verificar que todo pasa antes de tocar código (hecho: 420 tests)
- [x] 0.3 Listar con `grep` los tests que eligen la prestación como `radio` (`reserva-paciente`, `cuenta-paciente`, `turnos-paciente`, `responsivo`, etc.) y anotarlos en el PR

## 1. Diseño: mockups aprobados (compuerta antes del apply)

- [x] 1.1 Crear `mockups/` con el paso 1 de la reserva: selector cerrado, abierto con todas las prestaciones, filtrado, sin coincidencias, con una prestación elegida y con una lista larga (unas 12 prestaciones ficticias) en el celular; con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`; renderizarlos a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/previews/` y completar su `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal
- [x] 1.2 Presentar los mockups y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)`, `Preview: rendered`). Verificar que la sección UI Mockups lo dice; no empezar el apply sin esto

## 2. El selector con buscador

- [x] 2.1 RED: escribir `selector-buscable.test.ts` con "Buscar por parte del nombre", "Buscar sin mayúsculas ni tildes", "Sin coincidencias" y "Borrar la búsqueda muestra todas" y verlos fallar; GREEN: crear `shared/ui/selector-buscable.ts` con el filtrado local (D2) y la lista; TRIANGULATE: texto con espacios al borde, nombres con "ñ" y con tildes, lista vacía; REFACTOR: separar la normalización del texto. Verificar con `npm test -- selector-buscable`
- [x] 2.2 RED: escribir "Elegir con el teclado", "Elegir con el toque", "Cerrar sin elegir" y "Cambiar el texto quita la elección" y verlos fallar; GREEN: flechas, Enter, Escape, Tab, toque y la regla D5; TRIANGULATE: flecha arriba desde la primera opción, Enter sin opción activa, foco que sale del campo; REFACTOR: una función por tecla. Verificar con `npm test -- selector-buscable`
- [x] 2.3 RED: escribir "Roles y estados del selector" y verlo fallar; GREEN: roles, `aria-expanded`, `aria-controls`, `aria-activedescendant`, `aria-selected` y la región `aria-live` (D6); TRIANGULATE: con la lista cerrada no hay `aria-activedescendant`, y con "Sin coincidencias" se anuncia el mensaje; REFACTOR: ids únicos por instancia. Verificar con `npm test -- selector-buscable`

## 3. El paso 1 de la reserva

- [x] 3.1 RED: actualizar en `reserva-paciente.test.ts` "Prestaciones a elegir" ("Consulta · 30 minutos" en el selector abierto) y "Lo que se pide en el paso 2 no cambia", y los tests de "Pasar del paso 1 al 2", "Continuar sin elegir" y "Volver sin perder lo elegido" para elegir con el selector, y verlos fallar; GREEN: usar el selector en `paso1`, con la elección en `eleccion.prestacionId` y el campo rearmado al volver; TRIANGULATE: "No pudimos cargar las prestaciones…" sin selector ni "Continuar"; REFACTOR: quitar de `paso1` lo que ya no se usa. Verificar con `npm test -- reserva-paciente`
- [x] 3.2 Actualizar los demás tests que eligen la prestación como `radio` (los de 0.3) y verificar con `npm test` que los pasos 2 y 3 no cambiaron; informar cada test tocado

## 4. Estilos

- [x] 4.1 RED: escribir en `responsivo.test.ts` la comprobación de "Selector en el celular" (reglas de CSS: opciones de al menos 44 px, lista con `max-height` y desplazamiento propio, sin ancho fijo mayor a 320 px, solo `--ds-*`) y verla fallar; GREEN: agregar los estilos del selector con los tokens, según los mockups aprobados, en un bloque propio con un comentario de C-38; TRIANGULATE: 320 px de ancho; REFACTOR: reutilizar los tokens de `.btn` y `.control`. Verificar con `npm test -- responsivo`

## 5. Verificación integral

- [x] 5.1 Recorrer el delta y verificar que los 12 escenarios tienen un test con el nombre del escenario, sin duplicados. Verificar con un `grep` de los nombres
- [x] 5.2 Correr `npm test` y `npm run build` completos y verificar que pasan, con la cantidad de 0.2 más los nuevos y la lista de tests viejos que se actualizaron
- [x] 5.3 Verificación visual contra los mockups aprobados: con el backend y el frontend levantados, abrir `#/reserva` a 390 × 844 y 1440 × 900 (selector cerrado, abierto, filtrado, sin coincidencias, elegido, lista larga), guardar las capturas en `mockups/verify/`, registrar las diferencias por prioridad e inspeccionar la consola y las llamadas de red. Probar con el teclado (Tab, flechas, Enter, Escape) y, si se puede, con un lector de pantalla. Completar `Visual verify` en `design.md`
- [x] 5.4 Correr `openspec validate frontend-reserva-explorador-prestaciones --strict` y verificar que el change es válido

## Workflow follow-up

- Abrir el PR de `change/C-38-frontend-reserva-explorador-prestaciones` a `master`; Sofía revisa y mergea (nunca commitear directo a `master`).
- Avisar en el PR a Enzo (dueño de `frontend/`): se toca `reserva.ts` y `styles.css`, y el apply espera al merge de C-40.
- Archivar con `openspec archive frontend-reserva-explorador-prestaciones` después del merge; verificar el spec archivado de `frontend-reserva-paciente`.
- Marcar `[x]` en C-38 de `CHANGES.md` recién después del merge.
