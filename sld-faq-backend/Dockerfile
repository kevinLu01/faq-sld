# ============================================================
# Stage 1: 编译阶段
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 层缓存下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: 运行阶段（使用与构建阶段相同的镜像，避免额外拉取）
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine

WORKDIR /app

# 从编译阶段复制 jar
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
