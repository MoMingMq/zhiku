package org.zhiku.ai.model.provider;

import org.zhiku.ai.model.domain.vo.ModelParamDef;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.json.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ai_model.params 参数助手（对标 业界方案：参数定义含渲染组件元数据，value 为默认值）
 * <p>存储结构：ModelParamDef 列表 JSON（name/label/value/inputType/min/max/step/options）。
 * 已知采样参数映射 OpenAIChatOptions 具名字段；其余自定义参数经 extraBody 透传。
 *
 * @author zhiku
 */
public final class AiModelParamsHelper {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 1.0;

    /** 已知采样参数（具名字段映射，camelCase 与 OpenAI 原生 snake_case 均识别） */
    private static final List<String> KNOWN_KEYS = List.of(
        "temperature", "maxTokens", "max_tokens", "topP", "top_p");

    private AiModelParamsHelper() {
    }

    /**
     * 解析参数定义列表；params 为空/非法时返回空列表（构建侧全部走默认值）
     */
    public static List<ModelParamDef> parseDefs(String params) {
        if (StringUtils.isBlank(params)) {
            return List.of();
        }
        try {
            List<ModelParamDef> defs = JsonUtils.parseArray(params, ModelParamDef.class);
            return defs == null ? List.of() : defs;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 参数名 → 默认值 的请求 Map（供应用侧展示与构建侧取值）
     */
    public static Map<String, Object> toValueMap(String params) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ModelParamDef def : parseDefs(params)) {
            if (StringUtils.isNotBlank(def.getName())) {
                values.put(def.getName().trim(), def.getValue());
            }
        }
        return values;
    }

    public static double temperature(String params) {
        Object v = firstValue(params, "temperature");
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TEMPERATURE;
    }

    public static int maxTokens(String params) {
        Object v = firstValue(params, "maxTokens", "max_tokens");
        return v instanceof Number n ? n.intValue() : DEFAULT_MAX_TOKENS;
    }

    public static double topP(String params) {
        Object v = firstValue(params, "topP", "top_p");
        return v instanceof Number n ? n.doubleValue() : DEFAULT_TOP_P;
    }

    /**
     * 自定义参数（排除已知采样参数），作为 extraBody 透传；无自定义参数返回 null（不设置）
     */
    public static Map<String, Object> extraBody(String params) {
        Map<String, Object> all = toValueMap(params);
        if (all.isEmpty()) {
            return null;
        }
        Map<String, Object> extra = new LinkedHashMap<>(all);
        KNOWN_KEYS.forEach(extra::remove);
        return extra.isEmpty() ? null : extra;
    }

    private static Object firstValue(String params, String... keys) {
        Map<String, Object> values = toValueMap(params);
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    /**
     * 结构校验（保存前）：name 非空不重复；slider 必须有 min/max；select 必须有 options
     */
    public static List<String> validate(List<ModelParamDef> defs) {
        List<String> errors = new ArrayList<>();
        if (defs == null) {
            return errors;
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < defs.size(); i++) {
            ModelParamDef def = defs.get(i);
            String at = "第 " + (i + 1) + " 个参数";
            if (def == null || StringUtils.isBlank(def.getName())) {
                errors.add(at + "：参数名不能为空");
                continue;
            }
            String name = def.getName().trim();
            if (seen.containsKey(name)) {
                errors.add(at + "：参数名重复 " + name);
            }
            seen.put(name, true);
            if (StringUtils.isBlank(def.getInputType())) {
                errors.add(at + "：渲染组件类型不能为空");
            } else {
                String type = def.getInputType();
                if ("slider".equals(type) && (def.getMin() == null || def.getMax() == null)) {
                    errors.add(at + "：滑块组件必须配置最小值/最大值");
                }
                if ("select".equals(type) && (def.getOptions() == null || def.getOptions().isEmpty())) {
                    errors.add(at + "：下拉组件必须配置选项");
                }
            }
        }
        return errors;
    }

}
