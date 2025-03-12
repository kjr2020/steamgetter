FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY build/libs/steamgetter-1.0.jar app.jar
CMD ["java", "-jar", "app.jar"]