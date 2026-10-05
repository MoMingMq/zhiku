package org.zhiku.ai.knowledge.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zhiku.common.core.exception.ServiceException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalStorageService 单元测试（key 结构 / 路径穿越防护 / 幂等删除）
 *
 * @author zhiku
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBasePath(tempDir.toString());
        storage = new LocalStorageService(properties);
    }

    @Test
    void storeWritesFileAndReturnsKey() throws IOException {
        byte[] content = "hello kb".getBytes();
        String key = storage.store(1L, 100L, "手册.pdf", new ByteArrayInputStream(content));
        assertEquals("1/100.pdf", key);
        assertArrayEquals(content, Files.readAllBytes(tempDir.resolve(Paths.get("1/100.pdf"))));
    }

    @Test
    void storeWithoutExtUsesBin() {
        String key = storage.store(1L, 101L, "README", new ByteArrayInputStream("x".getBytes()));
        assertEquals("1/101.bin", key);
    }

    @Test
    void readMissingThrows() {
        ServiceException ex = assertThrows(ServiceException.class, () -> storage.read("1/999.pdf"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void pathTraversalRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> storage.read("../etc/passwd"));
        assertEquals("非法存储路径", ex.getMessage());
        assertThrows(ServiceException.class, () -> storage.delete("../../windows/system32"));
    }

    @Test
    void deleteMissingKeyIsSilent() {
        storage.delete("1/not-exists.pdf");
        assertFalse(Files.exists(tempDir.resolve(Paths.get("1/not-exists.pdf"))));
    }

    @Test
    void deleteRemovesStoredFile() {
        String key = storage.store(1L, 102L, "a.md", new ByteArrayInputStream("abc".getBytes()));
        storage.delete(key);
        assertFalse(Files.exists(tempDir.resolve(Paths.get(key))));
        assertTrue(Files.notExists(tempDir.resolve(Paths.get(key))));
    }

}
