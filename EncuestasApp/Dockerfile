# ── Etapa 1: compilación ──────────────────────────────────────────────────
# Si la imagen maven:3.9-eclipse-temurin-25 no está disponible,
# cambia 25 por 21 (LTS) y ajusta <java.version> en pom.xml.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# CAPA 1: solo el pom.xml → descarga dependencias una vez.
# Docker cachea esta capa: si pom.xml no cambió, este paso se SALTA por completo.
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# CAPA 2: el código fuente → solo recompila cuando src/ cambia.
# Las dependencias ya están en .m2 dentro de la capa anterior.
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Etapa 2: imagen de ejecución ──────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/votaciones-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
