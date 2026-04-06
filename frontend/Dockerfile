FROM nginx:alpine

# 复制本地编译好的 dist 文件
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
