package org.zhiku.system.mapper;

import org.zhiku.common.mybatis.core.mapper.BaseMapperPlus;
import org.zhiku.system.domain.SysDictData;
import org.zhiku.system.domain.vo.SysDictDataVo;

import java.util.List;

/**
 * 字典表 数据层
 *
 * @author Lion Li
 */
public interface SysDictDataMapper extends BaseMapperPlus<SysDictData, SysDictDataVo> {

    /**
     * 根据字典类型查询字典数据列表
     *
     * @param dictType 字典类型
     * @return 符合条件的字典数据列表
     */
    default List<SysDictDataVo> selectDictDataByType(String dictType) {
        return this.lambda()
            .eq(SysDictData::getDictType, dictType)
            .orderByAsc(SysDictData::getDictSort)
            .voList();
    }
}
