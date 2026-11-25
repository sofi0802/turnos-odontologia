# entorno-desarrollo Specification

## Purpose

Define cómo se levanta la aplicación completa (base de datos, backend y frontend) en una máquina de desarrollo con un solo comando, y qué garantías de seguridad, de datos ficticios y de convivencia con el arranque manual y los tests de integración cumple ese entorno.

## Requirements

### Requirement: Arranque de la aplicación completa con un solo comando
El entorno de desarrollo SHALL levantar la base de datos, el backend y el frontend con `docker compose up -d` desde la raíz del repo, usando solo el `.env` local, sin pasos manuales previos de Java, Gradle ni Node en el host. El backend MUST quedar en `http://localhost:8080` y el frontend en `http://localhost:5173`.

#### Scenario: Primer arranque desde un volumen vacío
- **GIVEN** un `.env` completo según `.env.example` y ningún volumen de la base creado
- **WHEN** el integrante corre `docker compose up -d --build` desde la raíz del repo
- **THEN** los servicios `postgres`, `backend` y `frontend` quedan en ejecución
- **AND** `GET http://localhost:8080/api/v1/public/sesion` responde `200` con un cuerpo que indica que no hay sesión
- **AND** `GET http://localhost:5173/` responde `200` con la página del frontend

#### Scenario: La base queda migrada y con el seed ficticio
- **GIVEN** el entorno levantado desde un volumen vacío
- **WHEN** se consulta la base `turnos` del servicio `postgres`
- **THEN** Flyway aplicó todas las migraciones y el seed de desarrollo, y existen los sillones y profesionales ficticios del seed
- **AND** existe también la base `turnos_test` para los tests de integración

### Requirement: El backend espera a que la base esté lista
El backend SHALL arrancar solo después de que la base de datos acepte conexiones, y MUST conectarse a ella por la red interna del compose, sin depender del puerto que la base publique en el host.

#### Scenario: Arranque ordenado
- **WHEN** se levanta el entorno con `docker compose up -d`
- **THEN** el servicio `backend` inicia recién cuando el healthcheck de `postgres` informa que está sano
- **AND** el log del backend no muestra errores de conexión a la base durante el arranque

#### Scenario: La base publicada en otro puerto del host
- **GIVEN** un `docker-compose.override.yml` local que publica `postgres` en el puerto `5433` del host
- **WHEN** se levanta el entorno con `docker compose up -d`
- **THEN** el backend se conecta igual a la base y `GET http://localhost:8080/api/v1/public/sesion` responde `200`
- **AND** los puertos `8080` y `5173` del backend y del frontend no cambian

### Requirement: Zona horaria canónica en el backend
El backend del entorno de desarrollo MUST correr con la zona horaria `America/Argentina/Buenos_Aires`, la misma que usan `bootRun` y los tests, para que PostgreSQL acepte la conexión y las horas de los turnos sean las del consultorio.

#### Scenario: El proceso del backend usa la zona canónica
- **WHEN** se inspecciona la línea de comando del proceso Java del servicio `backend`
- **THEN** incluye `-Duser.timezone=America/Argentina/Buenos_Aires`
- **AND** el backend se conecta a PostgreSQL sin el error de zona horaria inválida

### Requirement: Sesión, CSRF y CORS sin cambios en el entorno con Docker
El entorno con Docker SHALL conservar la autenticación por sesión con cookie `HttpOnly`, el CSRF activo y el CORS restringido a `APP_CORS_ORIGENES`, igual que el arranque manual. El navegador MUST seguir usando `localhost:5173` para el frontend y `localhost:8080` para la API. Nunca se usa JWT ni se desactiva el CSRF.

#### Scenario: Ingreso desde el frontend en Docker
- **GIVEN** el entorno levantado y la cuenta ADMIN inicial definida en el `.env`
- **WHEN** el integrante abre `http://localhost:5173`, ingresa desde Mi cuenta con esa cuenta y recarga la página
- **THEN** la sesión se mantiene, la cookie de sesión tiene el atributo `HttpOnly` y la API responde a los pedidos del frontend sin errores de CORS

#### Scenario: Pedido que cambia datos sin token CSRF
- **WHEN** se envía `POST http://localhost:8080/api/v1/public/login` sin el token CSRF
- **THEN** la API responde `403`

#### Scenario: Origen no permitido
- **WHEN** se envía un pedido a la API con un encabezado `Origin` distinto de los de `APP_CORS_ORIGENES`
- **THEN** la respuesta no incluye `Access-Control-Allow-Origin` para ese origen

