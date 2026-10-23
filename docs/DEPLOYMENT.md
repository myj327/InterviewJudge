# 部署与本地开发

## 环境

后端与判题运行在支持 Docker Engine、Compose 和宿主 Docker socket 的 Linux 主机上。完整服务栈建议准备 8 个逻辑 CPU、16 GiB 内存及足够镜像与卷空间。

源码构建使用 Java 17 / Maven 3.8；前端使用 Node.js 22 及 pnpm 9.15.9。数据库、Redis、RabbitMQ、Nacos 和 Elasticsearch 由 Compose 启动。

## 私有配置

在项目根目录复制示例配置，已有 `.env.local` 时保留现有文件：

```bash
cp -n .env.example .env.local
chmod 600 .env.local
openssl rand -hex 32
docker info --format '{{.DockerRootDir}}'
```

将生成的随机值填入 `.env.local` 的 DOJ_INTERNAL_TOKEN。按 Docker RootDir 设置 HOST_CODE_PATH 和 HOST_PROBLEM_CODE_PATH，例如默认 Docker 数据目录对应 `/var/lib/docker/volumes/interviewjudge-codes/_data` 与 `/var/lib/docker/volumes/interviewjudge-problem-codes/_data`。

默认 Provider 为 mock。真实 Provider 可配置 base URL、model 和 OPENAI_API_KEY；真实凭据只保存在被 Git 忽略的 `.env.local`。环境变量名称沿用兼容适配器，也可连接支持该接口协议的其他 Provider。

示例数据库密码和上游 JWT 密钥用于开发环境，正式部署时应配置自己的凭据和密钥。

## 启动后端

在项目根目录执行，Compose 文件必须按下列顺序合并：

```bash
sudo sysctl -w vm.max_map_count=262144
export COMPOSE_PARALLEL_LIMIT=1
dc() { docker compose --env-file .env.local -f deploy/compose.yml -f deploy/compose.override.yml "$@"; }

dc config --quiet
docker build -t code-runner-java -f deploy/Dockerfile.java deploy
dc build
dc up -d mysql redis rabbitmq nacos elasticsearch
dc up -d user-service problem-service sandbox-service submission-service gateway-service
dc ps
```

入口只绑定主机回环地址 `127.0.0.1:8080`。数据库、缓存、MQ 和服务发现端口没有发布到公网。沙箱容器通过宿主 socket 创建 runner，HOST_CODE_PATH 必须指向实际宿主代码卷。

MySQL 首次创建空数据卷时自动执行数据库结构及可靠性表初始化。已有数据库升级要先备份并按需执行 SQL 目录中的迁移脚本；修改初始化文件不会自动迁移已有卷。

首次使用可在页面注册账号，再由部署者将指定账号设为管理员，以使用考核管理。开发环境中可进入数据库交互终端：

```bash
dc exec mysql mysql -uroot -p
```

确认账号后执行 `UPDATE doj_user.user SET role=0 WHERE username='your-admin';`，再重新登录。

停止应用并保留数据卷：

```bash
dc stop
```

## 启动前端

```bash
cd upstream/D-OnlineJudge/DOJ-FE
corepack enable
corepack prepare pnpm@9.15.9 --activate
pnpm install --frozen-lockfile
pnpm exec vite --host 127.0.0.1
```

浏览器访问 Vite 输出的地址，通常为 `http://127.0.0.1:5173`。开发代理将 `/api` 转发到本机 8080；后端在远程主机时，可通过 SSH 将本机 8080 转发到远程的 `127.0.0.1:8080`。HTTP 补查可以在 WebSocket 不可达时恢复判题结果。

## 构建与聚焦测试

```bash
mvn -f upstream/D-OnlineJudge/DOJ-BE/pom.xml -pl submission-service -am \
  -Dtest=SubmissionKeyBindingTest,SubmissionConcurrentIntakeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

cd upstream/D-OnlineJudge/DOJ-FE
pnpm run build
```

后端 `src/test` 保留业务与安全回归；部分 Spring 集成测试需要对应中间件。前端聚焦回归位于 `tools/frontend/`，需使用支持 `node:module.stripTypeScriptTypes` 的 Node 版本。
