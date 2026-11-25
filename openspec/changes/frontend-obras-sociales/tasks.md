# Tasks

> Rama `change/C-49-frontend-obras-sociales`. No se empieza el grupo 1 hasta que la líder apruebe el propose y los mockups (`design.md`, "UI Mockups", `Approval: approved`).
>
> TDD estricto en cada grupo: primero los tests (Vitest con jsdom), verlos fallar, después el código. Cada escenario de `specs/frontend-coberturas/spec.md` y de los deltas de `frontend-configuracion-consultorio`, `frontend-navegacion` y `frontend-cuenta-paciente` tiene su test con un `it(...)` cuyo título es el nombre del escenario (regla dura 7). Datos ficticios del seed de la KB. Sin `any`, con `strict`.
>
> Criterio de aceptación visual: los mockups aprobados en `mockups/` y sus capturas en `mockups/previews/`.

## 0. Aprobación y avisos

- [x] 0.1 Pedir a la líder la revisión del propose y de los mockups, con las decisiones "(a confirmar)" de `design.md` (D2 acceso de la recepción, D3 "Mi cuenta" del paciente, D5 nomenclador en la misma pestaña, D6 sin confirmación al desactivar, D7 cambio de cobertura en dos pasos); registrar su respuesta y `Approval: approved (<fecha>, by <persona>)` en `design.md` y `mockups/README.md`. Verificar que ambos archivos lo digan antes de seguir
- [ ] 0.2 Avisar a Enzo (dueño de `frontend/`) y a Emanuel (ficha clínica de C-44 en `pages/pacientes.ts` y Configuración de C-34) qué archivos se tocan; verificar que el aviso quede en la descripción del PR
- [x] 0.3 Si la líder cambia una decisión "(a confirmar)", actualizar specs, design, mockups y capturas con `openspec-update-change` y verificar con `openspec validate frontend-obras-sociales --strict`

## 1. Mensajes, íconos y modelo

- [x] 1.1 Escribir en `test/mensajes.test.ts` los tests de los diez códigos de D8 y después sumarlos a `MENSAJES_POR_CODIGO`; verificar con `npm test`
- [x] 1.2 Sumar los íconos `credencial` y `cuenta` a `shared/ui/iconos.ts` (D10) con su test de que existen y son `aria-hidden`; verificar con `npm test`
- [x] 1.3 Escribir los tests de `coberturas/modelo.ts` y después el módulo (D4); verificar con `npm test`. Cubren: número válido, con espacios, con `#`, de 30 y 31 caracteres; fin igual, posterior y anterior al inicio; largos del catálogo (120, 20, 80, 20, 200 y uno más); "Vigente", "Próxima" y "Finalizada" con hoy 2026-11-24 en Buenos Aires; textos de vigencia; "Vigente desde" por defecto
- [x] 1.4 Escribir los tests de `coberturas/api.ts` (rutas, cuerpos con `sigla: null` y `prestacionId: null`, lectura defensiva) y después el módulo; verificar con `npm test`

## 2. Configuración → Obras sociales

- [x] 2.1 Escribir los tests del delta "Secciones según el rol" (incluido "El odontólogo no ve Obras sociales") y de "Pestaña "Obras sociales" solo para el administrador" en `test/configuracion-pestanas.test.ts`; después sumar `gestionaCoberturas` a `permisos.ts` y la pestaña en `pages/configuracion.ts` (D5); verificar con `npm test`
- [x] 2.2 Escribir los tests de "Lista de obras sociales y planes" y "Nomenclador en la pestaña", y después `coberturas/catalogo.ts` con las dos listas; verificar con `npm test`
- [x] 2.3 Escribir los tests de "Alta y edición de obras sociales", "Alta y edición de planes" y "Alta y edición de códigos del nomenclador", y después los diálogos; verificar con `npm test`
- [x] 2.4 Escribir los tests de "Activar y desactivar sin borrar" y "Errores del catálogo", y después las acciones y los errores; verificar con `npm test`

## 3. Sección de cobertura del paciente

