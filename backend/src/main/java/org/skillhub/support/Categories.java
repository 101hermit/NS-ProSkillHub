package org.skillhub.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一分类法（15 类，三系统逐字节一致）。
 *
 * <p>把 openEuler 的 9 个工程分类与 6 个行业场景**并列**在同一层，
 * 这样"格子窗浏览"不需要两套维度。规范见 docs/06 §4。
 */
public final class Categories {

    /** 一个分类：{@code kind} ∈ engineering / domain / general。 */
    public record Def(String code, String label, String kind, String sceneCode, int sortNo) {
    }

    public static final List<Def> ALL = List.of(
            new Def("development", "开发与构建", "engineering", "general", 10),
            new Def("engineering", "工程与编译", "engineering", "general", 20),
            new Def("quality", "质量与校验", "engineering", "general", 30),
            new Def("security", "安全加固", "engineering", "general", 40),
            new Def("performance", "性能优化", "engineering", "general", 50),
            new Def("operations", "监控与运维", "engineering", "general", 60),
            new Def("release", "发布与部署", "engineering", "general", 70),
            new Def("research", "研究与设计", "engineering", "general", 80),
            new Def("education", "教育", "domain", "edu", 110),
            new Def("finance", "金融", "domain", "fin", 120),
            new Def("government", "政务与法务", "domain", "gov", 130),
            new Def("medical", "医疗健康", "domain", "med", 140),
            new Def("media", "新闻传媒", "domain", "news", 150),
            new Def("traffic", "交通物流", "domain", "tra", 160),
            new Def("general", "通用与其他", "general", "general", 900));

    private static final Map<String, Def> BY_CODE = new LinkedHashMap<>();

    static {
        for (Def d : ALL) {
            BY_CODE.put(d.code(), d);
        }
    }

    private Categories() {
    }

    public static Def of(String code) {
        Def d = BY_CODE.get(code == null ? "" : code);
        return d == null ? BY_CODE.get("general") : d;
    }

    public static String label(String code) {
        return of(code).label();
    }

    /** 行业场景列表（供筛选项）。 */
    public static List<Def> domainCategories() {
        return ALL.stream().filter(d -> "domain".equals(d.kind())).toList();
    }
}
