#!/bin/bash
# OpenResty 部署脚本
# 位置: /data/docker/openresty/nginx/deploy.sh

set -e

echo "=========================================="
echo "  OpenResty 部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

cd /data/docker/platform

echo "[1/3] 重载配置..."
docker compose exec openresty nginx -s reload 2>/dev/null || echo "  (容器未运行，将启动)"

echo "[2/3] 启动 OpenResty..."
docker compose up -d openresty

echo "[3/3] 检查状态..."
docker compose ps openresty

echo ""
echo "=========================================="
echo "  ✅ OpenResty 部署完成！"
echo "=========================================="
echo "  访问地址: http://localhost:80"
echo "  配置目录: /data/docker/openresty/nginx/conf.d"
echo "  查看日志: docker compose -f /data/docker/platform/docker-compose.yml logs -f openresty"
echo "=========================================="
