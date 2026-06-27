FROM amazoncorretto:21 AS build

WORKDIR /workspace

RUN yum install -y tar gzip && yum clean all

# Copy wrapper + pom first so dependency resolution is cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN chmod +x mvnw

# Pre-resolve dependencies (best-effort; authoritative resolution is in package below).
RUN ./mvnw -B -e -q dependency:go-offline -DskipTests || true

# Build the thin jar (tests run in CI, not here).
COPY src/ src/
RUN ./mvnw -B -e clean package -DskipTests

# repackage emits the plain jar as *.jar.original, so this glob matches only the boot jar.
RUN cp target/*.jar app.jar

FROM amazoncorretto:21-alpine-jdk AS runtime

# Run as a non-root user (defense in depth).
RUN addgroup -S app && adduser -S -G app -H -h /app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
