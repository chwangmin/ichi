# ---- build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 의존성 캐시: wrapper + 빌드스크립트 먼저
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# 소스 복사 후 빌드
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- run stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