### Requirement: Recarga del frontend al editar
El frontend del entorno con Docker SHALL servirse en modo desarrollo con el código del repo montado, de modo que un cambio guardado en `frontend/src/` o en `design-system/` se vea en el navegador sin reconstruir la imagen ni reiniciar el contenedor.

#### Scenario: Edición de un archivo del frontend
- **GIVEN** el entorno levantado y la página abierta en `http://localhost:5173`
- **WHEN** el integrante guarda un cambio en un archivo de `frontend/src/` desde el host
- **THEN** el servidor de desarrollo detecta el cambio y el navegador muestra la versión nueva sin reconstruir la imagen

#### Scenario: Dependencias del contenedor separadas de las del host
- **GIVEN** un `frontend/node_modules` instalado en el host (por ejemplo en Windows)
- **WHEN** se levanta el servicio `frontend`
- **THEN** el contenedor usa sus propias dependencias instaladas para Linux y el `frontend/node_modules` del host no se modifica

### Requirement: Credenciales solo por variables de entorno
El entorno con Docker MUST tomar las credenciales y los valores sensibles solo de las variables del `.env` local. Ningún archivo versionado ni ninguna imagen construida MUST contener contraseñas, claves, tokens ni el archivo `.env`.

#### Scenario: El compose versionado no tiene secretos literales
- **WHEN** se revisa `docker-compose.yml` y su resultado con `docker compose config --no-interpolate`
- **THEN** las credenciales aparecen solo como referencias `${VARIABLE}` y no hay contraseñas, claves ni tokens literales

#### Scenario: Las imágenes no contienen el .env
- **WHEN** se listan los archivos de las imágenes del `backend` y del `frontend` y su historial de construcción
- **THEN** ninguna contiene un archivo `.env` ni valores de `DB_PASSWORD`, `APP_ADMIN_PASSWORD` u otras variables sensibles

#### Scenario: Falta una credencial obligatoria
- **GIVEN** un `.env` sin `DB_PASSWORD`
- **WHEN** se corre `docker compose up -d`
- **THEN** el comando falla antes de crear los contenedores con un mensaje que indica definir la variable en el `.env`

### Requirement: Servicios expuestos solo en la máquina local
Los puertos que el entorno publica en el host (base, backend y frontend) MUST quedar ligados a `127.0.0.1`, para que la aplicación de desarrollo y su base no sean accesibles desde la red local.

#### Scenario: Puertos publicados
- **WHEN** se listan los puertos publicados con `docker compose ps`
- **THEN** los de `backend` y `frontend` están ligados a `127.0.0.1`, y el de `postgres` a `127.0.0.1` salvo que un override local lo cambie

### Requirement: Persistencia de los datos de desarrollo fuera de las imágenes
Los datos de la base y el almacén de imágenes clínicas del backend SHALL guardarse en volúmenes de Docker, fuera de las imágenes, de modo que sobrevivan a `docker compose down` y a una reconstrucción de las imágenes.

#### Scenario: Reinicio del entorno
- **GIVEN** un turno ficticio creado y una imagen clínica ficticia subida en el entorno levantado
- **WHEN** se corre `docker compose down`, se reconstruyen las imágenes y se vuelve a correr `docker compose up -d`
- **THEN** el turno y la imagen clínica siguen disponibles

### Requirement: Convivencia con el arranque manual y los tests de integración
El entorno con Docker SHALL convivir con el arranque manual documentado (base en Docker y backend y frontend en el host, o PostgreSQL nativo) y con los tests de integración que corren en el host, sin cambiar el `.env` ni las variables `DB_URL` y `DB_TEST_URL` del host.

#### Scenario: Tests de integración con el entorno levantado
- **GIVEN** el entorno levantado con `docker compose up -d` y el `.env` cargado en la terminal del host
- **WHEN** se corre `./gradlew integrationTest` desde `backend/`
- **THEN** los tests usan la base `turnos_test` del servicio `postgres` y pasan

#### Scenario: Arranque manual sin el backend en Docker
- **GIVEN** solo el servicio `postgres` levantado con `docker compose up -d postgres`
- **WHEN** el integrante sigue los pasos del arranque manual del README (`bootRun` y `npm run dev`)
- **THEN** la aplicación funciona en `localhost:8080` y `localhost:5173` como antes de este change
