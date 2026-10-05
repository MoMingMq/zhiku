package org.zhiku.ai.knowledge.storage;

import java.io.InputStream;

/**
 * 文档原始文件存储抽象（D1：本地磁盘实现起步，未来可加 OSS 实现不动业务）
 *
 * @author zhiku
 */
public interface StorageService {

    /**
     * 存储上传文件
     *
     * @return 相对存储 key（{datasetId}/{documentId}.{ext}）
     */
    String store(Long datasetId, Long documentId, String originalName, InputStream in);

    /**
     * 读取文件流
     */
    InputStream read(String storageKey);

    /**
     * 删除文件（key 不存在不报错）
     */
    void delete(String storageKey);

}
