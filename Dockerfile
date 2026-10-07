# Multi-stage Dockerfile for the c2csectrade backend

# Stage 1: Build stage
FROM maven:3.9-amazoncorretto-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies (uses the Docker layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Install curl for the health check
RUN apk add --no-cache curl font-wqy-zenhei

# Create a non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

# Copy the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Switch to the non-root user
USER appuser

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start the application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]

