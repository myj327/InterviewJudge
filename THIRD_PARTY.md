# 第三方来源与许可证

## D-OnlineJudge

- 仓库：<https://github.com/Decade-qiu/D-OnlineJudge>
- 固定上游 commit：`519a23071aabd6891a77d832213180a27941a994`。
- 导入日期：2026-08-30。
- 源码位置：`upstream/D-OnlineJudge/`，不是独立 Git 子模块。
- 许可证：上游根目录实际提供 **Apache License 2.0**，已保留在该目录，并复制到本项目根 [LICENSE](LICENSE)。固定版本没有 `NOTICE` 文件。
- 上游 README 曾写 MIT；本项目依据实际许可证文件保留 Apache-2.0 文本。

## 复用与改动边界

复用了上游的用户、题目、基础提交、微服务拆分、缓存、消息队列、Docker Java 沙箱和基础页面。

本项目新增或实质调整了 Assessment / Attempt 考核业务、提交内容绑定与幂等回执、分阶段 Java 判题、白名单 Evidence 与结构化复盘、Outbox 与可靠消费、任务快照接续、结果补查，以及考核管理和报告页面。修改记录可从 Git 历史定位；没有将整套基础系统归因为从零实现。

公开源码继续保留上游许可证与适用的版权声明。历史部署日志、验收过程文件和面试材料不属于运行依赖，已从当前公开目录移出。

## 其他依赖

后端依赖见各模块 `pom.xml`；前端依赖及锁定版本见 `package.json`、`pnpm-lock.yaml`。相关组件继续使用各自的许可证。
