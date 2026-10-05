package org.zhiku.ai.knowledge.config;

import io.milvus.v2.client.MilvusClientV2;
import org.zhiku.ai.knowledge.vector.KbVectorStore;
import org.zhiku.ai.knowledge.vector.MilvusKbVectorStore;
import org.zhiku.ai.knowledge.vector.MilvusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量层条件装配（zhiku.ai.milvus.enabled=true 才装配；false 时知识库功能降级提示）
 *
 * @author zhiku
 */
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
@ConditionalOnProperty(prefix = "zhiku.ai.milvus", name = "enabled", havingValue = "true")
public class AiVectorAutoConfiguration {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClientV2(MilvusProperties properties) {
        return MilvusKbVectorStore.createClient(properties);
    }

    @Bean
    public KbVectorStore kbVectorStore(MilvusClientV2 client, MilvusProperties properties) {
        return new MilvusKbVectorStore(client, properties.getDatabase());
    }

}
