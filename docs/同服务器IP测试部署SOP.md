# 同服务器 IP 测试部署 SOP

更新时间：2026-06-25

## 1. 部署目标

本方案用于第一次练习完整上线流程，不依赖域名和 HTTPS。

浏览器访问：

```text
http://服务器公网IP
```

服务器内部结构：

```text
Nginx 80
  -> /       转发到 Next.js 前端 127.0.0.1:3000
  -> /api/   转发到 Java 后端 127.0.0.1:8080

Java 后端 127.0.0.1:8080
  -> MySQL 127.0.0.1:3306
  -> Redis 127.0.0.1:6379
  -> Python AI 服务 127.0.0.1:8000
  -> OSS / DashScope

Python AI 服务 127.0.0.1:8000
```

公网只开放：

```text
22   SSH
80   HTTP
```

不要开放：

```text
3306 MySQL
6379 Redis
8080 Java
8000 Python
3000 Next.js
```

## 2. 适用前提

推荐服务器系统：

```text
Ubuntu 22.04 LTS
```

如果使用 Alibaba Cloud Linux、CentOS 或 Windows Server，命令会不同。

## 3. 安装基础环境

登录服务器后执行：

```bash
sudo apt update
sudo apt install -y git nginx mysql-server redis-server openjdk-17-jdk maven python3 python3-venv python3-pip curl
```

安装 Node.js 22：

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
node -v
npm -v
```

## 4. 创建部署目录

```bash
sudo mkdir -p /opt/love-travel/{app,env,logs,backup}
sudo chown -R $USER:$USER /opt/love-travel
cd /opt/love-travel/app
```

把 GitHub 仓库拉到服务器：

```bash
git clone 你的GitHub仓库地址 project-001-love-travel-map
cd project-001-love-travel-map
```

## 5. 初始化 MySQL

进入 MySQL：

```bash
sudo mysql
```

创建数据库和专用账号：

```sql
CREATE DATABASE love_travel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'love_travel'@'localhost' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON love_travel.* TO 'love_travel'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

依次导入 SQL：

```bash
mysql -u love_travel -p love_travel < data/sql/001_init_schema.sql
mysql -u love_travel -p love_travel < data/sql/002_add_trip_city_code.sql
mysql -u love_travel -p love_travel < data/sql/003_update_travel_plan_day_for_frontend.sql
mysql -u love_travel -p love_travel < data/sql/004_multi_space_current_space.sql
mysql -u love_travel -p love_travel < data/sql/005_ai_travel_plan_agent.sql
```

## 6. 配置 Redis

第一次测试可以先只监听本机。编辑：

```bash
sudo nano /etc/redis/redis.conf
```

确认：

```text
bind 127.0.0.1 ::1
protected-mode yes
```

建议设置密码：

```text
requirepass 换成Redis强密码
```

重启 Redis：

```bash
sudo systemctl restart redis-server
sudo systemctl enable redis-server
```

## 7. 配置环境变量

创建 Java 后端环境变量：

```bash
nano /opt/love-travel/env/server.env
```

内容模板：

```bash
export SERVER_PORT=8080
export MYSQL_URL='jdbc:mysql://localhost:3306/love_travel?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME='love_travel'
export MYSQL_PASSWORD='换成MySQL强密码'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export REDIS_PASSWORD='换成Redis强密码'
export LOVE_TRAVEL_SESSION_SECRET='换成一段至少32位的随机字符串'
export FRONTEND_ALLOWED_ORIGINS='http://服务器公网IP'
export AI_SERVICE_BASE_URL='http://127.0.0.1:8000'
export TENCENT_COS_REGION='ap-guangzhou'
export TENCENT_COS_BUCKET='love-travel-images-jyh-1447356754'
export TENCENT_COS_SECRET_ID='你的腾讯云 SecretId'
export TENCENT_COS_SECRET_KEY='你的腾讯云 SecretKey'
export TENCENT_COS_PUBLIC_BASE_URL=''
export TENCENT_COS_SIGNED_URL_EXPIRE_MINUTES='10'
```

创建 Python AI 服务环境变量：

```bash
nano /opt/love-travel/env/ai.env
```

内容模板：

```bash
export ALIYUN_DASHSCOPE_API_KEY='你的DashScope API Key'
export QWEN_MODEL_NAME='qwen-plus'
```

注意：`/opt/love-travel/env/*.env` 只放服务器本地，不提交 GitHub。

## 8. 构建并启动 Python AI 服务

