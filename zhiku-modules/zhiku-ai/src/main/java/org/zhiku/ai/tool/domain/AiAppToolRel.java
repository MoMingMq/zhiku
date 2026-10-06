package org.zhiku.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 应用-工具挂载对象 ai_app_tool_rel
 *
 * @author zhiku
 */
@Data
@TableName("ai_app_tool_rel")
public class AiAppToolRel {

    /**
     * 关联ID
     */
    @TableId(value = "rel_id")
    private Long relId;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 工具ID
     */
    private Long toolId;

    /**
     * 创建者
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

}
