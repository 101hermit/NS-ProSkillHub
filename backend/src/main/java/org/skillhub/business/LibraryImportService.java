package org.skillhub.business;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.skillhub.config.SiteProperties;
import org.skillhub.mapper.AppMetaMapper;
import org.skillhub.mapper.CategoryMapper;
import org.skillhub.mapper.ContributorMapper;
import org.skillhub.mapper.RiskMapper;
import org.skillhub.mapper.SkillFileMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.mapper.SkillVersionMapper;
import org.skillhub.model.Category;
import org.skillhub.model.Contributor;
import org.skillhub.model.RiskAudit;
import org.skillhub.model.RiskSignal;
import org.skillhub.model.Skill;
import org.skillhub.model.SkillFile;
import org.skillhub.model.SkillVersion;
import org.skillhub.port.ClockPort;
import org.skillhub.port.LibraryPort;
import org.skillhub.support.Categories;
import org.skillhub.support.RiskScanner;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把统一 Skill 库（{@code skills/}）导入数据库。
 *
 * <p>为什么用 Java 导入器而不是几百 KB 的 {@code data.sql}：
 * <ul>
 *   <li>SKILL.md 里有引号、反斜杠、分号，写进 SQL 字面量极易被切坏</li>
 *   <li>同一份库要在 H2 与 MySQL 上都成立，SQL 方言差异会立刻暴露</li>
 *   <li>导入同时做风险扫描、文件摘要、分类计数 —— 这些是计算，不是数据</li>
 * </ul>
 *
 * <p>幂等：以 {@code skill} 表是否为空为开关。要强制重灌，删除 {@code ./data/db} 即可。
 */
@Service
public class LibraryImportService {

    private static final Logger log = LoggerFactory.getLogger(LibraryImportService.class);
    private static final int BATCH_LOG_EVERY = 50;

    /**
     * 导入进度（供 /api/v1/health 与 start 脚本使用）。
     *
     * <p>为什么要有这个：全新机器上数据库是空的，服务"能访问"比"库导完"早约 7 秒。
     * 如果健康检查只回答 ready=true，用户看到的就是一个空站点，会以为装了但坏了。
     * 把 state 暴露出来，启动脚本就能说"正在导入 150/375"，页面也能说明真实进度。
     *
     * <p>取值：idle（未开始）/ running（导入中）/ done（已完成）/ skipped（库已存在，跳过）
     * / failed（失败，message 里有人话原因）。
     *
     * <p>用 volatile 而不是 synchronized：只写一次读多次的进度标志，读端只做展示，不做决策。
     */
    private volatile String state = "idle";
    private volatile int progress = 0;
    private volatile int total = 0;
    private volatile String stateMessage = "尚未开始";

    private final LibraryPort library;
    private final SkillMapper skillMapper;
    private final SkillFileMapper fileMapper;
    private final SkillVersionMapper versionMapper;
    private final RiskMapper riskMapper;
    private final CategoryMapper categoryMapper;
    private final ContributorMapper contributorMapper;
    private final AppMetaMapper appMetaMapper;
    private final RiskScanner riskScanner;
    private final ClockPort clock;
    private final SiteProperties props;

    public LibraryImportService(LibraryPort library, SkillMapper skillMapper, SkillFileMapper fileMapper,
                                SkillVersionMapper versionMapper, RiskMapper riskMapper,
                                CategoryMapper categoryMapper, ContributorMapper contributorMapper,
                                AppMetaMapper appMetaMapper, RiskScanner riskScanner,
                                ClockPort clock, SiteProperties props) {
        this.library = library;
        this.skillMapper = skillMapper;
        this.fileMapper = fileMapper;
        this.versionMapper = versionMapper;
        this.riskMapper = riskMapper;
        this.categoryMapper = categoryMapper;
        this.contributorMapper = contributorMapper;
        this.appMetaMapper = appMetaMapper;
        this.riskScanner = riskScanner;
        this.clock = clock;
        this.props = props;
    }

    /** 导入结果。 */
    public record Report(boolean imported, int skillCount, int fileCount, int signalCount,
                         long elapsedMs, String message) {
    }

