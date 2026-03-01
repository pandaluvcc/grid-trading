# backend/Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制 jar 包
COPY target/*.jar app.jar

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]

