# Backend de turnos-odontologia para desarrollo (C-52, design D1).
# Etapa 1: compila el jar con el wrapper de Gradle y el JDK 21.
# Etapa 2: copia solo el jar y lo corre sin privilegios y con la zona horaria canónica.
# Ninguna credencial entra en la imagen: todo llega en tiempo de ejecución desde el .env (regla dura 4).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Capa de dependencias: solo cambia si cambian el wrapper o los build scripts.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src ./src
# Los tests corren en el host (./gradlew build); acá solo se arma el jar ejecutable.
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" /src/app.jar

# Se corre sobre el JDK y no sobre eclipse-temurin:21-jre: ese JRE no incluye el módulo jdk.random,
# y la reserva usa RandomGenerator.getDefault() (L32X64MixRandom), que falla al arrancar sin él.
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Usuario sin privilegios. El directorio de imágenes clínicas se crea con su dueño para que el
# volumen con nombre que se monta encima herede los permisos (los datos nunca quedan en la imagen).
RUN groupadd --system turnos \
    && useradd --system --gid turnos --home-dir /app --no-create-home turnos \
    && mkdir -p /app/data/imagenes \
    && chown -R turnos:turnos /app

COPY --from=build --chown=turnos:turnos /src/app.jar /app/app.jar
USER turnos

EXPOSE 8080
# En Windows, Java usa un alias viejo de la zona que PostgreSQL 16 rechaza: se fija la canónica (C-04, C-14).
ENTRYPOINT ["java", "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "/app/app.jar"]
