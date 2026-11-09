package org.skillhub.action.v1;

import java.io.IOException;
import org.skillhub.business.ContributionService;
import org.skillhub.common.ActorContext;
import org.skillhub.common.ApiException;
import org.skillhub.common.ApiResult;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.ContributionDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 在线贡献端点：四步状态机。
 *
 * <p>第 1 步同时接受三种投递：上传 {@code skill_md_file}、直接贴 {@code skill_md} 文本、
 * 或两者都给（文件优先）。附件包 {@code bundle} 可选。
 */
@RestController
@RequestMapping("/api/v1/contributions")
public class ContributionAction {

    private final ContributionService contributionService;
    private final ActorContext actorContext;

    public ContributionAction(ContributionService contributionService, ActorContext actorContext) {
        this.contributionService = contributionService;
        this.actorContext = actorContext;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResult<ContributionDtos.Detail> create(
            @RequestParam(value = "kind", defaultValue = "department") String kind,
            @RequestParam("contributor_name") String contributorName,
            @RequestParam("name") String name,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "version", defaultValue = "1.0.0") String version,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "source_url", required = false) String sourceUrl,
            @RequestParam(value = "existing_skill_id", required = false) String existingSkillId,
            @RequestParam(value = "auto_publish", defaultValue = "false") boolean autoPublish,
            @RequestParam(value = "skill_md", required = false) String skillMdText,
            @RequestPart(value = "skill_md_file", required = false) MultipartFile skillMdFile,
            @RequestPart(value = "bundle", required = false) MultipartFile bundle) {

        ContributionDtos.CreateRequest req = new ContributionDtos.CreateRequest();
        req.setKind(kind);
        req.setContributorName(contributorName);
        req.setName(name);
        req.setTitle(title);
        req.setVersion(version);
        req.setDescription(description);
        req.setSourceUrl(sourceUrl);
        req.setExistingSkillId(existingSkillId);
        req.setAutoPublish(autoPublish);
        req.setCodeDraft(skillMdText);

        String md = readText(skillMdFile);
        byte[] bundleBytes = readBytes(bundle);
        return ApiResult.ok(contributionService.create(req, md, bundleBytes, actorContext.current()));
    }

    @PostMapping("/{id}/cla")
    public ApiResult<ContributionDtos.Item> signCla(@PathVariable("id") long id,
                                                   @RequestBody(required = false) ContributionDtos.ClaRequest request) {
        String signature = request == null ? null : request.signature();
        return ApiResult.ok(contributionService.signCla(id, signature, actorContext.current()));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<ContributionDtos.Item> submit(@PathVariable("id") long id) {
        return ApiResult.ok(contributionService.submit(id, actorContext.current()));
    }

    @PostMapping("/{id}/publish")
    public ApiResult<ContributionDtos.Detail> publish(@PathVariable("id") long id) {
        return ApiResult.ok(contributionService.publish(id, actorContext.current()));
    }

    @PostMapping("/{id}/reject")
    public ApiResult<ContributionDtos.Item> reject(@PathVariable("id") long id,
                                                  @RequestParam(value = "reason", required = false) String reason) {
        return ApiResult.ok(contributionService.reject(id, reason, actorContext.current()));
    }

    @GetMapping
    public ApiResult<PageResult<ContributionDtos.Item>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kind", required = false) String kind) {
        PageQuery query = new PageQuery();
        query.setPage(page);
        query.setSize(size);
        return ApiResult.ok(contributionService.page(query, status, kind));
    }

    @GetMapping("/{id}")
    public ApiResult<ContributionDtos.Detail> detail(@PathVariable("id") long id) {
        return ApiResult.ok(contributionService.detail(id));
    }

    private static String readText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM, "读取上传的 SKILL.md 失败。怎么办：重试一次。");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.ofMessage(ErrorCode.INVALID_PARAM, "读取上传的附件包失败。怎么办：重试一次。");
        }
    }
}
