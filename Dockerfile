# Run stage - expects JAR to be pre-built locally
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/foodie-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
