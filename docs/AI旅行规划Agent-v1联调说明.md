# AI 旅行规划 Agent v1 联调说明

## 1. 当前已经完成的内容

本次已完成真实 SSE 链路，不再使用前端模拟生成。

链路如下：

```text
Next.js 前端
-> Spring Boot /api/ai/plan-days/{dayId}/generate-stream
-> Spring Boot 校验登录、空间、额度、Day 归属
-> Spring Boot 创建 ai_agent_run
-> Python FastAPI /internal/ai/plan-day/generate-stream
-> Python WeatherTool 查询天气
-> Python LangChain PromptTemplate + DashScope Qwen 生成计划
-> Spring Boot 保存 ai_plan_day_draft
-> 前端展示 draft
-> 用户应用 draft
-> Spring Boot 写入 travel_plan_day
```

## 2. 需要先执行数据库脚本

在 MySQL 中执行：

```text
D:\develop\codex-project\project-001-情侣旅行地图\data\sql\005_ai_travel_plan_agent.sql
```

这个脚本会新增：

```text
app_user.member_level
ai_agent_run
ai_agent_event
ai_plan_day_draft
```

注意：这个脚本只需要执行一次。如果重复执行，`app_user.member_level` 可能因为字段已存在而报错。

## 3. 需要配置的环境变量

Python AI 服务需要：

```text
ALIYUN_DASHSCOPE_API_KEY=你的百炼 API Key
QWEN_MODEL_NAME=qwen-plus
```

Java 后端需要确认：

```text
AI_SERVICE_BASE_URL=http://localhost:8000
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的本地 MySQL 密码
```

## 4. 启动顺序

建议按顺序启动：

```text
1. MySQL
2. Redis
3. Python AI 服务
4. Java Spring Boot 后端
5. Next.js 前端
```

Python AI 服务：

```cmd
cd D:\develop\codex-project\project-001-情侣旅行地图\apps\ai-service
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Java 后端：

```cmd
cd D:\develop\codex-project\project-001-情侣旅行地图\apps\server
mvn spring-boot:run
```

前端：

```cmd
cd D:\develop\codex-project\project-001-情侣旅行地图\apps\front
npm run dev
```

## 5. 页面测试方式

1. 登录账号。
2. 进入计划页。
3. 新增或选择某一天。
4. 点击“AI 规划”。
5. 添加想去地点，例如：

```text
青岛小麦岛
青岛八大关
青岛五四广场
```

6. 勾选必去地点。
7. 设置上午、下午、晚上是出去玩还是酒店休息。
8. 点击“开始生成”。
9. 页面应该流式显示：

```text
正在分析当天安排
正在查询天气
正在分析地点组合
正在安排上午、下午和晚上
```

10. 生成完成后点击“应用到这一天”。
11. 当前 Day 的标题和安排会更新。

## 6. 额度规则

普通用户：

```text
滚动 30 天 3 次
```

VIP 用户：

```text
滚动 30 天 10 次
```

目前用户默认都是：

```text
FREE
```

如果要手动把某个用户改成 VIP，可以在 MySQL 中执行：

```sql
UPDATE app_user SET member_level = 'VIP' WHERE id = 用户ID;
```

## 7. 常见问题

### 7.1 AI 提示 API Key 未配置

说明 Python AI 服务没有读取到：

```text
ALIYUN_DASHSCOPE_API_KEY
```

需要在启动 Python 服务前配置环境变量。

### 7.2 近 30 天 AI 规划次数已用完

说明 `ai_agent_run` 中已经有达到额度的记录。

测试时可以临时把用户改为 VIP，或清理测试账号的 AI 运行记录。

### 7.3 Java 调不到 Python

检查：

```text
AI_SERVICE_BASE_URL=http://localhost:8000
```

并访问：

```text
http://127.0.0.1:8000/internal/health
```

应该返回：

```json
{"status":"ok","service":"love-travel-ai-service"}
```

### 7.4 天气查询失败

v1 使用 Open-Meteo 免费天气服务。如果网络不可用或城市识别失败，AI 会继续生成计划，但会提示天气不可用或需要出行前确认。
