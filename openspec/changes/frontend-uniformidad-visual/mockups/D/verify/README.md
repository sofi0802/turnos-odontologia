# Verificación visual de C-39, etapa D (2026-11-24)

- Mockups aprobados por la líder (README de `../`). Design system Revision 3.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`; cuenta de recepción de prueba ficticia (`cuentas-de-prueba.local.md`); `prefers-reduced-motion: reduce`.
- Capturas: esta carpeta, como `<pantalla>-<ancho>.png` a 390 × 844 y 1440 × 900, con los mismos nombres que `../previews/`.
- La agenda se simuló solo en el navegador (respuesta de `GET /turnos` interceptada), con los mismos 12 turnos ficticios de los mockups: no se escribió nada en la base.

## Pantallas revisadas

| Pantalla | Resultado |
|---|---|
| Agenda del día, de la semana y vacía | coincide con `d-agenda-dia`, `d-agenda-semana` y `d-agenda-vacia` |
| Cancelar turno y Reprogramar turno | coincide con `d-agenda-cancelar` y `d-agenda-reprogramar` |
| Nuevo turno: inicial, resultados del paciente y horarios | coincide con `d-nuevo-turno`, `-resultados` y `-horarios` |
| Configuración: Profesionales (y detalle), Sillones, Prestaciones, Margen, Horarios, Ausencias (y diálogo) y Bloqueos | coincide con los `d-config-*` |

No se revisó: "Mi agenda" del odontólogo (usa los mismos componentes y no tiene mockup propio), los diálogos de error ni los formularios de alta de profesional y de bloqueo.

## Comprobaciones

- Medido en el navegador a 1440 px: el fondo del contenedor es `rgb(243, 246, 248)` (el suave) en la agenda y en Configuración; la tarjeta del turno es blanca con sombra; `.turno-persona` mide 18 px y no tiene fondo; "Cancelar turno" es `btn btn-peligro` (texto y borde `rgb(163, 38, 42)`) y "Reprogramar" es `btn` con borde neutro.
- Sin desplazamiento horizontal en ninguna captura; ningún pedido fuera de `localhost`; sin errores de la consola.
- Configuración muestra las siete pestañas del mockup.

## Diferencias con los mockups

Ninguna. Blockers: ninguno. Majors: ninguno. Minors: ninguno.
