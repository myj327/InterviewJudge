# 架构与数据边界

## 服务职责

| 模块 | 职责 |
| --- | --- |
| gateway-service | 登录凭据验证、请求路由 |
| user-service | 用户、登录、刷新令牌和解题统计 |
| problem-service | 题目、Assessment、Attempt、时间窗与考核归属 |
| submission-service | 提交记录、Outbox、判题结果、复盘任务与报告、WebSocket |
| sandbox-service | 提交入口、任务消费、私有工作目录与 Docker runner |
| common | DTO、Feign client、身份边界、消息确认与重试等公共组件 |

MySQL 保存业务事实，Redis 用于缓存、刷新令牌和排名；判题任务通过 RabbitMQ 调度。

## 提交受理与幂等

请求携带 attemptId、语言、源码和调用方生成的幂等键。先检查已受理回执；没有回执时再校验新提交的考核归属、状态与时间窗。

- `(attemptId, idempotencyKey)` 唯一约束决定提交身份。
- 同键命中时检查所有者、源码 SHA-256、语言及题目；一致则返回原 ID，不一致返回 409。
- 摘要按源码 UTF-8 编码实时计算，不存数据库；空白和换行变化仍视为内容不同。
- 已受理请求可以在截止或交卷后找回原回执；新提交仍需通过资格检查。
- Submission 与判题 Outbox 在同一个数据库事务保存，HTTP 返回 ID，不等待代码运行或模型。

## 消息与判题

Outbox dispatcher 在发送确认及路由检查成功后标记 SENT；消费者按至少一次投递设计，结果与统计副作用使用条件更新和唯一约束防重。明确的瞬时故障有限重试，无法处理的消息保留在 DEAD / DLQ。

Worker 从受理快照在自己的私有目录恢复源码与输入，启动受限 Java runner。编译、公开样例、完整测试依次执行，编译或公开样例失败后停止；完整阶段的 Wrong Answer 仍属于 VERDICT_READY。

Worker 确认结果已交给 RabbitMQ 后才 ACK 判题任务；结果消费者完成数据库事务后才 ACK 结果消息。可能重新执行判题，不宣称外部执行恰好一次。

## 复盘与权限

verdict 先落库，再异步进入复盘。复盘通过 review_job 的持久化租约认领，外部 Provider 调用在数据库事务之外执行；同版本报告有唯一约束。

Evidence 只包含源码、公开题目信息和判题摘要，排除隐藏输入、标准答案、原始运行输出及身份字段；已知敏感内容按模式遮盖，不保证识别任意个人信息。

报告查询区分 PENDING、RUNNING、FAILED、SKIPPED、SUCCEEDED。提交者及对应考核创建者可以读取，其他候选人不能越权。AI 失败不修改 verdict。

## 边界

当前为 Java 单题考核，完整输入整体执行并比较文本；不是多独立用例计分系统。WebSocket 提供即时通知，HTTP 补查用于断线或丢失推送后的结果恢复。

单机多应用实例的接续能力不等于主机、数据库或消息队列的集群高可用。判题 time 是阶段墙钟耗时，memory=-1 表示未测实际峰值内存。
