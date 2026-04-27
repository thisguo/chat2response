package ink.icoding.dianxin.chat2response.dto;

import ink.icoding.dianxin.chat2response.entity.ConversionProject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建转换项目请求体。
 */
public class ConversionProjectCreateDTO {

    /**
     * 上游 Chat Completions 服务基础地址, 例如 https://api.openai.com。
     */
    @NotBlank(message = "baseUrl不能为空")
    @Size(max = 500, message = "baseUrl长度不能超过500")
    private String baseUrl;

    /**
     * 上游 API Key。
     */
    @NotBlank(message = "apiKey不能为空")
    @Size(max = 512, message = "apiKey长度不能超过512")
    private String apiKey;

    /**
     * 上游 Chat Completions 模型名。
     */
    @NotBlank(message = "sourceModel不能为空")
    @Size(max = 255, message = "sourceModel长度不能超过255")
    private String sourceModel;

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

    /**
     * 将 DTO 转换为数据库实体。
     *
     * @return 数据库实体
     */
    public ConversionProject toDatabaseModel() {
        ConversionProject project = new ConversionProject();
        project.setBaseUrl(this.baseUrl == null ? null : this.baseUrl.trim());
        project.setApiKey(this.apiKey == null ? null : this.apiKey.trim());
        project.setSourceModel(this.sourceModel == null ? null : this.sourceModel.trim());
        return project;
    }

    /**
     * 从数据库实体转换为 DTO。
     *
     * @param project 数据库实体
     * @return DTO
     */
    public static ConversionProjectCreateDTO fromDatabaseModel(ConversionProject project) {
        ConversionProjectCreateDTO dto = new ConversionProjectCreateDTO();
        dto.setBaseUrl(project.getBaseUrl());
        dto.setApiKey(project.getApiKey());
        dto.setSourceModel(project.getSourceModel());
        return dto;
    }
}
