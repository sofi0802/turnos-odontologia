# Mockups de C-41 `cuentas-del-personal`

- Design system revision: 3 (`design-system/README.md`; la aprobación de las revisiones 2 y 3 sigue pendiente con la líder y no cambian ningún valor de `tokens.css`).
- Approval: approved (2026-11-24, by Sofia Indovino, la líder; PR #81).
- Preview: rendered (2026-11-24). Las capturas están en `previews/` a 390 × 844 y 1440 × 900, con Playwright y `prefers-reduced-motion: reduce`:
  - Las pantallas sin diálogo, a página completa.
  - Las pantallas con diálogo, solo lo que entra en la pantalla.
  - `usuarios-alta-390-abajo.png` muestra el final del diálogo desplazado.
  - Ninguna pantalla tiene desplazamiento horizontal, errores de consola ni pedidos fallidos.

Los aprueba la líder antes del apply (modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de los grupos 5 y 6 de `tasks.md`.

## Brief

- **Para quién**:
  - **Administrador**: da, quita y restablece el acceso del personal, casi siempre desde la computadora.
  - **Recepción y odontólogos**: entran con una contraseña temporal y la cambian, muchas veces desde el celular.
- **Qué tienen que poder hacer**:
  - El administrador crea una cuenta y le pasa la contraseña temporal a la persona.
  - La persona la reemplaza en su primer ingreso, sin poder hacer otra cosa antes.
  - Cualquier usuario del personal cambia su contraseña cuando quiere.
- **Pantallas afectadas**: Configuración (pestaña nueva "Usuarios" y detalle del profesional), una página nueva `#/cambiar-contrasena` y el menú lateral del personal ("Mi contraseña").
- **Tamaños**: mobile first a 390 × 844. A 1440 × 900 la barra lateral queda a la izquierda y la lista usa el ancho de Configuración (64 rem).
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Layout de la etapa B de C-39, ya aplicada en el código: encabezado y pie azules, barra lateral por rol y molde único de diálogos.
  - Clases de Configuración copiadas de `frontend/src/styles/12-configuracion.css` (C-34).
  - Voz de `brand.md` (voseo, mensajes que dicen qué pasó y qué hacer) y logo provisorio.
- **Base**: `mockups.css` es una copia del de C-39 etapa B, con las reglas de Configuración y las de C-41 al final. Las nuevas son `.marca-temporal`, `.marca-propia`, `.acceso`, `.contrasena-caja`, `.lateral-cuenta`, `.usuario-enlace` y `.pagina-angosta`. Todas usan tokens existentes y no suman valores nuevos.
- **Restricciones**: datos 100 % ficticios (`admin`, `recepcion`, `bruno.prueba`, `ana.ejemplo`, Ana Ejemplo, Bruno Prueba). La contraseña temporal que se ve es un ejemplo inventado. No hay recursos remotos.

## Decisiones de diseño

- **El rol se elige con tarjetas de opción (radio)**, cada una con una línea que explica qué puede hacer. El selector de profesional aparece solo para Odontólogo (obligatorio) y Administrador (opcional).
- **La contraseña temporal va en una caja de fuente monoespaciada**, con "Copiar" y un aviso de alerta: "no se vuelve a mostrar". El alfabeto no tiene caracteres confusos (design D2), así que se puede dictar.
- **Desactivar es un botón de peligro y restablecer, uno secundario.** Los dos piden confirmación y la confirmación dice que se cierran las sesiones abiertas.
- **La fila de tu propia cuenta** no tiene acciones. Lleva la marca "Tu cuenta" y una nota que indica dónde cambiar la contraseña.
- **"Mi contraseña" va en el menú lateral, separado de las páginas.** La etapa B de C-39 oculta el usuario del encabezado en el celular, así que el menú es el camino que siempre está. En la computadora, el usuario del encabezado también lleva a esa página. Esto ajustó la spec `frontend-cuentas-personal` y la design D9.
- **La pantalla obligatoria no tiene menú**, solo el formulario y "Salir", y aclara por qué hay que cambiarla.
- **En Configuración, la barra de pestañas se desplaza** hasta dejar a la vista la pestaña elegida. En el celular "Usuarios" es la última. Los mockups lo hacen con un script mínimo y la app tiene que hacer lo mismo.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `usuarios-lista.html` | Pestaña "Usuarios" del administrador: la cuenta propia sin acciones, recepción y dos odontólogos activos (uno con "Contraseña temporal pendiente") y una cuenta inactiva con "Activar" | Lista de cuentas en la pestaña Usuarios; Desactivar, reactivar y restablecer desde la lista; Secciones según el rol |
| `usuarios-alta.html` | "Agregar usuario" con el rol Odontólogo y Ana Ejemplo elegidos | Alta de una cuenta del personal |
| `usuarios-alta-estados.html` | Cuatro estados del alta: Recepción sin selector de profesional, Administrador con profesional opcional, Odontólogo sin profesional (error de campo) y usuario repetido (409) | Alta de una cuenta del personal; Errores de las cuentas |
| `contrasena-temporal.html` | Diálogo "Cuenta creada" con la contraseña temporal, "Copiar" y el aviso | Contraseña temporal mostrada una sola vez |
| `usuarios-estados.html` | Confirmar desactivar, confirmar restablecer, diálogo después de restablecer y error al cargar la lista con "Reintentar" | Desactivar, reactivar y restablecer desde la lista; Contraseña temporal mostrada una sola vez |
| `profesional-acceso.html` | Detalle de profesionales para el administrador: Ana Ejemplo sin cuenta, con "Dar acceso", y Bruno Prueba con "Acceso: bruno.prueba" | Dar acceso desde el detalle del profesional |
| `profesional-dar-acceso.html` | El alta abierta desde "Dar acceso", con el rol y la profesional fijos | Dar acceso desde el detalle del profesional |
| `cambiar-contrasena-obligatoria.html` | Primer ingreso con contraseña temporal: sin menú, aviso y formulario | Cambio obligatorio de la contraseña temporal; Formulario de cambio de contraseña |
| `cambiar-contrasena-voluntaria.html` | Cambio voluntario, con "Mi contraseña" marcada en el menú y "Volver a la agenda" | Formulario de cambio de contraseña |
| `cambiar-contrasena-estados.html` | Repetición distinta (error de campo, no se envía) y contraseña actual incorrecta (422, campos vacíos) | Formulario de cambio de contraseña |
| `menu-celular-mi-contrasena.html` | Celular con el menú abierto: "Mi contraseña" debajo de las páginas (solo 390) | Formulario de cambio de contraseña (cambio voluntario desde el menú) |

## Cómo verlos

- **Capturas**: `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`.
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre, por ejemplo, `http://127.0.0.1:8765/openspec/changes/cuentas-del-personal/mockups/usuarios-lista.html`.
