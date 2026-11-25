# Tasks

> Rama `change/C-50-frontend-presupuestos`. El grupo 1 no empieza hasta que la líder apruebe el propose y los mockups: `design.md`, sección "UI Mockups", con `Approval: approved`. Governance ALTO: datos de salud y dinero.
>
> TDD estricto en cada grupo:
> - primero los tests (Vitest con jsdom), verlos fallar y después el código;
> - cada escenario de `specs/frontend-aranceles/spec.md` y `specs/frontend-presupuestos/spec.md` tiene su `it(...)` con el nombre del escenario (regla dura 7);
> - datos ficticios del seed de la KB, sin `any` y con `strict`.
>
> Criterio de aceptación visual: los mockups aprobados en `mockups/` y sus capturas en `mockups/previews/`.

## 0. Aprobación y avisos

- [ ] 0.1 Pedir a la líder la revisión del propose y de los mockups, con las decisiones "(a confirmar)" de `design.md`:
  - D2: precios en "Prestaciones" y cobertura en cada plan;
  - D3: recepción en "Coberturas";
  - D4: estimado en el frontend;
  - D7: confirmación al aceptar y rechazar.

  Registrar su respuesta y `Approval: approved (<fecha>, by <persona>)` en `design.md` y `mockups/README.md`, y verificar que ambos lo digan antes de seguir.
- [ ] 0.2 Avisar a Enzo (dueño de `frontend/`) y a Emanuel (ficha de C-44 en `pages/pacientes.ts` y Configuración de C-34) qué archivos se tocan; verificar que el aviso quede en la descripción del PR
- [ ] 0.3 Si la líder cambia una decisión "(a confirmar)", actualizar specs, design, mockups y capturas con `openspec-update-change` y verificar con `openspec validate frontend-presupuestos --strict`

## 1. Mensajes y modelo

- [ ] 1.1 Escribir en `test/mensajes.test.ts` los tests de los cinco códigos de D8 y después sumarlos a `MENSAJES_POR_CODIGO`; verificar con `npm test`
- [ ] 1.2 Escribir los tests de `presupuestos/modelo.ts` y después el módulo (D4 y D5); verificar con `npm test`. Cubren:
  - el estimado en centavos con los casos de C-19: 15000 al 100 %, 25000 al 50 %, 25000.15 al 50 % (12500.08 y 12500.07), cantidad 2 y particular;
  - los totales;
  - el formato "$ 15.000,00";
  - el estado escrito, con vencido;
  - la validación de pieza FDI, cara de la pieza, cara sin pieza, cantidad 0, 1, 99 y 100, y código requerido;
  - el parseo del precio con coma y con punto, cero, negativo, tres decimales y el máximo;
  - el cuerpo del `POST`.
- [ ] 1.3 Escribir los tests de `presupuestos/api.ts` y `aranceles/api.ts` y después los módulos; verificar con `npm test`. Cubren:
  - las rutas;
  - la lectura defensiva de importes, de `pieza` y `cara` ausentes y de `vencido`;
  - el `PUT` con `precio` numérico y `porcentaje` entero;
  - el pedido de "Mis presupuestos" sin parámetros.
- [ ] 1.4 Sumar los ids de la obra social y del plan a la afiliación que lee `coberturas/api.ts` (D5), con su test de lectura defensiva; verificar con `npm test` y que los tests de C-49 sigan pasando

## 2. Aranceles en Configuración

- [ ] 2.1 Escribir los tests de "Precio particular en la pestaña Prestaciones", "Cambiar el precio particular" y "Aranceles solo para el administrador" (sin el de privacidad), y después `aranceles/precios.ts` con el gancho `extra` de "Prestaciones" (D2); verificar con `npm test` y que los tests de `configuracion-catalogos` sigan pasando
- [ ] 2.2 Escribir los tests de "Cobertura de un plan por código", "Guardar la cobertura de un plan" y "Porcentajes válidos", y después `aranceles/cobertura-plan.ts` y el botón "Cobertura" de cada plan en `coberturas/catalogo.ts`; verificar con `npm test` y que los tests de C-49 sigan pasando

## 3. Sección de presupuestos

