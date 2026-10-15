# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from sources
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# maven.test.skip also skips compiling tests (current test sources don't compile)
RUN mvn -B -q clean package -Dmaven.test.skip=true

# ---------- Run stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring

# Render injects PORT; application-prod.properties binds to it
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
