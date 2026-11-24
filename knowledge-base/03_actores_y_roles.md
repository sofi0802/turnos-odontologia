# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|---|---|---|
| **Recepcionista o secretaria** (`RECEPCIONISTA`) | Personal administrativo del consultorio | Panel web: carga, mueve y cancela turnos; carga sobreturnos; registra la llegada y la ausencia de los pacientes (RN-AG-17, RN-AG-20); administra los datos de contacto de los pacientes |
| **Odontólogo** (`ODONTOLOGO`) | Profesional que atiende | Panel web: ve **solo** su agenda (RN-AG-18), bloquea horarios, carga sobreturnos, registra la ficha, el odontograma y el periodontograma |
| **Administrador del consultorio** (`ADMIN`) | Dueño o encargado (puede ser el mismo odontólogo) | Panel web: configura profesionales, sillones, prestaciones y usuarios |
| **Paciente** (`PACIENTE`, cuenta propia, no es personal) | Persona que se atiende | Con **su cuenta**: reserva, ve sus turnos, cancela o reprograma con el número de reserva (RN-AG-07, RN-RO-08). Sin cuenta: confirma desde el link del recordatorio o llama al consultorio, que valida DNI y número de reserva |
| **Sistema de notificaciones** (actor técnico) | Tarea programada | Envía recordatorios por WhatsApp o email y procesa las confirmaciones |

**Segmento** (`discovery.md`, punto 2): odontólogo independiente (1 profesional y 1 sillón, a veces con secretaria) y consultorio chico (varios profesionales que comparten sillones).

## RBAC: matriz de permisos

C = crear, R = leer, U = modificar, D = eliminar o desactivar. "Propio" = solo sus propios turnos o datos.

| Recurso | ADMIN | RECEPCIONISTA | ODONTOLOGO | Paciente (público) |
|---|---|---|---|---|
| Profesionales, sillones, prestaciones | CRUD | R | R | R (solo nombre y prestaciones disponibles) |
| Usuarios del personal | CRUD | — | — | — |
| Turno (crear normal) | C | C | C | C (online, propio; RN-AG-07) |
| Turno (sobreturno) | C | C | C | **—** (RN-AG-06) |
| Turno (cancelar o reprogramar) | U | U (siempre) | U (siempre) | U propio, **hasta 24 h antes** (RN-AG-07) |
| Agenda (ver) | R | R | R (**solo la propia**: sus turnos del día y de la semana, RN-AG-18) [2026-11-23] | **Solo disponibilidad**: horarios libres, sin datos de otros pacientes |
| Bloqueo de agenda | CRUD | R, y reasigna los turnos afectados (RN-AG-19) [2026-11-23] | CRUD propio | — |
| Horarios de trabajo | CRUD | R | R (propios) | R indirecta (solo ve los huecos libres resultantes) |
| Especialidades (profesional-prestación) y sillón habitual | CRUD | R | R | R (solo qué profesionales hacen cada prestación) |
| Ausencias y licencias del profesional | CRUD | CRUD | R (propias) | — |
| Margen entre turnos (configuración) | U (mínimo 10 min) | R | R | — |
| Extensión de un turno por complicaciones | U | U | U | — |
| Llegada del paciente (RN-AG-20) [2026-11-24] | — | U (desde la agenda) | — | — |
| Ausencia del paciente (RN-AG-17) [2026-11-24] | — | U (desde la agenda) | U propio (desde la ficha) | — |
| Deshacer la ausencia del paciente (RN-AG-17) [2026-11-24] | — | U (si el horario sigue libre) | — (ve en la ficha que se deshizo) | — |
| Paciente (datos de contacto) | CRUD | CRUD | R | R/U propio (al reservar) |
| Ficha clínica y odontograma | R | **—** | CRUD | — |
| Periodontograma (RN-CL-04) [2026-11-24] | R | **—** | C y R (cada carga es una versión nueva; no se edita ni se borra) | — |
| Obras sociales, planes y nomenclador (RN-OS-01) [2026-11-24, C-18] | CRUD (sin borrar) | R | R | — |
| Afiliación del paciente (RN-OS-02) [2026-11-24, C-18] | CRUD (sin borrar) | CRUD (sin borrar; la carga al dar de alta o al atender al paciente) | R | R propia, en "Mi cuenta" (PA-21) |
| Aranceles y cobertura por plan (RN-PR-01, RN-PR-02) [2026-11-24, C-19] | CRUD (sin borrar) | R | R | — |
| Presupuesto (RN-PR-04 a RN-PR-06) [2026-11-24, C-19] | R | R, sin pieza ni cara (PA-24) | C y R, y cambia el estado | R propios, en "Mi cuenta" (PA-27) |
| Estadísticas o dashboard | R | R (operativas) | R (propias) | **—** |

> **Lección de la verificación de fuentes:** en la demo de VecDent, un rol "paciente" armado a mano veía el dashboard con estadísticas de la clínica y el menú de configuración (informe, verificación de fuentes, fila 5). El paciente **no es un rol del personal con menos permisos**: solo accede a endpoints públicos y a sus propios turnos.

## Rutas públicas

Accesibles sin sesión del personal:

- `GET /api/v1/public/disponibilidad`: horarios libres por prestación y fecha. No expone nombres de pacientes.
- `POST /api/v1/public/pacientes/registro` y `POST /api/v1/public/login`: alta e inicio de sesión de la cuenta de paciente.

**Rutas del paciente** (requieren sesión con rol `PACIENTE`; solo sus datos):
- `POST /api/v1/paciente/reservas`: reserva online.
- `GET /api/v1/paciente/turnos`: sus turnos.
- `POST /api/v1/paciente/reservas/{numeroReserva}/cancelar` y `/reprogramar`: hasta 24 h antes (RN-AG-07).
- `POST /api/v1/public/confirmaciones/{token}`: confirmación desde el link del recordatorio.
- `POST /api/v1/webhooks/whatsapp`: webhook del proveedor. Valida la firma.
- Frontend: página de reserva del paciente.
