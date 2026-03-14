#!/bin/bash
set -e

# SLD FAQ 生产部署脚本 — 新加坡服务器
# 用法: bash deploy/deploy.sh

REPO_DIR="/opt/sld-faq"
SITE_DIR="/opt/1panel/www/sites/apid.sldbd.com/index"

echo "=== 1. 拉取最新代码 ==="
cd "$REPO_DIR"
git pull origin master

echo "=== 2. 准备 .env ==="
if [ ! -f deploy/.env ]; then
    cp deploy/.env.prod deploy/.env
    echo "已创建 deploy/.env，请检查配置后重新运行"
    exit 1
fi

echo "=== 3. 构建前端 ==="
cd sld-faq-frontend
# 检查 node 是否可用
if ! command -v node &> /dev/null; then
    echo "安装 Node.js..."
    curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi
npm install --registry=https://registry.npmmirror.com
npm run build

echo "=== 4. 部署前端静态文件 ==="
# 保留 WW_verify 文件，清除旧的前端文件
sudo find "$SITE_DIR" -maxdepth 1 ! -name 'WW_verify_*' ! -name '.' -exec rm -rf {} + 2>/dev/null || true
sudo cp -r dist/* "$SITE_DIR/"
echo "前端文件已部署到 $SITE_DIR"

echo "=== 5. 构建并启动后端服务 ==="
cd "$REPO_DIR"
docker compose -f deploy/docker-compose.prod.yml up -d --build

echo "=== 6. 等待服务就绪 ==="
sleep 5
docker compose -f deploy/docker-compose.prod.yml ps

echo "=== 7. 验证 ==="
# 检查后端健康
for i in {1..10}; do
    if curl -sf http://127.0.0.1:18081/api/auth/config > /dev/null 2>&1; then
        echo "后端 API 就绪"
        break
    fi
    echo "等待后端启动... ($i/10)"
    sleep 3
done

echo ""
echo "=== 部署完成 ==="
echo "站点: https://apid.sldbd.com"
echo "后端: http://127.0.0.1:18081"
echo ""
echo "查看日志: docker compose -f deploy/docker-compose.prod.yml logs -f backend"
