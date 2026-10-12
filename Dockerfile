# Eclipse Temurin OpenJDK 25 JRE runtime image
FROM eclipse-temurin:25-jre

# Metadata
LABEL maintainer="Anuj Taware <anuj@housing-portal.local>"
LABEL description="Housing Society Billing Portal - Containerized Spring Boot Application"
LABEL version="v1.0"

# Set application working directory inside container
WORKDIR /app

# Copy the pre-packaged Spring Boot executable JAR into the container
COPY application/target/billing-portal-0.0.1-SNAPSHOT.jar app.jar

# Expose default application port
EXPOSE 8080

# Clean production entrypoint to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
