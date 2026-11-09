package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.config.NetEgress;
import org.skillhub.dto.EvalDtos;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.AgentPort;
import org.skillhub.port.McpPort;
import org.skillhub.support.Dates;
import org.skillhub.support.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接入管理：Agent 与 MCP 的注册、探活、以及"出网到底通不通"的自述。
 *
 * <p>标准版只要求"能连通即可"：一个 Agent（DeepSeek-flash）+ 一个 MCP 服务。
 * 多 Agent × 多 MCP 的连通矩阵属于专业版。
 */
@Service
public class AccessService {

    private final EvalMapper evalMapper;
    private final AgentPort agentPort;
    private final McpPort mcpPort;
    private final NetEgress egress;

    public AccessService(EvalMapper evalMapper, AgentPort agentPort, McpPort mcpPort, NetEgress egress) {
        this.evalMapper = evalMapper;
        this.agentPort = agentPort;
        this.mcpPort = mcpPort;
        this.egress = egress;
    }

    // ================================================================ Agent

    public List<EvalDtos.AgentItem> agents() {
        return evalMapper.listAgents().stream().map(AccessService::toItem).toList();
    }

    @Transactional
    public EvalDtos.AgentItem saveAgent(EvalDtos.AgentRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code");
        }
        String code = req.getCode().trim().toLowerCase(java.util.Locale.ROOT);
        MsEntities.AgentRecord old = evalMapper.findAgent(code);
        MsEntities.AgentRecord rec = new MsEntities.AgentRecord();
        rec.code = code;
        rec.name = Texts.firstNonBlank(req.getName(), old == null ? null : old.name, code);
        rec.provider = Texts.firstNonBlank(req.getProvider(), old == null ? null : old.provider,
                "openai-compatible");
        // 更新语义：没传的字段沿用旧值。否则一次只改 Key 的请求会把 baseUrl/model 清空，
        // 表现为「探活突然报 URI with undefined scheme」（本轮踩过这个坑）。
        rec.baseUrl = pick(req.getBaseUrl(), old == null ? null : old.baseUrl);
        rec.model = pick(req.getModel(), old == null ? null : old.model);
        rec.apiKey = pick(req.getApiKey(), old == null ? null : old.apiKey);
        rec.temperature = java.math.BigDecimal.valueOf(req.getTemperature() != null ? req.getTemperature()
                : (old != null && old.temperature != null ? old.temperature.doubleValue() : 0.3));
        rec.topP = java.math.BigDecimal.valueOf(req.getTopP() != null ? req.getTopP()
                : (old != null && old.topP != null ? old.topP.doubleValue() : 1.0));
        rec.maxTokens = req.getMaxTokens() != null ? req.getMaxTokens()
                : (old != null && old.maxTokens != null ? old.maxTokens : 2048);
        rec.enabled = req.getEnabled() != null ? req.getEnabled()
                : (old == null || !Boolean.FALSE.equals(old.enabled));
        if (old == null) {
            evalMapper.insertAgent(rec);
        } else {
            evalMapper.updateAgent(rec);
        }
        return toItem(evalMapper.findAgent(code));
    }

    @Transactional
    public void deleteAgent(String code) {
        if (evalMapper.findAgent(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "智能体「" + code + "」");
        }
        evalMapper.deleteAgent(code);
    }

    public EvalDtos.ProbeResult probeAgent(String code) {
        Map<String, Object> raw = agentPort.probe(code);
        return new EvalDtos.ProbeResult(code,
                Boolean.TRUE.equals(raw.get("ok")),
                intOf(raw.get("latency_ms")),
                String.valueOf(raw.getOrDefault("detail", "")),
                List.of());
    }

    // ================================================================ MCP

    public List<EvalDtos.McpItem> mcps() {
        return evalMapper.listMcps().stream().map(AccessService::toItem).toList();
    }

    @Transactional
    public EvalDtos.McpItem saveMcp(EvalDtos.McpRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "code");
        }
        String code = req.getCode().trim().toLowerCase(java.util.Locale.ROOT);
        MsEntities.McpRecord old = evalMapper.findMcp(code);
        MsEntities.McpRecord rec = new MsEntities.McpRecord();
        rec.code = code;
        rec.name = Texts.firstNonBlank(req.getName(), old == null ? null : old.name, code);
        rec.transport = Texts.firstNonBlank(req.getTransport(), old == null ? null : old.transport, "HTTP");
        rec.endpoint = pick(req.getEndpoint(), old == null ? null : old.endpoint);
        rec.toolName = pick(req.getToolName(), old == null ? null : old.toolName);
        rec.description = Texts.truncate(
                Texts.firstNonBlank(req.getDescription(), old == null ? null : old.description), 500);
        rec.headersJson = old == null || old.headersJson == null ? "{}" : old.headersJson;
        rec.enabled = req.getEnabled() != null ? req.getEnabled()
                : (old == null || !Boolean.FALSE.equals(old.enabled));
        if (old == null) {
            evalMapper.insertMcp(rec);
        } else {
            evalMapper.updateMcp(rec);
        }
        return toItem(evalMapper.findMcp(code));
    }

    @Transactional
    public void deleteMcp(String code) {
        if (evalMapper.findMcp(code) == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "MCP 服务「" + code + "」");
        }
        evalMapper.deleteMcp(code);
    }

    public EvalDtos.ProbeResult probeMcp(String code) {
        Map<String, Object> raw = mcpPort.probe(code);
        List<String> tools = mcpPort.tools(code);
        return new EvalDtos.ProbeResult(code,
                Boolean.TRUE.equals(raw.get("ok")),
                intOf(raw.get("latency_ms")),
                String.valueOf(raw.getOrDefault("detail", "")),
                tools);
    }

    /** 出网自述（写进 health，页面「接入管理」也读它）。 */
    public Map<String, Object> egressInfo() {
        return egress.describe();
    }

    /** 一次"体检"：把出网 + 每个 Agent/MCP 的连通性汇总成一张表。 */
    public Map<String, Object> doctor() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MsEntities.AgentRecord a : evalMapper.listAgents()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("kind", "agent");
            r.put("code", a.code);
            r.put("name", a.name);
            Map<String, Object> p = agentPort.probe(a.code);
            r.put("ok", p.get("ok"));
            r.put("latency_ms", p.get("latency_ms"));
            r.put("detail", p.get("detail"));
            rows.add(r);
        }
        for (MsEntities.McpRecord m : evalMapper.listMcps()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("kind", "mcp");
            r.put("code", m.code);
            r.put("name", m.name);
            Map<String, Object> p = mcpPort.probe(m.code);
            r.put("ok", p.get("ok"));
            r.put("latency_ms", p.get("latency_ms"));
            r.put("detail", p.get("detail"));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("egress", egress.describe());
        out.put("rows", rows);
        out.put("ok_count", rows.stream().filter(x -> Boolean.TRUE.equals(x.get("ok"))).count());
        out.put("total", rows.size());
        return out;
    }

    // ================================================================ 工具

    /** 更新语义：传了就改，没传（null 或空白）就沿用旧值。 */
    private static String pick(String incoming, String fallback) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return fallback == null ? "" : fallback;
    }

    private static EvalDtos.AgentItem toItem(MsEntities.AgentRecord r) {
        if (r == null) {
            return null;
        }
        return new EvalDtos.AgentItem(r.code, r.name, r.provider, r.baseUrl, r.model,
                mask(r.apiKey), dbl(r.temperature), dbl(r.topP),
                r.maxTokens == null ? 0 : r.maxTokens,
                Boolean.TRUE.equals(r.enabled), r.status, Dates.format(r.lastProbeAt),
                r.lastProbeMs == null ? 0 : r.lastProbeMs, nz(r.lastError));
    }

    private static EvalDtos.McpItem toItem(MsEntities.McpRecord r) {
        if (r == null) {
            return null;
        }
        return new EvalDtos.McpItem(r.code, r.name, r.transport, r.endpoint, r.toolName,
                nz(r.description), Boolean.TRUE.equals(r.enabled), r.status,
                Dates.format(r.lastProbeAt), r.lastProbeMs == null ? 0 : r.lastProbeMs, nz(r.lastError));
    }

    /** Key 展示脱敏：{@code sk-****abcd}；{@code env:XXX} 原样显示（它本身不是秘密）。 */
    private static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.startsWith("env:")) {
            return key;
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double dbl(java.math.BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
