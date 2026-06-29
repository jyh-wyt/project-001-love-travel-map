# AI 旅行记忆 RAG v1.2 技术方案

## 1. 方案定位

v1.2 的目标是在现有 AI 单日旅行规划 Agent 基础上，加入 Milvus 向量检索能力，让 AI 在生成旅行计划前参考当前旅行空间的历史日记和历史计划。

这一版不是重新做一个 AI 功能，而是把现有链路升级为“带旅行记忆的 RAG Agent”：

```text
前端 -> Java 权限/额度/空间校验 -> Python AI 服务 -> Milvus 检索历史记忆 -> LangChain 组装 Prompt -> Qwen 生成计划
```

核心原则：

1. 前端仍然只调用 Java 后端。
2. Java 仍然负责登录、空间权限、额度、限流和数据归属校验。
3. Python 负责 AI 编排、Embedding、Milvus 检索和 LangChain Prompt 组装。
4. Milvus 中的向量必须按 `spaceId` 隔离，不能跨用户、跨空间检索。
5. AI 生成的内容仍然只是草稿，用户确认后才写入正式计划。

## 2. 为什么选择 Milvus Standalone + Docker Compose

本项目 v1.2 选择 Milvus Standalone + Docker Compose，而不是 Milvus Lite。

原因：

1. 更接近企业 RAG 项目的真实部署形态。
2. 可以学习向量数据库独立服务、端口、安全组、服务编排和数据持久化。
3. 后续可平滑扩展到独立服务器或云上托管向量数据库。
4. 面试时可以讲清楚“为什么不用 MySQL LIKE 做语义检索”，以及“为什么需要向量数据库”。

注意：

1. Milvus 部署命令以后以官方 Docker Compose 文档为准。
2. 本项目文档只定义项目使用方式和接入边界，不把第三方部署脚本写死。
3. 腾讯云安全组不开放 Milvus 端口给公网，只允许服务器内网或本机服务访问。

## 3. v1.2 功能范围

### 3.1 本版要做

1. 增加旅行记忆索引表，用于记录哪些日记和计划已经写入 Milvus。
2. 增加 Python Embedding 服务模块，用阿里云百炼 Embedding 模型生成向量。
3. 增加 Python Milvus 客户端模块，用于创建 collection、upsert、delete、search。
4. 增加记忆索引接口：Java 把当前空间的历史日记和计划同步给 Python，Python 写入 Milvus。
5. AI 生成计划前，Python 根据当前城市、用户输入地点、备注和 Day 信息检索 Milvus。
6. LangChain Prompt 中加入“历史旅行偏好记忆”。
7. Agent 事件表记录 RAG 检索过程，例如 `RAG_SEARCH`、`RAG_RESULT`。

### 3.2 本版不做

1. 不做图片内容识别。
2. 不做公开社区内容检索。
3. 不做跨空间推荐。
4. 不做实时自动索引所有历史数据的复杂消息队列。
5. 不做 LangGraph 正式编排，先保留后续升级空间。
6. 不让前端直接调用 Python 或 Milvus。

## 4. 数据来源

v1.2 只使用两类历史内容：

### 4.1 历史旅行日记

来源表：

```text
trip_post
```

向量内容建议拼接：

```text
城市：青岛
旅行日期：2026年6.19-6.21
日记内容：今天去了小麦岛，适合慢慢散步拍照，晚上回酒店休息。
```

用途：

1. 判断用户喜欢的旅行节奏。
2. 判断用户常写的偏好，例如拍照、海边、轻松、不赶路。
3. 判断用户曾经去过的城市和场景。

### 4.2 历史旅行计划

来源表：

```text
travel_plan_day
```

向量内容建议拼接：

```text
计划标题：青岛海边轻松拍照一日
计划日期：2026-06-20
计划内容：上午八大关，下午小麦岛，晚上回酒店休息。
```

用途：

1. 判断用户过去如何安排上午、下午、晚上。
2. 判断用户是否偏好轻松路线。
3. 避免 AI 每次都生成过满、过累的计划。

## 5. Milvus Collection 设计