- [x] 3.1 Escribir los tests de "Cobertura vigente o particular" e "Historial de afiliaciones", y después `coberturas/seccion.ts` en modo lectura; verificar con `npm test`
- [x] 3.2 Escribir los tests de "Afiliar a un paciente" y "Validar la afiliación antes de enviarla", y después el diálogo "Afiliar" (D7); verificar con `npm test`
- [x] 3.3 Escribir los tests de "Errores de la API al afiliar o corregir" y "Corregir una afiliación", y después el diálogo "Corregir"; verificar con `npm test`
- [x] 3.4 Escribir los tests de "Cerrar una afiliación" (incluido el 409 `AFILIACION_CERRADA`) y después el diálogo; verificar con `npm test`
- [x] 3.5 Escribir los tests de "Solo lectura para el odontólogo" y "Coberturas accesibles" (foco al cerrar, etiquetas), y después ajustar la sección; verificar con `npm test`

## 4. Ficha y página "Coberturas"

- [x] 4.1 Escribir los tests de "Sección "Cobertura" en la ficha" y después montarla en `pages/pacientes.ts` entre la cabecera y el odontograma, con `destruir()` al cambiar de paciente; verificar con `npm test` y que los tests de C-44, C-46 y C-47 sigan pasando
- [x] 4.2 Escribir los tests de "Página "Coberturas" sin datos clínicos" y del delta "Página "Coberturas" para administración y recepción", y después `pages/coberturas.ts` y su registro en `app.ts` (D9); verificar con `npm test`, incluido que no se pide ninguna ruta clínica

## 5. Mi cuenta del paciente

- [x] 5.1 Escribir los tests del delta "Cuenta del paciente con sesión iniciada" y de "Mi cobertura del paciente", y después `coberturas/mi-cobertura.ts` y el cambio en `pages/mi-cuenta.ts` (D3); actualizar el test existente que esperaba la redirección a "Mis turnos" solo en lo que el delta cambia; verificar con `npm test`
- [x] 5.2 Escribir los tests del delta ""Mi cuenta" en el menú del paciente" y después `soloConSesion` en `Pagina` y el layout (D9); verificar con `npm test` y que los tests de navegación existentes sigan pasando

## 6. Privacidad

- [x] 6.1 Escribir los tests de "Cobertura solo en la pantalla": dirección, `localStorage`, `sessionStorage` y consola (con `vi.spyOn(console, ...)`) al afiliar, corregir, cerrar y en "Mi cobertura"; verificar con `npm test`

## 7. Estilos

- [x] 7.1 Crear `styles/22-coberturas.css` desde `mockups/coberturas.css` sin las reglas "Solo mockups" (D10), importarlo en `styles.css` y registrarlo en `REGLAS_POR_MODULO` de `test/estilos-modulos.test.ts`; verificar con `npm test` y `npm run build`

## 8. Verificación

- [x] 8.1 Correr `npm test`, `npx tsc --noEmit` y `npm run build` en `frontend/` y verificar que todo pase
- [x] 8.2 Verificación visual contra el backend de desarrollo con C-18: como `admin`, `recepcion`, `ana.ejemplo`, `paciente.uno` y `paciente.dos`, a 390 × 844 y 1440 × 900; comparar cada pantalla con su mockup aprobado, revisar la consola y los pedidos fallidos, guardar las capturas en `mockups/verify/` y anotar en `design.md` `Visual verify:` con las diferencias (blocker / major / minor) y lo no revisado
- [ ] 8.3 Abrir el PR a `master` con Enzo, Emanuel y Sofia como revisores; verificar que la descripción liste las decisiones confirmadas y los avisos de 0.2

## 9. Cierre

- [ ] 9.1 Después del merge del PR (no antes), marcar `[x]` C-49 en `CHANGES.md` en un PR aparte o en el que indique la líder
- [ ] 9.2 Antes de archivar, reconciliar "Menú según la sesión" de `frontend-navegacion` (D9): agregar un delta MODIFIED con "Coberturas" y "Mi cuenta" sobre el texto vigente en `openspec/specs` y verificar con `openspec validate frontend-obras-sociales --strict`

## Workflow follow-up

- Archivar el change después del merge y de la tarea 9.2.
- Pedir en un change de backend aparte, si la líder lo considera necesario, poder corregir la fecha de fin de una afiliación cerrada (design, "Limitación del backend").
