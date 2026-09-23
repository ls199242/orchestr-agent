# OrchestrAgent 多智能体协同编排系统

企业级多智能体协同编排系统（Multi-Agent Orchestration System），采用经典微内核 + 策略驱动架构，基于 **Java 17 / Spring Boot 3** 构建；配套提供基于 **Python FastAPI** 的可视化控制台与配置管理服务（`py-mock-console`）。

---

## 目录
- [一、项目架构与模块划分](#一项目架构与模块划分)
- [二、环境依赖与前置准备](#二环境依赖与前置准备)
- [三、项目启动指南（核心启动命令）](#三项目启动指南核心启动命令)
  - [1. 启动 Python 控制台 (py-mock-console)](#1-启动-python-控制台-py-mock-console)
  - [2. 启动 Java 核心编排服务 (orchestr-agent)](#2-启动-java-核心编排服务-orchestr-agent)
- [四、核心接口与联调验证](#四核心接口与联调验证)
- [五、测试与质量保障](#五测试与质量保障)
- [六、开发规范与核心约束](#六开发规范与核心约束)

---

## 一、项目架构与模块划分

整个工程由两个子系统构成：

```
orchestr-agent/
├── app/                           # [Java] 核心多智能体编排系统 (Spring Boot 3 + JDK 21)
│   ├── common/                    # 公共枚举、错误码、基础异常与通用工具
│   ├── prompt/                    # 提示词工程引擎与模板渲染
│   ├── memory/                    # 短期执行上下文与长期记忆管理
│   ├── repository/                # 7 大配置仓储（内存缓存 + 远程定时轮询拉取）
│   ├── integration/               # 大模型网络客户端（OpenAI 兼容协议）
│   ├── biz/                       # 核心业务逻辑：指挥调度 (Conductor)、质检评估 (Evaluator)、专精 Worker (Rag/Tool) 与 ReAct 工作流
│   ├── web/                       # Spring Boot 启动入口、REST Controller、SSE 流式打字机推送
│   └── test/                      # 单元测试与集成测试组件 (MockLlmClient 等)
├── py-mock-console/               # [Python] 可视化控制台与配置 Mock 服务 (FastAPI)
│   ├── app.py                     # FastAPI 主程序 (Mock REST API + 代理转发 + 静态站点)
│   ├── config_store.py            # 7 大仓储配置持久化存储与官方预设
│   ├── data/configs.json          # 动态配置持久化数据文件
│   ├── run.sh                     # 一键启动脚本
│   └── static/index.html          # 单页现代化可视化交互控制台
├── AGENTS.md                      # 核心开发与架构红线规范
└── pom.xml                        # Maven 多模块父级 POM
```

---

## 二、环境依赖与前置准备

| 组件 | 版本要求 | 说明 |
| :--- | :--- | :--- |
| **JDK** | 17+ | 项目基于 Java 17 构建 |
| **Maven** | 3.8+ | 建议配置国内镜像以加速依赖下载 |
| **Python** | 3.9+ | 运行 `py-mock-console` 所需 |
| **OpenAI API Key** | 可选 | 真实模型调用时注入环境变量：`export OPENAI_API_KEY="your-key"`（测试集成环境自带 Mock 支持） |

---

## 三、项目启动指南（核心启动命令）

> **💡 启动顺序建议**：
> 建议**优先启动 Python 控制台**（提供配置仓储数据源），**再启动 Java 核心服务**（Java 启动时会自动从 Python 端同步初始配置）。

### 1. 启动 Python 控制台 (py-mock-console)

- **默认服务端口**：`8000`
- **Web 控制台地址**：[http://127.0.0.1:8000](http://127.0.0.1:8000)

#### 首次运行（安装依赖）：
```bash
cd py-mock-console
pip install -r requirements.txt
```

#### 启动方式 A（推荐，一键脚本）：
```bash
cd py-mock-console
./run.sh
```

#### 启动方式 B（终端命令直接运行）：
```bash
cd py-mock-console
python3 -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

---

### 2. 启动 Java 核心编排服务 (orchestr-agent)

- **默认服务端口**：`8080`
- **配置数据源地址**：`http://127.0.0.1:8000`（在 `app/web/src/main/resources/application.yml` 中配置）

在项目根目录（`orchestr-agent`）下执行以下任一命令：

#### 启动方式 A（推荐，Maven 插件直接启动，适合开发调试）：
```bash
mvn clean spring-boot:run -pl app/web
```

#### 启动方式 B（打包为 Jar 包后启动，适合独立运行）：
```bash
# 1. 在根目录下编译并打包
mvn clean package -DskipTests

# 2. 运行 Web 模块构建生成的 Jar 包
java -jar app/web/target/orchestragent-web-1.0.0-SNAPSHOT.jar
```

#### 启动验证：
Java 实例启动完成后，会在控制台打印启动日志，并在后台开启每 10 秒一次的仓储拉取任务。浏览器访问下方接口可验证 7 大仓储状态：
```bash
curl http://127.0.0.1:8080/api/agent/config/overview
```

---

## 四、核心接口与联调验证

两者启动后，可通过 `http://127.0.0.1:8000` 可视化控制台进行交互，或直接调用 Java 提供的 REST/SSE API：

| 接口分类 | HTTP 方法与路径 | 描述 |
| :--- | :--- | :--- |
| **异步工作流** | `POST /api/agent/invoke` | 发起智能体异步编排任务，返回 `flowId` |
| **工作流状态** | `GET /api/agent/flow/{flowId}` | 查询指定工作流的运行状态与执行结果 |
| **终止工作流** | `POST /api/agent/stop/{flowId}` | 主动安全中断执行中的工作流 |
| **流式对话** | `POST /api/agent/chat` | 发起 Server-Sent Events (SSE) 打字机流式交互与思考过程推送 |
| **配置快照** | `GET /api/agent/config/overview` | 查看当前 Java 内存中 7 大仓储的实体数量与摘要 |
| **配置详情** | `GET /api/agent/config/all` | 获取当前 Java 内存中 7 大仓储的全量快照 |
| **热重载** | `POST /api/agent/config/reload` | 立即强制从远端拉取最新配置刷新本地仓储 |

---

## 五、测试与质量保障

在项目根目录下执行全量自动化测试（涵盖策略流执行、SSE 推送、任务打断、仓储同步与切面验证）：

```bash
mvn clean test
```

---

## 六、开发规范与核心约束

在日常开发与重构过程中，需严格遵循 [AGENTS.md](AGENTS.md) 约定的架构红线：
1. **单一事实来源 (Single Source of Truth)**：智能体元数据与模型超参统一归口到 `AgentConfigRepository`，策略配置仅维护绑定的专精 Worker 编码列表。
2. **Fail-Fast 铁律**：严禁伪造默认桩对象或假通过（False Positive）。配置缺失或模型异常时立即抛出业务异常。
3. **测试仿真与生产代码彻底解耦**：严禁在生产 HTTP 客户端中植入测试 Mock 分支，所有测试模拟统一集中在 `app/test` 模块内。
