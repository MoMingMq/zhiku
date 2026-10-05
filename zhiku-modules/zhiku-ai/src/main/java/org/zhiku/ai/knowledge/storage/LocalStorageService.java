package org.zhiku.ai.knowledge.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现（key 结构：{datasetId}/{documentId}.{ext}，resolve 后强制落在 basePath 内防路径穿越）
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Override
    public String store(Long datasetId, Long documentId, String originalName, InputStream in) {
        String ext = StringUtils.substringAfterLast(originalName, ".");
        if (StringUtils.isBlank(ext)) {
            ext = "bin";
        }
        String key = datasetId + "/" + documentId + "." + ext.toLowerCase();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("文档存储失败：" + e.getMessage());
        }
        return key;
    }

    @Override
    public InputStream read(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
            throw new ServiceException("文档文件不存在：" + storageKey);
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new ServiceException("文档读取失败：" + e.getMessage());
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("文档文件删除失败 key={}", storageKey, e);
        }
    }

    /**
     * 解析并校验 key：normalize 后必须仍位于 basePath 内，越界（路径穿越）抛错
     */
    private Path resolve(String key) {
        Path base = Paths.get(properties.getBasePath()).normalize();
        Path resolved = base.resolve(key).normalize();
        if (!resolved.startsWith(base)) {
            throw new ServiceException("非法存储路径");
        }
        return resolved;
    }

}
