# Verificación de C-39, etapa E: agenda semanal en grilla horaria (2026-11-24)

- La etapa se aplicó sin mockups previos, por pedido explícito del usuario (ver `design.md`). Estas capturas muestran lo aplicado y son lo que revisa la líder en el PR.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuenta de recepción de prueba ficticia (`cuentas-de-prueba.local.md`); `prefers-reduced-motion: reduce`.
- Capturas: `<pantalla>-<ancho>.png` a 390 × 844 y 1440 × 900.
- La agenda se simuló solo en el navegador (respuesta de `GET /turnos` interceptada, respetando los filtros), con una semana cargada: dos turnos a la misma hora, uno de 15 minutos, uno de 90 minutos, cinco a la misma hora, un sobreturno, un turno tardío y todos los estados. No se escribió nada en la base.
- Ajustes pedidos al revisar la primera versión (2026-11-24): los turnos se agrupan desde 2 a la misma hora; la tarjeta muestra siempre lo mismo (horario y nombre del profesional para la recepción y la administración, horario y nombre del paciente para el odontólogo); los días sin turnos quedan en blanco.

## Pantallas revisadas

| Pantalla | Resultado |
|---|---|
| `e-semana` | Siete columnas, escala de 08:00 a 18:00, turnos ubicados por hora y duración, bloques "2 turnos" y "5 turnos", y los días sin turnos en blanco; a 1440 px entran los siete días sin desplazar |
| `e-semana-desplazada` | Con la grilla desplazada, el encabezado y la escala siguen a la vista (a 1 px del borde de la grilla) |
| `e-detalle` | Detalle de un turno (uno ausente: sin acciones de cancelar ni reprogramar) |
| `e-semana-odontologo` | Agenda del odontólogo: cada tarjeta muestra el horario y el paciente |
| `e-grupo` | Un bloque de turnos a la misma hora que abre la lista completa |
| `e-semana-filtrada` | Con el filtro de profesional, el pedido lleva `profesionalId` y se ven 7 bloques y tarjetas |
| `e-dia` | La vista del día no cambia (lista de tarjetas) |
| `e-semana-vacia` | Semana sin turnos: siete columnas en blanco |

## Comprobaciones

- Sin desplazamiento horizontal de la página en ninguna captura; a 390 px la grilla se desplaza dentro de su contenedor (ancho de contenido 1064 px) sin comprimir las columnas.
- Sin errores de la consola y ningún pedido fuera de `localhost`.
- Se mantienen los siete días (no se agregó la opción de ocultar fines de semana), los filtros, la navegación, "Hoy", "Nuevo turno" y las vistas diaria y semanal.

## Límites

- Con un nombre largo, la tarjeta lo corta con puntos suspensivos; el detalle completo está en el nombre accesible, en el texto emergente y en el diálogo.
- No se volvió a comprobar en el navegador la cancelación desde un bloque (la versión anterior sí lo hizo), porque el primer bloque de la semana simulada son dos turnos atendidos, sin acciones.
- La altura mínima de la tarjeta es de 1,5 rem: un turno muy corto se ve más alto que su duración.
- No se probó con lector de pantalla ni con el teclado; las tarjetas son botones con nombre accesible completo y el detalle usa el diálogo estándar de la aplicación.
