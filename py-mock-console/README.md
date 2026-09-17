# OrchestrAgent Mock Console & Test Provider

基于 Python FastAPI 的独立测试与配置管理系统，为 `orchestr-agent` (Java) 提供：
1. **7 大仓储远程 Mock 数据提供端**：提供标准 REST API（`/api/strategy`、`/api/agent`、`/api/tool`、`/api/model`、`/api/dict`、`/api/prompt`、`/api/prompt-group`），供 Java 仓储定时轮询拉取同步。
2. **Web 交互可视化控制台**：包含现代 Web 前端界面，可在浏览器中便捷查看和在线编辑 7 大仓储配置，配置自动持久化到 `data/configs.json`。
3. **Java Controller 3 大方法联调交互入口**：
   - `POST /api/agent/chat`：SSE 实时打字机流式对话与思考过程展示。
   - `POST /api/agent/testInvoke`：同步全流程协同执行，展示最终产出与 Evaluator 质检评分。
   - `POST /api/agent/invoke`：异步任务启动与 flowId 状态查询。
4. **Java 仓储内存快照与热重载监控**：直连 Java 侧 `/api/agent/config/overview` 与 `/api/agent/config/all`，实时展示 Java 内存中的配置快照，支持一键发送全量热重载指令。

---

## 目录结构

```
py-mock-console/
├── app.py               # FastAPI 服务主程序 (Mock 接口 + 代理转发 + 静态站点)
├── config_store.py      # 配置持久化存储与官方默认预设
├── requirements.txt     # Python 依赖清单
├── run.sh               # 一键启动脚本
├── data/
│   └── configs.json     # 动态配置持久化文件
└── static/
    └── index.html       # 单页现代化前端控制台
```

---

## 快速启动

### 1. 启动 Python Mock 控制台 (端口 8000)

```bash
cd py-mock-console
./run.sh
```

打开浏览器访问：[http://127.0.0.1:8000](http://127.0.0.1:8000)

### 2. 启动 Java OrchestrAgent (端口 8080)

在 `orchestr-agent` 根目录下执行：

```bash
mvn clean spring-boot:run -pl app/web
```

Java 实例启动时会自动连接 `http://127.0.0.1:8000` 初始化 7 大仓储，并在后台以可配置周期（默认 10 秒）持续定时轮询刷新。
