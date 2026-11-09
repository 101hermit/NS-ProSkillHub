package org.skillhub.business;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.dto.ProDtos;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.mapper.ProMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.model.MsEntities;
import org.skillhub.model.ProEntities;
import org.skillhub.model.Skill;
import org.skillhub.port.IdPort;
import org.skillhub.support.Categories;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库与知识图谱（专业版独有）。
 *
 * <p><b>不使用图数据库</b>：实体与关系落在两张关系表里（{@code kg_entity} / {@code kg_relation}），
 * 图谱数据在内存里组装后再发给前端用 SVG 画。这样不引入 Neo4j / 向量库，
 * 符合"只用 MySQL 8 与 H2"的硬约束，同时保证图谱是**可导出、可复现**的。
 *
 * <p>实体来源被严格限制在三处（不允许外部注入知识）：
 * <ol>
 *   <li>Skill 库本身（每个 Skill 一个 SKILL 实体）</li>
 *   <li>分类/场景本体（15 个分类 → SKILL 的"能力"与"场景"节点）</li>
 *   <li>接入注册表（AGENT / MCP 节点）</li>
 * </ol>
 *
 * <p>关系类型：
 * <ul>
 *   <li>{@code BELONGS_TO}：Skill → 分类（能力归属）</li>
 *   <li>{@code IN_SCENE}：Skill → 场景</li>
 *   <li>{@code SIMILAR_TO}：同分类且共享标签的 Skill 之间（权重 = 共享标签数）</li>
 *   <li>{@code CO_OCCURS}：共享标签的 Skill 之间（弱关系，权重较低）</li>
 *   <li>{@code EVALUATED_BY}：Skill → Agent（有实测记录时）</li>
 * </ul>
 */
@Service
public class KnowledgeService {

    private static final int MAX_SIMILAR_PAIRS = 6000;

    private final ProMapper proMapper;
    private final SkillMapper skillMapper;
    private final EvalMapper evalMapper;
    private final IdPort idPort;

    public KnowledgeService(ProMapper proMapper, SkillMapper skillMapper, EvalMapper evalMapper,
                            IdPort idPort) {
        this.proMapper = proMapper;
        this.skillMapper = skillMapper;
        this.evalMapper = evalMapper;
        this.idPort = idPort;
    }

    // ================================================================ 重建

