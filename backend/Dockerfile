# syntax=docker/dockerfile:1
# ===========================================================================
# RaiseChat backend（Spring Boot / Java 21）コンテナイメージ。
# ECS Fargate で実行する想定（infrastructure.md §4）。マルチステージで
# ビルド成果物（bootJar）だけを軽量な JRE ランタイムに載せる。
# ===========================================================================

# --- build stage -----------------------------------------------------------
# Gradle wrapper（8.14.4 / Java 21）でビルドする。レイヤキャッシュを効かせるため、
# 依存解決に必要なファイルを先に入れてから src をコピーする。
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# --- runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 非 root ユーザーで実行する。
RUN useradd --system --uid 10001 --create-home appuser
USER appuser

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

# JVM はコンテナの CPU/メモリ割当に追従させる（Fargate のタスクサイズに合わせる）。
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
