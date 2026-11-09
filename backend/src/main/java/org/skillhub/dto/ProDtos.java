package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** 专业版对外形状：连通矩阵、实验矩阵、知识图谱、分析报告。 */
public final class ProDtos {

    private ProDtos() {
    }

    // ================================================================ 连通性
    /** 一格连通性。 */
    public record ConnCell(String agentCode, String agentName, String mcpCode, String mcpName,
                           boolean ok, int latencyMs, String detail) {
    }

    /** Agent × MCP 的连通矩阵。 */
    public record ConnMatrix(List<String> agents, List<String> mcps, List<ConnCell> cells,
                             int okCount, int totalCount, Map<String, Object> egress) {
    }

    // ================================================================ 实验矩阵
    /** 创建一个矩阵的请求。 */
    public static class MatrixRequest {
        private String name;
        private String matrixType = "SKILL_SCAN";
        private List<String> skillIds = List.of();
        private List<String> agentCodes = List.of();
        private List<String> mcpCodes = List.of();
        private Map<String, List<Double>> paramGrid;
        private String sceneCode = "general";
        private List<Case> cases = List.of();
        private Integer maxCells;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMatrixType() {
            return matrixType;
        }

        public void setMatrixType(String matrixType) {
            this.matrixType = matrixType;
        }

        public List<String> getSkillIds() {
            return skillIds;
        }

        public void setSkillIds(List<String> skillIds) {
            this.skillIds = skillIds == null ? List.of() : skillIds;
        }

        public List<String> getAgentCodes() {
            return agentCodes;
        }

        public void setAgentCodes(List<String> agentCodes) {
            this.agentCodes = agentCodes == null ? List.of() : agentCodes;
        }

        public List<String> getMcpCodes() {
            return mcpCodes;
        }

        public void setMcpCodes(List<String> mcpCodes) {
            this.mcpCodes = mcpCodes == null ? List.of() : mcpCodes;
        }

        public Map<String, List<Double>> getParamGrid() {
            return paramGrid;
        }

        public void setParamGrid(Map<String, List<Double>> paramGrid) {
            this.paramGrid = paramGrid;
        }

        public String getSceneCode() {
            return sceneCode;
        }

        public void setSceneCode(String sceneCode) {
            this.sceneCode = sceneCode;
        }

        public List<Case> getCases() {
            return cases;
        }

        public void setCases(List<Case> cases) {
            this.cases = cases == null ? List.of() : cases;
        }

        public Integer getMaxCells() {
            return maxCells;
        }

        public void setMaxCells(Integer maxCells) {
            this.maxCells = maxCells;
        }
    }

    /** 一条用例（输入 + 期望输出）。 */
    public record Case(String code, String input, String expected) {
    }

    /** 矩阵概要。 */
    public record MatrixItem(String code, String name, String matrixType, String sceneCode,
                             String status, int plannedCells, int doneCells,
                             String skillIds, String agentCodes, String mcpCodes,
                             String createdAt, String finishedAt) {
    }

    /** 矩阵单元。 */
    public record CellItem(long id, String skillId, String skillName, String agentCode, String mcpCode,
                           String sceneCode, String caseCode, String inputText, String expectedText,
                           String outputText, String reasoningText, String status,
                           int latencyMs, int ttftMs, double scoreTotal, String error) {
    }

    /** 矩阵详情。 */
    public record MatrixDetail(MatrixItem matrix, List<CellItem> cells, Map<String, Object> summary) {
    }

    /** 分组对比（按 skill / agent / 参数）。 */
    public record Compare(String matrixCode,
                          List<Map<String, Object>> bySkill,
                          List<Map<String, Object>> byAgent,
                          List<Map<String, Object>> byParams,
                          String conclusion) {
    }

    // ================================================================ 知识图谱
    public record GraphNode(String code, String type, String name, String label, double weight,
                            int degree) {
    }

    public record GraphEdge(String source, String target, String type, double weight) {
    }

    public record Graph(List<GraphNode> nodes, List<GraphEdge> edges, Map<String, Object> stats) {
    }

    public record SimilarItem(String skillId, String name, double score, String why) {
    }

    // ================================================================ 分析
    /** 一次分析的请求。 */
    public static class AnalyzeRequest {
        private String method = "DESCRIBE";
        /** 范围限定：可以是一段字符串，也可以是一个对象（例如 {metric_codes:[...]}），两者都接受。 */
        private Object scope = "";
        private String groupBy = "agent_code";
        private List<String> matrixCodes = List.of();
        private Map<String, Object> params = Map.of();

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Object getScope() {
            return scope;
        }

        public void setScope(Object scope) {
            this.scope = scope;
        }

        public String getGroupBy() {
            return groupBy;
        }

        public void setGroupBy(String groupBy) {
            this.groupBy = groupBy;
        }

        public List<String> getMatrixCodes() {
            return matrixCodes;
        }

        public void setMatrixCodes(List<String> matrixCodes) {
            this.matrixCodes = matrixCodes == null ? List.of() : matrixCodes;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params == null ? Map.of() : params;
        }
    }

    /** 分析报告。 */
    public record Report(String code, String title, String method, String scope,
                         String resultJson, String conclusion, String createdAt) {
    }
}
