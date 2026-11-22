# Design system del proyecto

Mode: system-and-mockups
Revision: 1
Approval: approved (2026-11-22, by user)
Decisión: Sofia Indovino (líder), 2026-11-22. Reemplaza la política `off` del 2026-11-18 a pedido explícito de la líder.

## Qué significa

- Hay un sistema visual común en esta carpeta. Todo cambio visual del frontend lo usa.
- **Cada change visual posterior** (por ejemplo C-12 `frontend-reserva-paciente` o un rediseño de la agenda de C-09) presenta **un mockup HTML** en `openspec/changes/<change>/mockups/`. La líder lo aprueba antes de implementar.
- Reparto acordado: la líder define el sistema; **Enzo, dueño de `frontend/`, lo aplica** en sus changes.

## Archivos

| Archivo | Para qué |
|---|---|
| `tokens.css` | Única fuente de colores, tipografía, espaciado, radios, sombras, foco y movimiento (con `prefers-reduced-motion`). Al final trae **alias con los nombres que ya usa `frontend/src/styles.css`** (`--color-acento`, `--espacio-1`, …), así el frontend lo adopta sin renombrar. |
| `brand.md` | Nombre, logo, tono, voz y reglas de uso, con su fuente o `(TBD)`. |
| `brand.js` | Constante `BRAND_NAME` y binding de `[data-brand-name]` para las vistas previas HTML. |
| `components.html` | Referencia de componentes y estados: encabezado, botones, campos, avisos, estados del turno, agenda del día, día vacío, reserva móvil del paciente, comprobante y diálogo. |
| `assets/logo.svg` | Logo **provisorio**, rotulado como tal. |
| `previews/` | Capturas a 1440×900 y 390×844 (página completa). |

## Fuentes

- Auditoría de la UI existente: `frontend/src/styles.css` y `frontend/index.html` (C-09). Se conservan el acento `#0b6e8a`, la fuente del sistema, el radio de 0.5rem y los controles de 44px.
- Dominio: `knowledge-base/05_reglas_de_negocio.md` (estados del turno, sobreturno, ausencia, margen) y `knowledge-base/03_actores_y_roles.md`.
- Sin fuentes remotas, sin cuentas externas ni Figma. Todos los datos de ejemplo son ficticios (RN-GL-01).

## Decisiones abiertas (TBD)

1. **Nombre del producto**: hoy "Turnos odontología" (descriptivo).
2. **Logo**: el de `assets/logo.svg` es un placeholder.
3. **Modo oscuro**: no está definido. Se agrega como Revision 2 si se pide.

## Cómo reemplazar lo provisorio

- **Nombre**: editar `BRAND_NAME` en `brand.js` (vistas previas) y `<title>` en `frontend/index.html` más el texto de `.marca` del encabezado del frontend. Actualizar `brand.md`.
- **Logo**: reemplazar `assets/logo.svg` manteniendo el nombre del archivo (alto de referencia de 32px en el encabezado). Actualizar `brand.md`.
- **Un valor visual**: cambiar solo el token en `tokens.css`. Cambiar un token compartido sube la `Revision`, vuelve `Approval` a `pending` y obliga a revisar los mockups afectados.

## Cómo ver las vistas previas

Abrir `design-system/components.html` con doble clic en el navegador (no requiere servidor), o ver las capturas en `previews/`.
