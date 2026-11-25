# Design

## Context

Ver `proposal.md` (Why). Estado actual observado en el repo:

- `docker-compose.yml` (C-03, dueño Juan Cruz) tiene solo `postgres:16`, publicado en `127.0.0.1:5432`, con el volumen `turnos-pgdata` y `docker/initdb/01-crear-turnos-test.sql`, que crea `turnos_test` la primera vez. Las credenciales salen de `${DB_USER:?…}` y `${DB_PASSWORD:?…}`.
- Hay overrides locales no versionados: por ejemplo, un `docker-compose.override.yml` (en `.git/info/exclude`) que publica `postgres` en `5433` porque el `5432` lo usa un PostgreSQL nativo.
- Backend: Spring Boot 3.5, Gradle 9.7.1 por wrapper (`gradlew` con LF por `.gitattributes`), toolchain Java 21 con el plugin foojay. El perfil por defecto es `dev`: lee `DB_URL`, `DB_USER` y `DB_PASSWORD` y Flyway carga `db/migration` y `db/seed`. `build.gradle` fija `user.timezone` solo para `Test` y `JavaExec`; el jar necesita `-Duser.timezone=America/Argentina/Buenos_Aires` (C-04, C-14).
- El almacén de imágenes clínicas (C-26) usa `IMAGENES_DIRECTORIO`, por defecto `./data/imagenes`, relativo al directorio de trabajo. Son datos de salud: no pueden quedar dentro de una imagen.
- Seguridad: sesión con cookie `HttpOnly` (`same-site: lax`), CSRF con `CookieCsrfTokenRepository` (cookie legible `XSRF-TOKEN`, también en `/public/login`) y CORS con credenciales solo para `APP_CORS_ORIGENES` (por defecto `http://localhost:5173`).
- Frontend (dueño Enzo): Vite 8 con Node >= 20.19, sin proxy. El cliente usa `import.meta.env.VITE_API_BASE_URL`. `vite.config.ts` permite `../design-system` en `server.fs.allow`, porque los estilos importan `design-system/tokens.css`.
- No hay cuentas de usuario en el seed: el primer ingreso usa la cuenta ADMIN inicial de `APP_ADMIN_USERNAME` y `APP_ADMIN_PASSWORD` del `.env`.

## Goals / Non-Goals

**Goals:**
- Un solo comando (`docker compose up -d`) para la aplicación completa en modo desarrollo, con recarga del frontend.
- Que el navegador vea exactamente los mismos orígenes que en el arranque manual, para no tocar sesión, CSRF ni CORS.
- Mantener el `.env` actual y los tests de integración en el host tal como están.

**Non-Goals:**
- Imágenes de producción (nginx, HTTPS, `SESSION_COOKIE_SECURE=true`) o despliegue.
- Correr los tests de integración dentro de un contenedor o con Testcontainers.
- Recarga en caliente del backend: un cambio en Java requiere `docker compose up -d --build backend`.
- Cambios de código de la aplicación, de `application.yml` o de migraciones.

## Decisions

### D1. Backend: Dockerfile en dos etapas con el wrapper de Gradle
- **Build**: imagen con JDK 21 (`eclipse-temurin:21-jdk`). Copia primero `gradlew`, `gradle/`, `build.gradle` y `settings.gradle` y resuelve dependencias, para reutilizar esa capa de la caché; después copia `src/` y corre `./gradlew bootJar --no-daemon -x test`.
  - Los tests se corren aparte en el host (`./gradlew build`): duplicarlos en la imagen alarga cada arranque.
  - La toolchain pide Java 21 y la imagen ya lo trae, así que foojay no descarga otro JDK.
- **Runtime**: `eclipse-temurin:21-jre`, usuario sin privilegios, `WORKDIR /app`, solo el jar copiado de la etapa de build.
  - `ENTRYPOINT ["java", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "/app/app.jar"]`.
  - El perfil `dev` es el por defecto de `application.yml`; igual se fija `SPRING_PROFILES_ACTIVE=dev` en el compose para que quede explícito.
- **Contexto**: `./backend`, con su propio `backend/.dockerignore` (`build`, `.gradle`, `data`, `.env*`).
- Alternativa descartada: copiar un jar compilado en el host. Exige Java en el host, que es justo lo que se quiere evitar.

