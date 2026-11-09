package org.skillhub.action.v1;

import org.skillhub.business.OpLogService;
import org.skillhub.business.RiskService;
import org.skillhub.common.ApiResult;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.RiskDtos;
import org.skillhub.dto.SiteDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 风险与审计端点：指标字典、在线试算、操作流水。 */
@RestController
public class RiskAction {

    private final RiskService riskService;
    private final OpLogService opLogService;

    public RiskAction(RiskService riskService, OpLogService opLogService) {
        this.riskService = riskService;
        this.opLogService = opLogService;
    }

    @GetMapping("/api/v1/risk/taxonomy")
    public ApiResult<RiskDtos.Taxonomy> taxonomy() {
        return ApiResult.ok(riskService.taxonomy());
    }

    @PostMapping("/api/v1/risk/scan")
    public ApiResult<RiskDtos.ScanResult> scan(@RequestBody RiskDtos.ScanRequest request) {
        return ApiResult.ok(riskService.scan(request == null ? null : request.content(),
                request == null ? null : request.fileName()));
    }

    /** GET 形态的试算，方便在浏览器地址栏里直接试。 */
    @GetMapping("/api/v1/risk/scan")
    public ApiResult<RiskDtos.ScanResult> scanGet(@RequestParam("content") String content) {
        return ApiResult.ok(riskService.scan(content, "SKILL.md"));
    }

    @GetMapping("/api/v1/op-log")
    public ApiResult<PageResult<SiteDtos.OpLogItem>> opLog(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "action", required = false) String action) {
        PageQuery query = new PageQuery();
        query.setPage(page);
        query.setSize(size);
        return ApiResult.ok(opLogService.page(query, action));
    }
}
