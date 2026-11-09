package org.skillhub.action.v1;

import jakarta.validation.Valid;
import java.util.List;
import org.skillhub.business.SkillService;
import org.skillhub.common.ActorContext;
import org.skillhub.common.ApiResult;
import org.skillhub.common.PageResult;
import org.skillhub.dto.SiteDtos;
import org.skillhub.dto.SkillDtos;
import org.skillhub.dto.SkillQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 端点：列表、详情、版本、评估、安装、下载、编辑、归档/恢复、检索。
 *
 * <p><b>为什么每个读端点都有"路径变量"和"?id="两种写法</b>：
 * {@code skill_id} 形如 {@code github:owner/repo/name}，含斜杠，作为路径变量时
 * 容器会把它切成多段而匹配失败。因此约定：
 * <ul>
 *   <li>{@code lib:pdf} 这类无斜杠 id → 路径变量可用（页面分享链接更好看）</li>
 *   <li>任何 id → {@code ?id=} 一定可用（前端统一走它，最稳）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillAction {

    private final SkillService skillService;
    private final ActorContext actorContext;

    public SkillAction(SkillService skillService, ActorContext actorContext) {
        this.skillService = skillService;
        this.actorContext = actorContext;
    }

    /**
     * 列表查询。
     *
     * <p><b>为什么每个条件都是显式 {@code @RequestParam} 而不是直接把 DTO 交给 Spring 绑定</b>：
     * 契约里的参数名是 snake_case（{@code risk_level} / {@code author_kind}），
     * 而 Spring 的默认绑定不会做下划线→驼峰的转换，直接绑 DTO 会导致
     * "参数传了但没生效"这种最难查的 bug。显式声明一次，语义一目了然。
     */
    @GetMapping
    public ApiResult<PageResult<SkillDtos.Card>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "author_kind", required = false) String authorKind,
            @RequestParam(value = "status", defaultValue = "published") String status,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "sort", defaultValue = "updated_at") String sort,
            @RequestParam(value = "order", defaultValue = "desc") String order) {
        SkillQuery query = new SkillQuery();
        query.setPage(page);
        query.setSize(size);
        query.setKw(kw);
        query.setCategory(category);
        query.setScene(scene);
        query.setRiskLevel(riskLevel);
        query.setAuthorKind(authorKind);
        query.setStatus(status);
        query.setSource(source);
        query.setSort(sort);
        query.setOrder(order);
        return ApiResult.ok(skillService.page(query));
    }

    @GetMapping("/by-id")
    public ApiResult<SkillDtos.Detail> detailById(@RequestParam("id") String id) {
        return ApiResult.ok(skillService.detail(id));
    }

    @GetMapping("/{skillId}")
    public ApiResult<SkillDtos.Detail> detail(@PathVariable("skillId") String skillId) {
        return ApiResult.ok(skillService.detail(skillId));
    }

    @GetMapping("/by-id/versions")
    public ApiResult<List<SkillDtos.Version>> versionsById(@RequestParam("id") String id) {
        return ApiResult.ok(skillService.versions(id));
    }

    @GetMapping("/{skillId}/versions")
    public ApiResult<List<SkillDtos.Version>> versions(@PathVariable("skillId") String skillId) {
        return ApiResult.ok(skillService.versions(skillId));
    }

    @GetMapping("/{skillId}/versions/{version}")
    public ApiResult<String> versionContent(@PathVariable("skillId") String skillId,
                                            @PathVariable("version") String version) {
        return ApiResult.ok(skillService.versionContent(skillId, version));
    }

    @GetMapping("/{skillId}/audit")
    public ApiResult<SkillDtos.Audit> audit(@PathVariable("skillId") String skillId) {
        return ApiResult.ok(skillService.audit(skillId));
    }

    @GetMapping("/by-id/audit")
    public ApiResult<SkillDtos.Audit> auditById(@RequestParam("id") String id) {
        return ApiResult.ok(skillService.audit(id));
    }

    /** 安装方式的通用形态（skill_id 含斜杠时用这个）。 */
    @GetMapping("/install")
    public ApiResult<List<SkillDtos.InstallMethod>> installById(@RequestParam("id") String id) {
        return ApiResult.ok(skillService.installMethods(skillService.require(id)));
    }
    @GetMapping("/{skillId}/install")
    public ApiResult<List<SkillDtos.InstallMethod>> install(@PathVariable("skillId") String skillId) {
        return ApiResult.ok(skillService.installMethods(skillService.require(skillId)));
    }

    /** 下载 ZIP。这是全站唯一返回二进制流的端点。 */
    @GetMapping("/{skillId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("skillId") String skillId,
                                          @RequestParam(value = "version", required = false) String version) {
        return zipResponse(skillService.download(skillId, version));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadById(@RequestParam("id") String id,
                                               @RequestParam(value = "version", required = false) String version) {
        return zipResponse(skillService.download(id, version));
    }

    @PostMapping("/{skillId}/install-record")
    public ApiResult<Void> recordInstall(@PathVariable("skillId") String skillId) {
        skillService.recordInstall(skillId);
        return ApiResult.ok();
    }

    @PutMapping("/{skillId}")
    public ApiResult<SkillDtos.Card> edit(@PathVariable("skillId") String skillId,
                                         @RequestBody SkillDtos.EditRequest request) {
        return ApiResult.ok(skillService.edit(skillId, request, actorContext.current()));
    }

    @PutMapping("/by-id")
    public ApiResult<SkillDtos.Card> editById(@RequestParam("id") String id,
                                             @RequestBody SkillDtos.EditRequest request) {
        return ApiResult.ok(skillService.edit(id, request, actorContext.current()));
    }

    @DeleteMapping("/{skillId}")
    public ApiResult<SkillDtos.Card> archive(@PathVariable("skillId") String skillId,
                                            @RequestParam(value = "hard", defaultValue = "false") boolean hard) {
        return ApiResult.ok(skillService.archive(skillId, hard, actorContext.current()));
    }

    @PostMapping("/{skillId}/restore")
    public ApiResult<SkillDtos.Card> restore(@PathVariable("skillId") String skillId) {
        return ApiResult.ok(skillService.restore(skillId, actorContext.current()));
    }

    @GetMapping("/search")
    public ApiResult<List<SiteDtos.SearchHit>> search(@RequestParam("kw") String keyword,
                                                      @RequestParam(value = "limit", defaultValue = "12") int limit) {
        return ApiResult.ok(skillService.search(keyword, limit));
    }

    private static ResponseEntity<byte[]> zipResponse(SkillService.Download payload) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(payload.data().length)
                .body(payload.data());
    }
}
