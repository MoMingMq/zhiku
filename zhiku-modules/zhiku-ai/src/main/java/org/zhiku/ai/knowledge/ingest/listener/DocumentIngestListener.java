package org.zhiku.ai.knowledge.ingest.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.ingest.DocumentIngestPipeline;
import org.zhiku.ai.knowledge.ingest.event.DocumentIngestEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文档入库事件监听器（专用线程池异步执行管线；向量服务不可用时管线置文档失败态）
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DocumentIngestListener {

    private final DocumentIngestPipeline pipeline;

    @Async("ingestExecutor")
    @EventListener
    public void onEvent(DocumentIngestEvent event) {
        pipeline.ingest(event.documentId());
    }

}