    @Transactional
    public Map<String, Object> rebuild() {
        proMapper.clearRelations();
        proMapper.clearEntities();

        List<Skill> skills = skillMapper.listAllCards();
        List<ProEntities.KgEntity> entities = new ArrayList<>();

        // 1) 分类（能力本体）
        for (Categories.Def def : Categories.ALL) {
            if (def.code().equals("general")) {
                continue;
            }
            entities.add(entity("cat:" + def.code(), def.kind().equals("domain") ? "SCENE" : "CAPABILITY",
                    def.label(), def.label(), 2.0, Map.of("kind", def.kind(),
                            "scene", def.sceneCode())));
        }
        // 2) 场景
        for (Categories.Def def : Categories.domainCategories()) {
            entities.add(entity("scene:" + def.sceneCode(), "SCENE", def.label(), def.label(), 2.5,
                    Map.of("scene", def.sceneCode())));
        }
        // 3) Agent / MCP
        for (MsEntities.AgentRecord a : evalMapper.listAgents()) {
            entities.add(entity("agent:" + a.code, "AGENT", a.name, a.model == null ? "" : a.model,
                    1.0, Map.of("code", a.code)));
        }
        for (MsEntities.McpRecord m : evalMapper.listMcps()) {
            entities.add(entity("mcp:" + m.code, "MCP", m.name, m.toolName == null ? "" : m.toolName,
                    1.0, Map.of("code", m.code)));
        }
        // 4) Skill
        for (Skill s : skills) {
            entities.add(entity("skill:" + s.skillId, "SKILL", s.name,
                    Texts.truncate(s.description == null ? "" : s.description, 80), 1.0,
                    Map.of("category", s.categoryCode, "scene", s.sceneCode,
                            "risk", s.riskLevel == null ? "" : s.riskLevel,
                            "evalCount", s.evalCount == null ? 0 : s.evalCount)));
        }
        // 5) 标签
        Set<String> tags = new LinkedHashSet<>();
        for (Skill s : skills) {
            tags.addAll(Texts.splitTags(s.tags));
        }
        for (String tag : tags) {
            entities.add(entity("tag:" + tag, "TAG", tag, "", 0.6, Map.of()));
        }

        int chunk = 150;
        for (int i = 0; i < entities.size(); i += chunk) {
            proMapper.insertEntities(entities.subList(i, Math.min(entities.size(), i + chunk)));
        }

        // 关系
        List<ProEntities.KgRelation> relations = new ArrayList<>();
        for (Skill s : skills) {
            String skillCode = "skill:" + s.skillId;
            if (s.categoryCode != null && !"general".equals(s.categoryCode)) {
                relations.add(relation(skillCode, "cat:" + s.categoryCode, "BELONGS_TO", 1.0,
                        "分类 " + Categories.label(s.categoryCode)));
            }
            if (s.sceneCode != null && !"general".equals(s.sceneCode)) {
                relations.add(relation(skillCode, "scene:" + s.sceneCode, "IN_SCENE", 1.0,
                        "场景 " + CardFactory.sceneLabel(s.sceneCode)));
            }
            for (String tag : Texts.splitTags(s.tags)) {
                relations.add(relation(skillCode, "tag:" + tag, "HAS_TAG", 0.6, "标签 " + tag));
            }
            if (s.evalCount != null && s.evalCount > 0) {
                for (MsEntities.EvalRun r : evalMapper.runsOfSkill(s.skillId)) {
                    relations.add(relation(skillCode, "agent:" + r.agentCode, "EVALUATED_BY", 1.0,
                            "实测 " + r.code));
                }
            }
        }

        // 相似 / 共现：同分类 + 共享标签（限制数量，避免 O(n²) 爆炸）
        Map<String, List<Skill>> byCategory = new LinkedHashMap<>();
        for (Skill s : skills) {
            byCategory.computeIfAbsent(s.categoryCode == null ? "general" : s.categoryCode,
                    k -> new ArrayList<>()).add(s);
        }
        int pairs = 0;
        outer:
        for (List<Skill> group : byCategory.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    if (++pairs > MAX_SIMILAR_PAIRS) {
                        break outer;
                    }
                    Skill a = group.get(i);
                    Skill b = group.get(j);
                    Set<String> ta = new LinkedHashSet<>(Texts.splitTags(a.tags));
                    Set<String> tb = new LinkedHashSet<>(Texts.splitTags(b.tags));
                    ta.retainAll(tb);
                    if (!ta.isEmpty()) {
                        double weight = ta.size() * 1.0;
                        relations.add(relation("skill:" + a.skillId, "skill:" + b.skillId,
                                "SIMILAR_TO", weight, "共享标签：" + String.join("/", ta)));
                    }
                }
            }
        }

        // 去重：kg_relation 上有 (src_code, dst_code, relation_type) 唯一键
        Map<String, ProEntities.KgRelation> unique = new LinkedHashMap<>();
        for (ProEntities.KgRelation r : relations) {
            unique.putIfAbsent(r.srcCode + "|" + r.dstCode + "|" + r.relationType, r);
        }
        List<ProEntities.KgRelation> deduped = new ArrayList<>(unique.values());
        for (int i = 0; i < deduped.size(); i += chunk) {
            proMapper.insertRelations(deduped.subList(i, Math.min(deduped.size(), i + chunk)));
        }

        // 全局快照
        ProEntities.KgSnapshot snap = new ProEntities.KgSnapshot();
        snap.code = idPort.code("kg");
        snap.name = "全局图谱快照";
        snap.scope = "GLOBAL";
        snap.sceneCode = "general";
        snap.entityCount = entities.size();
        snap.relationCount = deduped.size();
        snap.payloadJson = "{\"note\":\"快照只记录规模，图数据请用 /api/v1/kg/graph 或 /export\"}";
        proMapper.insertSnapshot(snap);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entity_count", entities.size());
        out.put("relation_count", deduped.size());
        out.put("skill_count", skills.size());
        out.put("snapshot", snap.code);
        return out;
    }

    // ================================================================ 查询

    /** 图谱数据（可按场景下钻）。{@code limit} 限制返回节点数（按度数取前 N）。 */
    public ProDtos.Graph graph(String scene, int limit) {
        List<ProEntities.KgEntity> entities = proMapper.listEntities();
        List<ProEntities.KgRelation> relations = proMapper.listRelations();

        Set<String> allowed = new LinkedHashSet<>();
        if (scene != null && !scene.isBlank() && !"global".equalsIgnoreCase(scene)) {
            // 场景下钻：只保留该场景的 Skill、该场景节点、以及与之相连的标签/分类
            allowed.add("scene:" + scene);
            for (Skill s : skillMapper.listAllCards()) {
                if (scene.equals(s.sceneCode)) {
                    allowed.add("skill:" + s.skillId);
                }
            }
        } else {
            for (ProEntities.KgEntity e : entities) {
                allowed.add(e.code);
            }
        }

        Map<String, Integer> degree = new LinkedHashMap<>();
        List<ProDtos.GraphEdge> edges = new ArrayList<>();
        for (ProEntities.KgRelation r : relations) {
            boolean srcOk = allowed.contains(r.srcCode);
            boolean dstOk = allowed.contains(r.dstCode);
            // 场景下钻时允许"从场景 Skill 连出去"的一跳
            boolean drill = scene != null && !scene.isBlank() && !"global".equalsIgnoreCase(scene);
            if (!srcOk && !dstOk && !drill) {
                continue;
            }
            if (drill && !srcOk && !dstOk) {
                continue;
            }
            edges.add(new ProDtos.GraphEdge(r.srcCode, r.dstCode, r.relationType,
                    r.weight == null ? 1 : r.weight));
            degree.merge(r.srcCode, 1, Integer::sum);
            degree.merge(r.dstCode, 1, Integer::sum);
        }

        Map<String, ProEntities.KgEntity> byCode = new LinkedHashMap<>();
        for (ProEntities.KgEntity e : entities) {
            if (allowed.contains(e.code) || degree.containsKey(e.code)) {
                byCode.put(e.code, e);
            }
        }

        List<ProDtos.GraphNode> nodes = new ArrayList<>();
        List<ProEntities.KgEntity> sorted = new ArrayList<>(byCode.values());
        sorted.sort(Comparator.comparingInt((ProEntities.KgEntity e) -> degree.getOrDefault(e.code, 0))
                .reversed());
        int max = Math.max(20, Math.min(limit, 300));
        for (ProEntities.KgEntity e : sorted) {
            if (nodes.size() >= max) {
                break;
            }
            nodes.add(new ProDtos.GraphNode(e.code, e.entityType, e.name, e.label,
                    e.weight == null ? 1 : e.weight, degree.getOrDefault(e.code, 0)));
        }
        Set<String> nodeCodes = new LinkedHashSet<>();
        nodes.forEach(n -> nodeCodes.add(n.code()));
        List<ProDtos.GraphEdge> kept = edges.stream()
                .filter(x -> nodeCodes.contains(x.source()) && nodeCodes.contains(x.target()))
                .toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("entities_total", entities.size());
        stats.put("relations_total", relations.size());
        stats.put("nodes", nodes.size());
        stats.put("edges", kept.size());
        stats.put("scope", scene == null || scene.isBlank() ? "global" : scene);
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (ProDtos.GraphNode n : nodes) {
            byType.merge(n.type(), 1, Integer::sum);
        }
        stats.put("by_type", byType);
        return new ProDtos.Graph(nodes, kept, stats);
    }

    public List<ProEntities.KgEntity> entities(String type, String kw, int limit) {
        return proMapper.searchEntities(
                type == null || type.isBlank() ? null : type.toUpperCase(Locale.ROOT),
                kw == null || kw.isBlank() ? null : "%" + kw.trim() + "%",
                Math.max(1, Math.min(limit, 500)));
    }

    public List<ProEntities.KgRelation> relations(String src) {
        if (src == null || src.isBlank()) {
            return proMapper.listRelations();
        }
        return proMapper.relationsOf(src);
    }

    /** 相似 Skill 推荐（读 SIMILAR_TO，按权重降序）。 */
    public List<ProDtos.SimilarItem> similar(String skillId, int top) {
        String code = skillId.startsWith("skill:") ? skillId : "skill:" + skillId;
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProEntities.KgRelation r : proMapper.relationsOfType(code, "SIMILAR_TO")) {
            String other = r.srcCode.equals(code) ? r.dstCode : r.srcCode;
            String otherId = other.startsWith("skill:") ? other.substring(6) : other;
            Skill s = skillMapper.findCardBySkillId(otherId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill_id", otherId);
            row.put("name", s == null ? otherId : s.name);
            row.put("score", r.weight == null ? 0 : r.weight);
            row.put("why", r.evidence == null ? "" : r.evidence);
            out.add(row);
        }
        out.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        int limit = Math.max(1, Math.min(top, 20));
        return out.stream().limit(limit)
                .map(m -> new ProDtos.SimilarItem((String) m.get("skill_id"), (String) m.get("name"),
                        (double) m.get("score"), (String) m.get("why")))
                .toList();
    }

    // ================================================================ 导出

    /** 导出图谱：{@code json} / {@code csv} / {@code graphml}。 */
    public String export(String format, String scene) {
        ProDtos.Graph g = graph(scene, 300);
        String fmt = format == null ? "json" : format.toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case "csv" -> csv(g);
            case "graphml" -> graphml(g);
            default -> json(g);
        };
    }

    private String csv(ProDtos.Graph g) {
        StringBuilder sb = new StringBuilder("kind,source,target,type,weight,name\n");
        for (ProDtos.GraphNode n : g.nodes()) {
            sb.append("node,").append(esc(n.code())).append(",,").append(esc(n.type())).append(',')
                    .append(n.weight()).append(',').append(esc(n.name())).append('\n');
        }
        for (ProDtos.GraphEdge e : g.edges()) {
            sb.append("edge,").append(esc(e.source())).append(',').append(esc(e.target())).append(',')
                    .append(esc(e.type())).append(',').append(e.weight()).append(",\n");
        }
        return sb.toString();
    }

    private String graphml(ProDtos.Graph g) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n");
        sb.append("  <key id=\"t\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"n\" for=\"node\" attr.name=\"name\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"w\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
        sb.append("  <graph id=\"G\" edgedefault=\"undirected\">\n");
        for (ProDtos.GraphNode n : g.nodes()) {
            sb.append("    <node id=\"").append(esc(n.code())).append("\">")
                    .append("<data key=\"t\">").append(esc(n.type())).append("</data>")
                    .append("<data key=\"n\">").append(esc(n.name())).append("</data>")
                    .append("</node>\n");
        }
        int i = 0;
        for (ProDtos.GraphEdge e : g.edges()) {
            sb.append("    <edge id=\"e").append(++i).append("\" source=\"").append(esc(e.source()))
                    .append("\" target=\"").append(esc(e.target())).append("\">")
                    .append("<data key=\"w\">").append(e.weight()).append("</data>")
                    .append("</edge>\n");
        }
        sb.append("  </graph>\n</graphml>\n");
        return sb.toString();
    }

    private String json(ProDtos.Graph g) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(g);
        } catch (Exception e) {
            throw ApiException.ofMessage(ErrorCode.INTERNAL_ERROR, "导出图谱失败：" + e.getMessage());
        }
    }

    /** 图谱快照列表（每次重建产生一个）。 */
    public List<ProEntities.KgSnapshot> snapshots() {
        return proMapper.listSnapshots();
    }
    // ================================================================ 内部

    private static ProEntities.KgEntity entity(String code, String type, String name, String label,
                                               double weight, Map<String, Object> props) {
        ProEntities.KgEntity e = new ProEntities.KgEntity();
        e.code = code;
        e.entityType = type;
        e.name = Texts.truncate(name, 190);
        e.label = Texts.truncate(label, 190);
        e.weight = weight;
        try {
            e.propsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(props);
        } catch (Exception ignore) {
            e.propsJson = "{}";
        }
        return e;
    }

    private static ProEntities.KgRelation relation(String src, String dst, String type, double weight,
                                                   String evidence) {
        ProEntities.KgRelation r = new ProEntities.KgRelation();
        r.srcCode = src;
        r.dstCode = dst;
        r.relationType = type;
        r.weight = weight;
        r.evidence = Texts.truncate(evidence, 500);
        return r;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace(",", " ");
    }
}
