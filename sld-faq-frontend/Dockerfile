# 使用本地已构建的 dist 目录，直接用 nginx:alpine 提供静态服务
# 前端需先在宿主机执行 npm run build 生成 dist/
FROM nginx:alpine AS runner

COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
