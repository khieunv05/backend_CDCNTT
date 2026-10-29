# syntax=docker/dockerfile:1
#
# Image chạy backend LEnglish. Chỉ dùng cho môi trường local/dev.
# Build:  docker build -t lenglish-backend .
# Hoặc:   docker compose --profile app up -d --build

# ---------- Giai đoạn 1: build bằng Maven + JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache ~/.m2 giữa các lần build để không tải lại toàn bộ dependency.
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true clean package

# ---------- Giai đoạn 2: image chạy, chỉ có JRE ----------
FROM eclipse-temurin:21-jre

# Chạy bằng user thường, không dùng root.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
# target/ chỉ còn duy nhất file fat-jar sau khi package (file *.jar.original không khớp *.jar).
COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Ứng dụng không có actuator; healthcheck chỉ kiểm tra cổng 8080 đã mở.
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=6 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
