# =========================
# ETAPA 1: CONSTRUCCIÓN
# =========================
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# =========================
# ETAPA 2: EJECUCIÓN
# =========================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system app && \
    useradd --system --gid app app

COPY --from=build --chown=app:app /app/target/*.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
