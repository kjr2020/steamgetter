FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY /var/lib/gits/steamgetter/build/libs/steamgetter-1.0.jar app.jar
CMD ["java", "-jar", "app.jar"]

