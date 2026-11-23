# Verificación visual de C-37 (2026-11-23)

- Design system revision: 1. Mockups aprobados: PR #64.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuentas de prueba ficticias de `cuentas-de-prueba.local.md`.
- Capturas: `<pantalla>-<ancho>.png` en esta carpeta (390 × 844, 1440 × 900 y 320 × 844 donde aplica).

## Pantallas revisadas

| Pantalla | 390 | 1440 | 320 | Resultado |
|---|---|---|---|---|
| Portada sin sesión | sí | sí | sí | coincide con el mockup |
| Portada con paciente | sí | sí | no | coincide |
| Portada con personal | sí | sí | no | coincide |
| Mi cuenta con la indicación | sí | sí | sí | coincide |

No se revisó: la portada con paciente y con personal a 320 px (la portada sin sesión sí).

## Comprobaciones

- Sin desplazamiento horizontal en ninguna captura.
- "Acceso personal": arriba a la derecha, 44 px de alto, a 390 y 320 px.
- Consola sin errores; ningún pedido a un origen distinto de `localhost`.
- Redirecciones contra el backend real: `#/` y direcciones desconocidas → `#/inicio`; `#/ingreso` sin sesión → `#/mi-cuenta`; con sesión del personal, `#/mi-cuenta` y `#/ingreso` → `#/agenda`; "Salir" de paciente y de personal → `#/inicio`; el logo → `#/inicio`; paciente tras el login → `#/mis-turnos`, personal → `#/agenda`.
- La portada con sesión no redirige sola y no muestra "Ingresar a mi cuenta" ni "Acceso personal".

## Diferencias con el mockup

| Prioridad | Pantalla | Esperado (mockup) | Observado | Reproducción |
|---|---|---|---|---|
| minor | Todas | Logo provisorio y avatar en el encabezado | Encabezado actual, sin logo ni avatar | Abrir `#/inicio` |
| minor | Todas | Fondo de página gris azulado | Fondo blanco | Abrir `#/inicio` |
| minor | Mi cuenta, 390 px | Encabezado sin menú | Con el botón "Menú" e "Ingresar" en una segunda fila | Abrir `#/mi-cuenta` a 390 px |

Las tres son del encabezado y el fondo de la aplicación, que rediseña C-39; no se tocan acá (design D3). Blockers: ninguno. Majors: ninguno.
