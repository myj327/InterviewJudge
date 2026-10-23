# InterviewJudge AI

面向校招笔试场景的代码考核平台，覆盖考核创建与发布、限时提交、异步 Java 判题、结果查询和结构化 AI 复盘。

## 主要功能

- **考核管理**：Assessment–Attempt–Submission 模型，支持考核发布、候选人参加、时间窗校验和交卷。
- **提交幂等**：以 `(attemptId, idempotencyKey)` 唯一约束识别重试；用 UTF-8 / SHA-256 源码摘要校验内容一致性，同键不同内容返回 409。
- **异步判题**：Submission 和 Outbox 在同一事务保存，通过 RabbitMQ 投递任务；消费者使用手动 ACK、有限重试、DLQ 和幂等结算。
- **分阶段沙箱**：Java 编译、公开样例、完整测试依次执行，前序失败停止后续阶段；Docker runner 受权限、网络、CPU、内存、PID、时间及输出限制。
- **AI 复盘**：默认 mock，也支持 OpenAI-compatible Provider；只读取白名单 Evidence，判题结果由确定性测试产生，模型不能修改 verdict。
- **结果恢复**：WebSocket 通知配合 HTTP 补查；Worker 使用独立目录恢复任务快照，复盘任务使用持久化租约管理。

## 技术栈

Java 17、Spring Boot、Spring Cloud Gateway、OpenFeign、Nacos、MySQL、Redis、RabbitMQ、MyBatis-Plus、Docker、Vue 3、TypeScript、Element Plus。

## 请求与执行流程

```text
浏览器 → Gateway → 提交校验 → MySQL（Submission + Outbox）→ 返回 submissionId
                                  ↓
                              RabbitMQ → Worker → Docker 分阶段判题
                                                       ↓
                                判题结果落库 ← RabbitMQ
                                      ↓
                              异步复盘 → 报告查询
```

## 目录

| 路径 | 内容 |
| --- | --- |
| `upstream/D-OnlineJudge/DOJ-BE/` | 后端服务、公共模块和 Java 测试 |
| `upstream/D-OnlineJudge/DOJ-FE/` | 前端源码及构建配置 |
| `upstream/D-OnlineJudge/docs/SQL/` | 数据库结构和迁移脚本 |
| `deploy/` | Compose、Java runner 镜像和数据库初始化 |
| `tools/frontend/` | 前端聚焦回归测试 |
| `docs/` | 架构和部署说明 |

## 开始使用

后端与判题需要能访问宿主 Docker daemon 的 Linux 环境；只运行前端无法完成登录、数据查询和判题。

- [部署与本地开发](docs/DEPLOYMENT.md)
- [架构与数据边界](docs/ARCHITECTURE.md)

当前考核支持 Java、单题和整份输入输出比较。AI 复盘与判题状态分开；默认不会发起真实模型调用。

## 来源与许可证

本项目在 [D-OnlineJudge](https://github.com/Decade-qiu/D-OnlineJudge) 的用户、题目、异步判题、Docker 沙箱和前端基础上开发，新增考核业务、分阶段判题、AI 复盘及消息可靠性处理。固定上游版本和改动边界见 [THIRD_PARTY.md](THIRD_PARTY.md)。

保留上游 Apache-2.0 许可证及源码版权声明，详见 [LICENSE](LICENSE)。
