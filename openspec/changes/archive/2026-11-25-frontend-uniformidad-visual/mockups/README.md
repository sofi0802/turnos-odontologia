# Capturas de C-39 `frontend-uniformidad-visual`

Cada etapa con cambio visual guarda aquí sus mockups y sus capturas (`A/` no lleva mockups; `B/`, `C/` y `D/` se crean con su etapa). Las carpetas `antes/` y `despues/` son las capturas comparables de la etapa A.

## Etapa A: tokens y CSS modular (2026-11-23)

La etapa A no cambia el aspecto de ninguna pantalla. Se comprobó con capturas de antes y después, tomadas con el mismo procedimiento:

- **Entorno**: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuentas de prueba ficticias (`cuentas-de-prueba.local.md`).
- **Procedimiento**: Playwright con `prefers-reduced-motion: reduce`, sin cursor de texto, a 390 × 844 y 1440 × 900, página completa.
- **Pantallas (36 capturas por carpeta)**: sin sesión (portada, Reservar turno cerrado y con el selector abierto, Mi cuenta y Crear cuenta), con paciente (portada, Mis turnos y Reservar turno) y con personal (portada, Agenda, Nuevo turno y las siete pestañas de Configuración).
- **`antes/`**: con `master` (447 tests). **`despues/`**: con los estilos en módulos y sin alias.
- **Prueba de que el método es reproducible**: dos corridas con el mismo código dieron las 36 capturas idénticas byte a byte.
- **Resultado**: las 36 capturas de `antes/` y `despues/` son idénticas byte a byte (comparación con `cmp`).

No se revisaron: otros estados que no están en la lista (por ejemplo, los diálogos de Configuración, la reprogramación o el calendario del Nuevo turno con un día elegido). El CSS que usan no cambió: los módulos son cortes contiguos del archivo anterior, en el mismo orden (verificado comparando el texto), y los únicos reemplazos son los alias por su `--ds-*` equivalente.
