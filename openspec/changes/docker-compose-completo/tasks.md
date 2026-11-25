# Tasks

> Rama `change/C-52-docker-compose-completo`. Gobernanza MEDIO: implementar con checkpoints y avisar las decisiones.
>
> Sin código de la aplicación ni migraciones. Sin UI nueva: `design.md` registra `Decision: no (no UI)`.
>
> Regla dura 7: cada escenario de `specs/entorno-desarrollo/spec.md` tiene su verificación. Es infraestructura, así que se verifica con comandos, que se ejecutan desde la raíz del repo en Git Bash salvo que se indique otra cosa.
>
> Cada resultado (comando, salida resumida y fecha) se anota en la descripción del PR. Nunca se pegan valores del `.env` en el PR ni en los logs anotados (regla dura 4).

## 0. Avisos

- [ ] 0.1 Avisar en el PR a Juan Cruz (dueño de `docker-compose.yml`) y a Enzo (dueño de `frontend/`) qué archivos se tocan y por qué (D3, D2). Verificar que el aviso, con su mención, quede en la descripción del PR.

## 1. Imagen del backend

- [x] 1.1 Crear `backend/.dockerignore` (excluye `.env*`, `build`, `.gradle` y `data`) y `backend/Dockerfile` en dos etapas según D1:
  - build con `eclipse-temurin:21-jdk` y `./gradlew bootJar --no-daemon -x test`, con la capa de dependencias separada;
  - runtime con `eclipse-temurin:21-jre`, usuario sin privilegios y `ENTRYPOINT` con `-Duser.timezone=America/Argentina/Buenos_Aires`.

  Verificar con `docker build -t turnos-backend:verif ./backend` (exit 0).
- [x] 1.2 Verificar que la imagen no tiene secretos ni datos locales:
  - `docker run --rm --entrypoint sh turnos-backend:verif -c 'find / -name ".env*" -not -path "/proc/*" 2>/dev/null; ls /app'` no lista ningún `.env` y en `/app` solo está el jar;
  - `docker history --no-trunc turnos-backend:verif | grep -Ei "DB_PASSWORD|APP_ADMIN_PASSWORD|TOKEN|SECRET"` no devuelve nada.

  Escenario "Las imágenes no contienen el .env" (parte backend).

## 2. Compose: base con healthcheck y backend

- [x] 2.1 En `docker-compose.yml`:
  - sumar a `postgres` el `healthcheck` con `pg_isready` (D3);
  - sumar el servicio `backend`: `build: ./backend`, `depends_on` con `condition: service_healthy`, `env_file: .env`, `SPRING_PROFILES_ACTIVE=dev`, `DB_URL=jdbc:postgresql://postgres:5432/turnos`, `IMAGENES_DIRECTORIO=/app/data/imagenes`, puerto `127.0.0.1:8080:8080` y el volumen `turnos-imagenes`.

  Verificar que `docker compose config --quiet` termina con exit 0.
- [x] 2.2 Escenarios "Primer arranque desde un volumen vacío" (parte backend), "Arranque ordenado" y "La base queda migrada y con el seed ficticio". En una copia de prueba, con un nombre de proyecto aparte para no borrar el volumen de trabajo:
  - `docker compose -p turnos-verif up -d --build postgres backend`;
  - `docker compose -p turnos-verif ps` muestra `postgres` como `healthy` antes de que inicie `backend` (orden de eventos con `docker compose -p turnos-verif events` o los timestamps de `logs`);
  - `docker compose -p turnos-verif logs backend` no tiene errores de conexión y muestra Flyway aplicado;
  - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/public/sesion` da `200`, y el cuerpo indica que no hay sesión;
  - `docker compose -p turnos-verif exec postgres psql -U "$DB_USER" -d turnos -c "select count(*) from sillon; select count(*) from profesional;"` da conteos mayores a 0;
  - `... psql -U "$DB_USER" -l` lista `turnos_test`.

  Al terminar, `docker compose -p turnos-verif down -v`.
- [x] 2.3 Escenario "Zona horaria canónica": `docker compose exec backend sh -c 'tr "\0" " " < /proc/1/cmdline'` incluye `-Duser.timezone=America/Argentina/Buenos_Aires`, y `docker compose logs backend | grep -i "invalid value for parameter \"TimeZone\""` no devuelve nada.
- [x] 2.4 Escenario "La base publicada en otro puerto del host": con un `docker-compose.override.yml` local que publica `postgres` en `5433:5432`, correr `docker compose up -d` y verificar:
  - `curl` a `http://localhost:8080/api/v1/public/sesion` da `200`;
  - `docker compose ps` muestra backend en 8080 y frontend en 5173 (este último después del grupo 3).

  Si el override lo crea la verificación, borrarlo después: nunca se versiona.
