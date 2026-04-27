package ink.icoding.dianxin.chat2response.vo;

import ink.icoding.dianxin.chat2response.dto.ConversionProjectCreateDTO;
import ink.icoding.dianxin.chat2response.entity.ConversionProject;

/**
 * 转换项目展示实体。
 */
public class ConversionProjectVO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 上游服务基础地址。
     */
    private String baseUrl;

    /**
     * 掩码后的 API Key。
     */
    private String apiKey;

    /**
     * 上游模型名。
     */
    private String sourceModel;

    /**
     * 自动生成的转换后模型名。
     */
    private String targetModel;

    /**
     * 是否启用。
     */
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

    /**
     * 从数据库实体转换为展示实体。
     *
     * @param project 数据库实体
     * @return 展示实体
     */
    public static ConversionProjectVO fromDatabaseModel(ConversionProject project) {
        ConversionProjectVO vo = new ConversionProjectVO();
        vo.setId(project.getId());
        vo.setBaseUrl(project.getBaseUrl());
//        vo.setApiKey(mask(project.getApiKey()));
        vo.setApiKey(project.getApiKey());
        vo.setSourceModel(project.getSourceModel());
        vo.setTargetModel(project.getTargetModel());
        vo.setEnabled(project.getEnabled());
        return vo;
    }

    /**
     * 转换为数据传输实体。
     *
     * @return 数据传输实体
     */
    public ConversionProjectCreateDTO toDataTransferModel() {
        ConversionProjectCreateDTO dto = new ConversionProjectCreateDTO();
        dto.setBaseUrl(this.baseUrl);
        dto.setApiKey(this.apiKey);
        dto.setSourceModel(this.sourceModel);
        return dto;
    }

    private static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return apiKey;
        }
        int visible = Math.min(4, apiKey.length());
        return "*".repeat(Math.max(0, apiKey.length() - visible)) + apiKey.substring(apiKey.length() - visible);
    }
}