- [ ] 3.1 Escribir los tests de "Lista de presupuestos del paciente" y "Detalle de un presupuesto", y después `presupuestos/seccion.ts` en modo lectura; verificar con `npm test`
- [ ] 3.2 Escribir los tests de "Armar un presupuesto", "Código de cada ítem" y "Piezas sugeridas del odontograma", y después `presupuestos/armado.ts` (D6); verificar con `npm test`
- [ ] 3.3 Escribir los tests de "Estimado antes de emitir" y "Cálculo del estimado" en el diálogo, y después conectar el modelo al armado; verificar con `npm test`
- [ ] 3.4 Escribir los tests de "Validar el presupuesto antes de emitirlo", "Emitir un presupuesto" y "Errores de la API al emitir", y después el envío y la ubicación de `items[i].<campo>` (D6); verificar con `npm test`
- [ ] 3.5 Escribir los tests de "Aceptar o rechazar un presupuesto" y "Rechazos al aceptar o rechazar", y después las acciones con confirmación (D7); verificar con `npm test`
- [ ] 3.6 Escribir los tests de "Presupuestos accesibles" y "Foco y pantallas chicas", y después ajustar la sección y el diálogo; verificar con `npm test`

## 4. Ficha, Coberturas y Mi cuenta

- [ ] 4.1 Escribir los tests de "Sección Presupuestos en la ficha" y "Solo lectura para el administrador en la ficha", y después montarla en `pages/pacientes.ts` debajo de "Cobertura", con `destruir()` al cambiar de paciente; verificar con `npm test` y que los tests de C-44, C-46, C-47 y C-49 sigan pasando
- [ ] 4.2 Escribir los tests de "Presupuestos en la página Coberturas" (incluido que no se pide ninguna ruta clínica ni `/sugerencias`), y después montarla en modo `recepcion` o `lectura` en `pages/coberturas.ts` (D3); verificar con `npm test`
- [ ] 4.3 Escribir los tests de "Mis presupuestos del paciente" y "Mis presupuestos vacíos o con error", y después `presupuestos/mis-presupuestos.ts` debajo de "Mi cobertura" en `pages/mi-cuenta.ts`; verificar con `npm test`

## 5. Privacidad

- [ ] 5.1 Escribir los tests de "Presupuestos solo en la pantalla" y del escenario de privacidad de `frontend-aranceles`. Revisar la dirección, `localStorage`, `sessionStorage` y la consola (con `vi.spyOn(console, ...)`) al emitir, aceptar, rechazar, cambiar un precio, guardar una cobertura y en "Mis presupuestos". Comprobar que el test falla con una fuga a propósito y después sacarla; verificar con `npm test`

## 6. Estilos

- [ ] 6.1 Crear `styles/25-presupuestos.css` desde los estilos de los mockups, sin las reglas "Solo mockups" (D9). Importarlo al final de `styles.css` y registrarlo en `test/estilos.ts` y en `REGLAS_POR_MODULO`; verificar con `npm test` y `npm run build`

## 7. Verificación

- [ ] 7.1 Correr `npm test`, `npx tsc --noEmit` y `npm run build` en `frontend/` y verificar que todo pase
- [ ] 7.2 Hacer la verificación visual contra el backend de desarrollo con C-19, con un paciente que tenga cuenta:
  - usuarios `admin`, `recepcion`, `ana.ejemplo` y un paciente, a 390 × 844 y 1440 × 900;
  - comparar cada pantalla con su mockup aprobado y revisar la consola y los pedidos fallidos;
  - guardar las capturas en `mockups/verify/` y anotar en `design.md` `Visual verify:` con las diferencias (blocker / major / minor) y lo no revisado.
- [ ] 7.3 Mergear `master` en la rama si avanzó, resolver conflictos y repetir 7.1; abrir el PR a `master` con Enzo, Emanuel y Sofia como revisores, y verificar que la descripción liste las decisiones confirmadas y los avisos de 0.2

## 8. Cierre

- [ ] 8.1 Después del merge del PR (no antes), marcar `[x]` C-50 en `CHANGES.md` en un PR aparte o en el que indique la líder

## Workflow follow-up

- Archivar el change después del merge.
- Si la líder prefiere un cálculo del backend en lugar del estimado (D4), pedir un change de backend aparte para `POST .../presupuestos/simulacion` y reemplazar el estimado cuando esté.
- Imprimir o descargar el presupuesto queda como mejora posterior (design, "Open Questions").
