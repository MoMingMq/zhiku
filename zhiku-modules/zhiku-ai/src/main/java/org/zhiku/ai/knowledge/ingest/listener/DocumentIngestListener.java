package org.zhiku.ai.knowledge.ingest.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.ingest.DocumentIngestPipeline;
import org.zhiku.ai.knowledge.ingest.event.DocumentIngestEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文档入库事件监听器（专用线程池异步执行管线；Milvus 未启用时不装配）
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(prefix = "zhiku.ai.milvus", name = "enabled", havingValue = "true")
public class DocumentIngestListener {

    private final DocumentIngestPipeline pipeline;

    @Async("ingestExecutor")
    @EventListener
    public void onEvent(DocumentIngestEvent event) {
        pipeline.ingest(event.documentId());
    }

}
