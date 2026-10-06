package org.zhiku.ai.tool.execute;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.zhiku.common.core.exception.ServiceException;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模板渲染器单测
 * <p>JsonUtils 静态初始化依赖 Spring 容器：@BeforeAll 向 hutool SpringUtil 注册最小 StaticApplicationContext（对齐 AiModelServiceTest 先例）。
 *
 * @author zhiku
 */
class ToolTemplateRendererTest {

    private final ToolTemplateRenderer renderer = new ToolTemplateRenderer();

    @BeforeAll
    static void initSpringUtil() {
        StaticApplicationContext context = new StaticApplicationContext();
        // SB4 走 Jackson3（tools.jackson），JsonUtils 静态块按类型取 JsonMapper
        context.getBeanFactory().registerSingleton("jsonMapper", tools.jackson.databind.json.JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @Test
    void rendersUrlQueryAndHeaders() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/v1/{{path}}",
            "[{\"key\":\"X-Api-Version\",\"value\":\"{{ver}}\"}]",
            "[{\"key\":\"city\",\"value\":\"{{city}}\"}]",
            null, null,
            Map.of("path", "weather", "ver", "2", "city", "北京"));
        assertEquals("https://api.example.com/v1/weather", result.url());
        assertEquals("https://api.example.com/v1/weather?city=%E5%8C%97%E4%BA%AC", result.urlWithQuery());
        assertEquals("2", result.headers().get("X-Api-Version"));
    }

    @Test
    void chinesePathVariableIsEncoded() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/v1/{{name}}", null, null, null, null,
            Map.of("name", "北京"));
        assertEquals("https://api.example.com/v1/%E5%8C%97%E4%BA%AC", result.url());
    }

    @Test
    void queryValueIsUrlEncoded() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/q", null,
            "[{\"key\":\"q\",\"value\":\"{{kw}}\"}]", null, null,
            Map.of("kw", "a b&c=1"));
        assertEquals("https://api.example.com/q?q=a+b%26c%3D1", result.urlWithQuery());
    }

    @Test
    void jsonBodyValuesAreJsonEscaped() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/msg", null, null,
            "{\"text\": \"{{msg}}\"}", "application/json",
            Map.of("msg", "他说\"hi\"\n换行"));
        assertEquals("{\"text\": \"他说\\\"hi\\\"\\n换行\"}", result.body());
    }

    @Test
    void renderedValueIsNotReParsedAsTemplate() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/q", null,
            "[{\"key\":\"q\",\"value\":\"{{kw}}\"}]", null, null,
            Map.of("kw", "{{injected}}"));
        // 参数值含 {{}} 时原样输出，不做二次解析
        assertEquals("https://api.example.com/q?q=%7B%7Binjected%7D%7D", result.urlWithQuery());
    }

    @Test
    void missingRequiredParamFails() {
        assertThrows(ServiceException.class, () -> renderer.render(
            "https://api.example.com/q", null,
            "[{\"key\":\"q\",\"value\":\"{{kw}}\"}]", null, null, Map.of()));
    }

    @Test
    void formBodyIsUrlEncoded() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/f", null, null,
            "a={{x}}&b=static", "application/x-www-form-urlencoded",
            Map.of("x", "1 2"));
        assertEquals("a=1+2&b=static", result.body());
    }

    @Test
    void plainBodyLeftUnescaped() {
        ToolTemplateRenderer.RenderedRequest result = renderer.render(
            "https://api.example.com/f", null, null,
            "raw {{x}}", "text/plain",
            Map.of("x", "a<b>c"));
        assertEquals("raw a<b>c", result.body());
    }
}
