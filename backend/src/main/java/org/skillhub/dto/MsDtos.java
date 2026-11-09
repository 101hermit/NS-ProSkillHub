package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** Skill-MS（库语义）对外形状：元数据、逻辑库、索引、约束、事件、事务台账、查询台。 */
public final class MsDtos {

    private MsDtos() {
    }

    /** {@code GET /api/v1/ms/schema}：把"库像数据库一样"讲清楚。 */
    public record Schema(
            List<TableInfo> tables,
            List<SetItem> skillsets,
            List<IndexItem> indexes,
            List<ConstraintItem> constraints,
            List<EventItem> events,
            Map<String, Long> stats,
            List<Concept> concepts) {
    }

    /** 一张"表"的元数据（列 + 行数）。 */
    public record TableInfo(String name, String label, String description, long rowCount, List<Column> columns) {
    }

    /** 一列。 */
    public record Column(String name, String type, String meaning) {
    }

    /** 一个概念解释（页面上的"术语小词典"由它驱动）。 */
    public record Concept(String term, String plain, String analogy) {
    }

    /** 逻辑库。 */
    public record SetItem(String code, String name, String description, String owner,
                          int skillCount, String status, String createdAt) {
    }

    /** 索引。 */
    public record IndexItem(String code, String name, String skillsetCode, String columnsCol,
                            String indexType, boolean uniqueFlag, String status, int entryCount,
                            long hitCount, String lastBuiltAt) {
    }

    /** 索引条目（证明索引是真数据）。 */
    public record IndexEntryItem(String skillId, String keyValue) {
    }

    /** 约束。 */
    public record ConstraintItem(String code, String name, String skillsetCode, String constraintType,
                                String constraintLabel, String targetColumn, String expression,
                                String severity, String status, int violationCount, String lastViolatedAt) {
    }

    /** 事件。 */
    public record EventItem(String code, String name, String skillsetCode, String triggerEvent,
                            String conditionExpr, String actionType, String actionLabel,
                            String actionPayload, boolean enabled, long firedCount) {
    }

    /** 事件派发日志。 */
    public record EventLogItem(long id, String eventCode, String triggerOn, String skillId,
                               String actionType, String payload, String status, int attempts,
                               String lastError, String createdAt) {
    }

    /** 写操作事务台账。 */
    public record TxItem(long id, String txId, String opType, String skillId, String actor,
                         String verdict, String constraintHits, String indexTouched,
                         String eventsFired, String createdAt) {
    }

    /** 约束校验报告。 */
    public record ValidateItem(String code, String name, String constraintType, String severity,
                               long violated, List<String> samples) {
    }

    /** 写操作的裁决结果（给页面解释"为什么被拦/为什么放行"）。 */
    public record Verdict(boolean passed, String opType, String skillId,
                          List<String> constraintHits, List<String> indexTouched,
                          List<String> eventsFired, String message) {
    }

    /** 查询台结果。 */
    public record QueryResult(List<String> columns, List<Map<String, Object>> rows,
                              int rowCount, long elapsedMs, String sqlEcho) {
    }

    /** 查询计划。 */
    public record QueryPlan(String table, String scanType, String usedIndex, String usedColumns,
                            int estimatedRows, boolean indexAvailable, String explanation) {
    }

    // ============================================================ 对象树与 DDL

    /** 对象树的一个节点（供「对象」页左侧导航使用）。 */
    public record ObjectNode(String kind, String code, String label, String subtitle, long count) {
    }

    /** 逻辑库节点：一个库下面挂着索引 / 约束 / 事件。 */
    public record SkillsetNode(String code, String name, String description, long skillCount,
                               List<ObjectNode> indexes, List<ObjectNode> constraints,
                               List<ObjectNode> events, long txCount) {
    }

    /** {@code GET /ms/objects} 的整体返回。 */
    public record ObjectTree(List<SkillsetNode> skillsets, Map<String, Long> totals, String generatedAt) {
    }

    /**
     * 一个对象的「DDL 卡」。
     *
     * <p>{@code ddl} 是给人看的声明式文本（借 SQL 的形状），{@code equivalentSql} 能在本站查询台真跑一遍，
     * {@code api} 是能真的改变系统的调用 —— 三者放在一起，DDL 才不是一个不可验证的漂亮承诺。
     */
    public record DdlDoc(String type, String code, String title, String ddl,
                         List<String> notes, String equivalentSql, String api, String rebuildApi) {
    }
}
