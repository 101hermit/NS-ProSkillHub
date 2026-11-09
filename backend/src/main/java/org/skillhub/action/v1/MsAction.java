package org.skillhub.action.v1;

import java.util.List;
import java.util.Map;
import org.skillhub.business.MsAdminService;
import org.skillhub.business.MsObjectService;
import org.skillhub.business.MsQueryService;
import org.skillhub.common.ActorContext;
import org.skillhub.common.ApiException;
import org.skillhub.common.ApiResult;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.dto.MsDtos;
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
 * Skill-MS 端点：元数据、逻辑库、索引、约束、事件、台账、查询台。
 *
 * <p>约定与公共端点一致：统一信封、snake_case、人话错误。
 * 写操作要求 {@code X-SKH-Role: admin}（库结构变更比改内容更危险）。
 */
@RestController
@RequestMapping("/api/v1/ms")
public class MsAction {

    private final MsAdminService admin;
    private final MsQueryService query;
    private final MsObjectService objects;
    private final ActorContext actorContext;

    public MsAction(MsAdminService admin, MsQueryService query, MsObjectService objects,
                    ActorContext actorContext) {
        this.admin = admin;
        this.query = query;
        this.objects = objects;
        this.actorContext = actorContext;
    }

    // ---------------------------------------------------------------- 对象树与 DDL
    /**
     * 对象树：库 → 索引 / 约束 / 事件。
     *
     * <p>只读，不需要 admin；页面左侧导航就是靠它撑起来的。
     */
    @GetMapping("/objects")
    public ApiResult<MsDtos.ObjectTree> objects() {
        return ApiResult.ok(objects.tree());
    }

    /**
     * 单个对象的 DDL 卡。
     *
     * <p>路径用查询参数而不是 {@code /ddl/{type}/{code}}：对象编号里可能出现 {@code /}
     * （例如演示用的 {@code local:验收脚本/xxx}），路径变量会被 Tomcat 当成两级路径。
     */
    @GetMapping("/ddl")
    public ApiResult<MsDtos.DdlDoc> ddl(@RequestParam("type") String type,
                                        @RequestParam("code") String code) {
        return ApiResult.ok(objects.ddl(type, code));
    }

    // ---------------------------------------------------------------- 元数据
    @GetMapping("/schema")
    public ApiResult<MsDtos.Schema> schema() {
        return ApiResult.ok(admin.schema());
    }

    @GetMapping("/concepts")
    public ApiResult<List<MsDtos.Concept>> concepts() {
        return ApiResult.ok(admin.concepts());
    }

    @GetMapping("/tables")
    public ApiResult<List<Map<String, Object>>> tables() {
        return ApiResult.ok(admin.queryableTables());
    }

    // ---------------------------------------------------------------- 逻辑库
    @GetMapping("/skillsets")
    public ApiResult<List<MsDtos.SetItem>> skillsets() {
        return ApiResult.ok(admin.skillsets());
    }

    @PostMapping("/skillsets")
    public ApiResult<MsDtos.SetItem> createSkillset(@RequestBody Map<String, String> body) {
        requireAdmin();
        return ApiResult.ok(admin.createSkillset(body.get("code"), body.get("name"),
                body.get("description"), actorContext.current()));
    }

    @DeleteMapping("/skillsets/{code}")
    public ApiResult<Void> deleteSkillset(@PathVariable("code") String code) {
        requireAdmin();
        admin.deleteSkillset(code, actorContext.current());
        return ApiResult.ok();
    }

    // ---------------------------------------------------------------- 索引
    @GetMapping("/indexes")
    public ApiResult<List<MsDtos.IndexItem>> indexes() {
        return ApiResult.ok(admin.indexes());
    }

    @PostMapping("/indexes")
    public ApiResult<MsDtos.IndexItem> createIndex(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResult.ok(admin.createIndex(str(body, "code"), str(body, "name"),
                str(body, "columns_col"), str(body, "skillset_code"),
                Boolean.TRUE.equals(body.get("unique_flag")), actorContext.current()));
    }

    @PostMapping("/indexes/{code}/rebuild")
    public ApiResult<Map<String, Object>> rebuildIndex(@PathVariable("code") String code) {
        requireAdmin();
        return ApiResult.ok(admin.rebuildIndex(code));
    }

    @GetMapping("/indexes/{code}/entries")
    public ApiResult<List<MsDtos.IndexEntryItem>> entries(@PathVariable("code") String code,
                                                         @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResult.ok(admin.indexEntries(code, limit));
    }

