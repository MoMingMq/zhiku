package org.zhiku.ai.model.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 模型参数定义（对标 业界方案 参数机制：Map 承载 + 渲染组件元数据）
 * <p>应用侧按 inputType 渲染对应调整控件：input 文本 / number 数字 / slider 滑块(min/max/step) /
 * switch 开关 / select 下拉(options)；value 为该参数默认值，应用侧可覆盖。
 *
 * @author zhiku
 */
@Data
public class ModelParamDef implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 参数名（透传给模型 API 的键，如 temperature / max_tokens / enable_search）
     */
    private String name;

    /**
     * 显示名（应用侧参数控件 label，缺省用 name）
     */
    private String label;

    /**
     * 默认值（类型由 inputType 决定：number/slider→数值、switch→布尔、input/select→字符串）
     */
    private Object value;

    /**
     * 渲染组件类型：input | number | slider | switch | select
     */
    private String inputType;

    /**
     * slider/number 最小值
     */
    private Double min;

    /**
     * slider/number 最大值
     */
    private Double max;

    /**
     * slider 步长
     */
    private Double step;

    /**
     * select 选项列表
     */
    private List<String> options;

}
