package org.skillhub.action.v1;

import java.util.List;
import org.skillhub.business.AccessService;
import org.skillhub.business.EvalService;
import org.skillhub.common.ApiResult;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.EvalDtos;
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
 * 实测与接入端点：单点实测、指标字典、质量汇总、Agent/MCP 注册与探活。
 */
@RestController
@RequestMapping("/api/v1")
public class EvalAction {

    private final EvalService evalService;
    private final AccessService accessService;

    public EvalAction(EvalService evalService, AccessService accessService) {
        this.evalService = evalService;
        this.accessService = accessService;
    }

    // ---------------------------------------------------------------- 实测
    @PostMapping("/eval/runs")
    public ApiResult<EvalDtos.RunItem> run(@RequestBody EvalDtos.RunRequest request) {
        return ApiResult.ok(evalService.run(request));
    }

    @GetMapping("/eval/runs")
    public ApiResult<PageResult<EvalDtos.RunItem>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "skill_id", required = false) String skillId,
            @RequestParam(value = "status", required = false) String status) {
        PageQuery q = new PageQuery();
        q.setPage(page);
        q.setSize(size);
        return ApiResult.ok(evalService.page(q, skillId, status));
    }

    @GetMapping("/eval/runs/{code}")
    public ApiResult<EvalDtos.RunItem> detail(@PathVariable("code") String code) {
        return ApiResult.ok(evalService.detail(code));
    }

    @GetMapping("/eval/metrics")
    public ApiResult<List<EvalDtos.MetricItem>> metrics() {
        return ApiResult.ok(evalService.metrics());
    }

    @GetMapping("/eval/summary")
    public ApiResult<EvalDtos.Summary> summary(@RequestParam("skill_id") String skillId) {
        return ApiResult.ok(evalService.summary(skillId));
    }

    @GetMapping("/eval/ranking")
    public ApiResult<List<java.util.Map<String, Object>>> ranking() {
        return ApiResult.ok(evalService.ranking());
    }

    // ---------------------------------------------------------------- 接入
    @GetMapping("/agents")
    public ApiResult<List<EvalDtos.AgentItem>> agents() {
        return ApiResult.ok(accessService.agents());
    }

    @PostMapping("/agents")
    public ApiResult<EvalDtos.AgentItem> createAgent(@RequestBody EvalDtos.AgentRequest request) {
        return ApiResult.ok(accessService.saveAgent(request));
    }

    @PutMapping("/agents/{code}")
    public ApiResult<EvalDtos.AgentItem> updateAgent(@PathVariable("code") String code,
                                                    @RequestBody EvalDtos.AgentRequest request) {
        request.setCode(code);
        return ApiResult.ok(accessService.saveAgent(request));
    }

    @DeleteMapping("/agents/{code}")
    public ApiResult<Void> deleteAgent(@PathVariable("code") String code) {
        accessService.deleteAgent(code);
        return ApiResult.ok();
    }

    @PostMapping("/agents/{code}/probe")
    public ApiResult<EvalDtos.ProbeResult> probeAgent(@PathVariable("code") String code) {
        return ApiResult.ok(accessService.probeAgent(code));
    }

    @GetMapping("/mcp")
    public ApiResult<List<EvalDtos.McpItem>> mcps() {
        return ApiResult.ok(accessService.mcps());
    }

    @PostMapping("/mcp")
    public ApiResult<EvalDtos.McpItem> createMcp(@RequestBody EvalDtos.McpRequest request) {
        return ApiResult.ok(accessService.saveMcp(request));
    }

    @PutMapping("/mcp/{code}")
    public ApiResult<EvalDtos.McpItem> updateMcp(@PathVariable("code") String code,
                                                @RequestBody EvalDtos.McpRequest request) {
        request.setCode(code);
        return ApiResult.ok(accessService.saveMcp(request));
    }

    @DeleteMapping("/mcp/{code}")
    public ApiResult<Void> deleteMcp(@PathVariable("code") String code) {
        accessService.deleteMcp(code);
        return ApiResult.ok();
    }

    @PostMapping("/mcp/{code}/probe")
    public ApiResult<EvalDtos.ProbeResult> probeMcp(@PathVariable("code") String code) {
        return ApiResult.ok(accessService.probeMcp(code));
    }

    /** 一键体检：出网策略 + 每个 Agent/MCP 的连通性。 */
    @PostMapping("/conn/doctor")
    public ApiResult<java.util.Map<String, Object>> doctor() {
        return ApiResult.ok(accessService.doctor());
    }

    @GetMapping("/conn/doctor")
    public ApiResult<java.util.Map<String, Object>> doctorGet() {
        return ApiResult.ok(accessService.doctor());
    }
}