    /**
     * 库为空才导入（{@code force=true} 时无视现有数据，用于测试）。
     */
    @Transactional
    public Report importIfEmpty(boolean force) {
        seedCategories();
        long existing = skillMapper.countAll();
        if (!force && existing > 0) {
            String msg = "Skill 库已存在（" + existing + " 个），跳过导入";
            log.info(msg);
            state = "skipped";
            stateMessage = msg;
            return new Report(false, (int) existing, 0, 0, 0, msg);
        }
        if (!library.available()) {
            String msg = "找不到 Skill 库目录（" + library.root() + "），站点将以空库启动。"
                    + "怎么办：在工程根执行 node tools/sync-skill-library.mjs";
            log.warn(msg);
            return new Report(false, 0, 0, 0, 0, msg);
        }

        long start = System.currentTimeMillis();
        List<LibraryPort.Entry> entries = library.catalog();
        total = entries.size();
        progress = 0;
        state = "running";
        stateMessage = "正在导入 Skill 库 0/" + total;
        if (entries.isEmpty()) {
            String msg = "Skill 库目录存在但没有条目（catalog.json 为空）";
            log.warn(msg);
            return new Report(false, 0, 0, 0, System.currentTimeMillis() - start, msg);
        }

        int fileTotal = 0;
        int signalTotal = 0;
        int index = 0;
        Set<String> contributorKeys = new HashSet<>();

        for (LibraryPort.Entry entry : entries) {
            index++;
            LibraryPort.SkillContent content = library.read(entry.slug());
            if (content == null) {
                log.warn("跳过无法读取的 Skill：{}", entry.slug());
                continue;
            }
            String skillId = "lib:" + entry.slug();
            Skill skill = toSkill(entry, content, skillId);
            skillMapper.insert(skill);

            List<SkillFile> files = new ArrayList<>(content.files().size());
            for (LibraryPort.FileRef ref : content.files()) {
                SkillFile f = new SkillFile();
                f.skillId = skillId;
                f.path = ref.path();
                f.bytes = ref.bytes();
                f.sha256 = ref.sha256();
                f.isPrimary = "SKILL.md".equals(ref.path());
                f.createdAt = clock.now();
                files.add(f);
            }
            if (!files.isEmpty()) {
                fileMapper.insertBatch(files);
                fileTotal += files.size();
            }

            SkillVersion version = new SkillVersion();
            version.skillId = skillId;
            version.version = skill.version;
            version.content = content.content();
            version.notes = "来自统一 Skill 库（" + props.getSiteName() + "）";
            version.sizeBytes = content.contentBytes();
            version.origin = "library";
            versionMapper.insert(version);

            RiskScanner.Report scan = riskScanner.scan("SKILL.md", content.content());
            signalTotal += writeAudit(skillId, scan);

            registerContributor(entry, contributorKeys);

            progress = index;
            stateMessage = "正在导入 Skill 库 " + index + "/" + total;
            if (index % BATCH_LOG_EVERY == 0) {
                log.info("已导入 {}/{} 个 Skill …", index, entries.size());
            }
        }

        refreshCategoryCounts();
        String libraryVersion = library.libraryVersion();
        putMeta("library_version", libraryVersion);
        putMeta("library_imported_at", org.skillhub.support.Dates.format(clock.now()));
        putMeta("library_skill_count", String.valueOf(index));

        long elapsed = System.currentTimeMillis() - start;
        String msg = "导入完成：" + index + " 个 Skill / " + fileTotal + " 个文件 / "
                + signalTotal + " 条风险信号，耗时 " + elapsed + " ms";
        log.info(msg);
        progress = index;
        state = "done";
        stateMessage = msg;
        return new Report(true, index, fileTotal, signalTotal, elapsed, msg);
    }

    /** 分类字典的 upsert（先查后写，不用方言特有的 UPSERT 语法）。 */
    public void seedCategories() {
        for (Categories.Def def : Categories.ALL) {
            Category c = new Category();
            c.code = def.code();
            c.label = def.label();
            c.kind = def.kind();
            c.sceneCode = def.sceneCode();
            c.sortNo = def.sortNo();
            c.skillCount = 0;
            Category existing = categoryMapper.findByCode(def.code());
            if (existing == null) {
                categoryMapper.insert(c);
            } else {
                c.skillCount = existing.skillCount == null ? 0 : existing.skillCount;
                categoryMapper.update(c);
            }
        }
    }

    public void refreshCategoryCounts() {
        for (Categories.Def def : Categories.ALL) {
            categoryMapper.updateCount(def.code(), (int) skillMapper.countByCategoryCode(def.code()));
        }
    }

    public void putMeta(String key, String value) {
        if (appMetaMapper.get(key) == null) {
            appMetaMapper.insert(key, value);
        } else {
            appMetaMapper.update(key, value);
        }
    }

    // ------------------------------------------------------------------ 内部

