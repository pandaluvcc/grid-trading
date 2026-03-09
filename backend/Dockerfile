# backend/Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 jar 包
COPY app.jar app.jar

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 暴露端口
EXPOSE 9090

# 启动命令
# --spring.config.additional-location 支持外部配置文件，方便后续接入配置中心
ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
    "--spring.config.additional-location=/app/config/"]
