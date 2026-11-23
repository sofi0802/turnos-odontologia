# Visual verify de C-36 `buscador-pacientes` (2026-11-23)

- Design system revision: 1. Mockups aprobados el 2026-11-23 (Sofia vía Juan Sebastián).
- Entorno: backend (`bootRun`, perfil dev) contra PostgreSQL 16 descartable, frontend con `vite` y la API real; datos 100% ficticios ("Paciente Uno", "Paciente Dos", "Carla Ficticia", "Julián Ejemplar") y una sesión `recepcion` de prueba creada solo para esta verificación. Capturas con Playwright a 390 × 844 y 1440 × 900. Los servidores y la base se detuvieron al terminar.
- Consola del navegador: sin errores. Pedidos fallidos: ninguno. Sin desplazamiento horizontal a 390 ni a 1440 (`scrollWidth - clientWidth = 0`). Opciones de 48 px de alto en los dos tamaños.
- El texto buscado no aparece en el log del backend durante la sesión.
- Flujo completo comprobado: buscar "99000003", elegir con el teclado, completar el formulario y `POST /api/v1/turnos` con `pacienteId` 3 devolvió `201` y "Turno creado. Número de reserva: …".

## Pantallas comparadas

| Mockup aprobado | Captura 390 | Captura 1440 | Resultado |
|---|---|---|---|
| `nuevo-turno-resultados.html` | `nuevo-turno-resultados-390.png` | `nuevo-turno-resultados-1440.png` | Coincide (2 opciones, la segunda activa, "2 pacientes encontrados."). |
| `nuevo-turno-seleccionado.html` | `nuevo-turno-seleccionado-390.png` | `nuevo-turno-seleccionado-1440.png` | Coincide con la diferencia minor 1. |
| `estados-buscador.html`: texto corto | `estado-texto-corto-390.png` | `estado-texto-corto-1440.png` | Coincide ("Escribí al menos 2 letras o 3 números del DNI."). |
| `estados-buscador.html`: sin resultados | `estado-sin-resultados-390.png` | `estado-sin-resultados-1440.png` | Coincide. |
| `estados-buscador.html`: paciente sin elegir | `estado-sin-elegir-390.png` | `estado-sin-elegir-1440.png` | Coincide ("Elegí un paciente de la lista."). |

No verificados con capturas: "Buscando…" (dura milisegundos con la API local; lo cubre el test "Buscando"), "Límite de 10 resultados" (el seed ficticio tiene 4 pacientes; lo cubre el test "Límite de resultados alcanzado"), error del servidor y sin permiso (se necesitaría apagar la API o usar otro rol; los cubren los tests "Error del servidor" y "Búsqueda sin permiso").

## Diferencias

- **blocker**: ninguna.
- **major**: ninguna.
- **minor 1**: el botón "Cambiar paciente" usa el estilo `boton-secundario` de la agenda (con borde) y no el estilo fantasma del mockup; a 390 px pasa a la línea de abajo dentro del recuadro. Es legible y cumple los 44 px; se puede ajustar en una revisión visual posterior si la líder lo pide.

## Prueba con teclado y lector de pantalla (tarea 7.4)

- Teclado, comprobado en el navegador real: escribir "paciente", flecha abajo dos veces mueve `aria-activedescendant` a la segunda opción, Enter elige "Uno, Paciente" sin enviar el formulario, y Escape cierra la lista (`aria-expanded="false"`) sin cerrar el diálogo. El foco pasa a "Cambiar paciente" al elegir y vuelve al campo al cambiar.
- Lector de pantalla (NVDA o Narrador de Windows): **pendiente**, lo tiene que hacer una persona. Los roles (`combobox`, `listbox`, `option`, `status`) y los atributos de ARIA están verificados por los tests, pero no se probó el anuncio real.
