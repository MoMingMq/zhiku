package org.zhiku.ai.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.tool.domain.AiAppToolRel;
import org.zhiku.ai.tool.domain.AiTool;
import org.zhiku.ai.tool.domain.bo.AiToolBo;
import org.zhiku.ai.tool.domain.vo.AiToolVo;
import org.zhiku.ai.tool.execute.AiToolCallbackFactory;
import org.zhiku.ai.tool.execute.ToolCallContext;
import org.zhiku.ai.tool.execute.ToolExecResult;
import org.zhiku.ai.tool.execute.ToolExecutor;
import org.zhiku.ai.tool.mapper.AiAppToolRelMapper;
import org.zhiku.ai.tool.mapper.AiToolMapper;
import org.zhiku.ai.tool.service.IAiToolService;
import org.zhiku.common.core.domain.PageResult;
import org.zhiku.common.core.exception.ServiceException;
import org.zhiku.common.core.utils.MapstructUtils;
import org.zhiku.common.core.utils.StringUtils;
import org.zhiku.common.json.utils.JsonUtils;
import org.zhiku.common.mybatis.core.page.PageQuery;
import org.zhiku.common.satoken.utils.LoginHelper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工具管理 Service 实现
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiToolServiceImpl implements IAiToolService {

    /**
     * function calling 工具名约束：字母开头，字母数字下划线连字符，总长 1-64
     */
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$");

    /**
     * 支持的请求方式
     */
    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    private final AiToolMapper baseMapper;
    private final AiAppToolRelMapper relMapper;
    private final ToolExecutor toolExecutor;
    private final AiToolCallbackFactory callbackFactory;

    @Override
    public PageResult<AiToolVo> queryPageList(AiToolBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiTool> lqw = buildQueryWrapper(bo);
        Page<AiToolVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        result.getRecords().forEach(this::maskAuth);
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    @Override
    public AiToolVo queryById(Long toolId) {
        AiToolVo vo = baseMapper.selectVoById(toolId);
        if (vo != null) {
            maskAuth(vo);
        }
        return vo;
    }

    @Override
    public Boolean insertByBo(AiToolBo bo) {
        validateTool(bo, null);
        AiTool add = toEntity(bo);
        return baseMapper.insert(add) > 0;
    }

    @Override
    public Boolean updateByBo(AiToolBo bo) {
        validateTool(bo, bo.getToolId());
        AiTool update = toEntity(bo);
        // 鉴权凭据留空表示不修改（MyBatis-Plus updateById 跳过 null 字段）
        if (StringUtils.isBlank(bo.getAuthValue())) {
            update.setAuthValue(null);
        }
        return baseMapper.updateById(update) > 0;
    }

    /**
     * Bo 转 Entity（protected 钩子：单测环境无 Spring 容器，MapstructUtils 不可用时可覆写）
     */
    protected AiTool toEntity(AiToolBo bo) {
        return MapstructUtils.convert(bo, AiTool.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return false;
        }
        // 级联删除挂载关系（物理删，rel 无逻辑删除）
        relMapper.delete(new LambdaQueryWrapper<AiAppToolRel>().in(AiAppToolRel::getToolId, toolIds));
        return baseMapper.deleteByIds(toolIds) > 0;
    }

    @Override
    public ToolExecResult testRun(Long toolId, Map<String, Object> params) {
        AiTool tool = requireTool(toolId);
        if (!"0".equals(tool.getStatus())) {
            throw new ServiceException("工具已停用，无法试运行");
        }
        return toolExecutor.execute(tool, params == null ? Map.of() : params, ToolCallContext.testRun(LoginHelper.getUserId()));
    }

    @Override
    public List<AiToolVo> listToolsByApp(Long appId) {
        List<Long> toolIds = relToolIds(appId);
        if (toolIds.isEmpty()) {
            return List.of();
        }
        List<AiToolVo> vos = baseMapper.selectVoList(
            new LambdaQueryWrapper<AiTool>().in(AiTool::getToolId, toolIds).orderByDesc(AiTool::getToolId));
        vos.forEach(this::maskAuth);
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean bindTools(Long appId, List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return false;
        }
        Set<Long> existing = relMapper.selectList(
                new LambdaQueryWrapper<AiAppToolRel>().eq(AiAppToolRel::getAppId, appId))
            .stream().map(AiAppToolRel::getToolId).collect(Collectors.toSet());
        Long operator = LoginHelper.getUserId();
        List<AiAppToolRel> toInsert = new ArrayList<>();
        for (Long toolId : toolIds) {
            if (toolId == null || existing.contains(toolId)) {
                continue;
            }
            AiAppToolRel rel = new AiAppToolRel();
            rel.setRelId(IdWorker.getId());
            rel.setAppId(appId);
            rel.setToolId(toolId);
            rel.setCreateBy(operator);
            rel.setCreateTime(new java.util.Date());
            toInsert.add(rel);
        }
        return toInsert.isEmpty() || relMapper.insertBatch(toInsert);
    }

    @Override
    public Boolean unbindTools(Long appId, List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return false;
        }
        return relMapper.delete(new LambdaQueryWrapper<AiAppToolRel>()
            .eq(AiAppToolRel::getAppId, appId)
            .in(AiAppToolRel::getToolId, toolIds)) > 0;
    }

    @Override
    public List<ToolCallback> listCallbacksByApp(Long appId) {
        List<Long> toolIds = relToolIds(appId);
        if (toolIds.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<AiTool>()
                .in(AiTool::getToolId, toolIds)
                .eq(AiTool::getStatus, "0")
                .orderByDesc(AiTool::getToolId))
            .stream().map(callbackFactory::build).collect(Collectors.toList());
    }

    private List<Long> relToolIds(Long appId) {
        return relMapper.selectList(new LambdaQueryWrapper<AiAppToolRel>().eq(AiAppToolRel::getAppId, appId))
            .stream().map(AiAppToolRel::getToolId).collect(Collectors.toList());
    }

    private AiTool requireTool(Long toolId) {
        AiTool tool = baseMapper.selectById(toolId);
        if (tool == null) {
            throw new ServiceException("工具不存在：" + toolId);
        }
        return tool;
    }

    /**
     * 构造查询条件
     */
    private LambdaQueryWrapper<AiTool> buildQueryWrapper(AiToolBo bo) {
        LambdaQueryWrapper<AiTool> lqw = new LambdaQueryWrapper<>();
        lqw.like(StringUtils.isNotBlank(bo.getToolName()), AiTool::getToolName, bo.getToolName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), AiTool::getStatus, bo.getStatus());
        lqw.orderByDesc(AiTool::getToolId);
        return lqw;
    }

    /**
     * 保存前业务校验：工具名唯一+合法、请求方式、URL 协议与 host 禁占位、Schema 根 object、超时上限、自定义鉴权头必填
     */
    private void validateTool(AiToolBo bo, Long excludeId) {
        String name = bo.getToolName();
        if (name == null || !TOOL_NAME_PATTERN.matcher(name).matches()) {
            throw new ServiceException("工具名不合法（字母开头，可含数字、下划线、连字符，最长64字符）");
        }
        LambdaQueryWrapper<AiTool> dup = new LambdaQueryWrapper<AiTool>().eq(AiTool::getToolName, name);
        if (excludeId != null) {
            dup.ne(AiTool::getToolId, excludeId);
        }
        if (baseMapper.selectCount(dup) > 0) {
            throw new ServiceException("工具名已存在: " + name);
        }
        String method = bo.getMethod() == null ? "" : bo.getMethod().toUpperCase();
        if (!SUPPORTED_METHODS.contains(method)) {
            throw new ServiceException("请求方式仅支持 GET/POST/PUT/DELETE");
        }
        bo.setMethod(method);
        String url = bo.getUrlTemplate() == null ? "" : bo.getUrlTemplate().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new ServiceException("URL 必须以 http:// 或 https:// 开头");
        }
        // authority 段（scheme 后至首个 / ? #）禁止 {{ 参数占位：host 由管理员写死，SSRF 攻击面从根收窄
        String authority = url.replaceFirst("^https?://", "").split("/|\\?|#")[0];
        if (authority.contains("{{")) {
            throw new ServiceException("URL 的主机部分不允许使用参数占位（{{}} 仅可出现在路径/查询/请求体）");
        }
        if (StringUtils.isNotBlank(bo.getParamSchema()) && !JsonUtils.isJsonObject(bo.getParamSchema())) {
            throw new ServiceException("入参 Schema 必须是 JSON object 根（Function Calling parameters 规范）");
        }
        if (bo.getTimeoutSeconds() != null && (bo.getTimeoutSeconds() < 1 || bo.getTimeoutSeconds() > 60)) {
            throw new ServiceException("超时秒数必须在 1-60 之间");
        }
        if ("2".equals(bo.getAuthType()) && StringUtils.isBlank(bo.getAuthKey())) {
            throw new ServiceException("自定义头鉴权必须填写头名称");
        }
    }

    /**
     * 鉴权凭据掩码：任何出口不回显明文
     */
    private void maskAuth(AiToolVo vo) {
        if (StringUtils.isBlank(vo.getAuthValue())) {
            vo.setAuthValue("");
            vo.setAuthValueSet(false);
        } else {
            vo.setAuthValue("***");
            vo.setAuthValueSet(true);
        }
    }

}
