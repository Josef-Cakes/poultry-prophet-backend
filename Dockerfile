FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/poultry-prophet-backend-0.1.0.jar app.jar
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
