package org.skillhub.business;

import java.util.ArrayList;
import java.util.List;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.config.SiteProperties;
import org.skillhub.dto.SiteDtos;
import org.skillhub.mapper.CategoryMapper;
import org.skillhub.mapper.DocMapper;
import org.skillhub.model.Category;
import org.skillhub.model.DocPage;
import org.skillhub.support.Categories;
import org.skillhub.support.Dates;
import org.springframework.stereotype.Service;

/**
 * 站点目录：分类字典、在线文档、产品元信息与能力开关。
 *
 * <p>它是"站点自身的说明书" —— 前端据此渲染菜单、隐藏未启用的能力、
 * 展示"本版本不做什么"。
 */
@Service
public class CatalogService {

    private final CategoryMapper categoryMapper;
    private final DocMapper docMapper;
    private final SiteProperties props;

    public CatalogService(CategoryMapper categoryMapper, DocMapper docMapper, SiteProperties props) {
        this.categoryMapper = categoryMapper;
        this.docMapper = docMapper;
        this.props = props;
    }

    /** 15 个分类 + 计数（顺序按 {@link Categories} 的 sort_no）。 */
    public List<SiteDtos.CategoryItem> categories() {
        List<Category> rows = categoryMapper.findAll();
        if (rows.isEmpty()) {
            // 极端情况（库还没导入完）：至少返回字典本身，页面不会白屏
            List<SiteDtos.CategoryItem> fallback = new ArrayList<>();
            for (Categories.Def d : Categories.ALL) {
                fallback.add(new SiteDtos.CategoryItem(d.code(), d.label(), d.kind(), d.sceneCode(), 0));
            }
            return fallback;
        }
        return rows.stream()
                .map(c -> new SiteDtos.CategoryItem(c.code, c.label, c.kind, c.sceneCode,
                        c.skillCount == null ? 0 : c.skillCount))
                .toList();
    }

    public List<SiteDtos.DocItem> docs() {
        return docMapper.findCatalog().stream()
                .map(d -> new SiteDtos.DocItem(d.docKey, d.title, d.section, d.summary,
                        d.sortNo == null ? 0 : d.sortNo))
                .toList();
    }

    public SiteDtos.DocDetail doc(String docKey) {
        DocPage page = docMapper.findByKey(docKey);
        if (page == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "文档「" + docKey + "」");
        }
        return new SiteDtos.DocDetail(page.docKey, page.title, page.section, page.summary,
                page.body == null ? "" : page.body, Dates.format(page.updatedAt));
    }

    /** 产品元信息（{@code GET /api/v1/meta}）。 */
    public SiteDtos.Meta meta() {
        return new SiteDtos.Meta(
                props.getProduct(),
                props.getProductName(),
                props.getAbbr(),
                props.getVersion(),
                props.getSiteName(),
                props.getTagline(),
                props.getPort(),
                props.getAccent(),
                "multifunction",
                features(),
                navigation());
    }

    /**
     * 能力开关：{@code enabled=false} 的项前端不渲染菜单，并在「关于」页说明原因。
     *
     * <p>这是三系统"同一套前端、不同能力"的机制：增量只往这里加一行。
     */
    public List<SiteDtos.Capability> features() {
        return List.of(
                new SiteDtos.Capability("auth.login", "登录 / 注册 / 访客", true, ""),
                new SiteDtos.Capability("skill.catalog", "Skill 库浏览与检索", true, ""),
                new SiteDtos.Capability("skill.edit", "Skill 编辑与删除", true, ""),
                new SiteDtos.Capability("skill.contribute", "在线贡献（个人/部门）", true, ""),
                new SiteDtos.Capability("skill.distribute", "分发（ZIP/命令/源码直链）", true, ""),
                new SiteDtos.Capability("risk.scan", "静态风险评估（6 维 32 指标）", true, ""),
                new SiteDtos.Capability("ms.library", "Skill-MS 库语义（逻辑库/索引/约束/事件/查询台）", true, ""),
                new SiteDtos.Capability("ms.query", "查询台（类 SQL 只读查询 + 查询计划）", true, ""),
                new SiteDtos.Capability("eval.live", "在线实测（Agent + MCP，单点）", true, ""),
                new SiteDtos.Capability("eval.quality", "简单质量评估（统计 + 最优化口径）", true, ""),
                new SiteDtos.Capability("access.registry", "接入管理（Agent / MCP 注册与探活）", true, ""),
                new SiteDtos.Capability("eval.matrix", "多维实验矩阵（多 Agent × 多超参 × 多场景）", true, ""),
                new SiteDtos.Capability("conn.matrix", "连通性矩阵（Agent × MCP 逐格探活）", true, ""),
                new SiteDtos.Capability("knowledge.graph", "知识图谱（实体/关系/相似/导出）", true, ""),
                new SiteDtos.Capability("insight.analysis", "知识级分析（描述统计/假设检验/TOPSIS/聚类/回归/AHP）", true, ""));
    }

    /** 侧栏导航（前端按此渲染，保证三系统结构一致）。 */
    public List<String> navigation() {
        return List.of("home", "skills", "contributors", "ms", "eval.live", "eval.quality",
                "conn", "matrix", "kg", "ix", "contribute", "docs", "risk", "about");
    }
}