    private Skill toSkill(LibraryPort.Entry e, LibraryPort.SkillContent content, String skillId) {
        Skill s = new Skill();
        s.skillId = skillId;
        s.slug = e.slug();
        s.name = e.name();
        s.title = titleOf(e, content.content());
        s.description = Texts.truncate(e.description(), 4000);
        s.categoryCode = Categories.of(e.category()).code();
        s.sceneCode = Texts.firstNonBlank(e.scene(), Categories.of(e.category()).sceneCode());
        s.tags = Texts.truncate(e.tags(), 500);
        s.version = Texts.firstNonBlank(e.version(), "1.0.0");
        s.source = Texts.firstNonBlank(e.source(), "library");
        s.sourceUrl = e.sourceUrl();
        s.sourceRepo = e.sourceRepo();
        s.license = Texts.firstNonBlank(e.license(), "UNKNOWN");
        s.author = Texts.firstNonBlank(e.author(), "library");
        s.authorKind = "personal".equals(e.authorKind()) ? "personal" : "department";
        s.riskScore = 0;
        s.riskLevel = "unrated";
        s.status = "published";
        s.content = content.content();
        s.contentBytes = content.contentBytes();
        s.fileCount = content.files().size();
        s.packageBytes = e.bytes();
        s.contentSha256 = content.contentSha256();
        s.downloadCount = 0;
        s.installCount = 0;
        s.starCount = 0;
        s.evalCount = 0;
        s.avgScore = java.math.BigDecimal.ZERO;
        s.skillsetCode = "default";
        s.createdAt = clock.now();
        s.updatedAt = clock.now();
        return s;
    }

    /** 标题取正文的第一个一级标题，没有就用 name。 */
    private static String titleOf(LibraryPort.Entry e, String content) {
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (t.startsWith("# ")) {
                return Texts.truncate(t.substring(2).trim(), 190);
            }
        }
        return Texts.truncate(e.name(), 190);
    }

    private void registerContributor(LibraryPort.Entry e, Set<String> processed) {
        String author = Texts.firstNonBlank(e.author(), "library");
        String key = e.source() + "|" + author;
        if (!processed.add(key)) {
            return;
        }
        if (contributorMapper.findOne(e.source(), author) != null) {
            return;
        }
        Contributor c = new Contributor();
        c.source = Texts.firstNonBlank(e.source(), "library");
        c.author = author;
        c.name = author;
        c.kind = "personal".equals(e.authorKind()) ? "personal" : "department";
        c.description = "来自 Skill 库的上游作者（" + Texts.firstNonBlank(e.sourceRepo(), "unknown") + "）";
        c.gitProfile = e.sourceUrl() == null ? "" : e.sourceUrl();
        contributorMapper.insert(c);
    }

    private int writeAudit(String skillId, RiskScanner.Report scan) {
        RiskAudit audit = new RiskAudit();
        audit.skillId = skillId;
        audit.riskScore = scan.score();
        audit.riskLevel = scan.level();
        audit.engine = "rule-1.0";
        audit.signalTotal = scan.signalTotal();
        audit.dimensionJson = toJson(scan.dimensionScores());
        audit.summary = scan.hits().isEmpty() ? "无风险信号"
                : "命中指标：" + String.join("、",
                        scan.hits().stream().map(RiskScanner.Hit::code).distinct().toList());
        audit.conclusion = conclusionOf(scan);
        riskMapper.insertAudit(audit);

        Skill patch = new Skill();
        patch.skillId = skillId;
        patch.riskScore = scan.score();
        patch.riskLevel = scan.level();
        skillMapper.updateSelective(patch);

        if (scan.hits().isEmpty()) {
            return 0;
        }
        List<RiskSignal> signals = new ArrayList<>(scan.hits().size());
        for (RiskScanner.Hit h : scan.hits()) {
            RiskSignal s = new RiskSignal();
            s.auditId = audit.id;
            s.signalCode = h.code();
            s.indicator = h.indicator();
            s.dimension = h.dimension();
            s.severity = h.severity();
            s.weight = h.weight();
            s.filePath = h.filePath();
            s.lineNo = h.lineNo();
            s.remediation = h.remediation();
            signals.add(s);
        }
        riskMapper.insertSignals(signals);
        return signals.size();
    }

    private static String conclusionOf(RiskScanner.Report scan) {
        return "导入时评估（engine rule-1.0）：" + RiskScanner.levelLabel(scan.level())
                + "，" + scan.signalTotal() + " 条信号，涉及 " + scan.dimensionScores().size() + " 个维度。";
    }

    private static String toJson(Map<String, Integer> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 供动作层查询"库导入状态"。 */
    public Map<String, Object> describe() {
        return Map.of(
                "available", library.available(),
                "root", library.root().toString(),
                "version", library.libraryVersion(),
                "entries", library.catalog().size(),
                "skillCount", skillMapper.countAll(),
                "state", state,
                "progress", progress,
                "total", total,
                "message", stateMessage);
    }

    /**
     * 启动阶段捕获到异常时调用，让 /health 能说清"库为什么是空的"。
     *
     * <p>失败不改状态为 done：前端与启动脚本据此仍会提示，而不是静默通过。
     */
    public void markFailed(String reason) {
        state = "failed";
        stateMessage = "导入失败：" + (reason == null || reason.isBlank() ? "未知原因" : reason);
    }
}