```bash
cd /opt/love-travel/app/project-001-love-travel-map/apps/ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
source /opt/love-travel/env/ai.env
nohup python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 > /opt/love-travel/logs/ai-service.log 2>&1 &
```

检查：

```bash
curl http://127.0.0.1:8000/internal/health
```

## 9. 构建并启动 Java 后端

```bash
cd /opt/love-travel/app/project-001-love-travel-map/apps/server
mvn clean package -DskipTests
source /opt/love-travel/env/server.env
nohup java -jar target/love-travel-server-0.1.0.jar > /opt/love-travel/logs/server.log 2>&1 &
```

检查：

```bash
curl http://127.0.0.1:8080/api/auth/me
```

如果返回未登录或 401，说明服务能响应，这是正常的。

## 10. 构建并启动 Next.js 前端

同服务器 IP 部署时，前端不要写死 `http://IP:8080`。

这里把 `NEXT_PUBLIC_API_BASE_URL` 设为空，让浏览器请求同源 `/api/...`：

```bash
cd /opt/love-travel/app/project-001-love-travel-map/apps/front
npm ci --legacy-peer-deps
NEXT_PUBLIC_API_BASE_URL= npm run build
nohup npm run start -- --hostname 127.0.0.1 --port 3000 > /opt/love-travel/logs/front.log 2>&1 &
```

检查：

```bash
curl http://127.0.0.1:3000
```

## 11. 配置 Nginx

创建配置：

```bash
sudo nano /etc/nginx/sites-available/love-travel
```

写入：

```nginx
server {
    listen 80;
    server_name _;

    client_max_body_size 60m;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用配置：

```bash
sudo ln -s /etc/nginx/sites-available/love-travel /etc/nginx/sites-enabled/love-travel
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

## 12. 浏览器验证

打开：

```text
http://服务器公网IP
```

依次测试：

```text
[ ] 首页能打开
[ ] 注册账号
[ ] 登录账号
[ ] 刷新后仍保持登录
[ ] 地图能打开
[ ] 城市日记能发布
[ ] 图片能上传 OSS
[ ] 计划能新增和编辑
[ ] AI 规划能返回内容
```

## 13. 常见问题

### 13.1 前端能打开，但登录失败

检查：

```bash
tail -n 100 /opt/love-travel/logs/server.log
tail -n 100 /var/log/nginx/error.log
```

重点看：

```text
MySQL 连接失败
Redis 连接失败
CORS allowed origins 不匹配
```

### 13.2 前端请求跑到 8080

说明前端构建时 `NEXT_PUBLIC_API_BASE_URL` 没有设为空。

重新构建：

```bash
cd /opt/love-travel/app/project-001-love-travel-map/apps/front
NEXT_PUBLIC_API_BASE_URL= npm run build
```

然后重启前端服务。

### 13.3 图片上传失败

检查 Java 环境变量：

```text
TENCENT_COS_REGION
TENCENT_COS_BUCKET
TENCENT_COS_SECRET_ID
TENCENT_COS_SECRET_KEY
```

同时确认 OSS bucket 权限和 endpoint 与实际地域一致。

### 13.4 AI 规划失败

检查：

```bash
tail -n 100 /opt/love-travel/logs/ai-service.log
tail -n 100 /opt/love-travel/logs/server.log
```

重点看：

```text
ALIYUN_DASHSCOPE_API_KEY 是否存在
Java 的 AI_SERVICE_BASE_URL 是否为 http://127.0.0.1:8000
Python 服务是否启动
```

## 14. 后续升级方向

这个 IP 测试部署跑通后，再升级为正式展示版：

```text
域名
HTTPS
systemd 守护进程
自动备份 MySQL
日志轮转
更严格的防火墙
前后端分域名或统一 HTTPS 入口
```
## 线上实战补充：2026-06-27

本节记录本项目第一次在腾讯云 Ubuntu 22.04 服务器完成同机部署后的最终确认规则。

### 1. 发布流程必须以 Git 为准

企业项目不要靠手工上传零散文件修线上问题。标准流程应为：

```text
本地修改代码 -> 本地构建验证 -> GitHub Desktop 提交并 Push -> 服务器 git pull -> 服务器构建 -> systemd 重启服务 -> 线上验证
```

服务器拉取 GitHub 如果出现 HTTP2 framing 或 timeout，可先执行：

```bash
git config --global http.version HTTP/1.1
git config --global http.postBuffer 524288000
git pull
```

这只是修复 Git 网络传输兼容问题，不改变项目发布原则。

### 2. 线上服务统一交给 systemd 托管

临时测试可以使用 `nohup`，正式部署应使用 systemd。当前项目使用：

