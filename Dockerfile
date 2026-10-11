# Multi-stage Dockerfile for the c2csectrade backend

# One Dockerfile builds every service: pass --build-arg MODULE=search or
# MODULE=chat for the other two. Core is the default.

# Stage 1: Build stage
FROM maven:3.9-amazoncorretto-17 AS build
ARG MODULE=core
WORKDIR /app

# Copy the poms and download dependencies (uses the Docker layer cache)
COPY pom.xml .
COPY common/pom.xml common/
COPY core/pom.xml core/
COPY search/pom.xml search/
COPY chat/pom.xml chat/
RUN mvn dependency:go-offline -B -pl ${MODULE} -am

# Copy the shared library and the chosen service, then build only those
COPY common/src ./common/src
COPY ${MODULE}/src ./${MODULE}/src
RUN mvn clean package -DskipTests -B -pl ${MODULE} -am

# Stage 2: Runtime stage
FROM amazoncorretto:17-alpine
ARG MODULE=core
WORKDIR /app

# Every ECS service listens on 8080; Search and Chat default to other ports
# only so they can run beside Core on a laptop.
ENV SERVER_PORT=8080

# Install curl for the health check
RUN apk add --no-cache curl font-wqy-zenhei

# Create a non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

# Copy the jar from the build stage
COPY --from=build /app/${MODULE}/target/*.jar app.jar

# Switch to the non-root user
USER appuser

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Start the application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]

