package org.skillhub.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.config.NetEgress;
import org.skillhub.dto.ProDtos;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.port.AgentPort;
import org.skillhub.port.McpPort;
import org.springframework.stereotype.Service;

/**
 * 连通性矩阵：把 <b>每个 Agent × 每个 MCP</b> 都探一次，形成一张"哪里通、哪里不通、为什么"的表。
 *
 * <p>这是专业版与标准版的第一个区别：标准版只要求"一个 Agent + 一个 MCP 能连通"，
 * 专业版要能回答"在三台模型、两个 MCP、六种场景的组合里，哪些组合能用"。
 *
 * <p>探活口径：任何 HTTP 响应（含 401/403）都算"通道可达"，因为那证明网络与证书都通了；
 * 只有连接失败 / TLS 失败 / 解析失败才算"不通"，并给出可执行的归因。
 */
@Service
public class ConnService {

    private final EvalMapper evalMapper;
    private final AgentPort agentPort;
    private final McpPort mcpPort;
    private final NetEgress egress;

    public ConnService(EvalMapper evalMapper, AgentPort agentPort, McpPort mcpPort, NetEgress egress) {
        this.evalMapper = evalMapper;
        this.agentPort = agentPort;
        this.mcpPort = mcpPort;
        this.egress = egress;
    }

    /** 生成连通矩阵（会真的逐格探活；Agent 与 MCP 数量都很小，串行即可）。 */
    public ProDtos.ConnMatrix matrix() {
        List<MsEntities.AgentRecord> agents = evalMapper.listAgents();
        List<MsEntities.McpRecord> mcps = evalMapper.listMcps();
        List<ProDtos.ConnCell> cells = new ArrayList<>();
        int ok = 0;
        for (MsEntities.AgentRecord a : agents) {
            Map<String, Object> probeA = agentPort.probe(a.code);
            boolean aOk = Boolean.TRUE.equals(probeA.get("ok"));
            int aMs = intOf(probeA.get("latency_ms"));
            for (MsEntities.McpRecord m : mcps) {
                Map<String, Object> probeM = mcpPort.probe(m.code);
                boolean mOk = Boolean.TRUE.equals(probeM.get("ok"));
                int mMs = intOf(probeM.get("latency_ms"));
                boolean both = aOk && mOk;
                if (both) {
                    ok++;
                }
                String detail;
                if (both) {
                    detail = "Agent 与 MCP 都可达";
                } else if (!aOk) {
                    detail = "智能体不可达：" + probeA.getOrDefault("detail", "");
                } else {
                    detail = "MCP 不可达：" + probeM.getOrDefault("detail", "");
                }
                cells.add(new ProDtos.ConnCell(a.code, a.name, m.code, m.name, both, aMs + mMs, detail));
            }
        }
        return new ProDtos.ConnMatrix(
                agents.stream().map(x -> x.code).toList(),
                mcps.stream().map(x -> x.code).toList(),
                cells, ok, cells.size(), egress.describe());
    }

    /** 供页面画热力图：把矩阵压成 {@code [行][列] = {text, color, title}}。 */
    public Map<String, Object> heatmap(ProDtos.ConnMatrix m) {
        List<List<Map<String, Object>>> values = new ArrayList<>();
        for (String agent : m.agents()) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (String mcp : m.mcps()) {
                ProDtos.ConnCell cell = m.cells().stream()
                        .filter(c -> c.agentCode().equals(agent) && c.mcpCode().equals(mcp))
                        .findFirst().orElse(null);
                Map<String, Object> v = new LinkedHashMap<>();
                if (cell == null) {
                    v.put("text", "—");
                    v.put("color", "var(--sk-muted)");
                    v.put("title", "没有这一格");
                } else if (cell.ok()) {
                    v.put("text", cell.latencyMs() + "ms");
                    v.put("color", "var(--sk-ok)");
                    v.put("title", cell.detail());
                } else {
                    v.put("text", "不通");
                    v.put("color", "var(--sk-danger)");
                    v.put("title", cell.detail());
                }
                row.add(v);
            }
            values.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", m.agents());
        out.put("cols", m.mcps());
        out.put("values", values);
        out.put("ok_count", m.okCount());
        out.put("total", m.totalCount());
        return out;
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
