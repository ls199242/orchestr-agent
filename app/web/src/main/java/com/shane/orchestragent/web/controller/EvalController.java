package com.shane.orchestragent.web.controller;

import com.shane.orchestragent.biz.eval.model.EvalRunRequestDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.model.StrategyMetadataDTO;
import com.shane.orchestragent.biz.eval.service.EvalRunnerService;
import com.shane.orchestragent.biz.eval.service.MetadataExportService;
import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.model.BaseResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 外部评测系统专用对接控制器
 * <p>
 * 提供端到端用例执行、角色级微运行与策略拓扑全景元数据导出 HTTP 契约入口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/eval")
public class EvalController {

    private final EvalRunnerService evalRunnerService;
    private final MetadataExportService metadataExportService;

    public EvalController(EvalRunnerService evalRunnerService,
                          MetadataExportService metadataExportService) {
        this.evalRunnerService = evalRunnerService;
        this.metadataExportService = metadataExportService;
    }

    /**
     * 1. 端到端用例同步评测入口
     * <p>
     * 接收 eval 系统下发的 caseId 与选项，从 eval 系统拉取对应 MockBundle，在完全隔离的上下文中驱动流程执行，
     * 执行完毕后同步产出符合 M3 规范的完整结构化 Trace 数据。
     *
     * @param request 评测运行请求载荷
     * @return 包含完整结构化 Trace 的统一包装对象 {@link BaseResult}
     * @throws BizException 业务异常
     */
    @PostMapping("/run")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "端到端评测用例执行")
    public BaseResult<ExecutionTraceDTO> runCase(@RequestBody EvalRunRequestDTO request) throws BizException {
        ExecutionTraceDTO trace = evalRunnerService.runCase(request);
        return BaseResult.ok(trace);
    }

    /**
     * 2.1 角色级评测：单跑 Coordinator 意图决策
     *
     * @param body 请求入参字典，包含 message 与 caseId
     * @return 包含 action、strategyCode、params 及决策耗时
     * @throws BizException 业务异常
     */
    @PostMapping("/node/coordinator")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "角色评测: Coordinator意图决策")
    public BaseResult<Map<String, Object>> runCoordinator(@RequestBody Map<String, String> body) throws BizException {
        String message = body.get("message");
        String caseId = body.get("caseId");
        return BaseResult.ok(evalRunnerService.runCoordinator(message, caseId));
    }

    /**
     * 2.2 角色级评测：单跑 Planner 战略规划
     *
     * @param body 请求入参字典，包含 strategyCode、target 与 caseId
     * @return 包含生成的 DAG 结构体及耗时
     * @throws BizException 业务异常
     */
    @PostMapping("/node/planner")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "角色评测: Planner战略规划")
    public BaseResult<Map<String, Object>> runPlanner(@RequestBody Map<String, String> body) throws BizException {
        String strategyCode = body.get("strategyCode");
        String target = body.get("target");
        String caseId = body.get("caseId");
        return BaseResult.ok(evalRunnerService.runPlanner(strategyCode, target, caseId));
    }

    /**
     * 2.3 角色级评测：单跑专精 Expert 专家
     *
     * @param body 请求入参字典，包含 expertCode、stitchedInput 与 caseId
     * @return 包含 Expert 结构化产出文本及耗时
     * @throws BizException 业务异常
     */
    @PostMapping("/node/expert")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "角色评测: Expert任务执行")
    public BaseResult<Map<String, Object>> runExpert(@RequestBody Map<String, String> body) throws BizException {
        String expertCode = body.get("expertCode");
        String stitchedInput = body.get("stitchedInput");
        String caseId = body.get("caseId");
        return BaseResult.ok(evalRunnerService.runExpert(expertCode, stitchedInput, caseId));
    }

    /**
     * 2.4 角色级评测：单跑 Evaluator 质检验收
     *
     * @param body 请求入参字典，包含 strategyCode、summary 与 caseId
     * @return 包含质检 verdict、score、critique 及 blamedStepIds
     * @throws BizException 业务异常
     */
    @PostMapping("/node/evaluator")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "角色评测: Evaluator质检审查")
    public BaseResult<Map<String, Object>> runEvaluator(@RequestBody Map<String, String> body) throws BizException {
        String strategyCode = body.get("strategyCode");
        String summary = body.get("summary");
        String caseId = body.get("caseId");
        return BaseResult.ok(evalRunnerService.runEvaluator(strategyCode, summary, caseId));
    }

    /**
     * 2.5 角色级评测：单跑 Reporter 交付成果汇报
     *
     * @param body 请求入参字典，包含 strategyCode、summary 与 caseId
     * @return 包含最终排版文案 finalAnswer 及耗时
     * @throws BizException 业务异常
     */
    @PostMapping("/node/reporter")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "角色评测: Reporter成果交付")
    public BaseResult<Map<String, Object>> runReporter(@RequestBody Map<String, String> body) throws BizException {
        String strategyCode = body.get("strategyCode");
        String summary = body.get("summary");
        String caseId = body.get("caseId");
        return BaseResult.ok(evalRunnerService.runReporter(strategyCode, summary, caseId));
    }

    /**
     * 3. 策略与拓扑全景元数据导出接口
     * <p>
     * 供外部 eval 系统查询全量策略、绑定 Expert、可用 Tool 池及入参定义，以自动派生池合规规则。
     *
     * @return 包含策略全景元数据的包装对象
     */
    @GetMapping("/metadata")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "全量策略元数据导出")
    public BaseResult<StrategyMetadataDTO> exportMetadata() {
        return BaseResult.ok(metadataExportService.exportMetadata());
    }
}
