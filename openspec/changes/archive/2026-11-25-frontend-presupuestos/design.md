# Design

## Context

La motivación está en `proposal.md` ("Why").

C-19 dejó esta API (specs en `openspec/changes/presupuestos-y-cobertura/specs/`):

- **Aranceles**:
  - `GET /api/v1/aranceles` (personal) → `[{prestacion:{id,nombre}, activa, precio|null, actualizadoEn|null}]`;
  - `PUT /api/v1/aranceles/{prestacionId}` (`ADMIN`) con `{precio}`.
- **Cobertura por plan**:
  - `GET .../obras-sociales/{id}/planes/{planId}/coberturas` (personal) → `[{nomencladorId, codigo, descripcion, activo, porcentaje}]`, solo los códigos con porcentaje cargado;
  - `PUT .../coberturas/{nomencladorId}` (`ADMIN`) con `{porcentaje}`.
- **Presupuestos del personal**, en `/api/v1/pacientes/{id}/presupuestos`:
  - `GET` (lista, la más reciente primero) y `GET /{presupuestoId}`;
  - `POST` (emitir), `/sugerencias`, `/{presupuestoId}/aceptar` y `/{presupuestoId}/rechazar`, solo `ODONTOLOGO`;
  - a la `RECEPCIONISTA` la API le manda los ítems **sin** `pieza` ni `cara` (PA-24).
- **Presupuestos del paciente**: `GET /api/v1/paciente/presupuestos` y `/{presupuestoId}`, identificado por la sesión.
- **Respuesta**: `{id, fecha, venceEl, estado, vencido, profesional:{id,nombre}, particular, cobertura:{afiliacionId, obraSocial, plan, numeroAfiliado}|null, items:[{id, prestacion, codigoNomenclador, pieza?, cara?, cantidad, precioUnitario, porcentajeCobertura, importe, cobertura, copago}], totales:{precio, cobertura, copago}, creadoEn}`. Los importes llegan como números JSON.
- **No hay un endpoint para calcular sin emitir**: los importes finales llegan recién con el `201`.

En el frontend:

- C-49 dejó:
  - `coberturas/seccion.ts`, con la sección "Cobertura" y su patrón de carga aparte (`vigente()`, `destruir()`, un contador para descartar respuestas viejas);
  - `coberturas/ui.ts` (`abrirDialogoDesde`, `marcarErrores`, `mostrarRechazo`);
  - `coberturas/catalogo.ts` (pestaña "Obras sociales") y `pages/coberturas.ts`;
  - "Mi cobertura" en `pages/mi-cuenta.ts`, con el comentario de que C-50 suma "Mis presupuestos".
- `configuracion/catalogo.ts` arma "Prestaciones" con `montarCatalogo` y su gancho `extra(fila, item, contexto)`, que ya usa el detalle del profesional.
- `ficha/odontograma-modelo.ts` tiene `carasDe(pieza)` e `imagenes-modelo.ts` tiene `esPiezaFdi`: sirven para validar pieza y cara igual que el backend.
- `coberturas/api.ts` lee la cobertura del paciente, pero descarta los ids de la obra social; el estimado los necesita.

## Goals / Non-Goals

**Goals:**
- Que el odontólogo arme y emita un presupuesto sin salir de la ficha, con un estimado claro antes de emitir.
- Que la recepción y el administrador vean los presupuestos sin datos clínicos fuera de lo permitido (PA-24).
- Que el administrador cargue precios y coberturas en las pestañas que ya conoce.

**Non-Goals:**
- Cambiar la API de C-19 o agregar endpoints (por ejemplo, uno para simular). Si la líder lo prefiere, va en un change de backend aparte (D4).
- Imprimir el presupuesto, mandarlo por email o WhatsApp, o cobrarlo (C-22 ya cobra los turnos, no los presupuestos).
- Topes, autorizaciones ni validación online con la obra social (C-21).

## Decisions

### D1. Dos capacidades nuevas y ningún delta
- `frontend-aranceles` (Configuración) y `frontend-presupuestos` (ficha, "Coberturas" y "Mi cuenta").
- No se modifica ningún requisito vigente:
  - las secciones nuevas se especifican como ADDED en estas capacidades;
  - la pestaña de precios es la que ya existe ("Prestaciones"), así que "Secciones según el rol" no cambia.
