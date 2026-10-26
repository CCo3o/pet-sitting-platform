FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system pets && useradd --system --gid pets pets && mkdir -p /app/data && chown pets:pets /app/data
COPY --from=build /app/target/pet-sitting-platform-0.1.0.jar /app/app.jar
USER pets
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
