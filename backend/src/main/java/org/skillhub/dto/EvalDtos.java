package org.skillhub.dto;

import java.util.List;
import java.util.Map;

/** 单点实测、指标、汇总、接入注册与连通性的对外形状。 */
public final class EvalDtos {

    private EvalDtos() {
    }

    /** 发起一次单点实测的请求。 */
    public static class RunRequest {
        private String skillId;
        private String agentCode = "local-gateway";
        private String mcpCode = "local-mcp";
        private String sceneCode = "general";
        private String inputText;
        private String expectedText;
        private boolean useMcp;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;

        public String getSkillId() {
            return skillId;
        }

        public void setSkillId(String skillId) {
            this.skillId = skillId;
        }

        public String getAgentCode() {
            return agentCode;
        }

        public void setAgentCode(String agentCode) {
            this.agentCode = agentCode;
        }

        public String getMcpCode() {
            return mcpCode;
        }

        public void setMcpCode(String mcpCode) {
            this.mcpCode = mcpCode;
        }

        public String getSceneCode() {
            return sceneCode;
        }

        public void setSceneCode(String sceneCode) {
            this.sceneCode = sceneCode;
        }

        public String getInputText() {
            return inputText;
        }

        public void setInputText(String inputText) {
            this.inputText = inputText;
        }

        public String getExpectedText() {
            return expectedText;
        }

        public void setExpectedText(String expectedText) {
            this.expectedText = expectedText;
        }

        public boolean isUseMcp() {
            return useMcp;
        }

        public void setUseMcp(boolean useMcp) {
            this.useMcp = useMcp;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    /** 一次实测的对外形状。 */
    public record RunItem(
            String code,
            String skillId,
            String skillName,
            String agentCode,
            String agentName,
            String mcpCode,
            String sceneCode,
            String status,
            String inputText,
            String expectedText,
            String outputText,
            String reasoningText,
            int latencyMs,
            int ttftMs,
            int promptTokens,
            int completionTokens,
            double scoreTotal,
            boolean offline,
            String error,
            String createdAt,
            String finishedAt,
            List<ScoreItem> scores) {
    }

    /** 单个指标分。 */
    public record ScoreItem(String code, String label, String dimension, String dimensionLabel,
                            double value, String display, double weight, String detail) {
    }

    /** 指标字典。 */
    public record MetricItem(String code, String label, String dimension, String dimensionLabel,
                             String unit, boolean higherBetter, double weight, String formula,
                             String description) {
    }

    /** 某个 Skill 的质量评估汇总。 */
    public record Summary(
            String skillId,
            int runCount,
            double avgScore,
            double bestScore,
            double worstScore,
            String bestCode,
            String worstCode,
            List<MetricAvg> metricAverages,
            List<Map<String, Object>> ranking,
            String conclusion) {
    }

    /** 指标均值。 */
    public record MetricAvg(String code, String label, String dimension, double avg, int n) {
    }

    /** Agent / MCP 注册项。 */
    public record AgentItem(String code, String name, String provider, String baseUrl, String model,
                            String apiKeyMasked, double temperature, double topP, int maxTokens,
                            boolean enabled, String status, String lastProbeAt, int lastProbeMs,
                            String lastError) {
    }

    public record McpItem(String code, String name, String transport, String endpoint, String toolName,
                          String description, boolean enabled, String status, String lastProbeAt,
                          int lastProbeMs, String lastError) {
    }

    /** 探活结果。 */
    public record ProbeResult(String code, boolean ok, int latencyMs, String detail, List<String> tools) {
    }

    /** 请求体：新建/更新 Agent。 */
    public static class AgentRequest {
        private String code;
        private String name;
        private String provider = "openai-compatible";
        private String baseUrl;
        private String model;
        private String apiKey;
        private Double temperature = 0.3;
        private Double topP = 1.0;
        private Integer maxTokens = 2048;
        private Boolean enabled = true;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** 请求体：新建/更新 MCP。 */
    public static class McpRequest {
        private String code;
        private String name;
        private String transport = "HTTP";
        private String endpoint;
        private String toolName;
        private String description;
        private Boolean enabled = true;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
