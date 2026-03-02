#!/bin/bash
# Grid Trading 自动部署脚本
# 运行在服务器上，由 Maven Wagon 触发

set -e  # 遇到错误立即退出

APP_NAME="grid-trading-backend"
JAR_FILE="backend-1.0.0.jar"
DEPLOY_PATH="/data/docker/grid-trading"

echo "=========================================="
echo "  Grid Trading 自动部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

cd $DEPLOY_PATH

echo "[1/4] 停止旧服务..."
docker compose down || echo "  (没有运行中的服务)"

echo "[2/4] 检查上传的文件..."
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ 错误: $JAR_FILE 文件不存在！"
    exit 1
fi
ls -lh $JAR_FILE

echo "[3/4] 构建新镜像..."
docker compose build backend

echo "[4/4] 启动服务..."
docker compose up -d

echo ""
echo "等待服务启动..."
sleep 8

echo ""
echo "检查服务状态..."
docker compose ps

echo ""
echo "后端日志 (最后 20 行):"
docker compose logs backend --tail=20

echo ""
echo "=========================================="
echo "  ✅ 部署完成！"
echo "=========================================="
echo "  后端 API: http://localhost:8080/api/strategies"
echo "  查看日志: docker compose logs -f backend"
echo "=========================================="

