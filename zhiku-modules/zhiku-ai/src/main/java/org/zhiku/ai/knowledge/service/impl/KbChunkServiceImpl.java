package org.zhiku.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zhiku.ai.knowledge.domain.KbChunk;
import org.zhiku.ai.knowledge.domain.vo.KbChunkVo;
import org.zhiku.ai.knowledge.mapper.KbChunkMapper;
import org.zhiku.ai.knowledge.service.IKbChunkService;
import org.zhiku.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库分块Service业务层处理
 *
 * @author zhiku
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KbChunkServiceImpl implements IKbChunkService {

    private final KbChunkMapper baseMapper;

    @Override
    public List<KbChunkVo> queryByDocumentId(Long documentId) {
        LambdaQueryWrapper<KbChunk> lqw = new LambdaQueryWrapper<>();
        lqw.eq(KbChunk::getDocumentId, documentId)
            .orderByAsc(KbChunk::getSeq);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public Boolean changeDisabled(Long chunkId, boolean disabled) {
        KbChunk chunk = baseMapper.selectById(chunkId);
        if (chunk == null) {
            throw new ServiceException("分块不存在：" + chunkId);
        }
        LambdaUpdateWrapper<KbChunk> luw = new LambdaUpdateWrapper<>();
        luw.eq(KbChunk::getChunkId, chunkId)
            .set(KbChunk::getDisabled, disabled ? "1" : "0");
        return baseMapper.update(null, luw) > 0;
    }

}
