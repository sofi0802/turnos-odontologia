# Tasks

> Strict TDD en cada tarea de código: RED (el test falla por la razón correcta) → GREEN → TRIANGULATE → REFACTOR. Un test por escenario del spec, con el nombre del escenario. TypeScript con `strict`, sin `any` y sin frameworks. Los tests corren con `cd frontend && npm test` (Vitest) y el chequeo de tipos con `npm run build`. Los tests de C-09 y C-12 tienen que seguir pasando. Datos ficticios (Paciente Uno, DNI `99000001`, Dra. Ana Ejemplo, Dr. Bruno Prueba).

## 0. Línea base y reproducción de la falla

- [ ] 0.1 Correr `cd frontend && npm test` y `npm run build` sobre la rama y registrar la cantidad de tests; verificar que todo pasa antes de tocar código
- [ ] 0.2 Reproducir contra el backend real y el seed ficticio por qué una reprogramación desde la agenda se rechaza con un horario que parecía válido: levantar PostgreSQL y el backend (`bootRun` con `.env`), abrir la agenda en el navegador, y probar los cuatro candidatos de D1 (sillón ocupado con el horario ofrecido por la disponibilidad, número de reserva sin guion, profesional no habilitado, código sin texto); guardar en `verify/reproduccion.md` los pasos, el pedido, la respuesta y la causa de cada uno. Verificar que el archivo nombra la causa confirmada y, si es del backend, a quién se avisa
- [ ] 0.3 Si 0.2 muestra una causa distinta de las de D1 o del backend, actualizar `design.md` y `tasks.md` con `openspec-update-change` y pedir confirmación antes de seguir. Verificar con `openspec validate frontend-reprogramacion-agenda --strict`

## 1. Diseño: mockups aprobados (compuerta antes del apply)

