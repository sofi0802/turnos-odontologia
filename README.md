# turnos-odontologia

Sistema de gestión de turnos y agenda de pacientes para consultorios odontológicos chicos de Argentina. Su diferencial es **impedir que se superpongan los turnos por profesional y por sillón**, con un margen configurable de 10 minutos o más.
Trabajo Global de Integración de **Desarrollo de Software**: del Discovery a la entrega del MVP, con Spec-Driven Development (OpenSpec) y Active Stack.

## Equipo

| Integrante | Rol |
|---|---|
| Sofia Indovino | Líder: fundación, ciclo OPSX, revisión de PR a `master` |
| Emanuel | Integrante |
| Juan Sebastián | Integrante |
| Juan Cruz | Integrante |
| Enzo | Integrante |

## Antes de empezar

1. Leé **[AGENTS.md](AGENTS.md)**: stack, skills y las reglas duras del proyecto.
2. Buscá tu change y su dueño en **[CHANGES.md](CHANGES.md)**. Trabajá en la rama `change/C-NN-<nombre>` y abrí un PR a `master`.

## Estado del proyecto

| Etapa | Estado |
|---|---|
| 0. Preparación del entorno | Hecho (`docs/evidencia/`) |
| 1. `/active-orchestrator` + `openspec init` | Hecho |
| 2. Discovery | Hecho: 5 fuentes verificadas por el equipo y [PDF](docs/discovery/informe-discovery.pdf) |
| 3. Base de conocimiento (`knowledge-base/`) | Hecho |
| 4. Roadmap (`CHANGES.md`) | Hecho: 44 changes en 10 fases. MVP: C-01 a C-14 y los ajustes C-33 a C-45 |
| 5. Skills y reglas del agente (`AGENTS.md`, `CLAUDE.md`) | Hecho: skills en el repo, [registro](.atl/skill-registry.md) y 10 reglas duras |
| 6. Ciclo OPSX sobre un change | Hecho: C-01 `crear-turno-sin-solapamientos` (explore → propose → apply → verify → archive), 12 escenarios con su test. [Evidencia](docs/evidencia/etapa-6-c01-tests.txt) |
| 7. Memoria del agente (Engram) | Hecho: memorias y consulta real ([evidencia y guion del video](docs/evidencia/etapa-7-engram.md)) |
| 8. MVP y entrega (C-14) | Hecho: los changes del MVP están mergeados; [verificación final](docs/evidencia/c14-entrega.md) |

El estado detallado de la fundación está en [`.active-orchestrator-state.json`](.active-orchestrator-state.json). El estado de cada change está en [CHANGES.md](CHANGES.md): de los changes del MVP, todos están archivados en OpenSpec salvo C-39 `frontend-uniformidad-visual`, que tiene la última etapa en curso.

## Qué hace el MVP

