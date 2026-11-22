# Marca

Revision: 1. Todo lo marcado **(TBD)** es provisorio y se ve como tal en las vistas previas.

| Elemento | Valor actual | Fuente | Estado |
|---|---|---|---|
| Nombre del producto | "Turnos odontología" | `frontend/index.html` (`<title>`) y encabezado de C-09 | **(TBD)**: nombre descriptivo, no una marca elegida |
| Logo | `assets/logo.svg`, rotulado "LOGO PROVISORIO" | Creado para este sistema | **(TBD)** |
| Imágenes / fotos | Ninguna | — | **(TBD)**: si se usan, solo de banco libre y sin pacientes reales (RN-GL-01) |
| Color de acento | `#0b6e8a` (verde azulado clínico) | `frontend/src/styles.css` de C-09 | Se conserva |
| Tipografía | Fuente del sistema (`system-ui`) | `frontend/src/styles.css` de C-09 | Se conserva: sin fuentes remotas |

## Tono visual

- **Clínico, tranquilo y claro.** El personal usa la agenda muchas horas: fondos blancos, superficies gris azulado, un solo color de acento.
- **El estado del turno se lee de un vistazo.** Cada estado (reservado, confirmado, sobreturno, ausente, atendido, cancelado) tiene su color **y** su texto. Nunca se depende solo del color.
- **El paciente usa el celular.** La reserva online (C-12) es mobile first: botones de 44px, un paso por pantalla y horarios como chips grandes.
- **Sin datos de salud a la vista fuera de la ficha.** Las tarjetas de la agenda muestran prestación, paciente, profesional y sillón. Antecedentes, alergias y odontograma solo aparecen en la ficha clínica (RN-CL-01).

## Voz

- Español rioplatense con voseo ("Elegí un horario", "Revisá los datos").
- Los mensajes de error dicen qué pasó y qué hacer ("Ese horario se ocupó recién. Elegí otro.") y nunca muestran códigos técnicos al paciente.

## Reglas de uso

- El logo va siempre con su área libre (8px) y nunca se deforma. Mientras sea provisorio, no se usa en materiales externos.
- El acento se usa en acciones principales, en la navegación activa y en el foco. No se usa como fondo de bloques grandes.
- El rojo (`--ds-error`) se reserva para errores y para turnos ausentes. No se usa para decorar.