- Razón: los deltas de C-49 sobre `frontend-configuracion-consultorio`, `frontend-cuenta-paciente` y `frontend-navegacion` siguen sin archivar, y un segundo delta sobre el mismo requisito obligaría a reconciliar dos veces.

### D2. Precios en "Prestaciones" y cobertura en cada plan *(confirmada por la líder, 2026-11-25)*
**Recomendación**:
- **Precio**: el `ADMIN` ve en cada fila de "Prestaciones" el precio ("$ 15.000,00" o "Sin precio") y "Cambiar precio", con el gancho `extra` de `montarCatalogo`. Los aranceles se piden aparte de la lista: si fallan, la lista se sigue viendo con su aviso y "Reintentar".
- **Cobertura**: en "Obras sociales", cada plan suma "Cobertura", que abre un diálogo con los códigos activos del nomenclador y un campo de porcentaje por código.

Alternativas:
- *Una pestaña "Aranceles" nueva*: junta precios y coberturas, pero suma una pestaña a las diez de Configuración y obliga a modificar "Secciones según el rol" (ver D1).
- *Cobertura como matriz plan × código*: rápida de llenar, pero no entra a 390 px y mezcla obras sociales distintas.

### D3. Recepción en "Coberturas" *(confirmada por la líder, 2026-11-25)*
**Recomendación**: la recepción ve los presupuestos en la página "Coberturas" de C-49, debajo de la cobertura y en solo lectura, porque no tiene acceso a la ficha (C-44).
- El administrador los ve en los dos lugares.
- Aunque la API ya filtra pieza y cara para la recepción, el componente tiene el modo `sinDatosClinicos`, que no los dibuja aunque llegaran (defensa en profundidad, RN-CL-01).

Alternativa: *un botón "Presupuestos" en el detalle del turno de la agenda*. Se descarta por ahora, igual que en C-49: toca la agenda.

### D4. Estimado en el frontend *(confirmada por la líder, 2026-11-25)*
El alcance pide ver el importe, la cobertura y el copago antes de emitir, y la API no tiene un cálculo sin emitir.

**Recomendación**: un estimado en `presupuestos/modelo.ts` con la misma regla de RN-PR-03:
- se calcula en **centavos enteros** para no arrastrar errores de punto flotante;
- la cobertura se redondea *half up*: `Math.floor((importe × porcentaje + 50) / 100)`, sobre centavos;
- toma los precios de `GET /aranceles`, el plan de la cobertura de hoy (`GET /pacientes/{id}/cobertura`) y sus porcentajes (`GET .../coberturas`);
- siempre está rotulado "Estimado" y, después de emitir, se muestran los valores de la API.

Alternativas:
- *Pedir al backend un `POST .../presupuestos/simulacion`*: no duplica la regla, pero exige un change de backend antes (Juan Sebastián) y frena C-50.
- *Emitir sin estimado*: más simple, pero no cumple el alcance y obliga a rechazar y volver a emitir para corregir.

El riesgo de que el estimado y el backend difieran se cubre con los mismos casos en los tests del modelo: 25000.15 × 50 % = 12500.08 y los totales de C-19.

### D5. Módulos
- **`presupuestos/api.ts`**: los pedidos y la lectura defensiva. Los importes se leen como número y se convierten a centavos; un ítem sin `pieza` ni `cara` (recepción) se lee con `null`. No escribe en la consola.
- **`presupuestos/modelo.ts`**, con funciones puras:
  - el estimado (D4), la validación de cada ítem (pieza FDI con `esPiezaFdi`, cara con `carasDe`, cantidad de 1 a 99, código requerido);
  - el estado escrito y el formato de pesos con `Intl.NumberFormat('es-AR', {style:'currency', currency:'ARS'})`, ajustado a "$ 15.000,00";
  - el parseo del precio que escribe el admin, que acepta coma o punto;
  - el cuerpo del `POST`.
