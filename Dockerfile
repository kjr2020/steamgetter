FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
ARG HTTP_PROXY=http://krmp-proxy.9rum.cc:3128
ARG HTTPS_PROXY=http://krmp-proxy.9rum.cc:3128
ENV HTTP_PROXY=$HTTP_PROXY
ENV HTTPS_PROXY=HTTPS_PROXY
COPY . .

RUN gradle clean build

FROM eclipse-temurin:17-jdk
WORKDIR /app

ARG HTTP_PROXY=http://krmp-proxy.9rum.cc:3128
ARG HTTPS_PROXY=http://krmp-proxy.9rum.cc:3128
ENV HTTP_PROXY=$HTTP_PROXY
ENV HTTPS_PROXY=$HTTPS_PROXY

COPY --from=builder /app/build/libs/*.jar app.jar

CMD ["java", "-jar", "app.jar"]

