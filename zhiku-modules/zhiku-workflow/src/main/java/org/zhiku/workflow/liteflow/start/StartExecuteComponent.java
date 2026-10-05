package org.zhiku.workflow.liteflow.start;

import cn.hutool.core.collection.CollUtil;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import org.zhiku.common.core.enums.BusinessStatusEnum;
import org.zhiku.common.core.exception.ServiceException;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.zhiku.workflow.api.domain.StartProcessReturnDTO;
import org.zhiku.workflow.common.ConditionalOnEnable;
import org.zhiku.workflow.domain.FlowInstanceBizExt;
import org.zhiku.workflow.domain.context.StartProcessContext;
import org.zhiku.workflow.mapper.FlwInstanceBizExtMapper;

import java.util.List;

/**
 * 启动流程实例并构建返回结果。
 *
 * @author may
 */
@ConditionalOnEnable
@RequiredArgsConstructor
@LiteflowComponent("startExecute")
public class StartExecuteComponent extends NodeComponent {

    private final InsService insService;
    private final TaskService taskService;
    private final FlwInstanceBizExtMapper flwInstanceBizExtMapper;

    @Override
    public void process() {
        StartProcessContext context = getContextBean(StartProcessContext.class);
        FlowParams flowParams = FlowParams.build()
            .handler(context.getStartProcessBo().getHandler())
            .flowCode(context.getStartProcessBo().getFlowCode())
            .variable(context.getVariables())
            .flowStatus(BusinessStatusEnum.DRAFT.getStatus());
        Instance instance = insService.start(context.getBusinessId(), flowParams);
        context.setInstance(instance);
        saveBizExt(context, instance);

        List<Task> taskList = taskService.list(new FlowTask().setInstanceId(instance.getId()));
        if (CollUtil.isEmpty(taskList)) {
            throw new ServiceException("流程启动失败，未生成任务");
        }
        if (taskList.size() > 1) {
            throw new ServiceException("请检查流程第一个环节是否为申请人！");
        }
        context.setTaskList(taskList);
        context.setStartProcessReturn(new StartProcessReturnDTO(instance.getId(), taskList.getFirst().getId()));
    }

    private void saveBizExt(StartProcessContext context, Instance instance) {
        FlowInstanceBizExt bizExt = context.getBizExt();
        bizExt.setInstanceId(instance.getId());
        bizExt.setBusinessId(instance.getBusinessId());
        flwInstanceBizExtMapper.saveOrUpdateByInstanceId(bizExt);
    }

}
