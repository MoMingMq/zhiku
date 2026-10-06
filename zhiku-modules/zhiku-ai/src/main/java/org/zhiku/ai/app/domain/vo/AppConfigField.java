package org.zhiku.ai.app.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对话类型专属配置字段定义（前端设置页按 component 动态渲染）
 *
 * @author zhiku
 */
@Data
public class AppConfigField implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段名（config JSON 键）
     */
    private String name;

    /**
     * 显示名
     */
    private String label;

    /**
     * 渲染组件键：kb-multi / tool-multi / input / number / switch / select
     */
    private String component;

    /**
     * 默认值
     */
    private Object defaultValue;

    /**
     * number 范围
     */
    private Integer min;

    private Integer max;

    /**
     * select 选项
     */
    private List<String> options;

    /**
     * 是否必填
     */
    private boolean required;

    /**
     * 字段说明
     */
    private String description;

    public AppConfigField() {
    }

    public AppConfigField(String name, String label, String component, Object defaultValue, boolean required, String description) {
        this.name = name;
        this.label = label;
        this.component = component;
        this.defaultValue = defaultValue;
        this.required = required;
        this.description = description;
    }

    public static AppConfigField number(String name, String label, Integer defaultValue, Integer min, Integer max, String description) {
        AppConfigField f = new AppConfigField(name, label, "number", defaultValue, false, description);
        f.min = min;
        f.max = max;
        return f;
    }

    public static AppConfigField switchField(String name, String label, boolean defaultValue, String description) {
        return new AppConfigField(name, label, "switch", defaultValue, false, description);
    }

    public static AppConfigField multi(String name, String label, String component, boolean required, String description) {
        return new AppConfigField(name, label, component, null, required, description);
    }
}
