package ink.icoding.dianxin.chat2response.controller.admin;

import ink.icoding.dianxin.chat2response.dto.ConversionProjectCreateDTO;
import ink.icoding.dianxin.chat2response.entity.ConversionProject;
import ink.icoding.dianxin.chat2response.service.ConversionProjectService;
import ink.icoding.dianxin.chat2response.vo.ConversionProjectVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台转换项目接口。
 */
@RestController
@RequestMapping("/admin/projects")
public class AdminProjectController {

    private final ConversionProjectService projectService;

    public AdminProjectController(ConversionProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 新增转换项目。
     *
     * @param dto 创建参数
     * @return 新增结果
     */
    @PostMapping
    public ConversionProjectVO create(@Valid @RequestBody ConversionProjectCreateDTO dto) {
        ConversionProject project = projectService.create(dto);
        return ConversionProjectVO.fromDatabaseModel(project);
    }

    /**
     * 查询项目列表。
     *
     * @return 项目列表
     */
    @GetMapping
    public List<ConversionProjectVO> list() {
        return projectService.listAll().stream().map(ConversionProjectVO::fromDatabaseModel).toList();
    }

    /**
     * 查询单个项目。
     *
     * @param id 项目ID
     * @return 项目信息
     */
    @GetMapping("/{id}")
    public ConversionProjectVO get(@PathVariable Long id) {
        return ConversionProjectVO.fromDatabaseModel(projectService.getById(id));
    }

    /**
     * 启停项目。
     *
     * @param id      项目ID
     * @param enabled 启用状态
     * @return 更新后项目
     */
    @PatchMapping("/{id}/enabled")
    public ConversionProjectVO switchEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return ConversionProjectVO.fromDatabaseModel(projectService.switchEnabled(id, enabled));
    }

    /**
     * 删除项目。
     *
     * @param id 项目ID
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
