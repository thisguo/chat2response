package ink.icoding.dianxin.chat2response.service;

import ink.icoding.dianxin.chat2response.dto.ConversionProjectCreateDTO;
import ink.icoding.dianxin.chat2response.entity.ConversionProject;
import ink.icoding.dianxin.chat2response.mapper.ConversionProjectMapper;
import ink.icoding.smartmybatis.entity.expression.Where;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 转换项目管理服务。
 */
@Service
public class ConversionProjectService {

    private final ConversionProjectMapper projectMapper;

    public ConversionProjectService(ConversionProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    /**
     * 新增转换项目并自动生成唯一转换模型。
     *
     * @param dto 新增请求
     * @return 新增后的项目
     */
    public synchronized ConversionProject create(ConversionProjectCreateDTO dto) {
        ConversionProject project = dto.toDatabaseModel();
        project.setBaseUrl(normalizeBaseUrl(project.getBaseUrl()));
        project.setTargetModel(generateUniqueTargetModel(project.getSourceModel()));
        project.setEnabled(Boolean.TRUE);
        projectMapper.insert(project);
        return project;
    }

    /**
     * 查询全部项目。
     *
     * @return 项目列表
     */
    public List<ConversionProject> listAll() {
        return projectMapper.selectAll();
    }

    /**
     * 按ID查询项目。
     *
     * @param id 项目ID
     * @return 项目
     */
    public ConversionProject getById(Long id) {
        ConversionProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + id);
        }
        return project;
    }

    /**
     * 启停项目。
     *
     * @param id      项目ID
     * @param enabled 启用状态
     * @return 更新后的项目
     */
    public ConversionProject switchEnabled(Long id, boolean enabled) {
        ConversionProject project = getById(id);
        project.setEnabled(enabled);
        projectMapper.updateById(project);
        return getById(id);
    }

    /**
     * 删除项目。
     *
     * @param id 项目ID
     */
    public void delete(Long id) {
        int rows = projectMapper.deleteById(id);
        if (rows == 0) {
            throw new IllegalArgumentException("项目不存在: " + id);
        }
    }

    /**
     * 根据项目 API Key 与转换模型加载项目。
     *
     * @param apiKey      API Key
     * @param targetModel 转换模型
     * @return 项目
     */
    public ConversionProject getEnabledProjectByApiKeyAndModel(String apiKey, String targetModel) {
        ConversionProject project = projectMapper.findByApiKeyAndTargetModel(apiKey, targetModel);
        if (project == null) {
            throw new IllegalArgumentException("无效的API Key或模型");
        }
        if (!Boolean.TRUE.equals(project.getEnabled())) {
            throw new IllegalStateException("项目已停用");
        }
        return project;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String generateUniqueTargetModel(String sourceModel) {
        String base = "rsp-" + safeModelSegment(sourceModel);
        for (int i = 0; i < 20; i++) {
            String candidate = base + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            long count = projectMapper.count(Where.where(ConversionProject::getTargetModel).eq(candidate));
            if (count == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("生成唯一模型失败, 请重试");
    }

    private String safeModelSegment(String sourceModel) {
        String cleaned = sourceModel == null ? "model" : sourceModel.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (cleaned.isBlank()) {
            return "model";
        }
        if (cleaned.length() > 32) {
            return cleaned.substring(0, 32);
        }
        return cleaned;
    }
}
