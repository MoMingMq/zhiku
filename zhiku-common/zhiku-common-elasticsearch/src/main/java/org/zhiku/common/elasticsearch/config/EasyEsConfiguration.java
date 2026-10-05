package org.zhiku.common.elasticsearch.config;

import org.dromara.easyes.spring.annotation.EsMapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * easy-es 配置
 *
 * @author Lion Li
 */
@AutoConfiguration
@ConditionalOnProperty(value = "easy-es.enable", havingValue = "true")
@EsMapperScan("org.zhiku.**.esmapper")
public class EasyEsConfiguration {

}
