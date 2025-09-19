FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/eureka-server-*.jar app.jar

EXPOSE 8761

CMD ["java", "-jar", "app.jar"]