- **Agenda sin solapamientos**: validación en el dominio con códigos de error estables (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`) y restricciones `EXCLUDE USING gist` de PostgreSQL por sillón y por profesional.
- **Personal** (recepción, odontólogos y administración): agenda por día y semana, nuevo turno guiado, reprogramar, cancelar, sobreturnos, llegada y ausencia del paciente, ficha clínica, buscador de pacientes, configuración del consultorio y cuentas del personal.
- **Pacientes**: reserva online, Mis turnos, confirmar, cancelar o reprogramar desde el link del recordatorio.
- **Recordatorios** simulados (WhatsApp y email detrás de puertos), sin datos de salud.

## Documentos principales

- [Instrucciones para agentes y reglas duras](AGENTS.md).
- [Roadmap de changes](CHANGES.md).
- [Base de conocimiento](knowledge-base/README.md).
- [Specs vigentes](openspec/specs/) y [changes archivados](openspec/changes/archive/).
- [Design system](design-system/README.md).
- [Informe de Discovery](docs/discovery/informe-discovery.md) ([PDF](docs/discovery/informe-discovery.pdf)): 16 sistemas, matriz ponderada y MVP recomendado.
- [Verificación de fuentes del equipo](docs/discovery/verificacion/README.md).
- [Checklist de Discovery (11 puntos)](docs/discovery/discovery.md): problema, usuarios, reglas de negocio RN-01 a RN-09.
- [Evidencia por sistema](docs/discovery/sources/relevamiento-fuentes.md).

## Stack

| Capa | Tecnologías |
|---|---|
| Backend | Java 21, Spring Boot 3 (Web, Validation, Data JPA, Security), Gradle (wrapper en el repo) |
| Base de datos | PostgreSQL 16 con `btree_gist`, migraciones con Flyway |
| Frontend | TypeScript vanilla con Vite y Vitest, responsivo, sin framework |
| Seguridad | Sesión de Spring Security con cookie `HttpOnly`, CSRF activo y acceso por rol |

## Cómo trabajamos (Git)

1. Cada tarea o change se hace en su propia rama, que sale de `master` (por ejemplo, `change/C-NN-<nombre>` o `fix/<nombre>`).
2. Cada integrante commitea con **su propio nombre y email de GitHub** (`git config user.name` / `git config user.email`).
3. Se abre un Pull Request a `master` y la líder lo revisa y lo mergea.
4. Nunca se suben datos reales de pacientes ni claves o credenciales.

## Instalación

### Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker | Cualquiera con `docker compose` | La aplicación completa (base, backend y frontend) con un solo comando. También da la base para los tests de integración |
| Java (JDK) | 21 | Solo para correr el backend o sus tests fuera de Docker. Si no lo tenés, el toolchain de Gradle lo descarga la primera vez |
| Node.js | 20.19 o más | Solo para correr el frontend o sus tests fuera de Docker |

Sin Docker también se puede: un PostgreSQL 15 o más instalado, con la extensión `btree_gist`, más Java y Node (ver [Correr la aplicación sin Docker](#correr-la-aplicación-sin-docker)). No hace falta instalar Gradle: el repo trae el wrapper (`gradlew` y `gradlew.bat`).

### Variables de entorno

Copiá `.env.example` a `.env` en la raíz del repo y completá los valores. El `.env` no se sube al repo.

| Variable | Obligatoria | Uso |
|---|---|---|
| `DB_USER`, `DB_PASSWORD` | Sí | Usuario y contraseña de PostgreSQL. `docker compose` crea el usuario con estos valores |
| `DB_URL` | Sí | Base de desarrollo vista **desde tu máquina** (`bootRun`): `jdbc:postgresql://localhost:5432/turnos`. Dentro de Docker, el compose la reemplaza por `jdbc:postgresql://postgres:5432/turnos` |
| `DB_TEST_URL` | Para los tests de integración | Base de los tests: `jdbc:postgresql://localhost:5432/turnos_test`. **Nunca** la apuntes a `turnos`: los tests vacían las tablas |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | Para el primer ingreso | Crean la cuenta ADMIN inicial al arrancar, solo si todavía no hay ningún administrador. La contraseña tiene que cumplir la política de contraseñas |
| `APP_CORS_ORIGENES` | No (por defecto `http://localhost:5173`) | Origen del frontend |
| `SESSION_TIMEOUT`, `SESSION_COOKIE_SECURE` | No (por defecto `30m` y `false`) | Duración de la sesión. En producción, la cookie va con `true` (HTTPS) |
| `NOTIFICACIONES_ACTIVO`, `NOTIFICACIONES_INTERVALO_MS` | No (por defecto `true` y 5 minutos) | Tarea de recordatorios simulados |
| `NOTIFICACIONES_BASE_URL`, `NOTIFICACIONES_RUTA_CANCELAR`, `NOTIFICACIONES_RUTA_REPROGRAMAR` | No | Links de los recordatorios hacia el frontend |
| `AGENDA_ASISTENCIA_ACTIVO`, `AGENDA_ASISTENCIA_INTERVALO_MS` | No (por defecto `true` y 5 minutos) | Tarea que pasa a ATENDIDO los turnos con llegada registrada que ya terminaron |
| `CONSULTORIO_NOMBRE`, `CONSULTORIO_DIRECCION` | No | Datos del consultorio que muestran los mensajes |
| `IMAGENES_DIRECTORIO` | No (por defecto `./data/imagenes`) | Dónde guarda el backend las imágenes clínicas. En Docker, el compose la fija en un volumen propio |
| `VITE_API_BASE_URL` | Solo fuera de Docker | Dirección de la API: `http://localhost:8080/api/v1`. En Docker la fija el compose; fuera de Docker, Vite la lee de la terminal o de `frontend/.env.local` |

## Correr la aplicación con Docker

Desde la raíz del repo, con el `.env` completo:

```bash
docker compose up -d --build
```

Levanta tres servicios:

| Servicio | Dirección | Qué hace |
|---|---|---|
| `postgres` | `127.0.0.1:5432` | PostgreSQL 16. La primera vez que se crea el volumen, `docker/initdb/01-crear-turnos-test.sql` crea las bases `turnos` y `turnos_test` |
| `backend` | `http://localhost:8080` | Arranca cuando la base está lista, aplica las migraciones de Flyway y carga el seed ficticio (perfil `dev`). La API está en `/api/v1` |
| `frontend` | `http://localhost:5173` | Vite en modo desarrollo: recarga sola cuando guardás un archivo de `frontend/` o `design-system/` |

Abrí `http://localhost:5173`. Ingresá con la cuenta ADMIN inicial desde **Mi cuenta** y creá desde **Configuración** las cuentas de recepción y de los odontólogos. Los pacientes crean su cuenta desde Mi cuenta. Usá solo datos ficticios.

Todo se publica solo en `127.0.0.1`, no en la red local. Las credenciales llegan desde el `.env` al arrancar y nunca quedan dentro de las imágenes. La base y las imágenes clínicas viven en volúmenes (`turnos-pgdata` y `turnos-imagenes`), así que sobreviven a `docker compose down` y a reconstruir las imágenes.

La primera construcción tarda varios minutos: descarga Gradle, las dependencias del backend y las del frontend. Las siguientes reutilizan la caché.

**Comandos útiles:**

| Para | Comando |
|---|---|
| Ver los logs | `docker compose logs -f backend` (o `frontend`, `postgres`) |
| Aplicar un cambio en el código Java | `docker compose up -d --build backend` |
| Renovar `node_modules` tras cambiar `package-lock.json` | `docker compose up -d --build -V frontend` |
| Apagar todo (los datos quedan) | `docker compose down` |
| Borrar también la base y las imágenes clínicas | `docker compose down -v` |

**Si un puerto está ocupado** (por ejemplo, ya tenés un PostgreSQL instalado en el 5432), creá un `docker-compose.override.yml` local, que no se sube al repo, y publicá la base en otro puerto:

```yaml
services:
  postgres:
    ports: !override
      - "127.0.0.1:5433:5432"
```

El backend del compose no cambia, porque se conecta a la base por la red interna. Lo que sí tenés que cambiar es el `DB_URL` y el `DB_TEST_URL` de tu `.env` (al 5433), que usan `bootRun` y los tests desde tu máquina. No corras `bootRun` ni `npm run dev` mientras el compose está levantado: chocan en el 8080 y el 5173.

**Si ya tenías el contenedor de antes** (el volumen ya existía), `turnos_test` no se crea sola. Creala una vez, con el usuario de tu `.env`:

```bash
docker exec turnos-postgres psql -U <DB_USER> -c "CREATE DATABASE turnos_test;"
```

## Correr la aplicación sin Docker

Sirve para quien no tiene Docker o prefiere correr el backend y el frontend en su máquina. La base puede ser un PostgreSQL instalado (15 o más, con `btree_gist` y las bases `turnos` y `turnos_test`) o solo el servicio `postgres` del compose:

```bash
docker compose up -d postgres
```

### 1. Cargar el `.env` en la terminal

El backend lee las variables del entorno, no del archivo. Antes de correr Gradle, cargalas en la terminal desde `backend/`:

```bash
cd backend
set -a; . ../.env; set +a      # Git Bash, Linux o macOS
```

En PowerShell:

```powershell
cd backend
Get-Content ..\.env | ForEach-Object { if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') } }
```

### 2. Backend

En la terminal con el `.env` cargado, desde `backend/`:

```bash
./gradlew bootRun        # en Windows: .\gradlew.bat bootRun
```

Arranca en `http://localhost:8080` con el perfil `dev`, que carga el seed ficticio (profesionales, sillones, prestaciones y horarios). La API está en `/api/v1`.

### 3. Frontend

En otra terminal, desde `frontend/`:

```bash
npm ci
VITE_API_BASE_URL=http://localhost:8080/api/v1 npm run dev
```

En PowerShell:

```powershell
$env:VITE_API_BASE_URL='http://localhost:8080/api/v1'; npm run dev
```

Abrí `http://localhost:5173` e ingresá como se explica en [Correr la aplicación con Docker](#correr-la-aplicación-con-docker).

Para un jar (`./gradlew bootJar`), pasá la zona horaria canónica: `java -Duser.timezone=America/Argentina/Buenos_Aires -jar build/libs/<jar>.jar`. En Windows, Java usa un alias viejo de la zona que PostgreSQL 16 rechaza; `bootRun`, los tests y la imagen de Docker ya la fijan.

## Tests

### Backend: tests de dominio (sin base de datos)

```bash
cd backend
./gradlew test        # en Windows: .\gradlew.bat test
```

No necesitan PostgreSQL ni el `.env`. Cada escenario GIVEN/WHEN/THEN de las specs tiene su test con el mismo nombre. Por ejemplo, los 12 escenarios de C-01 en `openspec/specs/agenda-turnos/spec.md` están en `CrearTurnoTest` y `MargenTest`. `./gradlew build` compila y corre estos mismos tests.

### Backend: tests de integración (PostgreSQL real)

Llevan `@Tag("integration")` y usan PostgreSQL real, porque H2 no soporta `btree_gist`. **No** forman parte de `build`. Con la base levantada (`docker compose up -d`, o solo `docker compose up -d postgres`) y el `.env` cargado (ver [Cargar el `.env` en la terminal](#1-cargar-el-env-en-la-terminal)):

```bash
./gradlew integrationTest
```

Usan la base `DB_TEST_URL` y **vacían sus tablas**. Para correr todo junto: `./gradlew build integrationTest`.

### Frontend

```bash
cd frontend
npm ci
npm test              # Vitest con jsdom
npm run build         # tsc --noEmit (strict) y build de Vite
```
