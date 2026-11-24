# Verificación visual de C-33 contra los mockups aprobados

- Fecha: 2026-11-23. Design system revisión 1. Mockups aprobados por la líder (PR #52).
- Método: la aplicación real (`vite`, `frontend/`) abierta en `#/agenda` en un navegador automatizado (Playwright) a 390 × 844 y a 1440 × 900, con la API **simulada** mediante interceptación de pedidos (`/api/v1/**`) y datos ficticios (Paciente Uno, DNI `99000001`, Ana Ejemplo, Bruno Prueba). Las respuestas simuladas copian lo que el backend real devolvió en la reproducción de la tarea 0.2 (`../../verify/reproduccion.md`).
- **No se probó contra el backend real con una sesión de personal**: la sesión real necesita las credenciales del `.env` del equipo, que no se usan en una verificación automatizada. Ver "Cómo repetirlo con el backend real".

## Diferencias, por prioridad

| Prioridad | Pantalla y ancho | Esperado (mockup) | Observado | Estado |
|---|---|---|---|---|
| **Major** | Horarios, 1440 px (y desde 768 px) | Seis botones en una fila dentro del diálogo, sin desplazamiento | Los botones no entraban: el "13:00" quedaba cortado y el diálogo mostraba una barra de desplazamiento horizontal. Causa: a partir de 768 px la grilla de C-12 fija 6 columnas y el diálogo del frontend medía 32 rem (el mockup mide 34 rem) | **Corregido.** El diálogo con horarios mide 34 rem y su grilla se ajusta al ancho (`styles.css`), con un test nuevo en `responsivo.test.ts`. Repetido: 768 y 1440 px, 6 columnas sin desbordarse; 390 px, 3; 320 px, 2 |
| Minor | Todas | El resumen del turno dice "Paciente Uno · Consulta · martes 1/12, 10:00 · Ana Ejemplo · Sillón 1" | Dice "Paciente Uno, 2026-11-23 de 10:00 a 10:30." Es el resumen que C-09 usa en todos los diálogos de la agenda (cancelar y reprogramar) | Aceptada: cambiarlo toca el resumen de C-09, fuera del alcance de este change |
| Minor | Rechazo por sillón, 390 y 1440 px | Aviso arriba, debajo del resumen, con "No se pudo reprogramar." en negrita | El aviso general queda encima de los botones, sin el título en negrita. El campo "Sillón" sí se marca en rojo con "Elegí otro sillón para ese horario." | Aceptada: en el celular, quien confirma está abajo del diálogo, y el aviso junto a los botones se ve sin desplazarse; arriba quedaría fuera de la pantalla |
| Minor | Todas | "Cerrar" a la izquierda y "Confirmar reprogramación" a la derecha | Orden contrario | Aceptada: es el orden de todos los diálogos de la agenda desde C-09 |

No hay blockers.

## Pantallas verificadas

| Captura | Qué muestra | Resultado |
|---|---|---|
| `horarios-390.png`, `horarios-1440.png`, `horarios-320.png` | Diálogo con profesional, fecha, horarios y sillón | Opciones del profesional: "Sin cambios", "Ana Ejemplo", "Bruno Prueba". Horarios como botones de 44 px. Sin desplazamiento horizontal de la página ni del diálogo |
| `rechazo-sillon-390.png`, `rechazo-sillon-1440.png` | Después de `SOLAPAMIENTO_SILLON` | "El sillón ya está ocupado en ese horario. Elegí otro sillón." El formulario sigue abierto con el DNI, el número y el horario |
| `exito-390.png` | Con el Sillón 2 | "Turno reprogramado. Nuevo número de reserva: P9XG-4KTW." El diálogo se cierra |
| `sin-horarios-390.png` | Otro profesional y un día sin horarios | "No hay horarios libres ese día." Confirmar no envía nada |
| `falla-horarios-390.png` | La consulta de horarios falla | "No pudimos cargar los horarios. Cambiá la fecha para volver a intentar." |
| `sin-elegir-390.png` | Confirmar sin elegir horario | "Elegí un horario." No se envía ningún pedido |
| `datos-incorrectos-390.png` | `DATOS_DE_RESERVA_INVALIDOS` | "El DNI o el número de reserva no son correctos." |

Pedidos enviados, observados en la red: el número de reserva escrito `" r4md2hzc "` salió como `R4MD-2HZC`; el `inicio` salió tal como lo dio la API de disponibilidad (`2026-11-23T11:00:00-03:00`); sin cambios, el pedido no lleva `profesionalId` ni `sillonId`; al cambiar de profesional lleva `profesionalId`; al elegir el Sillón 2 lleva `sillonId`.

Consola: solo los avisos de red de las respuestas `409` y `500` que se simularon a propósito. Sin errores de la aplicación.

## No verificado en el navegador

Solo están cubiertos por los tests unitarios: "Profesional no habilitado", "Profesional ausente o fuera de horario", "Buscando horarios" y "No se pudieron cargar los profesionales" (los mensajes y los estados se prueban con la API simulada de los tests, y sus estilos son los de los mockups).

## Cómo repetirlo con el backend real

1. Levantar PostgreSQL (`docker compose up -d`) y el backend con el perfil `dev`, y el frontend con `VITE_API_BASE_URL` apuntando a la API.
2. Ingresar con un usuario del personal y abrir la agenda del día de un turno `RESERVADO`.
3. Probar los pasos de `../../verify/reproduccion.md`: un horario que solo está libre en el otro sillón debe rechazarse con el mensaje de sillón y aceptarse al elegir el otro sillón.
