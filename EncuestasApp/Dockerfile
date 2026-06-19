# syntax=docker/dockerfile:1

# ── Etapa 1: compilación ──────────────────────────────────────────────────
# Si la imagen maven:3.9-eclipse-temurin-25 no está disponible,
# cambia 25 por 21 (LTS) y ajusta <java.version> en pom.xml.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copiar solo el pom.xml primero para cachear dependencias independientemente
# del código fuente. Si el pom.xml no cambia, este paso no se repite.
COPY pom.xml .

# --mount=type=cache persiste ~/.m2 entre builds en BuildKit (Docker Desktop lo
# tiene activo por defecto). Primera vez: descarga todo. Siguientes: usa caché.
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

# Copiar el código y compilar (también con caché de ~/.m2)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B -q

# ── Etapa 2: imagen de ejecución ──────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/votaciones-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
