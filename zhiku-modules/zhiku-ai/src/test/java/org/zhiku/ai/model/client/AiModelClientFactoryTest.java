package org.zhiku.ai.model.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zhiku.ai.model.domain.AiModel;
import org.zhiku.ai.model.mapper.AiModelMapper;
import org.zhiku.ai.model.provider.OpenAiCompatibleAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiModelClientFactory 单元测试（params 置空避开容器依赖的 JsonUtils 路径）
 *
 * @author zhiku
 */
@ExtendWith(MockitoExtension.class)
class AiModelClientFactoryTest {

    @Mock
    private AiModelMapper modelMapper;

    private final OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter();

    private AiModelClientFactory factory;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        factory = new AiModelClientFactory(java.util.List.of(adapter), modelMapper);
    }

    private AiModel model(String type) {
        AiModel model = new AiModel();
        model.setModelId(1L);
        model.setModelName("测试模型");
        model.setModelType(type);
        model.setProvider("openai_compatible");
        model.setBaseUrl("https://api.deepseek.com");
        model.setModelCode("deepseek-chat");
        model.setApiKey("sk-test");
        return model;
    }

    @Test
    void embeddingCachedAfterFirstBuild() {
        AiModel m = model("2");
        when(modelMapper.selectById(1L)).thenReturn(m);
        assertNotNull(factory.getEmbeddingModel(1L));
        assertNotNull(factory.getEmbeddingModel(1L));
        verify(modelMapper, times(1)).selectById(1L);
    }

    @Test
    void evictForcesRebuild() {
        AiModel m = model("1");
        when(modelMapper.selectById(1L)).thenReturn(m);
        assertNotNull(factory.getChatModel(1L));
        factory.evict(1L);
        assertNotNull(factory.getChatModel(1L));
        verify(modelMapper, times(2)).selectById(1L);
    }

    @Test
    void unsupportedProviderThrows() {
        AiModel m = model("1");
        m.setProvider("unknown_provider");
        when(modelMapper.selectById(1L)).thenReturn(m);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.getChatModel(1L));
        assertEquals("不支持的模型供应商：unknown_provider", ex.getMessage());
    }

    @Test
    void typeMismatchThrows() {
        when(modelMapper.selectById(1L)).thenReturn(model("2"));
        assertThrows(IllegalArgumentException.class, () -> factory.getChatModel(1L));
    }

    @Test
    void rerankNotSupportedByFactory() {
        when(modelMapper.selectById(1L)).thenReturn(model("3"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> factory.getChatModel(1L));
        assertEquals("Rerank 模型不走客户端工厂，请使用 RerankClient", ex.getMessage());
    }

    @Test
    void missingModelThrows() {
        when(modelMapper.selectById(9L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> factory.getChatModel(9L));
    }

}
