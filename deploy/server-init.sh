#!/bin/bash
# deploy/server-init.sh
# Ubuntu 22.04 服务器初始化脚本
#
# 使用方法（在服务器上执行）：
#   方法1：直接运行
#     bash server-init.sh
#
#   方法2：从本地上传后运行
#     scp deploy/server-init.sh root@你的服务器IP:/tmp/
#     ssh root@你的服务器IP "bash /tmp/server-init.sh"

set -e

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  Grid Trading 服务器初始化脚本             ║"
echo "║  适用系统: Ubuntu 22.04                    ║"
echo "╚════════════════════════════════════════════╝"
echo ""

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then
    echo "❌ 请使用 root 用户运行此脚本"
    echo "   执行: sudo bash server-init.sh"
    exit 1
fi

# 检查系统版本
echo "[1/6] 检查系统版本..."
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "  系统: $NAME $VERSION"
else
    echo "  ⚠️ 无法检测系统版本，继续执行..."
fi
echo ""

# 更新系统
echo "[2/6] 更新系统软件包..."
apt update
apt upgrade -y
echo "✓ 系统更新完成"
echo ""

# 安装 Docker
echo "[3/6] 安装 Docker..."
if command -v docker &> /dev/null; then
    echo "  Docker 已安装: $(docker --version)"
else
    apt install -y docker.io docker-compose-plugin
    systemctl start docker
    systemctl enable docker
    echo "✓ Docker 安装完成: $(docker --version)"
fi
echo "  Docker Compose: $(docker compose version)"
echo ""

# 安装 OpenResty
echo "[4/6] 安装 OpenResty..."
if command -v openresty &> /dev/null; then
    echo "  OpenResty 已安装"
else
    # 安装依赖
    apt install -y --no-install-recommends wget gnupg ca-certificates lsb-release

    # 添加 OpenResty 官方源
    wget -O - https://openresty.org/package/pubkey.gpg | gpg --dearmor -o /usr/share/keyrings/openresty.gpg

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/openresty.gpg] http://openresty.org/package/ubuntu $(lsb_release -sc) main" | tee /etc/apt/sources.list.d/openresty.list > /dev/null

    apt update
    apt install -y openresty

    systemctl start openresty
    systemctl enable openresty
    echo "✓ OpenResty 安装完成"
fi
openresty -v 2>&1 || true
echo ""

# 创建目录
echo "[5/6] 创建部署目录..."
mkdir -p /data/docker/grid-trading/{backend,frontend,mysql-data}
mkdir -p /usr/local/openresty/nginx/conf/conf.d
chmod -R 755 /data/docker/grid-trading
echo "✓ 目录结构:"
echo "  /data/docker/grid-trading/"
echo "  ├── backend/     # 后端 JAR + Dockerfile"
echo "  ├── frontend/    # 前端静态文件"
echo "  └── mysql-data/  # MySQL 数据持久化"
echo ""

# 配置 OpenResty
echo "[6/6] 配置 OpenResty..."
NGINX_CONF="/usr/local/openresty/nginx/conf/nginx.conf"
if grep -q "include /usr/local/openresty/nginx/conf/conf.d/\*.conf;" "$NGINX_CONF" 2>/dev/null; then
    echo "  nginx.conf 已配置 include"
else
    # 在 http 块中添加 include（在 http { 后面添加一行）
    sed -i '/http {/a\    include /usr/local/openresty/nginx/conf/conf.d/*.conf;' "$NGINX_CONF"
    echo "✓ nginx.conf 已更新，添加了 include 配置"
fi

# 验证 OpenResty 配置
openresty -t
echo ""

# 配置防火墙
echo "配置防火墙..."
if command -v ufw &> /dev/null; then
    ufw allow 22/tcp 2>/dev/null || true
    ufw allow 80/tcp 2>/dev/null || true
    ufw allow 8080/tcp 2>/dev/null || true
    echo "✓ UFW 防火墙已配置（如果已启用）"
else
    echo "  未检测到 UFW，跳过防火墙配置"
fi
echo ""

echo "╔════════════════════════════════════════════╗"
echo "║           初始化完成！                     ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "已安装组件:"
echo "  - Docker: $(docker --version 2>/dev/null | cut -d' ' -f3 || echo '未安装')"
echo "  - Docker Compose: $(docker compose version 2>/dev/null | cut -d' ' -f4 || echo '未安装')"
echo "  - OpenResty: $(openresty -v 2>&1 | cut -d'/' -f2 || echo '未安装')"
echo ""
echo "⚠️  重要提醒:"
echo "  1. 请确保京东云安全组已开放 80 和 8080 端口！"
echo "  2. 回到你的电脑，执行部署脚本: .\\deploy\\deploy.ps1"
echo ""



