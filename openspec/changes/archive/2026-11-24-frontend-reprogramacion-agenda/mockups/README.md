# Mockups de C-33 `frontend-reprogramacion-agenda`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: approved (2026-11-23, by Sofia Indovino, la líder; PR #52)
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: la recepción y el personal que reprograma turnos desde la agenda, en computadora y a veces desde el celular.
- **Qué tienen que poder hacer**: elegir un profesional habilitado y un horario libre de la lista, sin tener que adivinar horarios; y entender qué pasó cuando la reprogramación se rechaza.
- **Qué cambia respecto de C-09**: el diálogo "Reprogramar turno" reemplaza la hora libre por botones de horario, ofrece solo profesionales habilitados para la prestación y explica cada rechazo. La agenda de atrás no cambia.
- **Tamaños**: 390 × 844 y 1440 × 900. El diálogo mide 34 rem como máximo; en el celular ocupa el ancho disponible y **se desplaza por dentro** si no entra en la pantalla.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Componentes de `design-system/components.html`: encabezado del personal, campos, avisos, estados del turno, chips y diálogo.
  - Los chips son los de C-12 (`.chips` / `.chip`, de al menos 44 × 44 px).
  - El logo provisorio y el nombre de `brand.js`; la voz de `brand.md` (voseo, errores que dicen qué pasó y qué hacer).
- **Restricciones**: datos 100 % ficticios (Paciente Uno, DNI `99000001`, Ana Ejemplo, Bruno Prueba); sin datos de salud; sin recursos remotos.
- **Fuera del sistema**: las clases de página (`.app-encabezado`, `.pagina`, `.franja`, `.turno`, `.velo`) arman la agenda que se ve detrás del diálogo con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Pantalla y estado | Requisitos |
|---|---|---|
| `reprogramar-horarios.html` | Diálogo con el profesional "Sin cambios", la fecha, los horarios libres con el de las 11:00 elegido y el aviso de horarios que pisan el turno | Horarios libres al reprogramar; Profesionales habilitados al reprogramar |
| `reprogramar-sin-horarios.html` | El mismo diálogo con otro profesional y un día sin horarios libres | Horarios libres al reprogramar |
| `reprogramar-rechazo-sillon.html` | Después de `SOLAPAMIENTO_SILLON`: aviso arriba, el campo "Sillón" marcado, el horario, el DNI y el número conservados | Sillón al reprogramar; Mensajes de rechazo |
| `reprogramar-estados.html` | Siete estados secundarios, cada uno en su tarjeta: confirmar sin elegir horario, DNI o número incorrectos, profesional no habilitado, profesional ausente o fuera de horario, buscando horarios, falla la consulta de horarios y no se pudieron cargar los profesionales | Horarios libres; Identificación; Mensajes de rechazo |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png`. `reprogramar-horarios-390-abajo.png` muestra el final del diálogo en el celular, con los botones.
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre `http://127.0.0.1:8765/openspec/changes/frontend-reprogramacion-agenda/mockups/reprogramar-horarios.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 390 y a 1440 px, en las cuatro pantallas | No hay |
| Alto de los botones de horario | 44 px (ancho de 76 px a 1440 px, tres por fila a 390 px) |
| El diálogo cabe en 1440 × 900 | Sí, sin desplazamiento interno |
| El diálogo en 390 × 844 | Se desplaza por dentro; el botón "Confirmar reprogramación" queda al final |
| Errores y avisos de la consola | 0 |

## Decisiones para revisar

1. **El diálogo se desplaza por dentro en el celular** (`max-height` de la pantalla menos un margen), en lugar de dejar que la página se desplace. Evita perder la agenda de fondo, pero el botón de confirmar queda abajo y hay que bajar para verlo.
2. **Los botones de horario son los chips de C-12**, en una grilla que se ajusta al ancho: seis por fila en la computadora y tres en el celular.
3. **El sillón se conserva como selector** con "Sin cambios". El frontend no sabe qué sillón queda libre en un horario dado; si el rechazo es por sillón, el aviso lo explica y marca el campo.
4. **Aviso fijo de los horarios que pisan el turno actual**: la disponibilidad cuenta al propio turno como ocupado (`design.md`, D6), así que esos horarios no aparecen. El texto lo aclara en lugar de dejar la grilla "incompleta" sin explicación.
5. **"Profesional no habilitado" es un caso poco frecuente**: el selector ya solo ofrece habilitados, así que solo aparece si la habilitación cambió mientras el formulario estaba abierto. La lámina de estados lo muestra igual para tener su texto.
6. **La lámina `reprogramar-estados.html` no es una pantalla**: junta siete diálogos en tarjetas para poder compararlos. En la aplicación hay un solo diálogo abierto a la vez.
