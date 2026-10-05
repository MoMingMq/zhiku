package org.zhiku.ai.knowledge.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 连接配置（zhiku.ai.milvus.*）
 *
 * @author zhiku
 */
@Data
@ConfigurationProperties(prefix = "zhiku.ai.milvus")
public class MilvusProperties {

    /**
     * 是否启用（false 时知识库上传/检索降级提示，不崩）
     */
    private boolean enabled = false;

    /**
     * 连接地址（http://host:19530）
     */
    private String uri = "http://127.0.0.1:19530";

    /**
     * 认证 token（无认证留空）
     */
    private String token = "";

    /**
     * 数据库（default）
     */
    private String database = "default";

}
