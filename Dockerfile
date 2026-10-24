# ---------------------------------------------------------------------------
# Stage 1: BUILD - compile the Spring Boot app with Maven
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POM first so Docker can cache the dependency download layer.
# (This layer only re-runs when pom.xml changes -> much faster rebuilds.)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Now copy the source and build the executable jar.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: RUNTIME - small JRE-only image (no Maven, no source)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Best practice: don't run the app as root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy ONLY the built jar from the build stage.
COPY --from=build /workspace/target/*.jar app.jar

# The app listens on 8080 (override with the PORT env var on AWS).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
