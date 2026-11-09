/**
 * 标准版/专业版追加的数据库实体（与 {@code db/schema.sql} 的 MS / PRO 段一一对应）。
 *
 * <p>为什么集中在一个文件里用嵌套静态类：这些类都是"行数据搬运器"，
 * 每个只有 6–20 个 public 字段。一表一文件会得到 19 个 12 行的文件，
 * 反而更难看出"这个模块到底有哪些对象"。
 */
package org.skillhub.model;

public final class MsEntities {

    private MsEntities() {
    }

    /// 逻辑库（类比 database）。{@code default} 是内置库，不可删除。
    public static class Skillset {
        public Long id;
        public String code;
        public String name;
        public String description;
        public String owner;
        public Integer skillCount;
        public String status;
        public java.time.LocalDateTime createdAt;
        public java.time.LocalDateTime updatedAt;
    }

    /// 索引定义。状态机：{@code BUILDING → READY → STALE → DROPPED}。
    public static class IndexDef {
        public Long id;
        public String code;
        public String name;
        public String skillsetCode;
        public String columnsCol;
        public String indexType;
        public Boolean uniqueFlag;
        public String status;
        public Integer entryCount;
        public Long hitCount;
        public java.time.LocalDateTime lastBuiltAt;
        public java.time.LocalDateTime createdAt;
    }

    /// 索引条目（真数据，不是元数据）。
    public static class IndexEntry {
        public Long id;
        public String indexCode;
        public String skillId;
        public String keyValue;
    }

    /// 约束定义。{@code constraintType} ∈ NOT_NULL / UNIQUE / RANGE / ENUM / REGEX / CHECK_EXPR / REFERENCE。
    public static class ConstraintDef {
        public Long id;
        public String code;
        public String name;
        public String skillsetCode;
        public String constraintType;
        public String targetColumn;
        public String expression;
        public String severity;
        public String status;
        public Integer violationCount;
        public java.time.LocalDateTime lastViolatedAt;
        public java.time.LocalDateTime createdAt;
    }

    /// 事件（触发器）定义。
    public static class EventDef {
        public Long id;
        public String code;
        public String name;
        public String skillsetCode;
        public String triggerEvent;
        public String conditionExpr;
        public String actionType;
        public String actionPayload;
        public Boolean enabled;
        public Long firedCount;
        public java.time.LocalDateTime createdAt;
    }

    /// 事件派发日志（含重试与死信）。
    public static class EventLog {
        public Long id;
        public String eventCode;
        public String triggerOn;
        public String skillId;
        public String actionType;
        public String payload;
        public String status;
        public Integer attempts;
        public String lastError;
        public java.time.LocalDateTime createdAt;
        public java.time.LocalDateTime updatedAt;
    }

    /// 写操作事务台账：一次写操作被哪条约束放过、动了哪些索引、触发了哪些事件。
    public static class TxLog {
        public Long id;
        public String txId;
        public String opType;
        public String skillId;
        public String actor;
        public String verdict;
        public String beforeJson;
        public String afterJson;
        public String constraintHits;
        public String indexTouched;
        public String eventsFired;
        public java.time.LocalDateTime createdAt;
    }

    /// Agent 注册表。{@code apiKey} 支持 {@code env:NAME} 形式从环境变量读。
    public static class AgentRecord {
        public Long id;
        public String code;
        public String name;
        public String provider;
        public String baseUrl;
        public String model;
        public String apiKey;
        public java.math.BigDecimal temperature;
        public java.math.BigDecimal topP;
        public Integer maxTokens;
        public Boolean enabled;
        public String status;
        public java.time.LocalDateTime lastProbeAt;
        public Integer lastProbeMs;
        public String lastError;
        public java.time.LocalDateTime createdAt;
    }

    /// MCP 服务注册表。
    public static class McpRecord {
        public Long id;
        public String code;
        public String name;
        public String transport;
        public String endpoint;
        public String toolName;
        public String description;
        public String headersJson;
        public Boolean enabled;
        public String status;
        public java.time.LocalDateTime lastProbeAt;
        public Integer lastProbeMs;
        public String lastError;
        public java.time.LocalDateTime createdAt;
    }

    /// 指标字典。
    public static class MetricDef {
        public Long id;
        public String code;
        public String label;
        public String dimension;
        public String unit;
        public Boolean higherBetter;
        public java.math.BigDecimal weight;
        public String formula;
        public String description;
        public Integer sortNo;
    }

    /// 一次单点实测。
    public static class EvalRun {
        public Long id;
        public String code;
        public String skillId;
        public String agentCode;
        public String mcpCode;
        public String paramsJson;
        public String sceneCode;
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
        public java.time.LocalDateTime finishedAt;
    }

    /// 单次运行的指标分。
    public static class EvalScore {
        public Long id;
        public Long runId;
        public String metricCode;
        public Double metricValue;
        public Double weight;
        public String detail;
    }
}
