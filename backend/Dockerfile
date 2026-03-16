# backend/Dockerfile - 分离依赖，利用 Docker 缓存
# lib 目录变化少（缓存），jar 文件变化多（每次更新）

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 暴露端口
EXPOSE 9090

# 先复制 lib（依赖变化少，会被缓存）
COPY lib/ lib/

# 再复制 jar（代码变化多，每次重建）
COPY app.jar app.jar

# 启动命令（ZIP layout 使用 PropertiesLauncher，loader.path 指定外部 lib 目录）
ENTRYPOINT ["java", \
    "-Dloader.path=lib/", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
    "-Dspring.config.additional-location=/app/config/", \
    "-jar", "app.jar"]
