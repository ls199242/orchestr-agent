package com.shane.orchestragent.prompt;

/**
 * 提示词模板属性常量
 *
 * @author Shane
 */
public interface PromptPropertyConstant {

    String KEY_AGENT = "agents";
    String KEY_AGENT_NAMES = "agentNames";
    String KEY_REQUEST_PARAMETER_PREFIX = "request_";

    String KEY_TOOL_MESSAGES = "tool_messages";
    String KEY_WORKER_MESSAGES = "worker_messages";

    interface PLANNER {
        String KEY_PLAN_RESULT = "planResult";
    }

    interface CONDUCTOR {
        String KEY_REQUEST_PARAMETER_JSON = "requestParameter";
        String KEY_PLAN_STEPS = "plan_steps";
        String KEY_HISTORY_MESSAGES = "history_messages";
        String KEY_EVALUATOR_FEEDBACK = "evaluator_feedback";
    }

    interface WORKER {
        String KEY_SPEC = "workerSpec";
        String KEY_WORKER_TARGET = "workerTarget";
        String KEY_NAME = "workerName";
        String KEY_RESPONSE = "workerResponse";
        String KEY_PROMPT = "workerPrompt";
        String KEY_RAG_CONTENT = "ragContents";
    }

    interface TOOL {
        String KEY_NAME = "toolName";
        String KEY_RESPONSE = "toolResponse";
    }

    interface EVALUATOR {
        String KEY_CRITIQUE = "evaluatorCritique";
        String KEY_SCORE = "evaluatorScore";
        String KEY_SUGGESTED_REMEDY = "suggestedRemedy";
    }

    interface REPORTER {
        String KEY_OUTPUT_SCHEMA = "outputJsonSchema";
    }

    interface USER_TAGS {
        String KEY_USER_TAGS = "userTags";
    }
}
