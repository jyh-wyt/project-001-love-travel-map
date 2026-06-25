# project-001-love-travel-map

## 项目定位

情侣旅行地图是一个私密旅行记录与旅行计划网页。核心体验是：两个人在同一个旅行空间里，通过中国地图查看去过的省份和城市，在城市中记录照片与日记，并共同维护旅行计划。

当前项目已经从早期网页原型升级为带登录、空间隔离、MySQL、Redis、OSS、Java 后端、Python AI 服务的全栈项目。

## 当前核心功能

1. 账号注册与登录。
2. Redis 登录 Session 白名单、登录失败次数限制。
3. 个人空间与情侣空间。
4. 邀请码加入情侣空间，邀请码由 Redis 控制短期有效期。
5. 首页中国地图，支持省份下钻和城市标记。
6. 去过的城市显示统一的绿色标记和旅行时间。
7. 城市旅行记录流，支持日记和图片一起发布。
8. 图片上传走阿里云 OSS。
9. 城市记录图片支持 3D 图片球展示和图片预览。
10. 计划页面按 Day 列表维护每日安排。
11. AI 单日旅行规划 Agent：前端通过 Java 后端调用 Python FastAPI，Python 调用通义千问。
12. AI 规划支持 SSE 流式输出、天气工具、Redis 防重复点击和短窗口限流。
13. 设置页支持用户名、密码、主题和退出登录等基础能力。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Next.js / React |
| 后端 | Spring Boot / MyBatis-Plus |
| 数据库 | MySQL |
| 缓存与短期状态 | Redis |
| 图片存储 | 阿里云 OSS |
| AI 服务 | Python / FastAPI / LangChain |
| 大模型 | 阿里云百炼 / 通义千问 |
| 地图 | Apache ECharts |

## 本地运行方式

用户习惯使用 IDE 分别运行三端：

1. 前端：VS Code 打开 `apps/front`。
2. Java 后端：IntelliJ IDEA 打开 `apps/server`。
3. Python AI 服务：PyCharm 打开 `apps/ai-service`。
4. 同时需要启动 MySQL、Redis。

常用端口：

| 服务 | 地址 |
| --- | --- |
| 前端 | `http://127.0.0.1:3000` |
| Java 后端 | `http://127.0.0.1:8080` |
| Python AI 服务 | `http://127.0.0.1:8000` |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |

## 重要环境变量

Java 后端：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
LOVE_TRAVEL_SESSION_SECRET
ALIYUN_OSS_ENDPOINT
ALIYUN_OSS_BUCKET
ALIYUN_ACCESS_KEY_ID
ALIYUN_ACCESS_KEY_SECRET
ALIYUN_OSS_PUBLIC_BASE_URL
AI_SERVICE_BASE_URL
```

Python AI 服务：

```text
ALIYUN_DASHSCOPE_API_KEY
QWEN_MODEL_NAME
```

## V1 状态

V1 已完成本地开发闭环，当前验证结果见：

```text
docs/V1完成检查报告.md
```

后续扩展不再算 V1 阻塞项，建议围绕 AI 能力、企业工程化、部署上线和面试展示继续增强。