### D2. Frontend: Vite en modo desarrollo con el código montado
- `frontend/Dockerfile` sobre `node:22-bookworm-slim` (cumple `>=20.19`; Debian en lugar de Alpine para evitar problemas con los binarios nativos de Vite 8). Copia `frontend/package.json` y `frontend/package-lock.json`, corre `npm ci` y deja como comando `npm run dev -- --host 0.0.0.0 --port 5173 --strictPort`.
- **Contexto de build en la raíz del repo**, como pide la ficha, porque el frontend importa `../design-system`. Los archivos se ubican en `/app/frontend` y `/app/design-system`, de modo que `../design-system` resuelve igual que en el host y `server.fs.allow` no cambia.
- **Volúmenes**:
  - `./frontend:/app/frontend` y `./design-system:/app/design-system:ro`, para la recarga.
  - Un volumen anónimo en `/app/frontend/node_modules`: tapa el `node_modules` de Windows montado desde el host y conserva el de Linux instalado en la imagen.
- `--host 0.0.0.0` va por línea de comando, no en `vite.config.ts`, para no tocar el archivo de Enzo.
- **Polling de archivos**: en Docker Desktop para Windows, los eventos de archivos de un bind mount pueden no llegar al contenedor.
  - Primero se prueba con `CHOKIDAR_USEPOLLING=true` en el compose.
  - Si Vite 8 no lo respeta, se agrega a `vite.config.ts` `server.watch.usePolling` condicionado a una variable de entorno (`VITE_USE_POLLING=true`). Fuera de Docker queda apagado, y el cambio se avisa a Enzo.
- Alternativa descartada: nginx con el `dist` compilado. No recarga al editar (decisión de Emanuel del 2026-11-25).

### D3. docker-compose.yml: tres servicios, mismos orígenes para el navegador
- `postgres`: igual que hoy, más un `healthcheck` con `pg_isready -U "$${POSTGRES_USER}" -d turnos` (el `$$` evita que el compose interpole la variable).
- `backend`:
  - `build: ./backend`, `depends_on: postgres: condition: service_healthy`, puertos `127.0.0.1:8080:8080`;
  - `env_file: .env`, para tomar todas las variables opcionales que ya usa el backend, como `APP_ADMIN_*` y `CONSULTORIO_*`;
  - `environment`, que gana sobre `env_file`, con dos valores: `DB_URL: jdbc:postgresql://postgres:5432/turnos` (red interna, independiente del puerto que publique el host) e `IMAGENES_DIRECTORIO: /app/data/imagenes`.
  - Así el `.env` sigue apuntando a `localhost` para `bootRun` y los tests del host.
- `frontend`:
  - `build: { context: ., dockerfile: frontend/Dockerfile }`, puertos `127.0.0.1:5173:5173`;
  - `environment: VITE_API_BASE_URL: http://localhost:8080/api/v1`, porque es el navegador del host el que llama a la API;
  - `depends_on: backend` sin condición, porque Vite no necesita la API para arrancar;
  - no recibe el `.env`: el frontend no necesita credenciales.
- Volúmenes con nombre: `turnos-pgdata` (existente) y `turnos-imagenes`, montado en `/app/data/imagenes` del backend.
- **Por qué no cambian la cookie, el CSRF ni el CORS**: el navegador sigue abriendo `localhost:5173` y llamando a `localhost:8080`. Mismo sitio para `SameSite=Lax`, y `APP_CORS_ORIGENES` por defecto ya permite ese origen. Regla dura 8.
- **Con un override local**: un `docker-compose.override.yml` que reemplaza los `ports` de `postgres` (`!override`) no afecta al backend, que usa la red interna. Los puertos 8080 y 5173 no chocan con el 5432/5433 de la base. Si el integrante corre `bootRun` o `npm run dev` en el host al mismo tiempo, choca con 8080 o 5173: el README lo advierte y propone `docker compose up -d postgres` para el camino manual.

### D4. Credenciales y contexto de build
- `.dockerignore` en la raíz (contexto del frontend) excluye `.env`, `.env.*` (salvo `.env.example`), `**/node_modules`, `**/build`, `**/.gradle`, `**/dist`, `backend/data`, `data`, `.git`, `.claude`, `.agents`, `.playwright-mcp`, `openspec`, `knowledge-base` y `docs`. El build del frontend no necesita nada de eso, y achica el contexto.
- `backend/.dockerignore` excluye `.env*`, `build`, `.gradle` y `data`.
- Las variables sensibles llegan solo en tiempo de ejecución (`env_file`/`environment`), nunca como `ARG` ni `ENV` de los Dockerfile. Así no quedan en las capas ni en `docker history`.
- `docker compose config` sin flags muestra los valores interpolados desde el `.env` local. Por eso la verificación de "sin secretos literales" usa `--no-interpolate` y revisa el archivo versionado.

