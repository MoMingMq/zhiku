package org.zhiku.ai.model.provider;

import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.core.utils.StringUtils;

import java.util.Map;

/**
 * ai_model.params JSON 参数解析助手（temperature/maxTokens/topP，带默认值兜底）
 *
 * @author zhiku
 */
public final class AiModelParamsHelper {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 1.0;

    private AiModelParamsHelper() {
    }

    /**
     * 解析 params JSON，非法/为空时返回空 Map（不抛错，构建侧全部走默认值）
     */
    public static Map<String, Object> parse(String params) {
        if (StringUtils.isBlank(params)) {
            return Map.of();
        }
        try {
            return JsonUtils.parseMap(params);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static double temperature(String params) {
        Object v = parse(params).get("temperature");
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TEMPERATURE;
    }

    public static int maxTokens(String params) {
        Object v = parse(params).get("maxTokens");
        return v instanceof Number n ? n.intValue() : DEFAULT_MAX_TOKENS;
    }

    public static double topP(String params) {
        Object v = parse(params).get("topP");
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TOP_P;
    }

}
