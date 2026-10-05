package org.zhiku.ai.model.domain.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型连通性测试结果
 *
 * @author zhiku
 */
public record ModelTestResult(boolean success, long costMs, String message) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

}
