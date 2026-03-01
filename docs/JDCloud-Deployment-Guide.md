# 京东云服务器部署执行计划

> **服务器环境**：Ubuntu 22.04 64位  
> **本地环境**：Windows + PowerShell  
> **部署路径**：`/data/docker/grid-trading`  
> **部署方式**：一键脚本部署，在 IDE 中执行即可

## 📋 部署方案概述

| 组件 | 部署方式 | 端口 | 说明 |
|------|----------|------|------|
| 后端 (Spring Boot) | Docker 容器 | 8080 | Java 17 + Spring Boot 3.2.2 |
| 数据库 (MySQL) | Docker 容器 | 3306 | MySQL 8.0 |
| 前端 (Vue 3) | OpenResty | 80 | Nginx + Lua 扩展，性能更好 |
| 一键部署 | PowerShell 脚本 | - | IDE 内执行远程部署 |

---

## 📍 执行步骤总览

| 步骤 | 任务 | 在哪里操作 | 预计时间 |
|------|------|------------|----------|
| 1 | 本地配置 SSH 免密登录 | 你的电脑 PowerShell | 3 分钟 |
| 2 | 服务器环境初始化 | SSH 客户端（连接服务器后） | 10 分钟 |
| 3 | 修改部署配置 | 你的电脑 IDE | 2 分钟 |
| 4 | 执行一键部署 | 你的电脑 IDE 终端 | 5 分钟 |
| 5 | 浏览器验证 | 浏览器 | 1 分钟 |

---

## 步骤 1：本地配置 SSH 免密登录（你的电脑 PowerShell）

> 📍 **在哪里执行**：你的电脑，打开 PowerShell（按 `Win + X` → 选择 Windows PowerShell）
> 
> **为什么需要**：让部署脚本能自动连接服务器，不用每次输密码

### 1.1 检查是否有 SSH 密钥

```powershell
ls ~/.ssh/id_rsa
```

- **显示文件路径** → 已有密钥，跳到 1.3
- **红色错误"找不到路径"** → 需要创建，继续 1.2

### 1.2 创建 SSH 密钥（如果没有）

```powershell
ssh-keygen -t rsa -b 4096
```

**全程按 Enter 即可**（共 3 次回车），不需要输入任何内容。

### 1.3 复制公钥内容

```powershell
Get-Content ~/.ssh/id_rsa.pub
```

**复制输出的内容**（以 `ssh-rsa` 开头的一长串字符），下一步要用。

### 1.4 把公钥添加到服务器

切换到你的 **SSH 客户端**（已连接服务器的那个窗口），执行：

```bash
# 在服务器上执行（任意目录都可以）
mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "粘贴你刚才复制的公钥内容" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### 1.5 验证免密登录

回到你的电脑 **PowerShell**，测试：

```powershell
ssh root@你的服务器IP
```

- **直接进入服务器**（不需要输密码）→ ✅ 成功，输入 `exit` 退出
- **要求输入密码** → 公钥没添加成功，检查 1.4 步骤

---

## 步骤 2：服务器环境初始化（在你的 SSH 客户端操作）

> 📍 **在哪里执行**：你的 SSH 客户端窗口（已连接到服务器的那个）
> 
> **命令在哪执行**：登录服务器后，默认在 `/root` 目录，下面的命令**在任意目录都可以执行**，除非特别说明

### 2.1 更新系统

```bash
apt update && apt upgrade -y
```

等待完成，可能需要 1-2 分钟。

### 2.2 安装 Docker

```bash
# 安装 Docker 和 Docker Compose 插件
apt install -y docker.io docker-compose-plugin

# 启动 Docker 并设置开机自启
systemctl start docker
systemctl enable docker

# 验证安装是否成功
docker --version
docker compose version
```

**预期输出**：
```
Docker version 24.0.x, build xxx
Docker Compose version v2.x.x
```

看到版本号就说明安装成功了。

### 2.3 安装 OpenResty

一条一条执行（可以复制整块，会自动逐行执行）：

```bash
# 安装依赖
apt install -y --no-install-recommends wget gnupg ca-certificates lsb-release

