#!/bin/bash

# Clean and rebuild the project

echo "🧹 Cleaning the Maven build..."
mvn clean

echo "📦 Rebuilding the project (skipping tests)..."
mvn compile -DskipTests

echo "✅ Build complete!"
echo ""
echo "Start the project with:"
echo "  mvn spring-boot:run"

