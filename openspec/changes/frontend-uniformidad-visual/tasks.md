# Tasks

> Cuatro etapas, **de a un PR por vez** (A → B → C → D), cada una con su rama `change/C-39-<etapa>` y su PR a `master`. Strict TDD en cada tarea de código: RED (el test falla por la razón correcta) → GREEN → TRIANGULATE → REFACTOR. Un test por escenario del spec, con el nombre del escenario. TypeScript con `strict`, sin `any` y sin frameworks. Los tests corren con `cd frontend && npm test` (Vitest) y el chequeo de tipos con `npm run build`. Los tests existentes siguen pasando salvo los que dependen de la estructura del menú, del orden de los botones de un diálogo o de leer `styles.css`: se actualizan y se informa cada uno. Datos ficticios (Paciente Uno, `recepcion`). Antes de cada etapa visual: mockups aprobados por la líder y capturas de antes.

## 0. Línea base

- [x] 0.1 Verificar que las dependencias están mergeadas (C-33 a C-38 y C-40) y traer `master` a la rama; correr `cd frontend && npm test` y `npm run build` y registrar la cantidad de tests (447 al proponer) (hecho: 447 tests al empezar la etapa A)
- [x] 0.2 Verificar el estado de los archives pendientes de `frontend-navegacion` (`ingreso-unico-mi-cuenta`, `frontend-portada-y-acceso`) (hecho el 2026-11-23: siguen sin archivar; el equipo acordó que el apply no los espera, y el archive de C-39 va al final)
- [x] 0.3 Capturas de antes (390 × 844 y 1440 × 900) de cada pantalla, con sesión de paciente, de personal y sin sesión, en `mockups/antes/`. Verificar abriéndolas (hecho para la etapa A: 36 capturas en `mockups/antes/`; las de B, C y D se toman al empezar cada etapa)

## A. Tokens y CSS modular (sin cambio visual)

- [x] A.1 RED: escribir `test/estilos.ts` (ayudante que junta los módulos de `src/styles/` en el orden de `styles.css`) y los tests "Módulos importados en orden" y "Sin cambios de comportamiento" (todo `npm test` igual que antes), y verlos fallar; GREEN: partir `styles.css` en módulos (base, componentes, layout y uno por pantalla o grupo) con `@import`, sin cambiar una sola regla; TRIANGULATE: que el CSS generado por `npm run build` tenga las mismas reglas; REFACTOR: ordenar cada módulo por tema. Verificar con `npm test -- estilos responsivo portada`
- [x] A.2 Pasar los tests que hacen `import estilos from '../src/styles.css?raw'` al ayudante y verificar que pasan sin cambiar sus afirmaciones; informar cada test tocado
- [x] A.3 RED: escribir "Sin alias ni colores sueltos" y verlo fallar; GREEN: reemplazar `--color-*`, `--espacio-*` y `--radio` por sus `--ds-*` equivalentes en todos los módulos y quitar los alias de `design-system/tokens.css` (Revision 2); TRIANGULATE: colores `#rrggbb` o `rgb()` fuera de `tokens.css`; REFACTOR: unificar valores repetidos. Verificar con `npm test`
- [x] A.4 Actualizar `design-system/README.md` (Revision 2, `Approval: pending`, qué cambió y por qué) y presentar la renovación a la líder; capturas de después idénticas a las de antes (comparar con `mockups/antes/`). Registrar `Approval` en `design.md` y en el README del design system (hecho: Revision 2 con `Approval: pending`; falta que la líder la renueve)
- [ ] A.5 Correr `npm test` y `npm run build` y abrir el PR de la etapa A. Verificar que la cantidad de tests no baja

## B. Barra lateral, encabezado, pie y componentes comunes

