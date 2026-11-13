# ===== 构建阶段：Maven 打 fat jar =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先拷 pom 预下载依赖，源码变动时可复用该层缓存
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -q -B -DskipTests package

# ===== 运行阶段：仅 JRE，镜像更小 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# 运行时数据（uploads/、data/）由 compose 挂载命名卷持久化
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