- [x] 2.5 Escenario "Reinicio del entorno":
  - con la cuenta ADMIN inicial, crear un turno ficticio y subir una imagen clínica ficticia (un PNG generado, sin datos de personas) desde el frontend o con `curl`;
  - correr `docker compose down`, `docker compose build` y `docker compose up -d`;
  - verificar que el turno y la imagen siguen disponibles, por la API o en la pantalla.

## 3. Imagen y servicio del frontend

- [x] 3.1 Crear el `.dockerignore` de la raíz según D4 y `frontend/Dockerfile` según D2:
  - `node:22-bookworm-slim`, `WORKDIR /app/frontend`;
  - `COPY frontend/package*.json` y `npm ci`;
  - `CMD npm run dev -- --host 0.0.0.0 --port 5173 --strictPort`.

  Verificar con `docker build -f frontend/Dockerfile -t turnos-frontend:verif .` (exit 0) y con `docker run --rm turnos-frontend:verif node --version`, que muestra una versión >= 20.19.
- [x] 3.2 Sumar el servicio `frontend` a `docker-compose.yml`:
  - build con contexto `.` y `dockerfile: frontend/Dockerfile`;
  - `VITE_API_BASE_URL=http://localhost:8080/api/v1` y `CHOKIDAR_USEPOLLING=true`;
  - puerto `127.0.0.1:5173:5173`;
  - volúmenes `./frontend:/app/frontend`, `./design-system:/app/design-system:ro` y el anónimo `/app/frontend/node_modules`;
  - `depends_on: backend`.

  Escenario "Primer arranque desde un volumen vacío" (parte frontend): `docker compose up -d --build` y `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/`, que da `200`.
- [x] 3.3 Escenario "Edición de un archivo del frontend", en Windows con Docker Desktop:
  - con `http://localhost:5173` abierto, agregar un comentario o un texto temporal en un archivo de `frontend/src/` y guardar;
  - verificar en `docker compose logs frontend` que Vite registra el cambio (hmr update o page reload) y que el navegador lo muestra sin reconstruir;
  - revertir la edición.

  Si no recarga, aplicar el plan B de D2: `server.watch.usePolling` en `vite.config.ts`, condicionado a `VITE_USE_POLLING`, avisando a Enzo. Después repetir la verificación y `npm test` y `npm run build` en el host.
- [x] 3.4 Escenario "Dependencias del contenedor separadas de las del host":
  - anotar la fecha de modificación de `frontend/node_modules/.package-lock.json` en el host;
  - levantar el servicio;
  - verificar que la fecha no cambió y que `docker compose exec frontend sh -c 'ls node_modules/.bin/vite && uname -s'` muestra `vite` y `Linux`.
- [x] 3.5 Verificar que la imagen del frontend no tiene secretos (escenario "Las imágenes no contienen el .env", parte frontend):
  - `docker run --rm turnos-frontend:verif sh -c 'find / -name ".env*" -not -path "/proc/*" -not -path "*/node_modules/*" 2>/dev/null'` no lista nada;
  - `docker compose exec frontend env | grep -E "DB_|APP_ADMIN"` no devuelve nada.

## 4. Seguridad y credenciales

- [x] 4.1 Escenario "Ingreso desde el frontend en Docker":
  - con el entorno levantado, abrir `http://localhost:5173` e ingresar desde Mi cuenta con la cuenta ADMIN inicial del `.env`;
  - recargar y verificar que la sesión se mantiene;
  - en las herramientas del navegador, verificar que `JSESSIONID` tiene `HttpOnly` y que la consola no muestra errores de CORS.

  Usar el navegador disponible (Playwright o el panel del navegador). No pegar la contraseña en el PR.