    @DeleteMapping("/indexes/{code}")
    public ApiResult<Void> dropIndex(@PathVariable("code") String code) {
        requireAdmin();
        admin.dropIndex(code);
        return ApiResult.ok();
    }

    // ---------------------------------------------------------------- 约束
    @GetMapping("/constraints")
    public ApiResult<List<MsDtos.ConstraintItem>> constraints() {
        return ApiResult.ok(admin.constraints());
    }

    @PostMapping("/constraints")
    public ApiResult<MsDtos.ConstraintItem> createConstraint(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResult.ok(admin.saveConstraint(str(body, "code"), str(body, "name"),
                str(body, "constraint_type"), str(body, "target_column"),
                str(body, "expression"), str(body, "severity"), actorContext.current()));
    }

    @PutMapping("/constraints/{code}")
    public ApiResult<MsDtos.ConstraintItem> updateConstraint(@PathVariable("code") String code,
                                                            @RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResult.ok(admin.saveConstraint(code, str(body, "name"), str(body, "constraint_type"),
                str(body, "target_column"), str(body, "expression"), str(body, "severity"),
                actorContext.current()));
    }

    @DeleteMapping("/constraints/{code}")
    public ApiResult<Void> deleteConstraint(@PathVariable("code") String code) {
        requireAdmin();
        admin.deleteConstraint(code);
        return ApiResult.ok();
    }

    @PostMapping("/constraints/validate")
    public ApiResult<List<MsDtos.ValidateItem>> validate() {
        return ApiResult.ok(admin.validateAll());
    }

    // ---------------------------------------------------------------- 事件
    @GetMapping("/events")
    public ApiResult<List<MsDtos.EventItem>> events() {
        return ApiResult.ok(admin.events());
    }

    @PostMapping("/events")
    public ApiResult<MsDtos.EventItem> createEvent(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResult.ok(admin.saveEvent(str(body, "code"), str(body, "name"),
                str(body, "trigger_event"), str(body, "condition_expr"),
                str(body, "action_type"), str(body, "action_payload"),
                body.get("enabled") == null ? Boolean.TRUE : Boolean.valueOf(str(body, "enabled")),
                actorContext.current()));
    }

    @PutMapping("/events/{code}")
    public ApiResult<MsDtos.EventItem> updateEvent(@PathVariable("code") String code,
                                                  @RequestBody Map<String, Object> body) {
        requireAdmin();
        return ApiResult.ok(admin.saveEvent(code, str(body, "name"), str(body, "trigger_event"),
                str(body, "condition_expr"), str(body, "action_type"), str(body, "action_payload"),
                Boolean.TRUE, actorContext.current()));
    }

    @DeleteMapping("/events/{code}")
    public ApiResult<Void> deleteEvent(@PathVariable("code") String code) {
        requireAdmin();
        admin.deleteEvent(code);
        return ApiResult.ok();
    }

    @GetMapping("/events/logs")
    public ApiResult<PageResult<MsDtos.EventLogItem>> eventLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status) {
        PageQuery q = new PageQuery();
        q.setPage(page);
        q.setSize(size);
        return ApiResult.ok(admin.eventLogs(q, status));
    }

    @PostMapping("/events/logs/{id}/retry")
    public ApiResult<MsDtos.EventLogItem> retryEventLog(@PathVariable("id") long id) {
        requireAdmin();
        return ApiResult.ok(admin.retryEventLog(id));
    }

    // ---------------------------------------------------------------- 台账
    @GetMapping("/tx-log")
    public ApiResult<PageResult<MsDtos.TxItem>> txLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "skill_id", required = false) String skillId) {
        PageQuery q = new PageQuery();
        q.setPage(page);
        q.setSize(size);
        return ApiResult.ok(admin.txLogs(q, skillId));
    }

    // ---------------------------------------------------------------- 查询台
    @PostMapping("/query")
    public ApiResult<MsDtos.QueryResult> runQuery(@RequestBody Map<String, String> body) {
        return ApiResult.ok(query.run(body.get("sql")));
    }

    @PostMapping("/query/plan")
    public ApiResult<MsDtos.QueryPlan> explain(@RequestBody Map<String, String> body) {
        return ApiResult.ok(query.plan(body.get("sql")));
    }

    // ---------------------------------------------------------------- 内部
    private void requireAdmin() {
        if (!actorContext.current().isAdmin()) {
            throw ApiException.of(ErrorCode.ROLE_FORBIDDEN, actorContext.current().role());
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