- **`presupuestos/seccion.ts`**: `crearSeccionPresupuestos({ pacienteId, nombrePaciente, modo: 'odontologo' | 'lectura' | 'recepcion', api, mensajeDeError, vigente })` → `{ contenedor, destruir() }`. `modo` decide los botones y si se dibujan pieza y cara.
- **`presupuestos/armado.ts`**: el diálogo "Nuevo presupuesto". El borrador vive solo en memoria y se pierde al cerrar.
- **`presupuestos/mis-presupuestos.ts`**: la sección del paciente.
- **`aranceles/api.ts`**, **`aranceles/precios.ts`** (el `extra` de "Prestaciones" y el diálogo "Cambiar precio") y **`aranceles/cobertura-plan.ts`** (el diálogo "Cobertura").
- **`coberturas/api.ts`**: suma los ids de la obra social y del plan a la afiliación leída (`obraSocial.id`, ya en la respuesta de C-18). Así el estimado arma la ruta de `.../coberturas` sin otro pedido.

### D6. Armado del presupuesto
- **Diálogo amplio**: el molde de `shared/ui/dialogo.ts` con una clase de ancho grande en la computadora y pantalla completa a 390 px.
- **Ítems como tarjetas**, cada una con:
  - "Prestación", "Código" (solo si hace falta), "Pieza", "Cara" y "Cantidad";
  - una línea con el estimado y "Quitar";
  - el origen si viene del odontograma ("Caries", "A tratar").
- **"Agregar piezas sugeridas"** pide `/sugerencias` al tocarlo, no al abrir: el diálogo abre rápido y el odontólogo decide.
- **Totales y cobertura** quedan fijos al pie, arriba de los botones "Cancelar" y "Emitir presupuesto".
- **Validación**: la previa de cada ítem usa `marcarErrores`. Los `items[i].<campo>` de la API se ubican con un mapa de destinos por ítem, extendiendo `mostrarRechazo` con un resolvedor de campo.
- **Cuerpo del `POST`**: `nomencladorId` es el id del único código activo de la prestación, el elegido, o `null` si no tiene. El backend vuelve a validar todo.

### D7. Aceptar y rechazar con confirmación *(confirmada por la líder, 2026-11-25)*
- Las dos acciones son irreversibles (RN-PR-05), así que piden confirmación con `abrirDialogo` en modo `peligro` para "Rechazar" y normal para "Aceptar". El resumen dice la fecha, el copago y "No se puede deshacer.".
- Alternativa: aceptar sin confirmar, como activar y desactivar en C-34. Se descarta porque aquellas acciones sí se pueden revertir.

### D8. Mensajes
Se suman a `MENSAJES_POR_CODIGO`:
- `ARANCEL_INEXISTENTE`: "Una de las prestaciones ya no tiene precio. Revisá los aranceles.";
- `PRESUPUESTO_VENCIDO`: "El presupuesto venció: ya no se puede aceptar. Emití uno nuevo.";
- `PRESUPUESTO_CERRADO`: "Ese presupuesto ya estaba aceptado o rechazado.";
- `CODIGO_NOMENCLADOR_REQUERIDO`: "Una de las prestaciones tiene varios códigos: elegí uno.";
- `CODIGO_NOMENCLADOR_INVALIDO`: "El código elegido no corresponde a la prestación o está inactivo.".

`PRESTACION_INACTIVA`, `PRESTACION_INEXISTENTE`, `PIEZA_INVALIDA` y `CARA_INVALIDA` ya existen y sirven tal cual.

### D9. Estilos e íconos
- `frontend/src/styles/25-presupuestos.css`, importado al final de `styles.css` y registrado en `test/estilos.ts` y en `REGLAS_POR_MODULO`. Solo tokens de la Revision 5; sin `outline: none`.
- Los importes van con `font-variant-numeric: tabular-nums` y alineados a la derecha en la computadora.
- No hay íconos nuevos: la sección usa `documento` (C-47).

