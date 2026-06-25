# Redis 使用说明

## 当前用途

本项目中 Redis 只保存短期状态，不保存旅行记录、日记、图片、计划等长期业务数据。长期数据仍以 MySQL 为准。

## Key 设计

| Key | 用途 | TTL |
| --- | --- | --- |
| `love-travel:session:{hash}` | 登录 Session 白名单 | 7 天 + 随机抖动 |
| `love-travel:login:fail:{account}` | 登录失败次数计数 | 10 分钟 |
| `love-travel:login:lock:{account}` | 登录失败过多后的临时锁定 | 10 分钟 |
| `love-travel:invite-code:{code}` | 邀请码 1 分钟有效期与防重复占用 | 约 1 分钟 |
| `love-travel:ai:plan-day:generating:{userId}:{dayId}` | AI 规划防重复点击锁 | 3 分钟 |
| `love-travel:ai:plan-day:rate:{userId}` | AI 规划短窗口限流 | 1 分钟 |

## 防缓存问题策略

1. 缓存穿透：不把不存在的长期业务对象作为主要查询缓存；Session、邀请码、锁和限流都是短期状态，Redis miss 后会直接拒绝或回查 MySQL 的受控路径。
2. 缓存击穿：AI 生成使用 `SET NX EX` 互斥锁，同一用户同一天只能有一个生成任务运行。
3. 缓存雪崩：Session TTL 增加随机抖动，避免大量 Session 在同一秒集中失效。
4. 数据一致性：邀请码和 AI 运行记录仍写 MySQL，Redis 只负责短期有效期、防重复和限流。
5. 暴力破解防护：登录失败次数使用 Redis 计数，10 分钟内错误 5 次会临时锁定账号 10 分钟；登录成功后清理失败计数与锁定 key。

## 面试表达

可以这样介绍：

> 项目中 Redis 没有被当作 MySQL 替代品，而是用于登录 Session 白名单、登录失败次数限制、邀请码 TTL、防重复点击和 AI 接口限流。长期数据落 MySQL，短期状态进 Redis。通过 TTL 随机抖动、SET NX 锁和限流 key，避免缓存穿透、击穿和雪崩问题，同时提升登录安全。
