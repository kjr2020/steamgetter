FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV HTTP_PROXY=http://krmp-proxy.9rum.cc:3128
ENV HTTPS_PROXY=http://krmp-proxy.9rum.cc:3128
COPY . .

RUN gradle clean build

FROM eclipse-temurin:17-jdk
WORKDIR /app

ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV HTTP_PROXY=http://krmp-proxy.9rum.cc:3128
ENV HTTPS_PROXY=http://krmp-proxy.9rum.cc:3128

COPY --from=builder /app/build/libs/*.jar app.jar

CMD ["java", "-jar", "app.jar"]

