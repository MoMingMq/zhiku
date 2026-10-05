package org.zhiku.ai.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档入库专用线程池（解析/分块/向量化为 CPU+IO 混合任务，独立池防拖垮全局）
 *
 * @author zhiku
 */
@Configuration
public class IngestExecutorConfig {

    @Bean("ingestExecutor")
    public ThreadPoolTaskExecutor ingestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingest-");
        // 队列满时由调用线程执行（上传接口线程），保证不丢任务
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
