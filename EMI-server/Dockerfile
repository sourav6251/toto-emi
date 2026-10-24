# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Ensure Maven respects container memory limits on Render free tier (512MB RAM)
ENV MAVEN_OPTS="-Xmx400m -XX:+UseSerialGC"

COPY pom.xml .
COPY src ./src

# Build JAR with tests skipped and progress logs visible (avoids silent timeouts)
RUN mvn -B clean package -DskipTests

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
