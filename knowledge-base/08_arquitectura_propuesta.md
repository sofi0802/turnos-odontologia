# Arquitectura Propuesta

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|---|---|---|
| Arquitectura hexagonal (puertos y adaptadores) | Backend: `domain` y `application` no dependen de Spring ni de JPA | Las reglas de agenda se prueban con tests unitarios puros, uno por escenario del spec |
| Objeto de dominio con invariantes | `Turno`, `Intervalo`, `Agenda.verificarConflicto(...)` | Encapsula RN-AG-01 a RN-AG-05 en un solo lugar |
| Caso de uso o servicio de aplicación | `CrearTurno`, `CancelarTurno`, `ReprogramarTurno` | Orquesta la validación, la persistencia y la auditoría en una transacción |
| Repositorio | `TurnoRepository` (puerto) con un adaptador JPA | Aísla el acceso a datos |
| Restricción de exclusión en la base | `EXCLUDE USING gist` en `turno` | Garantiza RN-AG-12 ante la concurrencia (DD-03) |
| Adaptador de notificaciones | `NotificacionPort` con `WhatsAppAdapter` y `EmailAdapter` (simulados en el TP) | Sin cuentas reales y canal alternativo (RN-NO-02) |
| DTO y manejador global de errores | `@RestControllerAdvice` que mapea las excepciones de dominio a `409` y `422` con códigos estables | Contrato de errores verificable por tests |

## Estructura de directorios

```
turnos-odontologia/
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat
│   └── src/
│       ├── main/java/ar/turnos/odontologia/
│       │   ├── agenda/
│       │   │   ├── domain/          # Turno, Intervalo, Agenda, excepciones de negocio
│       │   │   ├── application/     # CrearTurno, CancelarTurno, ReprogramarTurno
│       │   │   ├── infrastructure/  # adaptadores JPA, entidades, repositorios
│       │   │   └── api/             # controllers y DTOs
│       │   ├── configuracion/       # profesionales, sillones, prestaciones
│       │   ├── pacientes/
│       │   ├── notificaciones/
│       │   ├── clinica/             # ficha y odontograma
│       │   └── shared/              # seguridad, errores, auditoría, reloj (Clock)
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/        # Flyway V1__..., incluye la extensión btree_gist
│       └── test/java/...            # unitarios de dominio y tests de integración
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── features/{agenda,reserva,configuracion}/
│       ├── shared/{api,ui,validacion}/
│       └── pages/
├── openspec/                        # specs y changes (OPSX)
├── knowledge-base/                  # esta KB
└── docs/                            # Discovery y evidencia
```

> Cada épica vive en su paquete o carpeta: **un dueño por módulo**. Así se reducen los conflictos de merge entre los 5 integrantes (riesgo del Discovery, punto 10).

## Seguridad

- **Autenticación** (personal y pacientes): Spring Security con usuario y contraseña (hash BCrypt) y **sesión del lado del servidor con cookie** (`HttpOnly`, `Secure` en producción, `SameSite=Lax`), con protección CSRF habilitada (DD-10).
- **Autorización**: por rol (`ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`) con `@PreAuthorize` y la matriz de `03_actores_y_roles.md`.
- **Paciente**: sin cuenta del personal. Accede solo a `/public/**` con **códigos o tokens de un solo uso**, aleatorios y no secuenciales.
- **Validación de entrada**: Bean Validation en los DTOs; las reglas de negocio están en el dominio, no en el controller.
- **Datos sensibles**: la ficha y el odontograma son datos de salud (Ley 25.326 y Ley 26.529). Acceso restringido (RN-CL-01) y nada de datos de salud en las notificaciones (RN-NO-01).
- **Secrets management**: variables de entorno. El repositorio solo tiene `.env.example`, sin valores reales (`.gitignore` ya excluye `.env`).
- **Hora**: se inyecta un `Clock` para poder probar RN-AG-07 y RN-AG-09 sin depender de la hora real.

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|---|---|---|---|
| `DB_URL` | JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/turnos` | N |
| `DB_USER` | Usuario de la base | `turnos` | N |
| `DB_PASSWORD` | Contraseña de la base | `(solo en .env local)` | **Y** |
| `APP_TIMEZONE` | Zona horaria del consultorio | `America/Argentina/Buenos_Aires` | N |
| `APP_MARGEN_MINUTOS` | Margen entre turnos (RN-AG-04) | `10` | N |
| `APP_CANCELACION_HORAS` | Anticipación mínima para el paciente (RN-AG-07) | `24` | N |
| `SESSION_TIMEOUT` | Duración de la sesión inactiva | `30m` | N |
| `WHATSAPP_API_TOKEN` | Token del proveedor (no se usa en el TP) | `(vacío)` | **Y** |
| `SMTP_HOST` / `SMTP_PORT` | Servidor de email (de desarrollo) | `localhost` / `1025` | N |
| `VITE_API_BASE_URL` | URL de la API para el frontend | `http://localhost:8080/api/v1` | N |
| `IMAGENES_DIRECTORIO` | Carpeta del almacén de imágenes clínicas (C-26, DD-17). Contiene datos de salud: permisos solo para el proceso e incluirla en los respaldos | `./data/imagenes` | N |
