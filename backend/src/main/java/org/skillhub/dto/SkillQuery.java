package org.skillhub.dto;

import java.util.Set;
import org.skillhub.common.PageQuery;

/**
 * Skill 列表查询条件（对应 {@code GET /api/v1/skills}）。
 *
 * <p>排序字段走**白名单**：{@link #orderBy()} 只会返回受信任的 SQL 片段，
 * 非白名单输入自动回落到默认排序，因此可以安全地交给 {@code ${}} 拼接。
 */
public class SkillQuery extends PageQuery {

    private static final Set<String> SORTABLE = Set.of(
            "updated_at", "created_at", "download_count", "install_count",
            "star_count", "risk_score", "name", "eval_count", "avg_score");

    private String kw;
    private String category;
    private String scene;
    private String riskLevel;
    private String authorKind;
    private String status = "published";
    private String source;
    private String sort = "updated_at";
    private String order = "desc";

    public String getKw() {
        return kw;
    }

    public void setKw(String kw) {
        this.kw = kw;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getAuthorKind() {
        return authorKind;
    }

    public void setAuthorKind(String authorKind) {
        this.authorKind = authorKind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    /** 供 MyBatis 直接嵌入的排序片段（已白名单化，不含用户原始输入）。 */
    public String getOrderBy() {
        String col = SORTABLE.contains(sort) ? sort : "updated_at";
        String dir = "asc".equalsIgnoreCase(order) ? "ASC" : "DESC";
        // 加第二排序键保证分页稳定（同名时不会来回跳）
        return col + " " + dir + ", id DESC";
    }

    /** LIKE 参数（Java 侧拼通配符，避免 SQL 里做字符串拼接）。 */
    public String getLikeKw() {
        return kw == null || kw.isBlank() ? null : "%" + kw.trim() + "%";
    }

    /** {@code status=all} 表示不过滤。 */
    public String getStatusFilter() {
        return (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) ? null : status;
    }
}
