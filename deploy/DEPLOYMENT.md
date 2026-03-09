# Grid Trading 完整部署教程

> 本教程将从零开始，一步步教你如何在服务器上部署 Grid Trading 应用。
> 适合想要学习完整部署过程的开发者。

---

## 目录

- [第一部分：准备工作](#第一部分准备工作)
- [第二部分：服务器环境搭建](#第二部分服务器环境搭建)
- [第三部分：本地构建](#第三部分本地构建)
- [第四部分：上传文件到服务器](#第四部分上传文件到服务器)
- [第五部分：服务器端配置与启动](#第五部分服务器端配置与启动)
- [第六部分：验证与测试](#第六部分验证与测试)
- [第七部分：日常运维](#第七部分日常运维)
- [附录：常见问题](#附录常见问题)

---

## 第一部分：准备工作

### 1.1 你需要准备的东西

| 项目 | 说明 |
|------|------|
| 一台云服务器 | 本教程以 Ubuntu 22.04 为例，IP: 117.72.157.115 |
| SSH 客户端 | Windows 用户可使用 PowerShell 或 PuTTY |
| 本地开发环境 | Maven 3.6+、Node.js 18+、Git |

### 1.2 本地与生产环境差异说明

| 环境 | 数据库 | 说明 |
|------|--------|------|
| 本地开发 | H2 内存数据库 | 无需安装，数据不持久化，重启后数据丢失 |
| 生产环境 | MySQL 8.0 | Docker 容器部署，数据持久化到服务器磁盘 |

> **注意**：本地开发时使用 H2 内存数据库，方便快速开发和测试。部署到服务器后会切换为 MySQL 数据库，数据会持久保存。

### 1.3 架构预览

部署完成后的架构如下：

```
┌─────────────────────────────────────────────────────────────┐
│                      用户浏览器                              │
│                    http://117.72.157.115                    │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   Docker 容器环境                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              OpenResty (Nginx) 容器                   │   │
│  │                    端口 80                            │   │
│  │  ┌─────────────────┐  ┌─────────────────────────┐   │   │
│  │  │  静态文件服务    │  │  API 反向代理 /api/*    │   │   │
│  │  │  Vue 前端页面    │  │  → backend:9090/api/*  │   │   │
│  │  └─────────────────┘  └─────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Backend (Spring Boot) 容器               │   │
│  │                    端口 9090                          │   │
│  │              Java 17 + JAR 应用                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 MySQL 8.0 容器                        │   │
│  │                    端口 3306                          │   │
│  │              数据库: grid_trading                     │   │
│  │              用户: grid_user                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 端口说明

| 端口 | 服务 | 外部访问 | 说明 |
|------|------|---------|------|
| 22 | SSH | 是 | 服务器管理 |
| 80 | OpenResty | 是 | 前端页面入口 |
| 9090 | Backend | 否 | 仅容器内部访问 |
| 3306 | MySQL | 否 | 仅容器内部访问 |

### 1.5 服务器目录结构

为了方便后续扩展其他项目，采用以下目录结构：

```
/data/docker/
├── platform/                           # 后端项目统一目录
│   └── grid-trading/                   # 网格交易后端项目
│       ├── app.jar                     # 后端 JAR 包
│       ├── Dockerfile                  # 后端 Dockerfile
│       ├── init.sql                    # 数据库初始化脚本
│       └── config/                     # 外部配置文件目录
│           └── application-prod.yml    # 生产环境配置
│
├── mysql/                              # MySQL 数据目录（所有项目共用）
│   ├── auto.cnf
│   ├── ibdata1
│   ├── grid_trading/                   # 网格交易数据库
│   └── ...
│
├── openresty/                          # OpenResty（所有项目共用）
│   └── nginx/
│       ├── conf.d/                     # Nginx 配置目录
│       │   └── default.conf            # 网格交易配置
│       └── content/                    # 前端项目统一目录
│           └── grid-trading/           # 网格交易前端项目
│               ├── index.html
│               └── assets/
│
└── docker-compose.yml                  # Docker Compose 配置
```

**设计说明**：
- `platform/` - 存放所有后端项目，每个项目一个子目录
- `platform/grid-trading/config/` - 外部配置文件目录，支持动态修改配置，方便后续接入配置中心
- `mysql/` - MySQL 数据目录，所有项目共用一个 MySQL 实例
- `openresty/` - OpenResty 作为统一入口，可以代理多个前端项目
- `openresty/nginx/content/` - 存放所有前端项目，每个项目一个子目录

---

## 第二部分：服务器环境搭建

### 2.1 连接服务器

打开 PowerShell（或终端），使用 SSH 连接服务器：

```powershell
ssh root@117.72.157.115
```

首次连接会提示确认指纹，输入 `yes` 然后输入密码。

### 2.2 更新系统

连接成功后，首先更新系统软件包：

```bash
# 更新软件包列表
apt update

# 升级已安装的软件包
apt upgrade -y
```

### 2.3 安装 Docker

Docker 是容器化平台，我们将用它来运行所有服务。

```bash
# 安装 Docker 和 Docker Compose 插件
apt install -y docker.io docker-compose-plugin

# 启动 Docker 服务
systemctl start docker

# 设置 Docker 开机自启
systemctl enable docker

# 验证安装
docker --version
docker compose version
```

预期输出类似：
```
Docker version 24.0.5, build 24.0.5-0ubuntu1~22.04.1
Docker Compose version v2.20.2
```

### 2.4 创建项目目录

```bash
# 创建后端项目目录
mkdir -p /data/docker/platform/grid-trading/config

# 创建 MySQL 数据目录
mkdir -p /data/docker/mysql

# 创建 OpenResty 目录结构
mkdir -p /data/docker/openresty/nginx/conf.d
mkdir -p /data/docker/openresty/nginx/content/grid-trading

# 查看创建的目录结构
tree /data/docker/ 2>/dev/null || find /data/docker/ -type d
```

目录结构说明：
```
/data/docker/
├── platform/grid-trading/
│   └── config/               # 外部配置文件目录
├── mysql/                    # MySQL 数据目录
└── openresty/nginx/
    ├── conf.d/               # Nginx 配置目录
    └── content/grid-trading/ # 网格交易前端项目
```

### 2.5 配置云服务商安全组

**重要！** 登录你的云服务商控制台，在安全组/防火墙中开放以下端口：

| 端口 | 协议 | 用途 |
|------|------|------|
| 22 | TCP | SSH 管理 |
| 80 | TCP | HTTP 访问 |

---

## 第三部分：本地构建

回到你的本地电脑，我们需要构建前端和后端。

### 3.1 构建后端 JAR 包

打开 PowerShell，进入项目目录：

```powershell
cd D:\01-develop\02-project\panda\grid-trading\backend

# 清理并打包（跳过测试）
mvn clean package -DskipTests
```

构建成功后，JAR 包位于：
```
backend\target\grid-trading-0.0.1-SNAPSHOT.jar
```

### 3.2 构建前端静态文件

```powershell
cd D:\01-develop\02-project\panda\grid-trading\frontend

# 安装依赖
npm install

# 构建生产版本
npm run build
```

构建成功后，静态文件位于：
```
frontend\dist\
├── index.html
├── assets/
│   ├── index-xxx.js
│   └── index-xxx.css
└── ...
```

---

## 第四部分：上传文件到服务器

### 4.1 上传后端文件

```powershell
# 在项目根目录执行

# 上传 JAR 包
scp backend/target/*.jar root@117.72.157.115:/data/docker/platform/grid-trading/app.jar

# 上传 Dockerfile
scp backend/Dockerfile root@117.72.157.115:/data/docker/platform/grid-trading/

# 上传数据库初始化脚本
scp backend/init.sql root@117.72.157.115:/data/docker/platform/grid-trading/

# 上传外部配置文件
scp backend/src/main/resources/application-prod.yml root@117.72.157.115:/data/docker/platform/grid-trading/config/
```

### 4.2 上传前端文件

```powershell
# 上传整个 dist 目录到 grid-trading 子目录
scp -r frontend\dist\* root@117.72.157.115:/data/docker/openresty/nginx/content/grid-trading/
```

### 4.3 上传 Nginx 配置

```powershell
# 上传 Nginx 配置文件
scp deploy\nginx\grid-trading.conf root@117.72.157.115:/data/docker/openresty/nginx/conf.d/default.conf
```

### 4.4 上传 docker-compose.yml

```powershell
# 上传 Docker Compose 配置
scp docker-compose.yml root@117.72.157.115:/data/docker/
```

---

## 第五部分：服务器端配置与启动

### 5.1 SSH 连接到服务器

```powershell
ssh root@117.72.157.115
```

### 5.2 验证文件上传成功

```bash
# 查看后端文件
ls -la /data/docker/platform/grid-trading/

# 查看外部配置文件
ls -la /data/docker/platform/grid-trading/config/

# 查看前端文件
ls -la /data/docker/openresty/nginx/content/grid-trading/

# 查看 Nginx 配置
ls -la /data/docker/openresty/nginx/conf.d/

# 查看 docker-compose.yml
ls -la /data/docker/docker-compose.yml
```

预期输出：
```
# 后端文件
/data/docker/platform/grid-trading/
├── app.jar
├── Dockerfile
├── init.sql
└── config/
    └── application-prod.yml    # 外部配置文件

# 前端文件
/data/docker/openresty/nginx/content/grid-trading/
├── index.html
└── assets/...

# Nginx 配置
/data/docker/openresty/nginx/conf.d/
└── default.conf

# Docker Compose
/data/docker/docker-compose.yml
```

### 5.3 修改外部配置文件（可选）

如果需要修改生产环境配置，可以直接编辑外部配置文件：

```bash
# 编辑配置文件
vi /data/docker/platform/grid-trading/config/application-prod.yml

# 修改后重启后端容器即可生效
docker compose restart backend
```

> **优势**：外部配置文件可以在不重新构建镜像的情况下修改配置，方便后续接入配置中心（如 Nacos、Apollo 等）。

### 5.4 理解 docker-compose.yml 配置

在启动之前，让我们理解一下 `docker-compose.yml` 的配置内容：

```bash
cat /data/docker/docker-compose.yml
```

配置文件包含三个服务：

#### MySQL 数据库服务

```yaml
mysql:
  image: mysql:8.0
  container_name: mysql
  restart: always
  environment:
    MYSQL_ROOT_PASSWORD: Root@2026!      # root 用户密码
    MYSQL_DATABASE: grid_trading          # 自动创建的数据库名
    MYSQL_USER: grid_user                 # 应用用户名
    MYSQL_PASSWORD: GridTrading@2026      # 应用用户密码
    TZ: Asia/Shanghai                     # 时区设置
  volumes:
    - /data/docker/mysql:/var/lib/mysql   # 数据持久化
    - /data/docker/platform/grid-trading/init.sql:/docker-entrypoint-initdb.d/init.sql:ro  # 初始化脚本
  command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**重要说明**：
- `MYSQL_DATABASE: grid_trading` - 容器启动时会自动创建这个数据库
- `MYSQL_USER` 和 `MYSQL_PASSWORD` - 创建一个普通用户，后端应用使用这个用户连接
- `/data/docker/mysql:/var/lib/mysql` - 将 MySQL 数据目录挂载到宿主机，实现数据持久化
- `healthcheck` - 健康检查，确保 MySQL 完全启动后，后端服务才开始启动

#### 后端服务

```yaml
backend:
  build:
    context: /data/docker/platform/grid-trading
    dockerfile: Dockerfile
  container_name: grid-backend
  restart: always
  depends_on:
    mysql:
      condition: service_healthy        # 等待 MySQL 健康后才启动
  environment:
    - SPRING_PROFILES_ACTIVE=prod       # 使用生产环境配置
    - MYSQL_PASSWORD=GridTrading@2026   # 数据库密码
    - TZ=Asia/Shanghai
  volumes:
    - /data/docker/platform/grid-trading/config:/app/config:ro  # 外部配置文件挂载
```

**重要说明**：
- `SPRING_PROFILES_ACTIVE=prod` - 激活 `application-prod.yml` 配置文件
- `volumes` - 挂载外部配置文件目录，支持动态修改配置
- 后端会自动连接到名为 `mysql` 的容器（Docker 内部 DNS 解析）
- 外部配置文件优先级高于 JAR 内部配置，方便后续接入配置中心

**重要说明**：
- `SPRING_PROFILES_ACTIVE=prod` - 激活 `application-prod.yml` 配置文件
- 后端会自动连接到名为 `mysql` 的容器（Docker 内部 DNS 解析）

#### 前端 + 反向代理服务

```yaml
openresty:
  image: openresty/openresty:alpine
  container_name: openresty
  restart: always
  ports:
    - "80:80"
  volumes:
    - /data/docker/openresty/nginx/content/grid-trading:/usr/share/nginx/html:ro
    - /data/docker/openresty/nginx/conf.d:/etc/nginx/conf.d:ro
  depends_on:
    - backend
```

### 5.4 启动所有服务

```bash
cd /data/docker

# 构建并启动所有容器（后台运行）
docker compose up -d --build
```

这个命令会：
1. 下载 MySQL 8.0 镜像（约 500MB，首次需要几分钟）
2. 下载 OpenResty 镜像（约 50MB）
3. 构建后端 Java 镜像（基于 Eclipse Temurin JRE 17）
4. 启动 MySQL 容器，等待健康检查通过
5. 启动后端容器，连接到 MySQL
6. 启动 OpenResty 容器，提供前端服务

### 5.5 查看容器状态

```bash
# 查看运行中的容器
docker compose ps
```

预期输出：
```
NAME            IMAGE                        STATUS          PORTS
mysql           mysql:8.0                    running         3306/tcp
grid-backend    grid-trading-backend         running         9090/tcp
openresty       openresty/openresty:alpine   running         0.0.0.0:80->80/tcp
```

### 5.6 查看启动日志

如果容器状态不是 `running`，查看日志排查问题：

```bash
# 查看所有容器日志
docker compose logs

# 仅查看后端日志
docker compose logs backend

# 实时跟踪日志
docker compose logs -f backend
```

---

## 第六部分：验证与测试

### 6.1 测试前端页面

在浏览器中访问：
```
http://117.72.157.115/
```

应该能看到 Grid Trading 的首页。

### 6.2 测试后端 API

在浏览器中访问：
```
http://117.72.157.115/api/strategies
```

应该返回 JSON 格式的策略列表（初始为空数组 `[]`）。

### 6.3 服务器端测试

```bash
# 测试前端
curl http://localhost/

# 测试后端 API
curl http://localhost/api/strategies

# 测试健康检查
curl http://localhost/health
```

### 6.4 验证 MySQL 数据库

确认 MySQL 数据库正常工作：

```bash
# 进入 MySQL 容器
docker exec -it mysql mysql -u grid_user -p'GridTrading@2026'

# 在 MySQL 命令行中执行：
mysql> SHOW DATABASES;
mysql> USE grid_trading;
mysql> SHOW TABLES;
mysql> SELECT COUNT(*) FROM strategy;
mysql> EXIT;
```

预期输出：
```
+-------------------------+
| Tables_in_grid_trading |
+-------------------------+
| grid_line               |
| strategy                |
| trade_record            |
+-------------------------+
```

> **说明**：表结构由 JPA 自动创建（`ddl-auto: update`），首次启动时表是空的。

### 6.5 查看数据持久化

确认数据已持久化到服务器磁盘：

```bash
# 查看 MySQL 数据目录
ls -la /data/docker/mysql/

# 应该看到 MySQL 的数据文件
# 例如: auto.cnf, ib_buffer_pool, ibdata1, mysql/, grid_trading/ 等
```

---

## 第七部分：日常运维

### 7.1 常用命令

```bash
# 进入项目目录
cd /data/docker

# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f              # 所有服务
docker compose logs -f backend      # 仅后端
docker compose logs -f mysql        # 仅数据库
docker compose logs -f openresty    # 仅 Nginx

# 重启服务
docker compose restart              # 重启所有
docker compose restart backend      # 仅重启后端
docker compose restart mysql        # 仅重启 MySQL

# 停止服务
docker compose down

# 启动服务
docker compose up -d

# 重新构建并启动
docker compose up -d --build
```

### 7.2 MySQL 相关命令

```bash
# 进入 MySQL 容器
docker exec -it mysql bash

# 直接连接 MySQL
docker exec -it mysql mysql -u grid_user -p'GridTrading@2026'

# 使用 root 用户连接
docker exec -it mysql mysql -u root -p'Root@2026!'

# 查看 MySQL 日志
docker compose logs mysql

# 实时查看 MySQL 错误日志
docker exec mysql tail -f /var/lib/mysql/error.log

# 查看 MySQL 进程列表
docker exec mysql mysql -u root -p'Root@2026!' -e "SHOW PROCESSLIST;"

# 查看 MySQL 状态
docker exec mysql mysql -u root -p'Root@2026!' -e "SHOW STATUS;"
```

### 7.3 更新部署

当你修改了代码需要重新部署时：

**步骤 1：本地重新构建**
```powershell
# 构建后端
cd D:\01-develop\02-project\panda\grid-trading\backend
mvn clean package -DskipTests

# 构建前端
cd D:\01-develop\02-project\panda\grid-trading\frontend
npm run build
```

**步骤 2：上传新文件**
```powershell
# 上传后端
scp backend\target\*.jar root@117.72.157.115:/data/docker/platform/grid-trading/app.jar

# 上传前端
scp -r frontend\dist\* root@117.72.157.115:/data/docker/openresty/nginx/content/grid-trading/
```

**步骤 3：服务器重启容器**
```bash
cd /data/docker
docker compose up -d --build
```

### 7.4 数据备份

```bash
# 备份 MySQL 数据库
docker exec mysql mysqldump -u root -p'Root@2026!' grid_trading > backup_$(date +%Y%m%d_%H%M%S).sql

# 查看备份文件
ls -la backup_*.sql
```

### 7.5 数据恢复

```bash
# 恢复数据库
docker exec -i mysql mysql -u root -p'Root@2026!' grid_trading < backup_20240101_120000.sql
```

### 7.6 数据库账号密码汇总

| 账号类型 | 用户名 | 密码 | 用途 |
|---------|--------|------|------|
| MySQL Root | root | Root@2026! | 数据库管理员 |
| MySQL 应用 | grid_user | GridTrading@2026 | 后端应用连接 |

> **安全建议**：生产环境请修改默认密码！

---

## 附录：常见问题

### Q1: SSH 连接超时

**原因**: 安全组未开放 22 端口或服务器防火墙阻止

**解决**:
1. 检查云服务商安全组是否开放 22 端口
2. 检查服务器防火墙：
```bash
ufw status
ufw allow 22/tcp
```

### Q2: Docker 命令需要 sudo

**原因**: 当前用户不在 docker 组

**解决**:
```bash
# 将当前用户加入 docker 组
usermod -aG docker $USER

# 重新登录生效
```

### Q3: 容器启动失败

**排查步骤**:
```bash
# 查看容器日志
docker compose logs

# 查看容器详情
docker compose ps -a

# 查看具体容器日志
docker logs grid-backend
docker logs mysql
```

### Q4: MySQL 连接失败

**症状**: 后端日志显示 `Communications link failure`

**解决**:
```bash
# 检查 MySQL 是否健康
docker compose ps mysql

# 查看 MySQL 日志
docker compose logs mysql

# 重启 MySQL 并等待
docker compose restart mysql
sleep 30
docker compose restart backend
```

### Q5: 前端页面 404 或空白

**排查**:
```bash
# 检查前端文件是否存在
ls -la /data/docker/openresty/nginx/content/grid-trading/

# 检查 Nginx 配置
docker exec openresty cat /etc/nginx/conf.d/default.conf

# 重载 Nginx 配置
docker exec openresty nginx -s reload
```

### Q6: 端口被占用

**症状**: `Error: port is already allocated`

**解决**:
```bash
# 查看端口占用
netstat -tlnp | grep :80

# 停止占用端口的服务
systemctl stop nginx
systemctl stop apache2
```

### Q7: 内存不足

**症状**: 容器频繁重启

**解决**:
```bash
# 查看内存使用
free -h

# 查看 Docker 资源使用
docker stats

# 创建 swap 文件（如果内存不足）
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
```

### Q8: MySQL 数据丢失

**症状**: 重启容器后数据消失

**原因**: 数据未正确持久化

**排查**:
```bash
# 检查数据目录是否有内容
ls -la /data/docker/mysql/

# 如果目录为空，检查 docker-compose.yml 中的 volumes 配置
```

### Q9: 如何修改数据库密码

**步骤**:
```bash
# 1. 进入 MySQL 容器修改密码
docker exec -it mysql mysql -u root -p'Root@2026!'
mysql> ALTER USER 'grid_user'@'%' IDENTIFIED BY '新密码';
mysql> FLUSH PRIVILEGES;
mysql> EXIT;

# 2. 修改 docker-compose.yml 中的密码
# 3. 修改 application-prod.yml 中的密码（或环境变量）
# 4. 重启容器
docker compose down
docker compose up -d
```

---

## 附录：配置文件参考

### application-prod.yml（生产环境配置）

```yaml
server:
  port: 9090

spring:
  datasource:
    # 注意：mysql 是 Docker 容器名称，Docker 内部 DNS 会自动解析
    url: jdbc:mysql://mysql:3306/grid_trading?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: grid_user
    password: ${MYSQL_PASSWORD:GridTrading@2026}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update    # 自动更新表结构，不会删除数据
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect

logging:
  level:
    com.gridtrading: INFO

baidu:
  ocr:
    app-id: ${BAIDU_OCR_APP_ID:your-app-id}
    api-key: ${BAIDU_OCR_API_KEY:your-api-key}
    secret-key: ${BAIDU_OCR_SECRET_KEY:your-secret-key}
```

> **关键点**：`url` 中的 `mysql` 是 Docker Compose 中定义的服务名，Docker 内部 DNS 会自动将其解析为对应容器的 IP 地址。

### application.yml（本地开发配置）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:grid_trading    # H2 内存数据库
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop    # 重启时重建表（仅开发用）
```

> **注意**：本地开发使用 H2 内存数据库，无需安装 MySQL，方便快速开发测试。

### nginx/conf.d/default.conf

```nginx
# Grid Trading 前端配置

server {
    listen 80;
    server_name _;

    # 前端静态文件
    root /usr/share/nginx/html;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1000;
    gzip_proxied any;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    # 前端路由支持（Vue Router History 模式）
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://grid-backend:9090/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_connect_timeout 30s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 健康检查端点
    location /health {
        access_log off;
        return 200 "OK";
        add_header Content-Type text/plain;
    }
}
```

---

## 总结

恭喜你完成了整个部署过程！你现在掌握了：

1. ✅ 如何在服务器上安装 Docker
2. ✅ 如何构建 Java 后端和 Vue 前端
3. ✅ 如何使用 SCP 上传文件
4. ✅ 如何使用 Docker Compose 编排多个容器
5. ✅ 如何配置 Nginx 反向代理
6. ✅ 如何部署 MySQL 数据库并实现数据持久化
7. ✅ 本地 H2 数据库与生产 MySQL 数据库的区别
8. ✅ 基本的运维命令和数据库管理
9. ✅ 合理的服务器目录结构设计，方便后续扩展

**访问地址**: http://117.72.157.115/

**目录结构回顾**:
```
/data/docker/
├── platform/grid-trading/              # 后端项目
├── mysql/                              # MySQL 数据
├── openresty/nginx/
│   ├── conf.d/default.conf             # Nginx 配置
│   └── content/grid-trading/           # 前端项目
└── docker-compose.yml
```
