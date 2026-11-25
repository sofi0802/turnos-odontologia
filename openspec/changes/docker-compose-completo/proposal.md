# Proposal

## Why

Hoy `docker compose up` levanta solo PostgreSQL: para usar la aplicación hay que cargar el `.env` en una terminal, correr `./gradlew bootRun` con Java 21, abrir otra terminal, instalar Node y correr `npm run dev` con `VITE_API_BASE_URL` (README, pasos 3 y 4). Cada integrante repite esos pasos y tropieza con los mismos problemas (zona horaria de Java en Windows, versión de Node, variables sin cargar). Con el MVP completo (C-14) y la base en Docker (C-03), conviene que la aplicación entera arranque con un solo comando, sin perder el camino manual para quien no tenga Docker (PA-11, SU-04).

## What Changes

- Nuevo `backend/Dockerfile` en dos etapas: compila el jar con el wrapper de Gradle y JDK 21, y lo corre sobre una imagen de solo JRE 21 con el perfil `dev` (seed ficticio) y `-Duser.timezone=America/Argentina/Buenos_Aires`.
- Nuevo `frontend/Dockerfile` para Vite en modo desarrollo (Node >= 20.19), con el código de `frontend/` y `design-system/` montados como volúmenes para que la página recargue al editar, y `node_modules` en un volumen propio del contenedor.
- `docker-compose.yml` suma los servicios `backend` (puerto 8080, espera a que la base esté sana y se conecta por la red interna) y `frontend` (puerto 5173); `postgres` gana un healthcheck. Los puertos se publican solo en `127.0.0.1`, como la base.
- Nuevo `.dockerignore` que deja afuera del contexto de build `.env`, `node_modules`, `build`, `.gradle`, `dist` y el almacén local de imágenes clínicas.
- README: el arranque principal pasa a ser `docker compose up -d`; el arranque manual sin Docker (o solo con la base en Docker) queda documentado. `.env.example` explica qué variables usa cada camino.
- Sin cambios en el código de la aplicación, en la API ni en las migraciones. La cookie `HttpOnly`, el CSRF y `APP_CORS_ORIGENES` no cambian: el navegador sigue usando `localhost:5173` y `localhost:8080` (regla dura 8).
- No es **BREAKING**: el `.env` actual sigue sirviendo y los tests de integración siguen corriendo en el host contra la base del compose.

## Capabilities

### New Capabilities
- `entorno-desarrollo`: cómo se levanta la aplicación completa en una máquina de desarrollo (base, backend y frontend), qué garantías de seguridad y de datos ficticios cumple ese entorno, y su convivencia con el arranque manual y los tests de integración.

### Modified Capabilities
<!-- Ninguna: no cambia el comportamiento de la aplicación ni de su API. -->

## Impact

- **Archivos nuevos**: `backend/Dockerfile`, `frontend/Dockerfile`, `.dockerignore` (raíz, contexto del frontend) y `backend/.dockerignore` (contexto del backend).
- **Archivos modificados**: `docker-compose.yml` (dueño: Juan Cruz), `README.md`, `.env.example`. Posiblemente `frontend/vite.config.ts` (dueño: Enzo) solo si la recarga en Windows exige configurar el polling de archivos (ver design.md).
- **KB**: nota en `knowledge-base/10_preguntas_abiertas.md` (PA-11) y `09_decisiones_y_supuestos.md` (SU-04), si la líder la aprueba en el PR.
- **Sin cambios**: código Java y TypeScript, migraciones Flyway, `application.yml`, seguridad (sesión, CSRF, CORS).
- **Dependencias nuevas**: imágenes base públicas de Docker (JDK/JRE 21 y Node). Nada se agrega al build de Gradle ni a `package.json`.
- **Equipo**: Juan Cruz y Enzo reciben aviso en el PR. Los integrantes sin Docker siguen con el arranque manual.
- **Gobernanza**: MEDIO (configuración de desarrollo compartida por el equipo).
