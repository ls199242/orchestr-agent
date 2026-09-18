# OrchestrAgent 核心开发与架构规范

本文档定义了 `orchestr-agent` 工程的强制性架构与代码规范，所有在此工程中进行的开发、重构与维护工作必须严格遵守以下规则。

---

## 一、作业范围规范（核心红线）

1. **单一仓库作业约束**：
   - 当前工作区为多项目环境（包含 `laiye-expert-core` 与 `orchestr-agent`）。
   - **绝对禁止修改任何 `laiye-expert-core`（expert）的代码与文件**。
   - 所有需求实现、接口调用、功能重构均只能在 `orchestr-agent` 仓库范围内闭环。
2. **严禁主动探查 expert 代码与状态**：
   - 除非用户在当前对话中明确说要查看或比对 `laiye-expert-core`（expert）的代码，否则**严禁主动对 expert 目录执行任何文件查看、代码检索、目录遍历或 git 状态查询**。
   - 所有日常任务的上下文检索、执行、状态确认及代码改动仅限在 `orchestr-agent` 仓库内独立进行。

---

## 二、初建期代码纯粹性规范（拒绝向下兼容）

1. **无历史包袱与拒绝过渡兼容**：
   - 当前系统处于建设初期阶段，不存在既有线上系统的兼容负担与历史版本包袱。
   - **全面禁止使用 `@Deprecated` 废弃注解**。如果代码或配置已不合时宜，必须立即彻底删除，严禁以“向下兼容”为借口保留冗余分支。
2. **单一事实来源（Single Source of Truth）**：
   - 智能体（包括系统智能体和业务专精 Worker）的元数据、模型超参及提示词模板统一归口到 `AgentConfigRepository`（对应实体为 `AgentConfigDO`）。
   - 策略配置 `StrategyConfigDO` 中仅维护绑定的专精 Worker 编码列表（`workerCodes: List<String>`），严禁维护内嵌式实体或双轨配置。

---

## 三、严禁生产代码伪造与假兜底（Fail-Fast 铁律）

1. **测试仿真与生产代码彻底解耦**：
   - **严禁在生产代码（如 `OpenAiCompatibleLlmClient` 等）中为了跑通单元测试而植入任何硬编码仿真响应（如 `generateMockResponse`）或 `isMock` 逻辑**。
   - 生产 HTTP 客户端必须保持纯粹性，只负责实际的网络请求与协议解析。
   - 单元测试与集成测试所需的模拟行为必须严格存放在 `app/test/src/test/java` 目录下（如使用 `MockLlmClient` 或 Mockito），不得污染生产代码。
2. **严禁伪造桩对象掩盖缺失**：
   - 当策略声明依赖某个 Worker（`workerCodes`）或工具（`toolCodes`）时，若对应仓储未检索到配置，必须严格 **Fail-Fast** 抛出 `BizException` 中断流程，严禁动态伪造“默认 Worker 桩”或“默认 Tool 桩”。
   - 当大模型未配置且字典无全局默认模型时，必须直接抛出异常，严禁隐式硬编码模型名称。
3. **严禁假通过（False Positive）**：
   - 评估智能体（`EvaluatorAgent`）在返回空内容或返回内容无法解析为有效 JSON 时，必须直接抛出异常中断或判定为质检不合格，严禁隐式兜底为 100 分满分通过（`defaultPass`）。
   - 指挥智能体（`ConductorAgent`）在返回无法识别的指令或返回空动作时，必须抛出指挥调度异常，严禁静默伪造 `FINISH` 结束标记。
