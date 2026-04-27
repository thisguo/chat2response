package ink.icoding.dianxin.chat2response.entity;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;

/**
 * 转换项目数据库实体, 用于存储 Chat Completions 到 Responses 的转换配置。
 */
@TableName(value = "conversion_project", description = "转换项目配置表")
public class ConversionProject extends PO {

    /**
     * 主键ID。
     */
    @ID
    @TableField(value = "id", description = "主键ID")
    private Long id;

    /**
     * 上游 Chat Completions 服务基础地址。
     */
    @TableField(value = "base_url", description = "上游服务基础地址", length = 500)
    private String baseUrl;

    /**
     * 上游服务 API Key, 同时作为本网关访问此转换模型的凭证。
     */
    @TableField(value = "api_key", description = "项目API密钥", length = 512)
    private String apiKey;

    /**
     * 上游 Chat Completions 使用的模型名。
     */
    @TableField(value = "source_model", description = "源模型名", length = 255)
    private String sourceModel;

    /**
     * 网关自动生成的 Responses 风格模型名, 全局唯一。
     */
    @TableField(value = "target_model", description = "转换后模型名(唯一)", length = 255)
    private String targetModel;

    /**
     * 项目是否启用, true 表示可被网关调用。
     */
    @TableField(value = "enabled", description = "是否启用")
    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSourceModel() {
        return sourceModel;
    }

    public void setSourceModel(String sourceModel) {
        this.sourceModel = sourceModel;
    }

    public String getTargetModel() {
        return targetModel;
    }

    public void setTargetModel(String targetModel) {
        this.targetModel = targetModel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
