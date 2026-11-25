# Mockups de C-50 `frontend-presupuestos`

- **Design system revision**: 5, aprobada. No suma tokens: `presupuestos.css` usa solo los de la Revision 5.
- **Approval**: pending. Los aprueba la líder (Sofia Indovino) antes del apply; son el criterio de aceptación visual.
- **Preview**: rendered (2026-11-25). Capturas de página completa en `previews/`, a 1440 × 900 y 390 × 844, hechas con Playwright (Chromium) sobre un servidor estático local. En ninguna pantalla:
  - hay desplazamiento horizontal a 390 px;
  - hay errores ni advertencias de consola;
  - falla algún pedido.

## Cómo verlos

Los mockups cargan:
- `design-system/tokens.css`;
- `frontend/src/styles.css` tal como está en `master`, con `24-coberturas.css` de C-49;
- `presupuestos.css`, que es la propuesta del módulo `25-presupuestos.css`. Sus reglas marcadas "Solo mockups" no pasan al módulo.

`mockup.js` arma el encabezado y la barra lateral según el rol de la página (`data-rol`), con el menú vigente después de C-15, C-22 y C-49. `design-system/brand.js` pone el nombre de la marca.

Para evitar bloqueos de `file://`, serví la raíz del repo y abrí la página desde ahí:

```powershell
python -m http.server 8768 --bind 127.0.0.1
```

Después abrí, por ejemplo, `http://127.0.0.1:8768/openspec/changes/frontend-presupuestos/mockups/presupuestos-ficha.html`.

## Brief

- **Para quién**:
  - el administrador, que carga los precios particulares y la cobertura de cada plan;
  - el odontólogo, que arma, emite, acepta y rechaza presupuestos en la ficha (PA-23);
  - la recepción, que los consulta sin pieza ni cara (PA-24);
  - el paciente, que ve los suyos en "Mi cuenta" (PA-27).
- **Qué tienen que poder hacer**:
  - armar un presupuesto rápido a partir del odontograma;
  - entender antes de emitir cuánto cubre la obra social y cuánto paga el paciente;
  - distinguir de un vistazo un presupuesto pendiente, vencido o cerrado;
  - no aceptar ni rechazar por error.
- **Tamaños**: 390 × 844 (todo apilado, el armado a pantalla completa) y 1440 × 900 (ítems en una fila de cuatro campos, totales alineados a la derecha).
- **Restricciones**:
  - tokens del design system y estados escritos, no solo por color;
  - controles de 44 px y diálogos con el molde de `shared/ui/dialogo.ts`;
  - datos ficticios del seed de la KB;
  - ninguna pieza, cara ni importe en la dirección ni en el navegador.

## Pantallas

| Archivo | Qué muestra | Requisitos de los specs |
|---|---|---|
| `config-precios.html` | Admin, Configuración → "Prestaciones" con el precio o "Sin precio" y "Cambiar precio"; el diálogo normal y con un precio de tres decimales | `frontend-aranceles`: Precio particular en la pestaña "Prestaciones", Cambiar el precio particular |
| `config-cobertura-plan.html` | Admin, "Obras sociales" con "Cobertura" en cada plan; el diálogo de Plan Base; validación y falla a mitad del guardado | `frontend-aranceles`: Cobertura de un plan por código, Guardar la cobertura de un plan, Porcentajes válidos |
| `presupuestos-ficha.html` | Odontóloga en la ficha de Paciente Uno: un pendiente con el detalle abierto, uno vencido y uno aceptado | Sección "Presupuestos" en la ficha, Lista de presupuestos del paciente, Detalle de un presupuesto, Aceptar o rechazar un presupuesto |
| `presupuestos-armado.html` | "Nuevo presupuesto" con dos piezas sugeridas y un ítem manual, el estimado y los totales; la validación previa con una prestación de varios códigos; un rechazo de la API | Armar un presupuesto, Código de cada ítem, Piezas sugeridas del odontograma, Estimado antes de emitir, Cálculo del estimado, Validar el presupuesto antes de emitirlo, Errores de la API al emitir |
| `presupuestos-confirmar.html` | Confirmación de "Aceptar" y de "Rechazar", y los avisos de `PRESUPUESTO_VENCIDO` y `PRESUPUESTO_CERRADO` | Aceptar o rechazar un presupuesto, Rechazos al aceptar o rechazar |
| `presupuestos-admin.html` | Admin en la ficha: solo lectura, con pieza y cara | Solo lectura para el administrador en la ficha |
| `presupuestos-recepcion.html` | Recepción en "Coberturas": los presupuestos de Paciente Uno sin pieza ni cara | Presupuestos en la página "Coberturas" |
| `mis-presupuestos.html` | Paciente en "Mi cuenta": sus presupuestos, sin presupuestos y falla | Mis presupuestos del paciente, Mis presupuestos vacíos o con error |
| `presupuestos-estados.html` | Cargando, falla con "Reintentar", lista vacía y armado sin sugerencias | Sección "Presupuestos" en la ficha, Piezas sugeridas del odontograma |

Capturas: `previews/<archivo>-1440.png` y `previews/<archivo>-390.png` para cada archivo de la tabla.

## Decisiones visuales

- **Un presupuesto es una tarjeta**:
  - arriba, la fecha con la etiqueta de estado y, debajo, el vencimiento, el profesional y la cobertura usada;
  - a la derecha, las acciones;
  - abajo, los tres totales con su rótulo: el copago, en negrita y con el color de acento, porque es lo que paga el paciente.
- **Estados escritos**:
  - "Pendiente" en info;
  - "Vencido" en alerta, con la ayuda "ya no se puede aceptar";
  - "Aceptado" en ok;
  - "Rechazado" en gris.

  Los cerrados van sobre fondo neutro.
- **Detalle en renglones**: la prestación, el código, la pieza y la cara, y la cantidad por el precio, con sus tres importes a la derecha en la computadora y debajo en el celular.
- **Armado por ítems**:
  - cada ítem es una tarjeta numerada; si viene del odontograma lleva su origen ("Caries", "A tratar");
  - el estimado va en una línea debajo de los campos;
  - los totales van fijos al pie, con la aclaración de que el importe final lo calcula el sistema.
- **"Código"** aparece solo cuando la prestación tiene más de uno, a lo ancho del ítem, para no ocupar lugar en el caso común.
- **Acciones con confirmación**: "Rechazar" es una acción de peligro (borde rojo); "Aceptar" es la principal. Las dos piden confirmar.