- [x] 1.1 Crear `mockups/` con el diálogo "Reprogramar turno" (identificación, profesional, fecha, botones de horario, sillón, aviso de horarios que pisan el turno), el día sin horarios y el rechazo por sillón, con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`; renderizarlos a 390 × 844 y 1440 × 900, guardar las capturas en `mockups/previews/` y completar su `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal
- [ ] 1.2 Presentar los mockups y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)`, `Preview: rendered`). Verificar que la sección UI Mockups lo dice; no empezar el apply sin esto

## 2. Lógica pura

- [ ] 2.1 RED: escribir los tests de `normalizarNumeroReserva` ("Número de reserva sin guion": minúsculas, espacios, con y sin guion, y un valor que no tiene 8 símbolos queda como se ingresó) y verlos fallar; GREEN: crear la función en `frontend/src/agenda/`; TRIANGULATE: guiones o espacios de más, entrada vacía; REFACTOR: documentar el formato. Verificar con `npm test -- formularios`
- [ ] 2.2 RED: escribir en `mensajes.test.ts` los tests de "DNI o número de reserva incorrectos", "Profesional no habilitado", "Profesional ausente o fuera de horario" y "Código desconocido" y verlos fallar; GREEN: agregar `DATOS_DE_RESERVA_INVALIDOS` a `MENSAJES_POR_CODIGO` y el texto del rechazo por sillón de la reprogramación (sin cambiar el de la creación de turnos); TRIANGULATE: ningún código deja un mensaje vacío ni muestra el código; REFACTOR: revisar que el texto de C-09 de la creación sigue igual. Verificar con `npm test -- mensajes formularios`

## 3. Datos del formulario

- [ ] 3.1 RED: escribir los tests de la carga de profesionales habilitados ("Solo los habilitados para la prestación", "No se pudo cargar la lista de habilitados") con la API simulada y verlos fallar; GREEN: crear en `frontend/src/agenda/` la consulta a `GET /public/profesionales?prestacionId=` y su lectura tipada sin `any`; TRIANGULATE: el profesional actual no se repite, respuesta con forma inesperada; REFACTOR: reutilizar el lector del catálogo si encaja. Verificar con `npm test -- agenda`
- [ ] 3.2 RED: escribir los tests del estado de horarios ("Se muestran los horarios del profesional", "Cambiar de profesional o de fecha vuelve a pedir los horarios", "Día sin horarios libres", "Falla la consulta de horarios") y verlos fallar, incluido uno de respuesta tardía descartada; GREEN: crear `frontend/src/agenda/reprogramacion.ts` que arma la consulta con `consultarDisponibilidad` y maneja horario elegido, carga y error; TRIANGULATE: se descarta la respuesta de una consulta anterior, el horario elegido se limpia al cambiar fecha o profesional; REFACTOR: separar el estado de la vista. Verificar con `npm test -- agenda`

## 4. Formulario

- [ ] 4.1 RED: escribir los tests de `abrirReprogramacion` ("Recepción reprograma un turno", "Confirmar sin elegir horario", "Reprogramación rechazada por el sillón", "Elegir otro sillón después de un rechazo", "Reprogramar a un horario ofrecido", "Aviso de los horarios que pisan el turno actual", "Número de reserva sin guion") con la API simulada y verlos fallar; GREEN: reemplazar la hora libre por la grilla de `crearGrillaHorarios`, usar los profesionales habilitados, normalizar el número de reserva, enviar el `inicio` del botón y mantener el selector de sillón; TRIANGULATE: el pedido no lleva `profesionalId` ni `sillonId` cuando no se cambiaron, el DNI y el número quedan cargados después de un rechazo; REFACTOR: acotar los cambios a `abrirReprogramacion` para no chocar con C-35 y C-36. Verificar con `npm test -- formularios agenda`
- [ ] 4.2 RED: escribir en `responsivo.test.ts` la comprobación de "Horarios en el celular" (regla en el CSS: botones de al menos 44 × 44 px, filas que se ajustan, sin desplazamiento horizontal) siguiendo el patrón de C-09 y verla fallar; GREEN: agregar los estilos con los tokens del design system; TRIANGULATE: ancho de 320 px; REFACTOR: reutilizar `.chips` de C-12. Verificar con `npm test -- responsivo`

## 5. Verificación integral

- [ ] 5.1 Recorrer el delta y verificar que los 18 escenarios tienen un test con el nombre del escenario, sin duplicados. Verificar con un `grep` de los nombres
- [ ] 5.2 Correr `npm test` y `npm run build` completos y verificar que pasan, incluidos los tests de C-09 y C-12 y la cantidad de 0.1 más los nuevos
- [ ] 5.3 Verificación visual contra los mockups aprobados: con el backend y el frontend levantados, abrir "Reprogramar turno" a 390 × 844 y a 1440 × 900, guardar las capturas en `mockups/verify/` y registrar las diferencias por prioridad; inspeccionar la consola y las llamadas de red. Verificar y completar `Visual verify` en `design.md`
- [ ] 5.4 Repetir la reproducción de 0.2 con el formulario nuevo y verificar que un horario ofrecido se reprograma, o que el rechazo muestra el motivo correcto; dejar el resultado en `verify/reproduccion.md`
- [ ] 5.5 Correr `openspec validate frontend-reprogramacion-agenda --strict` y verificar que el change es válido (el aviso de que el archive espera al de C-09 es esperado)

## Workflow follow-up

- Abrir el PR de `change/C-33-frontend-reprogramacion-agenda` a `master`; Sofía revisa y mergea (nunca commitear directo a `master`).
- Avisar en el PR a Enzo (dueño de `frontend/`), y a Sofía y a Juan Sebastián con lo que resulte de 0.2 sobre el backend (sillón sin elegir y horarios que pisan el propio turno). Coordinar el orden de los PR con C-35 y C-36.
- Archivar con `openspec archive frontend-reprogramacion-agenda` **solo después del archive de C-09**, porque el delta modifica una capacidad que ese archive crea; verificar los specs archivados.
- Marcar `[x]` en C-33 de `CHANGES.md` recién después del merge.