collection 名称：

```text
love_travel_memory
```

字段设计：

| 字段 | 类型 | 说明 |
|---|---|---|
| `memory_id` | VarChar | 业务唯一 ID，例如 `post_123`、`plan_day_456` |
| `space_id` | Int64 | 当前旅行空间 ID，用于数据隔离 |
| `user_id` | Int64 | 内容创建人 |
| `source_type` | VarChar | `TRIP_POST` 或 `PLAN_DAY` |
| `source_id` | Int64 | 原始数据 ID |
| `city_code` | VarChar | 城市编码，可为空 |
| `city_name` | VarChar | 城市名称，可为空 |
| `content` | VarChar | 向量对应的原文摘要 |
| `created_at` | VarChar | 原始内容创建时间 |
| `embedding` | FloatVector | Embedding 向量 |

索引建议：

```text
向量字段：embedding
检索过滤：space_id == 当前空间 ID
topK：5
```

说明：

1. `space_id` 是最重要的安全边界。
2. `content` 不存敏感密钥、Cookie、密码等内容。
3. 删除日记或计划时，要同步删除 Milvus 中对应 `memory_id`。

## 6. MySQL 新增表

新增迁移文件建议：

```text
data/sql/006_ai_travel_memory_rag.sql
```

新增表：

```sql
CREATE TABLE IF NOT EXISTS ai_travel_memory_index (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  memory_id VARCHAR(80) NOT NULL,
  space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  city_code VARCHAR(30) NULL,
  city_name VARCHAR(80) NULL,
  content_hash VARCHAR(64) NOT NULL,
  indexed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_memory_id (memory_id),
  KEY idx_ai_memory_space_type (space_id, source_type),
  KEY idx_ai_memory_source (source_type, source_id)
);
```

用途：

1. 判断某条日记或计划是否已经索引。
2. 内容修改后通过 `content_hash` 判断是否需要重新生成 embedding。
3. 业务删除后可以找到 Milvus 里的 `memory_id` 并删除。

## 7. Java 后端改动

### 7.1 新增记忆构建服务

建议新增模块：

```text
apps/server/src/main/java/com/lovetravel/server/modules/ai/service/AiTravelMemoryService.java
```

职责：

1. 查询当前空间可索引的历史日记和历史计划。
2. 生成标准化 memory 文本。
3. 计算 `content_hash`。
4. 调用 Python `/internal/ai/memories/upsert`。
5. 更新 `ai_travel_memory_index`。

### 7.2 AI 生成计划前触发记忆同步

在现有 AI 生成计划链路中加入：

```text
校验登录/空间/额度
-> 同步当前空间最近 N 条旅行记忆
-> 调用 Python 生成计划 SSE
```

建议第一版限制：

```text
最近 100 条日记 + 最近 100 条计划
```

原因：

1. 控制 embedding 成本。
2. 控制同步耗时。
3. 对第一版个性化规划已经足够。

### 7.3 删除同步

当用户删除日记或计划时：

1. Java 查询 `ai_travel_memory_index`。
2. 调 Python `/internal/ai/memories/delete`。
3. MySQL 里把索引记录 `deleted = 1`。

如果 Python 或 Milvus 暂时不可用：

1. 不影响主业务删除。
2. 记录错误日志。
3. 后续可以做后台补偿任务。

## 8. Python AI 服务改动

建议新增目录：

```text
apps/ai-service/app/features/memory/
  router.py
  schemas.py
  embedding_service.py
  milvus_store.py
  rag_service.py
```

### 8.1 EmbeddingService

职责：

1. 接收文本。
2. 调用阿里云百炼 Embedding 模型。
3. 返回向量。

环境变量建议：

```text
DASHSCOPE_API_KEY
QWEN_EMBEDDING_MODEL_NAME
```

如果当前项目已经使用：

```text
ALIYUN_DASHSCOPE_API_KEY
```

则优先复用同一个 key，避免重复配置。

### 8.2 MilvusMemoryStore

职责：

