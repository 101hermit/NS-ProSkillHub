package org.skillhub.action.v1;

import java.util.List;
import java.util.Map;
import org.skillhub.business.ConnService;
import org.skillhub.business.InsightService;
import org.skillhub.business.KnowledgeService;
import org.skillhub.business.MatrixService;
import org.skillhub.common.ApiResult;
import org.skillhub.dto.ProDtos;
import org.skillhub.model.ProEntities;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 专业版端点：连通矩阵、实验矩阵、知识图谱、分析报告。
 *
 * <p>与公共端点同一套契约：统一信封、snake_case、人话错误。
 */
@RestController
@RequestMapping("/api/v1")
public class ProAction {

    private final ConnService connService;
    private final MatrixService matrixService;
    private final KnowledgeService knowledgeService;
    private final InsightService insightService;

    public ProAction(ConnService connService, MatrixService matrixService,
                     KnowledgeService knowledgeService, InsightService insightService) {
        this.connService = connService;
        this.matrixService = matrixService;
        this.knowledgeService = knowledgeService;
        this.insightService = insightService;
    }

    // ---------------------------------------------------------------- 连通矩阵
    @GetMapping("/conn/matrix")
    public ApiResult<ProDtos.ConnMatrix> connMatrix() {
        return ApiResult.ok(connService.matrix());
    }

    @GetMapping("/conn/heatmap")
    public ApiResult<Map<String, Object>> connHeatmap() {
        return ApiResult.ok(connService.heatmap(connService.matrix()));
    }

    // ---------------------------------------------------------------- 实验矩阵
    @PostMapping("/eval/matrix")
    public ApiResult<ProDtos.MatrixItem> createMatrix(@RequestBody ProDtos.MatrixRequest request) {
        return ApiResult.ok(matrixService.create(request));
    }

    @GetMapping("/eval/matrix")
    public ApiResult<List<ProDtos.MatrixItem>> listMatrices() {
        return ApiResult.ok(matrixService.list());
    }

    @GetMapping("/eval/matrix/{code}")
    public ApiResult<ProDtos.MatrixDetail> matrixDetail(@PathVariable("code") String code) {
        return ApiResult.ok(matrixService.detail(code));
    }

    @PostMapping("/eval/matrix/{code}/run")
    public ApiResult<ProDtos.MatrixDetail> runMatrix(@PathVariable("code") String code) {
        return ApiResult.ok(matrixService.run(code));
    }

    /**
     * 导出实验报告（Markdown，直接下载成 .md 文件）。
     *
     * <p>为什么不用统一信封包一层 JSON：这个端点的产物是<b>文档</b>不是数据，
     * 用户要的是"点一下就下载一份能贴进周报的东西"。包成 JSON 反而要在前端再解一遍。
     * 出错时仍然返回统一信封（由全局异常处理器接管），所以契约没有被破坏。
     *
     * <p>文件名只保留矩阵编号里的安全字符：编号可能含冒号等字符，直接当文件名在 Windows 上会失败。
     */
    @GetMapping("/eval/matrix/{code}/report.md")
    public ResponseEntity<String> matrixReport(@PathVariable("code") String code) {
        String markdown = matrixService.markdown(code);
        String safe = code == null ? "report" : code.replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"matrix-" + safe + ".md\"")
                .header("X-SKH-Report-Format", "markdown-v1")
                .body(markdown);
    }

    @GetMapping("/eval/matrix/{code}/compare")
    public ApiResult<ProDtos.Compare> compareMatrix(@PathVariable("code") String code) {
        return ApiResult.ok(matrixService.compare(code));
    }

    @DeleteMapping("/eval/matrix/{code}")
    public ApiResult<Void> deleteMatrix(@PathVariable("code") String code) {
        matrixService.delete(code);
        return ApiResult.ok();
    }

    // ---------------------------------------------------------------- 知识图谱
    @PostMapping("/kg/rebuild")
    public ApiResult<Map<String, Object>> rebuildKg() {
        return ApiResult.ok(knowledgeService.rebuild());
    }

    @GetMapping("/kg/graph")
    public ApiResult<ProDtos.Graph> graph(
            @RequestParam(value = "scope", defaultValue = "global") String scope,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "limit", defaultValue = "150") int limit) {
        return ApiResult.ok(knowledgeService.graph(scene == null ? scope : scene, limit));
    }

    @GetMapping("/kg/entities")
    public ApiResult<List<ProEntities.KgEntity>> entities(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ApiResult.ok(knowledgeService.entities(type, kw, limit));
    }

    @GetMapping("/kg/relations")
    public ApiResult<List<ProEntities.KgRelation>> relations(
            @RequestParam(value = "src", required = false) String src) {
        return ApiResult.ok(knowledgeService.relations(src));
    }

    @GetMapping("/kg/similar/{skillId}")
    public ApiResult<List<ProDtos.SimilarItem>> similar(@PathVariable("skillId") String skillId,
                                                       @RequestParam(value = "top", defaultValue = "5") int top) {
        return ApiResult.ok(knowledgeService.similar(skillId, top));
    }

    /** 导出：json / csv / graphml（返回纯文本，前端用下载链接打开）。 */
    @GetMapping("/kg/export")
    public ApiResult<String> export(
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "scope", defaultValue = "global") String scope) {
        return ApiResult.ok(knowledgeService.export(format, scope));
    }

    @GetMapping("/kg/snapshots")
    public ApiResult<List<ProEntities.KgSnapshot>> snapshots() {
        return ApiResult.ok(knowledgeService.snapshots());
    }

    // ---------------------------------------------------------------- 分析
    @PostMapping("/ix/analyze")
    public ApiResult<ProDtos.Report> analyze(@RequestBody ProDtos.AnalyzeRequest request) {
        return ApiResult.ok(insightService.analyze(request));
    }

    @GetMapping("/ix/reports")
    public ApiResult<List<ProDtos.Report>> reports(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResult.ok(insightService.reports(limit));
    }

    @GetMapping("/ix/reports/{code}")
    public ApiResult<ProDtos.Report> report(@PathVariable("code") String code) {
        return ApiResult.ok(insightService.report(code));
    }
}
