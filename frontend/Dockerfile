# Multi-stage Dockerfile for the c2csectrade frontend

# Stage 1: Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy the package files and install dependencies
COPY package*.json ./
RUN npm ci

# Copy the source and build
COPY . .
RUN npm run build

# Stage 2: Production stage with Nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Install curl for the health check
RUN apk add --no-cache curl

# Remove the default nginx page
RUN rm -rf ./*

# Copy the build output from the build stage
COPY --from=build /app/dist .

# Copy the nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose the port
EXPOSE 80

# Health check (curl)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:80 || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]