1. 初始化 Milvus 连接。
2. 创建或检查 collection。
3. upsert 旅行记忆。
4. delete 旅行记忆。
5. search 当前空间相似记忆。

环境变量建议：

```text
MILVUS_URI=http://127.0.0.1:19530
MILVUS_TOKEN=
MILVUS_COLLECTION=love_travel_memory
```

Standalone 本机部署时，默认只让 Python 服务从本机访问。

### 8.3 RagService

职责：

1. 根据生成计划请求拼接检索 query。
2. 调用 EmbeddingService 得到 query embedding。
3. 调用 MilvusMemoryStore 检索 topK。
4. 把结果整理成短文本，交给 LangChain Prompt。

检索 query 示例：

```text
城市：青岛
想去地点：八大关、小麦岛、栈桥
必去地点：小麦岛
备注：想轻松拍照，不想太赶
```

## 9. Python 内部接口

### 9.1 批量写入记忆

```text
POST /internal/ai/memories/upsert
```

请求：

```json
{
  "items": [
    {
      "memoryId": "post_123",
      "spaceId": 1,
      "userId": 2,
      "sourceType": "TRIP_POST",
      "sourceId": 123,
      "cityCode": "370200",
      "cityName": "青岛市",
      "content": "城市：青岛市\n日记内容：今天去了小麦岛，适合慢慢散步拍照。"
    }
  ]
}
```

响应：

```json
{
  "success": true,
  "indexedCount": 1
}
```

### 9.2 删除记忆

```text
POST /internal/ai/memories/delete
```

请求：

```json
{
  "memoryIds": ["post_123"]
}
```

响应：

```json
{
  "success": true,
  "deletedCount": 1
}
```

### 9.3 生成计划接口增加 RAG

原接口保持不变：

```text
POST /internal/ai/plan-day/generate-stream
```

请求体增加字段：

```json
{
  "rag": {
    "enabled": true,
    "spaceId": 1,
    "topK": 5
  }
}
```

Python 内部根据原有请求信息生成 query，然后检索 Milvus。

## 10. LangChain Prompt 结构

v1.2 Prompt 应包含四类信息：

1. 系统角色：你是温柔、私密、不过度赶路的情侣旅行规划助手。
2. 用户本次输入：城市、想去地点、必去地点、上午/下午/晚上状态、备注。
3. 工具结果：天气结果。
4. RAG 记忆：当前空间历史日记和历史计划中检索出的偏好。

RAG 记忆示例：

```text
以下是这个旅行空间过去的旅行记忆，只能作为偏好参考，不能编造用户没有说过的事实：
1. 青岛市历史日记：用户喜欢在海边慢慢散步和拍照，晚上倾向回酒店休息。
2. 历史计划：过去常把八大关安排在上午，把小麦岛安排在下午。
3. 历史计划：用户偏好一天不超过 3 个主要地点。
```

约束：

1. 如果 RAG 为空，正常生成，不报错。
2. RAG 只影响风格和偏好，不覆盖用户本次明确选择。
3. 如果本次必去地点和历史偏好冲突，以本次必去地点为准。

## 11. Agent 事件记录

继续使用现有 `ai_agent_event` 表。

新增事件类型：

```text
RAG_SYNC_START
RAG_SYNC_DONE
RAG_SEARCH
RAG_RESULT
RAG_EMPTY
RAG_ERROR
```

事件示例：

```json
{
  "eventType": "RAG_RESULT",
  "eventMessage": "已检索到 5 条历史旅行记忆",
  "eventJson": {
    "topK": 5,
    "resultCount": 5,
    "sourceTypes": ["TRIP_POST", "PLAN_DAY"]
  }
}
```

## 12. 安全与权限

1. 前端不传 `spaceId`，Java 从当前登录用户的当前空间获得。
2. Java 调 Python 时才传 `spaceId`。
3. Python 检索 Milvus 必须带 `space_id == 当前空间 ID` 过滤条件。
4. Milvus 端口不开放公网。
5. Embedding 和 Prompt 不包含密码、Cookie、AccessKey。
6. 旅行日记和计划属于私密数据，不能跨空间训练或检索。
7. 如果用户删除内容，Milvus 中对应向量也要删除。

