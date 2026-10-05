package org.zhiku.ai.knowledge.ingest.event;

/**
 * 文档入库事件（上传/重试/重切后发布，异步监听器执行管线）
 *
 * @param documentId 文档ID
 * @author zhiku
 */
public record DocumentIngestEvent(Long documentId) {
}
