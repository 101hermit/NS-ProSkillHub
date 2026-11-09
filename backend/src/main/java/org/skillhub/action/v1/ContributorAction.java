package org.skillhub.action.v1;

import org.skillhub.business.ContributorService;
import org.skillhub.common.ApiResult;
import org.skillhub.dto.SiteDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 贡献者端点：列表（含个人/部门计数）+ 详情（档案 + 其全部 Skill）。 */
@RestController
@RequestMapping("/api/v1/contributors")
public class ContributorAction {

    private final ContributorService contributorService;

    public ContributorAction(ContributorService contributorService) {
        this.contributorService = contributorService;
    }

    @GetMapping
    public ApiResult<SiteDtos.ContributorPage> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "24") int size,
            @RequestParam(value = "kind", required = false) String kind) {
        return ApiResult.ok(contributorService.page(page, size, kind));
    }

    @GetMapping("/{source}/{author}")
    public ApiResult<SiteDtos.ContributorDetail> detail(@PathVariable("source") String source,
                                                       @PathVariable("author") String author) {
        return ApiResult.ok(contributorService.detail(source, author));
    }
}