# 添加 OpenResty 官方软件源
wget -O - https://openresty.org/package/pubkey.gpg | gpg --dearmor -o /usr/share/keyrings/openresty.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/openresty.gpg] http://openresty.org/package/ubuntu $(lsb_release -sc) main" | tee /etc/apt/sources.list.d/openresty.list > /dev/null

# 更新并安装
apt update
apt install -y openresty

# 启动并设置开机自启
systemctl start openresty
systemctl enable openresty

# 验证安装
openresty -v
```

**预期输出**：
```
nginx version: openresty/1.25.x.x
```

### 2.4 创建部署目录

> 📍 按你的习惯，服务放在 `/data/docker`

```bash
# 创建目录结构
mkdir -p /data/docker/grid-trading/{backend,frontend,mysql-data}

# 创建 OpenResty 配置目录
mkdir -p /usr/local/openresty/nginx/conf/conf.d

# 设置权限
chmod -R 755 /data/docker/grid-trading
```

### 2.5 配置 OpenResty 加载自定义配置

```bash
# 用 nano 编辑器打开配置文件
nano /usr/local/openresty/nginx/conf/nginx.conf
```

打开后，用方向键找到这一行：
```
http {
```

在 `http {` 的**下一行**，添加：
```nginx
    include /usr/local/openresty/nginx/conf/conf.d/*.conf;
```

添加后看起来像这样：
```nginx
http {
    include /usr/local/openresty/nginx/conf/conf.d/*.conf;
    ... 其他内容 ...
```

**保存退出**：
1. 按 `Ctrl + O`（保存）
2. 按 `Enter`（确认文件名）
3. 按 `Ctrl + X`（退出）

**验证配置正确**：
```bash
openresty -t
```

应该显示：
```
nginx: the configuration file /usr/local/openresty/nginx/conf/nginx.conf syntax is ok
nginx: configuration file /usr/local/openresty/nginx/conf/nginx.conf test is successful
```

### 2.6 配置防火墙（可选）

> Ubuntu 22.04 默认防火墙可能没开启，如果你不确定，执行下面的命令不会有问题

```bash
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # 网站
ufw allow 8080/tcp  # 后端 API
```

### 2.7 京东云安全组配置

> 📍 **在哪里操作**：打开浏览器，登录京东云控制台网页

1. 打开 [京东云控制台](https://console.jdcloud.com/)
2. 找到 **云主机** → 点击你的服务器
3. 点击 **安全组** → **配置规则**
4. **添加入站规则**：

| 协议 | 端口 | 源IP | 说明 |
|------|------|------|------|
| TCP | 80 | 0.0.0.0/0 | 网站访问 |
| TCP | 8080 | 0.0.0.0/0 | 后端 API |

> ⚠️ 这一步很重要！如果不配置安全组，外网访问不了！

### 2.8 服务器初始化完成

现在服务器准备好了，**不用退出 SSH**，继续看下一步。

---

## 步骤 3：修改部署配置（你的电脑 IDE）

> ⚠️ 以下操作在你的电脑上，用 IDE 打开项目进行

### 3.1 配置文件我已经帮你创建好了

在项目中已经有这些文件（我之前已创建）：

| 文件 | 作用 | 是否需要修改 |
|------|------|--------------|
| `deploy/config.ps1` | 服务器连接配置 | ✅ **需要修改** |
| `deploy/deploy.ps1` | 一键部署脚本 | ❌ 不需要 |
| `deploy/nginx/grid-trading.conf` | Nginx 配置 | ❌ 不需要 |
| `backend/Dockerfile` | 后端 Docker 镜像 | ❌ 不需要 |
| `backend/src/main/resources/application-prod.yml` | 生产环境配置 | ❌ 不需要 |
| `docker-compose.yml` | Docker 编排 | ❌ 不需要 |

### 3.2 修改服务器 IP 配置

**打开文件** `deploy/config.ps1`，找到第一行：

```powershell
$SERVER_IP = "your-server-ip"
```

**改成你的京东云服务器 IP**，比如：

```powershell
$SERVER_IP = "116.198.xxx.xxx"
```

保存文件。

### 3.3 确认 SSH 免密登录

在 IDE 的终端（Terminal）中测试：

```powershell
ssh root@你的服务器IP
```

如果：
- **直接进入服务器**（不需要输入密码）→ ✅ 配置正确，输入 `exit` 退出
- **需要输入密码** → 需要配置密钥，参考步骤 1

---

## 步骤 4：执行一键部署（你的电脑 IDE）

### 4.1 打开 IDE 终端

在 IDE（IntelliJ IDEA / VS Code）中：
- **IntelliJ IDEA**：点击底部的 `Terminal` 标签
- **VS Code**：按 `` Ctrl + ` `` 打开终端

### 4.2 进入项目目录

```powershell
cd C:\panda\02-codes\00-project\grid-trading
```

### 4.3 执行部署脚本

```powershell
.\deploy\deploy.ps1
```

### 4.4 观察输出

脚本会依次执行：

```
╔════════════════════════════════════════════╗
║       Grid Trading 一键部署脚本            ║
╚════════════════════════════════════════════╝

[1/6] 测试 SSH 连接...
✓ SSH 连接成功！

[2/6] 构建后端 JAR 包...
（这里会显示 Maven 构建过程，需要 1-2 分钟）
✓ 后端构建完成！

[3/6] 构建前端...
（这里会显示 npm 构建过程，需要 30 秒）
✓ 前端构建完成！

[4/6] 上传文件到服务器...
  ├─ 上传后端 JAR 包...
  ├─ 上传 Dockerfile...
  ├─ 上传 docker-compose.yml...
  ├─ 上传前端构建文件...
  ├─ 上传 Nginx 配置...
✓ 文件上传完成！

[5/6] 远程执行部署...
（这里会显示 Docker 构建和启动日志）
✓ 远程部署完成！

[6/6] 验证部署状态...
  ├─ 测试后端 API...
  │  ✓ 后端 API: 正常 (HTTP 200)
  ├─ 测试前端页面...
  │  ✓ 前端页面: 正常 (HTTP 200)

╔════════════════════════════════════════════╗
║           部署完成！                       ║
╚════════════════════════════════════════════╝

访问地址:
  前端首页: http://你的服务器IP/
  后端 API: http://你的服务器IP:8080/api/strategies
```

### 4.5 常见错误处理

**错误 1：SSH 连接失败**
```
✗ SSH 连接失败！
```
解决：检查服务器 IP 是否正确，检查 SSH 密钥是否配置

**错误 2：Maven 构建失败**
```
✗ 后端构建失败！
```
解决：确保本地已安装 JDK 17 和 Maven，运行 `mvn -v` 检查

**错误 3：npm 构建失败**
```
✗ 前端构建失败！
```
解决：确保本地已安装 Node.js，运行 `node -v` 检查

---

## 步骤 5：浏览器验证

### 5.1 访问前端页面

打开浏览器，输入：

```
http://你的服务器IP/
```

**预期效果**：看到 Grid Trading 的策略列表页面

### 5.2 测试功能

1. 点击 **创建策略** 按钮
2. 填写表单，点击提交
3. 如果成功跳转到策略详情页，说明前后端都正常工作

### 5.3 直接访问 API（可选）

打开浏览器，输入：

```
http://你的服务器IP:8080/api/strategies
```

**预期效果**：看到 JSON 数据（可能是空数组 `[]`）

---

## 🔧 常见问题排查

### 问题 1：页面显示 502 Bad Gateway

**原因**：后端服务没有启动成功

**排查步骤**：
```bash
# SSH 到服务器
ssh root@你的服务器IP

# 查看容器状态
cd /data/docker/grid-trading
docker compose ps

# 查看后端日志
docker compose logs backend
```

### 问题 2：页面能打开但 API 报错

**原因**：可能是 CORS 或端口问题

**排查步骤**：
```bash
# 在服务器上测试 API
curl http://localhost:8080/api/strategies

# 如果本地能访问但外网不能，检查安全组
```

### 问题 3：数据库连接失败

**排查步骤**：
```bash
# 查看 MySQL 容器日志
docker compose logs mysql

# 检查 MySQL 是否正常
docker compose exec mysql mysql -u grid_user -pGridTrading@2026 -e "SHOW DATABASES;"
```

### 问题 4：样式/JS 加载失败 (404)

**原因**：Nginx 配置没有正确加载

**排查步骤**：
```bash
# 检查 Nginx 配置
openresty -t

# 重新加载
openresty -s reload

# 查看配置文件是否存在
ls -la /usr/local/openresty/nginx/conf/conf.d/
```

---

## 🔄 日常使用：代码改了怎么部署？

以后你改了代码，只需要在 IDE 终端执行一条命令：

### 改了前端和后端代码

```powershell
.\deploy\deploy.ps1
```

### 只改了前端代码（更快）

```powershell
.\deploy\deploy.ps1 -FrontendOnly
```

### 只改了后端代码

```powershell
.\deploy\deploy.ps1 -BackendOnly
```

---

## 🛠️ 服务器运维命令

当你需要查看服务器状态时，先 SSH 到服务器：

```powershell
ssh root@你的服务器IP
```

### 查看服务状态

```bash
cd /data/docker/grid-trading
docker compose ps
```

### 查看后端日志

```bash
docker compose logs -f backend --tail=100
```

### 重启所有服务

```bash
docker compose restart
```

### 停止所有服务

```bash
docker compose down
```

### 查看 MySQL 数据

```bash
docker compose exec mysql mysql -u grid_user -pGridTrading@2026 grid_trading
```

---

## ✅ 执行检查清单

按顺序打勾，完成部署：

### 步骤 1：本地准备
- [ ] PowerShell 中执行 `ls ~/.ssh/id_rsa` 确认有 SSH 密钥
- [ ] 如果没有，执行 `ssh-keygen -t rsa -b 4096` 创建
- [ ] 执行 `Get-Content ~/.ssh/id_rsa.pub` 复制公钥

### 步骤 2：服务器初始化
- [ ] SSH 客户端已连接到服务器
- [ ] 添加公钥到 `~/.ssh/authorized_keys`
- [ ] 安装 Docker：`apt install -y docker.io docker-compose-plugin`
- [ ] 启动 Docker：`systemctl start docker && systemctl enable docker`
- [ ] 安装 OpenResty（按文档 2.3 步骤）
- [ ] 创建目录：`mkdir -p /data/docker/grid-trading/{backend,frontend,mysql-data}`
- [ ] 配置 nginx.conf 添加 include 行
- [ ] 京东云安全组开放 80、8080 端口

### 步骤 3：配置部署
- [ ] 编辑 `deploy/config.ps1`，填入服务器 IP
- [ ] 测试免密登录：`ssh root@服务器IP`（不需要输密码）

### 步骤 4：执行部署
- [ ] 执行 `.\deploy\deploy.ps1`
- [ ] 等待脚本完成，看到"部署完成"

### 步骤 5：验证
- [ ] 浏览器访问 `http://服务器IP/`
- [ ] 能看到策略列表页面
- [ ] 尝试创建一个策略

---

## 📝 后续可选优化

部署成功后，你可以考虑：

1. **绑定域名**：买个域名指向服务器 IP，比 IP 访问更方便
2. **配置 HTTPS**：使用 Let's Encrypt 免费证书，更安全
3. **设置定时备份**：定期备份 MySQL 数据

---

**文档版本**：v1.1  
**更新日期**：2026-03-02  
**服务器环境**：Ubuntu 22.04 64位