```text
love-travel-front.service  -> Next.js 前端，127.0.0.1:3000
love-travel-server.service -> Spring Boot 后端，127.0.0.1:8080
love-travel-ai.service     -> FastAPI AI 服务，127.0.0.1:8000
```

常用命令：

```bash
systemctl status love-travel-front --no-pager
systemctl status love-travel-server --no-pager
systemctl status love-travel-ai --no-pager

systemctl restart love-travel-front
systemctl restart love-travel-server
systemctl restart love-travel-ai

journalctl -u love-travel-front -n 100 --no-pager
journalctl -u love-travel-server -n 100 --no-pager
journalctl -u love-travel-ai -n 100 --no-pager
```

注意：`systemctl status` 默认会进入分页器，按 `q` 退出；也可以统一加 `--no-pager`。

### 3. systemd 环境变量文件不要带 export

开发时的 shell 环境变量文件可以写：

```bash
export MYSQL_USERNAME='love_travel'
```

systemd 的 `EnvironmentFile` 更适合使用：

```text
MYSQL_USERNAME='love_travel'
```

因此本项目使用：

```bash
sed -E "s/^export //" /opt/love-travel/env/server.env > /opt/love-travel/env/server.systemd.env
sed -E "s/^export //" /opt/love-travel/env/ai.env > /opt/love-travel/env/ai.systemd.env
```

修改 `/opt/love-travel/env/*.env` 后，必须重新生成对应的 `*.systemd.env`，再重启服务。

### 4. 环境变量名必须和代码一致

本次部署中踩过的坑：文档里写了 `DB_*`、`OSS_*`、`DASHSCOPE_API_KEY`，但代码实际读取的是其他名字，导致服务启动后仍然使用默认值或认为配置为空。

当前后端实际读取：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
FRONTEND_ALLOWED_ORIGINS
AI_SERVICE_BASE_URL
TENCENT_COS_REGION
TENCENT_COS_BUCKET
TENCENT_COS_SECRET_ID
TENCENT_COS_SECRET_KEY
TENCENT_COS_PUBLIC_BASE_URL
TENCENT_COS_SIGNED_URL_EXPIRE_MINUTES
```

当前 Python AI 服务实际读取：

```text
ALIYUN_DASHSCOPE_API_KEY
QWEN_MODEL_NAME
```

排查方法：

```bash
grep -R "MYSQL_USERNAME\|TENCENT_COS\|DASHSCOPE" -n apps/server/src/main apps/ai-service/app
```

### 5. GeoJSON 静态资源本地化

线上首页地图不应在运行时依赖第三方 GeoJSON 地址。第三方接口一旦超时、跨域失败或被网络拦截，首页地图就会加载失败。

当前项目已将地图资源放到：

```text
apps/front/public/geojson/bound/
```

前端访问路径：

```text
/geojson/bound/100000_full.json
/geojson/bound/{provinceCode}_full.json
```

部署后可验证：

```bash
curl -I http://127.0.0.1:3000/geojson/bound/100000_full.json
curl -I http://127.0.0.1/geojson/bound/100000_full.json
```

返回 `200 OK` 才说明静态地图文件已被正确发布。

### 6. 数据库与 Redis 安全规则

MySQL 和 Redis 只允许服务器本机或内网访问，不开放公网端口。

当前公网只开放：

```text
22 SSH
80 HTTP
```

不开放：

```text
3306 MySQL
6379 Redis
8080 Java
8000 Python
3000 Next.js
```

Redis 配置要求：

```text
bind 127.0.0.1 ::1
protected-mode yes
requirepass 强密码
```

MySQL 使用项目专用账号 `love_travel`，不要使用 root 账号跑业务。

### 7. AccessKey 与 API Key 安全规则

腾讯云 COS SecretId/SecretKey、DashScope API Key、数据库密码、Redis 密码只允许放在服务器环境变量文件中，不允许提交到 GitHub。

如果 Key 曾经出现在聊天、截图、提交记录或公开仓库中，应在云控制台禁用旧 Key，重新生成新 Key，并更新服务器环境变量。

### 8. 最终线上链路

```text
浏览器
  -> http://服务器公网IP
  -> Nginx 80
    -> /       -> Next.js 127.0.0.1:3000
    -> /api/   -> Spring Boot 127.0.0.1:8080

Spring Boot
  -> MySQL 127.0.0.1:3306
  -> Redis 127.0.0.1:6379
  -> FastAPI AI 127.0.0.1:8000
  -> Tencent COS

FastAPI AI
  -> DashScope / Qwen
```
