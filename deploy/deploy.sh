#!/bin/bash
set -e

# SLD FAQ 生产部署脚本 — 新加坡服务器
#
# 用法:
#   bash deploy/deploy.sh              # 拉代码 + 重建全部服务
#   bash deploy/deploy.sh backend      # 拉代码 + 只重建后端
#   bash deploy/deploy.sh frontend     # 拉代码 + 只重建前端
#   bash deploy/deploy.sh app          # 拉代码 + 重建前端+后端（不动DB/Redis/MinIO）
#   bash deploy/deploy.sh db           # 拉代码 + 重建基础设施（postgres+redis+minio）
#   bash deploy/deploy.sh restart      # 不拉代码，只重启全部容器
#   bash deploy/deploy.sh logs         # 查看所有服务日志
#   bash deploy/deploy.sh logs backend # 查看后端日志
#   bash deploy/deploy.sh ps           # 查看服务状态

REPO_DIR="/opt/sld-faq"
COMPOSE="docker compose -f deploy/docker-compose.prod.yml"
TARGET="${1:-all}"

cd "$REPO_DIR"

# ─── 快捷命令 ────────────────────────────────────────────
if [ "$TARGET" = "logs" ]; then
    shift
    exec $COMPOSE logs -f --tail=100 "$@"
fi

if [ "$TARGET" = "ps" ]; then
    exec $COMPOSE ps
fi

if [ "$TARGET" = "restart" ]; then
    echo "=== 重启全部容器（不拉代码、不重建） ==="
    $COMPOSE restart
    sleep 3
    $COMPOSE ps
    exit 0
fi

# ─── 正常部署流程 ────────────────────────────────────────

echo "=== 1. 拉取最新代码 ==="
git pull origin master

echo "=== 2. 检查 .env ==="
if [ ! -f deploy/.env ]; then
    cp deploy/.env.prod.example deploy/.env
    echo "已创建 deploy/.env，请填入实际配置后重新运行"
    exit 1
fi

echo "=== 3. 构建并启动 ($TARGET) ==="
case "$TARGET" in
    all)
        $COMPOSE up -d --build
        ;;
    backend)
        $COMPOSE up -d --build backend
        ;;
    frontend)
        $COMPOSE up -d --build frontend
        ;;
    app)
        $COMPOSE up -d --build backend frontend
        ;;
    db)
        $COMPOSE up -d --build postgres redis minio
        ;;
    *)
        echo "未知参数: $TARGET"
        echo "可选: all | backend | frontend | app | db | restart | logs | ps"
        exit 1
        ;;
esac

echo "=== 4. 等待服务就绪 ==="
sleep 3
$COMPOSE ps

# 验证后端
if [ "$TARGET" = "all" ] || [ "$TARGET" = "backend" ] || [ "$TARGET" = "app" ]; then
    for i in {1..10}; do
        if curl -sf http://127.0.0.1:18081/api/auth/config > /dev/null 2>&1; then
            echo "✓ 后端 API 就绪"
            break
        fi
        [ "$i" -eq 10 ] && echo "✗ 后端启动超时，请检查日志"
        sleep 3
    done
fi

# 验证前端
if [ "$TARGET" = "all" ] || [ "$TARGET" = "frontend" ] || [ "$TARGET" = "app" ]; then
    for i in {1..5}; do
        if curl -sf http://127.0.0.1:3080 > /dev/null 2>&1; then
            echo "✓ 前端就绪"
            break
        fi
        [ "$i" -eq 5 ] && echo "✗ 前端启动超时，请检查日志"
        sleep 2
    done
fi

echo ""
echo "=== 部署完成 ==="
echo "站点: https://apid.sldbd.com"
echo "日志: bash deploy/deploy.sh logs [服务名]"
