package org.zhiku.ai.app.mapper;

import org.zhiku.ai.app.domain.AiMessage;
import org.zhiku.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 对话消息 Mapper 接口（第二泛型用实体自身，Vo 由服务层手动组装）
 *
 * @author zhiku
 */
public interface AiMessageMapper extends BaseMapperPlus<AiMessage, AiMessage> {

}
