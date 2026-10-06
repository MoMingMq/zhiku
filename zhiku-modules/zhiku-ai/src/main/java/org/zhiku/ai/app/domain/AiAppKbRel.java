package org.zhiku.ai.app.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 应用-知识库关联对象 ai_app_kb_rel
 *
 * @author zhiku
 */
@Data
@TableName("ai_app_kb_rel")
public class AiAppKbRel {

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
     * 知识库ID
     */
    private Long datasetId;

    /**
     * 创建者
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

}
