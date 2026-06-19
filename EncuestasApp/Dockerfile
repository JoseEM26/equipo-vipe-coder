# ── Etapa 1: compilación ──────────────────────────────────────────────────
# Si la imagen maven:3.9-eclipse-temurin-25 no está disponible,
# cambia 25 por 21 (LTS) y ajusta <java.version> en pom.xml si es necesario.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Descargar dependencias primero (capa cacheada mientras pom.xml no cambie)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Compilar el proyecto
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Etapa 2: imagen de ejecución ──────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/votaciones-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
