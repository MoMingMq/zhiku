package org.zhiku.ai.model.provider;

import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.core.utils.StringUtils;

import java.util.Map;

/**
 * ai_model.params 参数 Map 助手（对标 业界方案 模型参数机制：任意 key-value，应用侧按值类型动态生成控件）
 * <p>已知参数（temperature/maxTokens/topP）映射为 OpenAIChatOptions 具名字段；
 * 其余自定义参数（如 enable_search、response_format）经 extraBody 透传给 OpenAI 兼容端点。
 *
 * @author zhiku
 */
public final class AiModelParamsHelper {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 1.0;

    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_TOKENS = "maxTokens";
    private static final String KEY_TOP_P = "topP";

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
        Object v = parse(params).get(KEY_TEMPERATURE);
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TEMPERATURE;
    }

    public static int maxTokens(String params) {
        Object v = parse(params).get(KEY_MAX_TOKENS);
        return v instanceof Number n ? n.intValue() : DEFAULT_MAX_TOKENS;
    }

    public static double topP(String params) {
        Object v = parse(params).get(KEY_TOP_P);
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TOP_P;
    }

    /**
     * 自定义参数（排除已知三项），作为 extraBody 透传；无自定义参数返回 null（不设置）
     */
    public static Map<String, Object> extraBody(String params) {
        Map<String, Object> all = parse(params);
        if (all.isEmpty()) {
            return null;
        }
        Map<String, Object> extra = new java.util.LinkedHashMap<>(all);
        extra.remove(KEY_TEMPERATURE);
        extra.remove(KEY_MAX_TOKENS);
        extra.remove(KEY_TOP_P);
        return extra.isEmpty() ? null : extra;
    }

}