- [x] B.1 Mockups de la etapa B en `mockups/B/` (barra lateral con sesión de paciente y de personal; cajón abierto y cerrado en el celular; encabezado y pie azules; sin sesión; botones con hover, foco, presionado y deshabilitado; diálogos con el orden de botones y el aviso; estados vacíos y de carga; íconos), con HTML y CSS locales que importan `design-system/tokens.css` y `brand.js`; renderizar a 390 × 844 y 1440 × 900, capturas en `mockups/B/previews/` y `README.md`. Verificar abriendo las capturas y que no hay desplazamiento horizontal
- [x] B.2 Presentar los mockups de B y registrar la aprobación de la líder en `design.md` (`Approval: approved (<fecha>, by user)` para la etapa B). No empezar el apply de B sin esto
- [x] B.3 (la portada deja de usar "Acceso personal" y usa "Ingresar", D10: como `frontend-portada-y-acceso` sigue sin archivar, se corrigió su propio delta `specs/frontend-portada/spec.md` en lugar de agregar uno nuevo acá, y se actualizaron los tests de C-37 que lo nombran: "El personal entra desde la portada", "El botón del personal en el celular", "Paciente con sesión en la portada", "Personal con sesión en la portada", "Volver a la portada desde otra página" y "Sin recursos de otros orígenes") RED: actualizar los escenarios "Abrir el menú en el celular", "Elegir una página en el celular", "Menú siempre visible en la computadora", "Menú de un visitante", "Menú del personal" y "El menú cambia al ingresar como paciente", y escribir "Cerrar el cajón con Escape o tocando fuera", y verlos fallar; GREEN: la barra lateral y el cajón en `layout.ts` (D4), con el foco gestionado y el encabezado simple sin sesión; TRIANGULATE: 320 px, cerrar al elegir y sin desplazamiento horizontal; REFACTOR: separar la estructura de la sesión. Verificar con `npm test -- navegacion sesion`
- [x] B.4 RED: escribir "Contraste del encabezado y del pie" y "El pie en todas las páginas" y verlos fallar; GREEN: encabezado y pie azules con texto blanco (D5) y el pie en todas las páginas; TRIANGULATE: enlaces y botones dentro del encabezado con foco visible sobre el azul; REFACTOR: tokens para las variantes sobre azul. Verificar con `npm test -- estilos`
- [x] B.5 RED: escribir "Hover y presionado del botón principal", "Foco visible con el teclado", "Botón deshabilitado" y "Transiciones con movimiento reducido" y verlos fallar; GREEN: los estados de cada estilo de botón con los tokens (D6) y el bloque de `prefers-reduced-motion`; TRIANGULATE: `.chip`, `.opcion` y las filas elegibles; REFACTOR: un solo juego de reglas. Verificar con `npm test -- estilos`
- [x] B.6 RED: escribir "Orden de los botones en los diálogos" y "Lugar del aviso en los diálogos" y verlos fallar; GREEN: `shared/ui/dialogo.ts` y los siete diálogos con ese molde (D8); TRIANGULATE: los textos de los botones y de los mensajes no cambian y los tests de cada diálogo siguen pasando (informar los que se actualizan); REFACTOR: quitar el armado repetido. Verificar con `npm test`
- [x] B.7 RED: escribir "Lista vacía con ilustración" y "Carga con indicador" y verlos fallar; GREEN: `shared/ui/iconos.ts`, `estado-vacio.ts` y el indicador de carga (D7), usados en Mis turnos, la agenda y las listas de Configuración; TRIANGULATE: `aria-hidden` en las ilustraciones y `aria-busy` en la carga; REFACTOR: una función por ilustración. Verificar con `npm test`
- [x] B.8 Actualizar `design-system/components.html` y sus capturas con la barra lateral, el pie y los estados nuevos
- [x] B.9 Verificación visual contra los mockups de B con el backend y el frontend levantados: capturas en `mockups/B/verify/` a 390 × 844 y 1440 × 900 con las cuentas de prueba (`cuentas-de-prueba.local.md`), diferencias por prioridad, consola, red y prueba con el teclado. Correr `npm test` y `npm run build` y abrir el PR de la etapa B

## C. Pantallas del paciente

- [x] C.1 Mockups de la etapa C en `mockups/C/` (portada, Reservar turno en los tres pasos, Mis turnos, Mi cuenta y confirmación de asistencia, con los componentes de B), capturas a 390 × 844 y 1440 × 900 y `README.md`. Verificar abriendo las capturas
- [x] C.2 Presentar los mockups de C y registrar la aprobación de la líder en `design.md`. No empezar el apply de C sin esto
- [x] C.3 Aplicar los componentes y estilos de B a las pantallas del paciente sin cambiar su comportamiento: los tests de C-12, C-33, C-37 y C-38 siguen pasando; escribir los tests de las reglas de CSS nuevas (espaciado, tarjetas, jerarquía) y verlos fallar antes. Verificar con `npm test`
- [x] C.4 Verificación visual contra los mockups de C (capturas en `mockups/C/verify/`, diferencias por prioridad, consola, red y teclado), `npm test`, `npm run build` y PR de la etapa C

## D. Pantallas del personal

- [x] D.1 Verificar que C-40 está mergeado (lo está) y traer `master`; mockups de la etapa D en `mockups/D/` (agenda del día y de la semana, Nuevo turno guiado, reprogramar y cancelar, buscador de pacientes y las siete pestañas de Configuración con sus diálogos), capturas a 390 × 844 y 1440 × 900 y `README.md`. Verificar abriendo las capturas
- [x] D.2 Presentar los mockups de D y registrar la aprobación de la líder en `design.md`. No empezar el apply de D sin esto
- [x] D.3 Aplicar los componentes y estilos de B a las pantallas del personal sin cambiar su comportamiento: los tests de C-09, C-33, C-34, C-35, C-36 y C-40 siguen pasando. Verificar con `npm test`
- [x] D.4 Verificación visual contra los mockups de D (capturas en `mockups/D/verify/`, diferencias por prioridad, consola, red y teclado), `npm test`, `npm run build` y PR de la etapa D

## 7. Cierre

- [ ] 7.1 Recorrer los deltas y verificar que los 28 escenarios tienen un test con el nombre del escenario, sin duplicados. Verificar con un `grep` de los nombres
- [ ] 7.2 Completar `Preview` y `Visual verify` de cada etapa en `design.md`; correr `openspec validate frontend-uniformidad-visual --strict` y verificar que el change es válido

## Workflow follow-up

- Cada etapa abre su PR de `change/C-39-<etapa>` a `master`; Sofía revisa y mergea (nunca commitear directo a `master`).
- Avisar en cada PR a Enzo (dueño de `frontend/`). Avisar antes de la etapa A: nadie más debería tocar `styles.css` mientras se parte.
- Archivar con `openspec archive frontend-uniformidad-visual` **solo después de la etapa D** y de los archives de `ingreso-unico-mi-cuenta` y `frontend-portada-y-acceso`; verificar los specs archivados (`frontend-navegacion` y `frontend-sistema-visual`).
- Marcar `[x]` en C-39 de `CHANGES.md` recién después del merge de la etapa D.
