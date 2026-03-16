#!/bin/bash
# MySQL 部署脚本
# 位置: /data/docker/mysql/deploy.sh

set -e

echo "=========================================="
echo "  MySQL 部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

cd /data/docker/platform

echo "[1/3] 启动 MySQL..."
docker compose up -d mysql

echo "[2/3] 等待 MySQL 就绪..."
sleep 10

echo "[3/3] 检查状态..."
docker compose ps mysql

echo ""
echo "=========================================="
echo "  ✅ MySQL 部署完成！"
echo "=========================================="
echo "  数据目录: /data/docker/mysql/data"
echo "  查看日志: docker compose -f /data/docker/platform/docker-compose.yml logs -f mysql"
echo "=========================================="
