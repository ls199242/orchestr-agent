package com.shane.orchestragent.test.flow;

import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.BaseStrategyFlow;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.service.impl.SseServiceImpl;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SseService 及 attachFlow 编排绑定测试
 *
 * @author Shane
 */
public class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    public void setUp() {
        this.sseService = new SseServiceImpl();
    }

    @Test
    @DisplayName("测试基础 SSE 客户端创建、消息推送与关闭")
    public void testBasicSseClientOperations() {
        String clientId = "test-client-1";
        SseEmitterUTF8 emitter = sseService.createSseClient(clientId);
        Assertions.assertNotNull(emitter);

        Assertions.assertDoesNotThrow(() -> sseService.send(clientId, "test_event", "hello"));
        Assertions.assertDoesNotThrow(() -> sseService.close(clientId));
    }

    @Test
    @DisplayName("测试 attachFlow 绑定工作流并在 FINISHED 时正常触发完成")
    public void testAttachFlowFinish() throws Exception {
        StrategyContext context = new DefaultStrategyContext();

        BaseStrategyFlow testFlow = new BaseStrategyFlow(context, 5) {
            @Override
            protected String doExecute() throws BizException {
                return "执行成功成果";
            }
        };

        SseEmitterUTF8 emitter = sseService.attachFlow(testFlow);
        Assertions.assertNotNull(emitter);

        // 执行流程
        testFlow.execute();

        Assertions.assertEquals(FlowStateEnum.FINISHED, testFlow.getState());
        Assertions.assertEquals("执行成功成果", context.getRecommendResult());
        Assertions.assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.getField(emitter, "complete")));
    }

    @Test
    @DisplayName("测试 attachFlow 绑定工作流并在主动终止时触发 stopped 事件")
    public void testAttachFlowStopped() throws Exception {
        StrategyContext context = new DefaultStrategyContext();

        BaseStrategyFlow testFlow = new BaseStrategyFlow(context, 5) {
            @Override
            protected String doExecute() throws BizException {
                return null;
            }
        };

        SseEmitterUTF8 emitter = sseService.attachFlow(testFlow);

        // 主动停止
        testFlow.stop();

        Assertions.assertEquals(FlowStateEnum.STOPPED, testFlow.getState());
        Assertions.assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.getField(emitter, "complete")));
    }

    @Test
    @DisplayName("测试 attachFlow 在流程抛出异常时设置 completeWithError")
    public void testAttachFlowError() {
        StrategyContext context = new DefaultStrategyContext();

        BaseStrategyFlow testFlow = new BaseStrategyFlow(context, 5) {
            @Override
            protected String doExecute() throws BizException {
                throw new BizException("TEST_ERROR", "测试流程异常");
            }
        };

        SseEmitterUTF8 emitter = sseService.attachFlow(testFlow);
        testFlow.execute();

        Assertions.assertEquals(FlowStateEnum.ERROR, testFlow.getState());
        Assertions.assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.getField(emitter, "complete")));
    }

    @Test
    @DisplayName("测试 attachFlow 在主动停止时状态同步")
    public void testAttachFlowStop() {
        StrategyContext context = new DefaultStrategyContext();

        AtomicBoolean stoppedCalled = new AtomicBoolean(false);
        BaseStrategyFlow testFlow = new BaseStrategyFlow(context, 5) {
            @Override
            protected String doExecute() {
                return null;
            }

            @Override
            public void stop() {
                super.stop();
                stoppedCalled.set(true);
            }
        };

        SseEmitterUTF8 emitter = sseService.attachFlow(testFlow);
        Assertions.assertNotNull(emitter);

        testFlow.stop();
        Assertions.assertTrue(stoppedCalled.get());
        Assertions.assertTrue(testFlow.isStopped());
    }
}