### D10. Privacidad
- **Fuera del navegador**: ningún `console.*` con datos, nada en `localStorage` ni `sessionStorage`, y la dirección queda en la ruta de la página.
- **Borrador**: vive en la memoria del diálogo.
- **Mensajes de error**: nunca incluyen piezas, caras ni importes escritos.
- **Recepción**: ni la sección ni sus pedidos tocan rutas clínicas. El modo `recepcion` no pide `/sugerencias` ni dibuja pieza ni cara.

## Risks / Trade-offs

- **[Riesgo] El estimado difiere del cálculo del backend.** → Centavos enteros, los mismos casos de prueba que C-19 y el rótulo "Estimado". La pantalla siempre termina mostrando lo que devuelve la API.
- **[Riesgo] Datos de salud a la recepción.** → La API ya filtra (PA-24) y el modo `recepcion` no los dibuja; un test verifica que no aparecen "Pieza" ni el número de pieza.
- **[Riesgo] Conflictos con C-51** en `styles.css`, Configuración y `mensajes.ts`. → Un PR por vez, en orden (C-50 antes que C-51).
- **[Trade-off] Muchos pedidos al abrir el armado**: aranceles, nomenclador, cobertura del paciente y porcentajes del plan. Son livianos y se piden una vez por diálogo; hasta tenerlos, "Emitir presupuesto" queda deshabilitado.
- **[Trade-off] Varios `PUT` al guardar la cobertura de un plan**: la API es por código. Se mandan solo los cambiados, en orden, y si uno falla se dice qué quedó guardado.

## Migration Plan

Sin migraciones ni cambios de backend. Rollback: revertir el PR. Los precios, las coberturas y los presupuestos cargados quedan en el backend.

## Open Questions

- ¿El presupuesto debería poder imprimirse o descargarse para dárselo al paciente? No cambia estos specs; se puede sumar después.

## UI Mockups

- Design system revision: 5 (sin tokens nuevos).
- Decision: yes (2026-11-25). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-presupuestos/mockups/` (ver su `README.md`).
- Approval: approved (2026-11-25, by Sofia Indovino). Aprobó el PR #139 y confirmó D2, D3, D4 y D7 tal como están recomendadas.
- Preview: rendered (2026-11-25). Nueve pantallas en `mockups/previews/`, a 390 × 844 y 1440 × 900, con Playwright sobre un servidor estático local; sin desplazamiento horizontal, sin errores de consola ni pedidos fallidos.
- Visual verify: passed with minor differences (2026-11-25). Frontend con Vite contra el backend de desarrollo (perfil `dev`, PostgreSQL 16 en el puerto 55432), Playwright con Chromium, 8 pantallas × 390 × 844 y 1440 × 900 (16 capturas en `mockups/verify/`). Sin errores ni advertencias de consola, sin pedidos fallidos y sin desplazamiento horizontal en ninguna. En "Coberturas", la sección de la recepción no contiene "Pieza".
  - Revisadas: `config-precios` y `config-cobertura-plan` (admin), `presupuestos-ficha`, `presupuestos-armado` y `presupuestos-confirmar` (`ana.ejemplo`), `presupuestos-admin` (admin), `presupuestos-recepcion` (`recepcion`) y `mis-presupuestos` (paciente).
  - Diferencias (todas minor):
    1. "Cara" está habilitada aunque no haya pieza, para que la validación "Indicá la pieza para elegir una cara." (del spec) se pueda alcanzar; el mockup la mostraba deshabilitada.
    2. El profesional se ve como lo devuelve la API ("Ana Ejemplo") y no "Dra. Ana Ejemplo".
    3. "Mis presupuestos" muestra el número de afiliado de la cobertura usada; el mockup no lo mostraba.
    4. Solo de datos: en la base `dev` no hay presupuestos vencidos, aceptados ni rechazados, y `N-0301` está inactivo, así que "Extracción simple" sale "Sin código".
  - No revisado en el navegador:
    - el pie del armado (totales y botones), que queda dentro del scroll del diálogo modal y la captura de página completa no lo incluye;
    - los estados de carga y error;
    - los presupuestos vencidos y cerrados;
    - los rechazos 409 y las piezas sugeridas: el odontograma `dev` está vacío.

    Todo eso está cubierto por los tests con jsdom.

