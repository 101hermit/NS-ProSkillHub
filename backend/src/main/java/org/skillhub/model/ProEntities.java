/**
 * 专业版独有的数据库实体（实验矩阵 / 知识图谱 / 分析报告）。
 *
 * <p>与 {@link org.skillhub.model.MsEntities} 一样，集中在一个文件里用嵌套静态类，
 * 因为它们都是"行数据搬运器"，一表一文件反而看不清全貌。
 */
package org.skillhub.model;

public final class ProEntities {

    private ProEntities() {
    }

    /// 一次实验矩阵（{@code SKILL_SCAN} 或 {@code SKILL_COMPARE}）。
    public static class Matrix {
        public Long id;
        public String code;
        public String name;
        public String matrixType;
        public String skillIds;
        public String agentCodes;
        public String mcpCodes;
        public String paramGrid;
        public String sceneCode;
        public String caseJson;
        public String status;
        public Integer plannedCells;
        public Integer doneCells;
        public String summaryJson;
        public java.time.LocalDateTime createdAt;
        public java.time.LocalDateTime finishedAt;
    }

    /// 矩阵里的一个单元（= 一次完整调用 + 结论）。
    public static class Cell {
        public Long id;
        public String matrixCode;
        public String skillId;
        public String agentCode;
        public String mcpCode;
        public String paramsJson;
        public String sceneCode;
        public String caseCode;
        public String inputText;
        public String expectedText;
        public String outputText;
        public String reasoningText;
        public String status;
        public Integer latencyMs;
        public Integer ttftMs;
        public Integer promptTokens;
        public Integer completionTokens;
        public java.math.BigDecimal scoreTotal;
        public String scoreJson;
        public String error;
        public java.time.LocalDateTime createdAt;
    }

    /// 知识实体：Skill / 场景 / 能力 / Agent / MCP / 标签。
    public static class KgEntity {
        public Long id;
        public String code;
        public String entityType;
        public String name;
        public String label;
        public Double weight;
        public String propsJson;
        public java.time.LocalDateTime createdAt;
    }

    /// 知识关系：属于 / 提供 / 使用 / 相似 / 共现。
    public static class KgRelation {
        public Long id;
        public String srcCode;
        public String dstCode;
        public String relationType;
        public Double weight;
        public String evidence;
        public java.time.LocalDateTime createdAt;
    }

    /// 图谱快照（可按全局或按场景下钻）。
    public static class KgSnapshot {
        public Long id;
        public String code;
        public String name;
        public String scope;
        public String sceneCode;
        public Integer entityCount;
        public Integer relationCount;
        public String payloadJson;
        public java.time.LocalDateTime createdAt;
    }

    /// 分析报告（统计检验 / TOPSIS / AHP / 聚类 / 回归）。
    public static class IxReport {
        public Long id;
        public String code;
        public String title;
        public String method;
        public String scope;
        public String paramsJson;
        public String resultJson;
        public String conclusion;
        public java.time.LocalDateTime createdAt;
    }
}
