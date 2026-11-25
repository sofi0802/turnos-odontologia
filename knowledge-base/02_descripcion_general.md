# Descripción General

## Stack tecnológico

Lo definió el equipo durante el Discovery (`discovery.md`, punto 9).

| Capa | Tecnologías | Versión mínima |
|---|---|---|
| Backend | Java + **Spring Boot** (Web, Validation, Data JPA, Security) | Java 21, Spring Boot 3.x *(versión exacta a fijar en el change de base)* |
| Build | **Gradle** (wrapper incluido en el repo) | 8.x |
| Base de datos | **PostgreSQL** | 15+ (requiere la extensión `btree_gist` para la restricción de exclusión, ver DD-03) |
| Migraciones | Flyway | Supuesto, ver SU-04 |
| Tests backend | JUnit 5, AssertJ, Spring Boot Test | — |
| Frontend | **TypeScript vanilla** con diseño responsivo, sin framework | TypeScript 5.x; bundler Vite (supuesto, ver SU-05) |

## Arquitectura general

Monolito modular con API REST. El dominio de agenda queda aislado de la infraestructura (puertos y adaptadores), para que las reglas de solapamiento se puedan probar sin base de datos.

```
 ┌────────────────────────┐       HTTPS/JSON        ┌──────────────────────────────────────┐
 │ Frontend (TS vanilla)  │ ─────────────────────▶  │ Backend Spring Boot (API REST)       │
 │  - Panel del personal  │                         │  ├─ api (controllers, DTOs)          │
 │  - Reserva del paciente│ ◀─────────────────────  │  ├─ application (casos de uso)       │
 └────────────────────────┘                         │  ├─ domain (Turno, Agenda, reglas)   │
                                                    │  └─ infrastructure (JPA, notific.)   │
                                                    └──────────────┬───────────────────────┘
                                                                   │
                                       ┌───────────────────────────┼─────────────────────────┐
                                       ▼                           ▼                         ▼
                                 PostgreSQL               WhatsApp Business API        Servidor SMTP
                              (EXCLUDE constraint)        (adaptador; simulado en TP)  (adaptador; simulado)
```

**Justificación**:
- El problema central es **una regla de dominio**: que no haya solapamientos con margen. Ponerla en un dominio puro permite un test por cada escenario del spec, que es lo que exige la consigna en la Etapa 6.
- La base de datos repite la garantía con una restricción de exclusión (DD-03), así que dos reservas concurrentes no pueden pasar las dos.
- Las integraciones externas van detrás de puertos, para simularlas sin cuentas reales (restricción del TP y riesgo de costo de WhatsApp).

## Integraciones externas

| Servicio | Propósito | Tipo | v1 |
|---|---|---|---|
| WhatsApp Business API (Cloud API de Meta) | Recordatorio y confirmación de turno | REST y webhook | Sí, **detrás de un puerto y simulado** en el TP. Costo de referencia: USD 0,026 por mensaje (dato publicado por DentalSoft) |
| Email (SMTP) | Canal alternativo si el paciente no tiene WhatsApp (RN-NO-02) | SMTP | Sí, simulado (log o servidor SMTP de desarrollo) |
| Mercado Pago | Señas de la reserva online (C-23, RN-SE) | REST y webhook | No (posterior): **detrás de un puerto**, con una pasarela simulada y el sandbox de Mercado Pago, sin credenciales reales |
| ARCA (ex AFIP) | Facturación electrónica | SOAP | No (posterior) |
| Webservices de obras sociales | Validación de afiliado y prestación | SOAP o REST | No (posterior) |
| Google Calendar | Sincronización de agenda | REST | No (posterior) |

## API REST (resumen)

Base: `/api/v1`. Formato JSON. Fechas en ISO-8601 con zona horaria.

| Recurso | Endpoints principales | Rol |
|---|---|---|
| Profesionales | `GET/POST /profesionales`, `PUT/DELETE /profesionales/{id}` | ADMIN |
| Sillones | `GET/POST /sillones`, `PUT/DELETE /sillones/{id}` | ADMIN |
| Prestaciones | `GET/POST /prestaciones`, `PUT/DELETE /prestaciones/{id}` | ADMIN |
| Turnos | `POST /turnos` (crear), `GET /turnos?desde&hasta&profesionalId&sillonId`, `POST /turnos/{id}/cancelar`, `POST /turnos/{id}/reprogramar`, `POST /turnos/sobreturno` | RECEPCIONISTA, ODONTOLOGO |
| Bloqueos | `GET/POST /profesionales/{id}/bloqueos`, `DELETE /bloqueos/{id}`; `POST /profesionales/{id}/bloqueos/{bloqueoId}/turnos/{turnoId}/reasignar` | ODONTOLOGO, ADMIN; la recepción lee y reasigna (RN-AG-19) |
| Horarios de trabajo | `GET/PUT /profesionales/{id}/horarios` | ADMIN |
| Disponibilidad (pública) | `GET /public/disponibilidad?prestacionId&fecha[&profesionalId]` (sin `profesionalId` = "sin preferencia") | Público |
| Cuenta de paciente | `POST /public/pacientes/registro`, `POST /public/login` | Público |
| Reserva online | `POST /paciente/reservas`, `GET /paciente/turnos`, `POST /paciente/reservas/{numeroReserva}/cancelar`, `POST /paciente/reservas/{numeroReserva}/reprogramar` | PACIENTE (con sesión) |
| Cancelación por el consultorio | `POST /turnos/{id}/cancelar` con DNI y número de reserva verificados | RECEPCIONISTA |
| Ausencias y licencias | `GET/POST /profesionales/{id}/ausencias`, `DELETE /ausencias/{id}` | ADMIN, RECEPCIONISTA |
| Especialidades | `PUT /profesionales/{id}/prestaciones`, `PUT /profesionales/{id}/sillon-habitual` | ADMIN |
| Extensión | `POST /turnos/{id}/extender` (con motivo) | Personal |
| Confirmación | `POST /public/confirmaciones/{token}` (y el webhook de WhatsApp) | Paciente |
| Pacientes y ficha | `GET/POST /pacientes`, `GET/PUT /pacientes/{id}/ficha`, `PUT /pacientes/{id}/odontograma` | ODONTOLOGO (la ficha), RECEPCIONISTA (los datos de contacto) |

**Errores de negocio**: `409 Conflict` con un código estable (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `FUERA_DE_HORARIO`, `SIN_DISPONIBILIDAD`, `SIN_SILLON_DISPONIBLE`, `PROFESIONAL_NO_HABILITADO`, `CANCELACION_FUERA_DE_PLAZO`). `422` para validaciones de entrada.
