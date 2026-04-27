package ink.icoding.dianxin.chat2response.mapper;

import ink.icoding.dianxin.chat2response.entity.ConversionProject;
import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 转换项目数据访问层。
 */
@Mapper
public interface ConversionProjectMapper extends SmartMapper<ConversionProject> {

    /**
     * 按转换后模型名查询项目。
     *
     * @param targetModel 转换后模型名
     * @return 项目实体
     */
    default ConversionProject findByTargetModel(String targetModel) {
        return this.selectOne(Where.where(ConversionProject::getTargetModel).eq(targetModel));
    }

    /**
     * 按项目 API Key 和转换后模型查询项目。
     *
     * @param apiKey      API Key
     * @param targetModel 转换后模型名
     * @return 项目实体
     */
    default ConversionProject findByApiKeyAndTargetModel(String apiKey, String targetModel) {
        return this.selectOne(
            Where.where(ConversionProject::getApiKey).eq(apiKey)
                .and(ConversionProject::getTargetModel).eq(targetModel)
        );
    }
}
