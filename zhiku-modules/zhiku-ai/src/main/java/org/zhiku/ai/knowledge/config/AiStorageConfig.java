package org.zhiku.ai.knowledge.config;

import org.zhiku.ai.knowledge.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库域配置（存储属性启用）
 *
 * @author zhiku
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class AiStorageConfig {

}
