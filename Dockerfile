# ---- Build: compile and package with the Maven wrapper ------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies in their own layer, so it is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run with `./mvnw test`, outside the image build (see README).
RUN ./mvnw -B -q -DskipTests package

# ---- Run: JRE only, as a non-root user -----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 portfolio
COPY --from=build /workspace/target/institution-portfolio-*.jar app.jar
USER portfolio

EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
