WORKDIR /app

# 소스 코드 복사
COPY . .

# Gradle 빌드 실행
RUN gradle build -x test

# 2️⃣ 실행 단계
FROM eclipse-temurin:17-jdk
WORKDIR /app

# builder 단계에서 생성된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 시작 시 실행할 명령어
CMD ["java", "-jar", "app.jar"]