## 13. 失败降级策略

RAG 不能影响主功能可用性。

### 13.1 记忆同步失败

处理：

1. 记录日志。
2. Agent 事件写 `RAG_ERROR`。
3. AI 仍然可以不带历史记忆生成计划。

### 13.2 Milvus 检索失败

处理：

1. Python 返回 `RAG_ERROR` 进度事件。
2. 继续调用 Qwen 生成普通计划。
3. Java 保存草稿时记录本次 RAG 不可用。

### 13.3 Embedding 服务失败

处理：

1. upsert 失败时不写入索引成功状态。
2. search 失败时返回空记忆。
3. 不影响用户保存日记和计划。

## 14. 部署新增配置

### 14.1 腾讯云服务器

新增组件：

```text
Milvus Standalone
```

建议只在服务器内部访问：

```text
127.0.0.1:19530 或 Docker 内网地址
```

安全组：

```text
不开放 19530 到公网
```

### 14.2 Python 环境变量

```text
ALIYUN_DASHSCOPE_API_KEY=你的百炼Key
QWEN_MODEL_NAME=qwen3.5-omni-plus
QWEN_EMBEDDING_MODEL_NAME=待确认的百炼Embedding模型
MILVUS_URI=http://127.0.0.1:19530
MILVUS_TOKEN=
MILVUS_COLLECTION=love_travel_memory
RAG_TOP_K=5
```

### 14.3 Java 环境变量

Java 不直接连接 Milvus，暂不新增 Milvus 配置。

Java 只需要继续保留：

```text
AI_SERVICE_BASE_URL=http://127.0.0.1:8000
```

## 15. 面试讲法

可以这样讲：

> 我在项目中把 AI 旅行规划升级成了 RAG Agent。用户写过的旅行日记和历史计划会被转换成向量存入 Milvus。用户再次生成旅行计划时，系统会按当前空间 ID 做向量检索，取回相似历史记忆，再交给 LangChain 组装 Prompt，让大模型生成更符合用户偏好的计划。Java 后端负责鉴权、空间隔离、额度和限流，Python FastAPI 负责 Embedding、Milvus 检索和大模型编排。Milvus 不暴露公网，所有检索都按 spaceId 过滤，避免用户数据串空间。

可以展开的知识点：

1. RAG：检索增强生成，先检索相关历史资料，再让大模型回答。
2. Embedding：把文本转成向量，让机器可以按语义相似度搜索。
3. Milvus：专门存储和检索向量的数据服务。
4. LangChain：把 Prompt、模型调用、工具结果和 RAG 上下文编排起来。
5. Agent Harness：用 `ai_agent_run` 和 `ai_agent_event` 记录每次 AI 运行和工具调用，方便审计和排查。
6. 权限隔离：向量检索必须带 `spaceId` 过滤，不能只靠 prompt 提醒模型。

## 16. 实施顺序

建议按以下顺序开发：

1. 新增 MySQL 表 `ai_travel_memory_index`。
2. Python 增加 memory 模块、EmbeddingService、MilvusMemoryStore。
3. Python 增加 `/internal/ai/memories/upsert` 和 `/delete`。
4. Java 增加 AiTravelMemoryService，同步历史日记和历史计划。
5. AI 生成计划前触发记忆同步。
6. Python 生成计划前检索 Milvus。
7. LangChain Prompt 加入历史旅行记忆。
8. 增加 Agent 事件记录。
9. 本地联调。
10. 腾讯云部署 Milvus，并更新部署文档。

## 17. 后续升级方向

1. 加 LangGraph，把流程拆成节点：校验输入、同步记忆、检索记忆、天气工具、生成计划、结构化输出。
2. 图片识别：对旅行照片生成 caption，再写入向量库。
3. 多空间长期记忆管理：让用户选择是否启用 AI 记忆。
4. 用户可删除 AI 记忆：提供“清空本空间 AI 记忆”的设置入口。
5. 增加 LangSmith 或自建日志看板，观察每次 Agent 运行效果。
