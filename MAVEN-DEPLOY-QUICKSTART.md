# 🚀 Maven 自动部署 - 快速启动

> **只需 2 步，像你公司一样简单部署**（不需要 SSH 免密登录）

---

## ⚡ 快速配置（3 分钟）

### 1️⃣ 修改服务器配置

打开 `backend/pom.xml`，找到这几行：

```xml
<properties>
    <java.version>17</java.version>
    <!-- 部署服务器配置 - 修改这里 -->
    <server.ip>your-server-ip</server.ip>
    <server.username>root</server.username>
    <server.password>your-password</server.password>
    <deploy.path>/data/docker/grid-trading</deploy.path>
</properties>
```

**修改为你的服务器信息**，比如：

```xml
<properties>
    <java.version>17</java.version>
    <!-- 部署服务器配置 - 修改这里 -->
    <server.ip>116.198.245.123</server.ip>
    <server.username>root</server.username>
    <server.password>your_root_password</server.password>
    <deploy.path>/data/docker/grid-trading</deploy.path>
</properties>
```

保存文件。

---

### 2️⃣ 上传部署脚本（首次）

**PowerShell 执行**：

```powershell
# 进入项目目录
cd D:\01-develop\02-project\panda\grid-trading

# 上传脚本（会要求输入密码）
scp backend/deploy.sh root@你的服务器IP:/data/docker/grid-trading/

# 设置权限
ssh root@你的服务器IP "chmod +x /data/docker/grid-trading/deploy.sh"
```

> 💡 **提示**：这一步只需要做一次，以后就不用了。

---

## ✅ 开始使用

### 改了后端代码，一键部署

```powershell
cd backend
mvn clean install -DskipTests
```

**就这么简单！**

输出示例：

```
[INFO] Building jar: backend-1.0.0.jar
[INFO] Installing backend-1.0.0.jar to local repo
[INFO] --- wagon-maven-plugin:2.0.2:upload-single ---
Uploading: backend-1.0.0.jar to scp://root:***@116.198.245.123/data/docker/grid-trading
Progress: [========================================] 100%
[INFO] --- wagon-maven-plugin:2.0.2:sshexec ---
==========================================
  Grid Trading 自动部署
==========================================
[1/4] 停止旧服务...
[2/4] 检查上传的文件...
-rw-r--r-- 1 root root 45M Mar  2 15:30 backend-1.0.0.jar
[3/4] 构建新镜像...
[4/4] 启动服务...
✅ 部署完成！
==========================================
[INFO] BUILD SUCCESS
```

---

## 🎯 IntelliJ IDEA 快捷方式（可选）

配置一键部署按钮：

1. 点击右上角 `Add Configuration...`
2. 点击 `+` → 选择 `Maven`
3. 填写：
   - **Name**: `🚀 Deploy Backend`
   - **Command line**: `clean install -DskipTests`
   - **Working directory**: `$ProjectFileDir$/backend`
4. 点击 `OK`

**使用**：点击右上角 ▶️ 按钮即可部署

---

## 📝 常用命令

```powershell
# 完整部署（编译 + 上传 + 重启）
mvn clean install -DskipTests

# 只编译打包（不上传）
mvn clean package -DskipTests

# 强制重新部署（不重新编译）
mvn wagon:upload-single wagon:sshexec
```

---

## ❓ 常见问题

### 问题：连接服务器失败

**错误**：
```
[ERROR] Failed to connect to server
```

**解决**：
1. 检查服务器 IP 是否正确
2. 检查服务器密码是否正确
3. 检查服务器是否开放 22 端口：`telnet 服务器IP 22`

---

### 问题：密码包含特殊字符

如果密码包含特殊字符（如 `&`, `<`, `>`），需要用 XML 转义：

| 字符 | 转义后 |
|------|--------|
| `&` | `&amp;` |
| `<` | `&lt;` |
| `>` | `&gt;` |
| `"` | `&quot;` |
| `'` | `&apos;` |

**例如**：密码是 `abc&123`，应该写成：
```xml
<server.password>abc&amp;123</server.password>
```

---

### 问题：deploy.sh 权限不足

**错误**：
```
sh: deploy.sh: Permission denied
```

**解决**：
```powershell
ssh root@你的服务器IP "chmod +x /data/docker/grid-trading/deploy.sh"
```

---

### 问题：想看服务器日志

```powershell
# SSH 到服务器
ssh root@你的服务器IP

# 查看实时日志
cd /data/docker/grid-trading
docker compose logs -f backend

# 按 Ctrl+C 退出
```

---

## 🎉 完成！

现在你有了和公司一样简单的部署方式：

```powershell
cd backend
mvn clean install -DskipTests
```

**就这么简单！不需要配置 SSH 免密登录！** 🚀

> 💡 **和你公司的方案完全一样**：直接在 pom.xml 中配置服务器 IP、用户名、密码即可

