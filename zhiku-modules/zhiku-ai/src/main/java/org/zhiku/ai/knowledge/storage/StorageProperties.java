package org.zhiku.ai.knowledge.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档存储配置
 *
 * @author zhiku
 */
@Data
@ConfigurationProperties(prefix = "zhiku.ai.storage")
public class StorageProperties {

    /**
     * 文档存储根目录（docker 挂卷点）
     */
    private String basePath = "./data/kb-files";

}
