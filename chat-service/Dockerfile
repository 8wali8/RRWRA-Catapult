FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/chat-service-*.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]