### D5. Documentación
- README: "Instalación" y "Correr la aplicación" pasan a tener primero el camino con Docker (`cp .env.example .env`, completar, `docker compose up -d --build`, abrir `localhost:5173`).
- El camino manual sin Docker, o solo con la base en Docker, queda como sección propia, con los pasos actuales (PA-11, SU-04).
- El README suma cómo:
  - ver logs (`docker compose logs -f backend`);
  - reconstruir el backend tras un cambio en Java;
  - renovar `node_modules` tras cambiar `package-lock.json` (`docker compose up -d --build -V frontend`);
  - resolver un puerto ocupado con un override local.
- `.env.example`: se aclara que `DB_URL` y `DB_TEST_URL` son las del host (tests y `bootRun`), y que el compose reemplaza `DB_URL` solo dentro del contenedor. También que `VITE_API_BASE_URL` la fija el compose para el contenedor.

## Risks / Trade-offs

- [Recarga de Vite sin eventos de archivo en Windows] → `CHOKIDAR_USEPOLLING` y, si no alcanza, `server.watch.usePolling` condicionado a una variable (D2). La tarea 3 lo verifica en Windows.
- [El volumen anónimo de `node_modules` queda viejo al cambiar `package-lock.json`] → documentar `docker compose up -d --build -V frontend`.
- [El primer build del backend descarga Gradle y dependencias (varios minutos, requiere red)] → capa de dependencias separada para la caché; documentar que la primera vez tarda.
- [Doble backend: `bootRun` en el host y contenedor a la vez → 8080 ocupado] → README: para el camino manual, `docker compose up -d postgres`.
- [El backend en el contenedor y los tests de integración comparten el servidor PostgreSQL] → usan bases distintas (`turnos` y `turnos_test`), como hoy; los tests nunca apuntan a `turnos`.
- [Imágenes clínicas en un volumen con nombre, fuera de `backend/data/`] → las de un `bootRun` previo no aparecen en el contenedor. Con datos ficticios no hay migración que hacer; se documenta.
- [Healthcheck del backend no incluido] → el frontend no lo necesita. Se evita sumar `curl` a la imagen JRE.

## Migration Plan

1. Mergear con aviso a Juan Cruz y a Enzo (tarea 1).
2. Cada integrante corre `docker compose down` (el volumen de la base se conserva) y después `docker compose up -d --build`.
3. Rollback: `docker compose down` y `docker compose up -d postgres` devuelven el entorno de antes; el arranque manual sigue funcionando sin cambios.

## Open Questions

- PA-11 sigue abierta: quién del equipo tiene Docker. No cambia el diseño, porque el camino manual queda documentado.

## UI Mockups

- Design system revision: no aplica
- Decision: no (no UI) (2026-11-25)
- Mockups: no aplica
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Notas del apply (2026-11-25)

1. **Imagen de ejecución del backend: JDK en lugar de JRE (cambia D1).** `eclipse-temurin:21-jre` no incluye el módulo `jdk.random`, y la reserva usa `RandomGenerator.getDefault()` (`L32X64MixRandom`): el backend fallaba al arrancar con "No implementation of the random number generator algorithm". Como el change no toca código de la aplicación, la etapa de ejecución usa `eclipse-temurin:21-jdk`. Sigue copiando solo el jar y corriendo con un usuario sin privilegios. Alternativa para más adelante: un runtime armado con `jlink` que sume `jdk.random`.
2. **`/app/data/imagenes` existe vacío en la imagen**: es el punto de montaje del volumen `turnos-imagenes`, creado con dueño `turnos` para que el volumen herede los permisos. No contiene datos.
3. **Recarga del frontend**: con `CHOKIDAR_USEPOLLING=true` alcanzó; Vite registró `hmr update` al guardar en Windows con Docker Desktop. No hizo falta el plan B ni tocar `vite.config.ts`.
4. **`container_name: turnos-postgres`** (de C-03) impide levantar dos proyectos a la vez: la copia de prueba de la tarea 2.2 (`-p turnos-verif`) se corrió después de `docker compose down` del entorno de trabajo. Se dejó el nombre, porque el README lo usa en `docker exec turnos-postgres`.
5. **Tarea 2.5** se verificó sin iniciar sesión: la cantidad de turnos de la base y un archivo ficticio en el volumen de imágenes clínicas siguieron iguales después de `docker compose down`, `docker compose build` y `docker compose up -d`.
6. **Tarea 4.5**: en PowerShell, `$env:DB_PASSWORD=''` borra la variable y el compose toma la del `.env`. La verificación usó un `--env-file` temporal con `DB_PASSWORD` vacío: falló con "required variable DB_PASSWORD is missing a value" y no creó contenedores.
