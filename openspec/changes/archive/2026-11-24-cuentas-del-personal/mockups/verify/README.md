# Verificación visual de C-41 contra los mockups aprobados

- Fecha: 2026-11-24, rama `change/C-41-cuentas-del-personal`.
- Entorno: PostgreSQL en Docker con V21 aplicada, backend con `bootRun` y frontend con Vite en `http://localhost:5173`.
- Navegador: Playwright, con `prefers-reduced-motion: reduce`, a 390 × 844 y 1440 × 900. Para tener dos sesiones a la vez se usó un segundo contexto.
- Cuentas: cuentas de prueba locales con datos ficticios (`admin.prueba`, `bruno.prueba`). Las credenciales están en `cuentas-prueba.local.md`, que no se commitea.
- Resultado: **passed**, con las diferencias menores de abajo. No hubo errores de consola ni respuestas 5xx.

## Flujo recorrido

1. `admin.prueba` abre Configuración → Usuarios y ve las cuentas, con "Tu cuenta" en la propia y sin acciones en ella.
2. En Profesionales → Bruno Prueba → "Acceso a la aplicación" → "Dar acceso", crea `bruno.prueba`. El diálogo muestra la contraseña temporal una sola vez; al cerrarlo, la contraseña sale de la página y no queda en `localStorage` ni `sessionStorage`.
3. La lista muestra "Contraseña temporal pendiente" en `bruno.prueba`.
4. `bruno.prueba` entra con la temporal en otra sesión y queda en `#/cambiar-contrasena`, sin menú y solo con "Salir". Si pide `#/agenda`, vuelve al cambio.
5. Cambia la contraseña y llega a la agenda con "Tu contraseña se cambió.".
6. El administrador restablece la contraseña de Bruno, confirmando, y ve la temporal nueva. Bruno entra con ella y la cambia.
7. El administrador desactiva a Bruno con la sesión de él abierta. El siguiente pedido de Bruno lo lleva a `#/mi-cuenta`.
8. El administrador reactiva a Bruno y él vuelve a entrar con su contraseña.

## Capturas guardadas

| Captura | Mockup |
|---|---|
| `usuarios-lista-1440.png`, `usuarios-lista-390.png` | `usuarios-lista.html` |
| `usuarios-alta-1440.png`, `usuarios-alta-390.png` | `usuarios-alta.html` |
| `usuarios-alta-sin-profesional-1440.png`, `-390.png` | `usuarios-alta-estados.html` (Odontólogo sin profesional) |
| `profesional-acceso-1440.png`, `-390.png` | `profesional-acceso.html` (profesional con cuenta) |
| `cambiar-contrasena-obligatoria-1440.png`, `-390.png` | `cambiar-contrasena-obligatoria.html` |
| `cambiar-contrasena-voluntaria-1440.png`, `-390.png` | `cambiar-contrasena-voluntaria.html` |
| `menu-celular-mi-contrasena-390.png` | `menu-celular-mi-contrasena.html` |

**Datos personales:** la base de desarrollo tiene profesionales y pacientes cargados a mano que no son del seed ficticio.

- Las capturas de las pantallas que los muestran se tomaron con la máscara de Playwright sobre esos nombres (recuadros magenta).
- En las capturas con un diálogo, la máscara también tapa una parte del diálogo, porque se dibuja encima de todo.
- Estas pantallas se miraron durante la verificación, pero sus capturas no se guardaron porque mostraban esos datos: el diálogo de la contraseña temporal, las confirmaciones, el detalle con "Dar acceso" y la agenda después del cambio. Las cubren los tests de `frontend/test/cuentas-personal.test.ts`.

## Diferencias

| Prioridad | Pantalla | Tamaño | Esperado (mockup) | Observado | Estado |
|---|---|---|---|---|---|
| menor | Diálogo de la contraseña temporal | 390 y 1440 | Usuario `bruno.prueba` | `Bruno.prueba`: la regla `.detalle-lista dd::first-letter` de las pantallas del paciente ponía la primera letra en mayúscula | Corregida: `dd.dato-literal` en `16-cuentas.css`, verificada con el estilo calculado (`none`) |
| menor | Alta y detalle | 390 y 1440 | Detalle con prestaciones como etiquetas | El detalle del administrador muestra casillas y sillones editables (C-34), con "Acceso a la aplicación" al final | Esperada: el mockup simplificó el detalle; el bloque nuevo coincide |
| menor | Dar acceso | 1440 | — | En la captura de página completa, el velo del diálogo cubría solo la parte visible | Efecto de la captura de página completa, no de la pantalla |

Pantallas revisadas: todas las de la tabla de `../README.md`. No se revisó: el error de carga con "Reintentar" en el navegador; lo cubren los tests.