- [x] 4.2 Escenario "Pedido que cambia datos sin token CSRF": `curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -d '{"username":"x","password":"y"}' http://localhost:8080/api/v1/public/login` da `403`.
- [x] 4.3 Escenario "Origen no permitido": `curl -s -D - -o /dev/null -H "Origin: http://evil.localhost" http://localhost:8080/api/v1/public/sesion` no incluye `Access-Control-Allow-Origin: http://evil.localhost`. Con `-H "Origin: http://localhost:5173"` sí incluye ese origen.
- [x] 4.4 Escenario "El compose versionado no tiene secretos literales":
  - `docker compose config --no-interpolate` muestra las credenciales solo como `${...}`;
  - `git grep -nEi "password|secret|token" -- docker-compose.yml backend/Dockerfile frontend/Dockerfile .dockerignore backend/.dockerignore` solo encuentra nombres de variables o comentarios.
- [x] 4.5 Escenario "Falta una credencial obligatoria":
  - correr `DB_PASSWORD= docker compose up -d` (la variable vacía de la terminal gana sobre el `.env`, y `${DB_PASSWORD:?…}` rechaza el valor vacío);
  - verificar que falla con exit distinto de 0 y el mensaje "definí DB_PASSWORD en .env", y que no se creó ningún contenedor.
- [x] 4.6 Escenario "Puertos publicados": `docker compose ps --format "{{.Name}} {{.Ports}}"` muestra `127.0.0.1:8080->8080`, `127.0.0.1:5173->5173` y la base en `127.0.0.1:5432`, o en el puerto del override local.

## 5. Documentación

- [x] 5.1 Actualizar `README.md` según D5:
  - el arranque con Docker primero;
  - el camino manual (base en Docker o PostgreSQL nativo, `bootRun` y `npm run dev`) en su propia sección;
  - logs, reconstrucción del backend, renovación de `node_modules` (`-V`), puertos ocupados y override local, y que la primera construcción tarda.

  Verificar siguiendo el README tal cual en una terminal nueva: los comandos corren como están escritos.
- [ ] 5.2 Actualizar `.env.example` (solo nombres y valores de ejemplo, regla dura 4):
  - aclarar que `DB_URL` y `DB_TEST_URL` son las del host;
  - aclarar que el compose reemplaza `DB_URL` e `IMAGENES_DIRECTORIO` dentro del contenedor y fija `VITE_API_BASE_URL` para el frontend.

  Verificar con `git diff .env.example`: no agrega valores reales.
- [ ] 5.3 Registrar en `knowledge-base/10_preguntas_abiertas.md` y en `09_decisiones_y_supuestos.md` (SU-04), si la líder lo aprueba en el PR, que el entorno completo corre con Docker y que el camino manual sigue documentado. Verificar con `git diff knowledge-base/`.

## 6. Verificación integral

- [x] 6.1 Escenario "Tests de integración con el entorno levantado": con `docker compose up -d` y el `.env` cargado en Git Bash (`set -a; . ../.env; set +a` desde `backend/`), correr `./gradlew build integrationTest`. Verificar que termina en verde.
- [x] 6.2 Escenario "Arranque manual sin el backend en Docker":
  - correr `docker compose down` y `docker compose up -d postgres`;
  - seguir el camino manual del README (`./gradlew bootRun` y, en otra terminal, `npm ci` y `npm run dev` con `VITE_API_BASE_URL`);
  - verificar que `curl` a `localhost:8080/api/v1/public/sesion` y a `localhost:5173` dan `200`.
- [x] 6.3 Frontend en el host: `cd frontend && npm test && npm run build`, en verde (sin cambios de código; confirma que el plan B de 3.3, si se aplicó, no rompió nada).
- [x] 6.4 Correr `openspec validate docker-compose-completo --strict` (exit 0). Marcar `[x]` en la ficha de C-52 de `CHANGES.md` al abrir el PR a `master`.

## Workflow follow-up

- La líder revisa y mergea el PR.
- Después del merge: sincronizar la spec `entorno-desarrollo` y archivar el change (`openspec-archive-change`).
