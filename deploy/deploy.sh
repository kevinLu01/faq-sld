#!/bin/bash
set -e

# SLD FAQ 生产部署脚本 — 新加坡服务器
# 用法: bash deploy/deploy.sh

REPO_DIR="/opt/sld-faq"

echo "=== 1. 拉取最新代码 ==="
cd "$REPO_DIR"
git pull origin master

echo "=== 2. 准备 .env ==="
if [ ! -f deploy/.env ]; then
    cp deploy/.env.prod.example deploy/.env
    echo "已创建 deploy/.env，请填入实际配置后重新运行"
    exit 1
fi

echo "=== 3. 构建并启动所有服务 ==="
docker compose -f deploy/docker-compose.prod.yml up -d --build

echo "=== 4. 等待服务就绪 ==="
sleep 5
docker compose -f deploy/docker-compose.prod.yml ps

echo "=== 5. 验证 ==="
for i in {1..10}; do
    if curl -sf http://127.0.0.1:18081/api/auth/config > /dev/null 2>&1; then
        echo "后端 API 就绪"
        break
    fi
    echo "等待后端启动... ($i/10)"
    sleep 3
done

for i in {1..5}; do
    if curl -sf http://127.0.0.1:3080 > /dev/null 2>&1; then
        echo "前端就绪"
        break
    fi
    echo "等待前端启动... ($i/5)"
    sleep 2
done

echo ""
echo "=== 部署完成 ==="
echo "站点: https://apid.sldbd.com"
echo "查看日志: docker compose -f deploy/docker-compose.prod.yml logs -f"
