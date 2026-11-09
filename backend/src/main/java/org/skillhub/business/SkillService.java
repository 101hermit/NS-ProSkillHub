package org.skillhub.business;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.skillhub.common.Actor;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageResult;
import org.skillhub.config.SiteProperties;
import org.skillhub.dto.SkillDtos;
import org.skillhub.dto.SiteDtos;
import org.skillhub.dto.SkillQuery;
import org.skillhub.mapper.RiskMapper;
import org.skillhub.mapper.SkillFileMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.mapper.SkillVersionMapper;
import org.skillhub.model.RiskAudit;
import org.skillhub.model.Skill;
import org.skillhub.model.SkillFile;
import org.skillhub.model.SkillVersion;
import org.skillhub.port.LibraryPort;
import org.skillhub.port.PackagePort;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Categories;
import org.skillhub.support.Dates;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill 的读与写（资源站的核心域服务）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>读：列表分页、详情、版本、评估报告、安装方式、ZIP 下载、关键词检索</li>
 *   <li>写：编辑（含版本快照）、软删/归档、恢复</li>
 *   <li>不做：贡献流程（{@link ContributionService}）、库导入（{@link LibraryImportService}）</li>
 * </ul>
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final int SEARCH_CANDIDATE_LIMIT = 200;

    private final SkillMapper skillMapper;
    private final SkillFileMapper fileMapper;
    private final SkillVersionMapper versionMapper;
    private final RiskMapper riskMapper;
    private final RiskService riskService;
    private final org.skillhub.support.RiskScanner riskScanner;
    private final OpLogService opLogService;
    private final CardFactory cardFactory;
    private final PackagePort packagePort;
    private final LibraryPort libraryPort;
    private final SiteProperties props;

    /**
     * 真正生效的端口。
     *
     * <p>{@code SiteProperties#getPort()} 是 {@code application.yml} 里写的<b>默认值</b>；
     * 用 {@code start.cmd 30810}、{@code SERVER_PORT=30810} 或 {@code -Dserver.port} 改过之后，
     * 它和真正监听的端口就不是一回事了。安装命令与下载直链是要<b>交给用户复制粘贴</b>的绝对 URL，
     * 拼错端口 = 用户拿到一条必然失败的命令，而默认端口下永远测不出这个问题。
     *
     * <p>所以这里让 Spring 先解析（yaml / 环境变量 / 系统属性三种来源它都认），解析不到才退回默认值。
     */
    @org.springframework.beans.factory.annotation.Value("${server.port:0}")
    private int effectivePort;
    private final MsEngine msEngine;

    public SkillService(SkillMapper skillMapper, SkillFileMapper fileMapper,
                        SkillVersionMapper versionMapper, RiskMapper riskMapper,
                        RiskService riskService, org.skillhub.support.RiskScanner riskScanner,
                        OpLogService opLogService, CardFactory cardFactory,
                        PackagePort packagePort, LibraryPort libraryPort, SiteProperties props,
                        MsEngine msEngine) {
        this.skillMapper = skillMapper;
        this.fileMapper = fileMapper;
        this.versionMapper = versionMapper;
        this.riskMapper = riskMapper;
        this.riskService = riskService;
        this.riskScanner = riskScanner;
        this.opLogService = opLogService;
        this.cardFactory = cardFactory;
        this.packagePort = packagePort;
        this.libraryPort = libraryPort;
        this.props = props;
        this.msEngine = msEngine;
    }

    // ------------------------------------------------------------------ 读

    public PageResult<SkillDtos.Card> page(SkillQuery query) {
        long total = skillMapper.countCards(query);
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<Skill> rows = skillMapper.listCards(query);
        return PageResult.of(query, total, cardFactory.cards(rows));
    }

    public SkillDtos.Detail detail(String skillId) {
        Skill skill = require(skillId);
        SkillDtos.Card card = cardFactory.card(skill);
        List<SkillDtos.FileItem> files = fileMapper.findBySkillId(skill.skillId).stream()
                .map(f -> new SkillDtos.FileItem(f.path, f.bytes == null ? 0 : f.bytes,
                        Boolean.TRUE.equals(f.isPrimary)))
                .toList();
        RiskAudit audit = riskMapper.findLatestMeta(skill.skillId);
        return new SkillDtos.Detail(
                card,
                skill.content == null ? "" : skill.content,
                files,
                riskService.toAudit(audit, audit == null ? List.of() : riskMapper.findSignals(audit.id)),
                installMethods(skill),
                versions(skill.skillId),
                related(skill));
    }

    public List<SkillDtos.Version> versions(String skillId) {
        require(skillId);
        return versionMapper.findBySkillId(skillId).stream()
                .map(v -> new SkillDtos.Version(v.version, v.notes == null ? "" : v.notes,
                        v.sizeBytes == null ? 0 : v.sizeBytes, v.origin, Dates.format(v.createdAt)))
                .toList();
    }

    /** 单个版本的正文（不存在时回落到当前正文，保证下载永远有内容）。 */
    public String versionContent(String skillId, String version) {
        Skill skill = require(skillId);
        if (version == null || version.isBlank()) {
            return skill.content == null ? "" : skill.content;
        }
        SkillVersion v = versionMapper.findBySkillIdAndVersion(skillId, version);
        if (v == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "版本 " + version);
        }
        return v.content == null ? "" : v.content;
    }

    public SkillDtos.Audit audit(String skillId) {
        require(skillId);
        RiskAudit audit = riskMapper.findLatestMeta(skillId);
        if (audit == null) {
            return riskService.toAudit(null, List.of());
        }
        return riskService.toAudit(audit, riskMapper.findSignals(audit.id));
    }

    /** 本站基地址：交给用户粘贴的绝对 URL 都基于它（见 effectivePort 的注释）。 */
    private String selfBase() {
        return "http://127.0.0.1:" + (effectivePort > 0 ? effectivePort : props.getPort());
    }

    /** 三种分发方式（doc 要求：下载 ZIP / 安装命令 / 源码直链）。 */
    public List<SkillDtos.InstallMethod> installMethods(Skill skill) {
        String base = selfBase();
        List<SkillDtos.InstallMethod> out = new ArrayList<>();
        out.add(new SkillDtos.InstallMethod("download", "下载 ZIP",
                "得到一个压缩包，解压后把里面的文件夹放进 ~/.claude/skills。",
                base + "/api/v1/skills/" + urlEncode(skill.skillId) + "/download", "url"));
        out.add(new SkillDtos.InstallMethod("command", "复制安装命令",
                "在终端里粘贴这条命令，会自动下载并解压到 ~/.claude/skills。",
                "curl -fL -o /tmp/" + skill.slug + ".zip \"" + base + "/api/v1/skills/"
                        + urlEncode(skill.skillId) + "/download\" && mkdir -p ~/.claude/skills && "
                        + "unzip -oq /tmp/" + skill.slug + ".zip -d ~/.claude/skills",
                "shell"));
        if (skill.sourceUrl != null && !skill.sourceUrl.isBlank()) {
            out.add(new SkillDtos.InstallMethod("source", "源码直链",
                    "直接看上游原文。支持 GitHub / Gitee / GitLab 三种格式。",
                    skill.sourceUrl, "url"));
        }
        return out;
    }

    /** 下载载荷（文件名 + 字节）。 */
    public record Download(byte[] data, String filename) {
    }

    /**
     * 打包下载：优先从磁盘把整个 Skill 目录打进去（含脚本与参考资料）；
     * 磁盘上找不到时回落到"只打 SKILL.md"，保证任何情况下都能下载到东西。
     */
    @Transactional
    public Download download(String skillId, String version) {
        Skill skill = require(skillId);
        List<PackagePort.FileContent> contents = new ArrayList<>();

        if (libraryPort.available()) {
            LibraryPort.SkillContent sc = libraryPort.read(skill.slug);
            if (sc != null) {
                for (LibraryPort.FileRef ref : sc.files()) {
                    if (!"SKILL.md".equals(ref.path())) {
                        byte[] bytes = libraryPort.readFile(skill.slug, ref.path());
                        if (bytes != null) {
                            contents.add(new PackagePort.FileContent(ref.path(), bytes));
                        }
                    }
                }
            }
        }
        String md = versionContent(skillId, version);
        contents.add(new PackagePort.FileContent("SKILL.md", md.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        byte[] zip = packagePort.zip(skill.slug, contents);
        skillMapper.incrementDownload(skillId);
        opLogService.record(Actor.GUEST, "skill.download", "skill", skillId,
                "version=" + Texts.firstNonBlank(version, skill.version) + " bytes=" + zip.length);
        return new Download(zip, skill.slug + ".zip");
    }

    /** 标记一次"安装命令被复制"（前端点按钮时调用，用于统计）。 */
    @Transactional
    public void recordInstall(String skillId) {
        require(skillId);
        skillMapper.incrementInstall(skillId);
    }

    /**
     * 关键词检索：名称 100 / 标题 60 / 描述 40 / 标签 30 / 正文 10 加权。
     *
     * <p>Candidate 由 SQL 的 LIKE 粗筛（≤200 条），打分与排序在 Java 侧做 ——
     * 这样权重可读、可测，且不需要依赖任何全文索引（H2/MySQL 行为一致）。
     */
    public List<SiteDtos.SearchHit> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        List<Skill> candidates = skillMapper.searchCandidates("%" + kw + "%", SEARCH_CANDIDATE_LIMIT);
        List<SiteDtos.SearchHit> hits = new ArrayList<>();
        for (Skill s : candidates) {
            int score = 0;
            StringBuilder where = new StringBuilder();
            if (contains(s.name, kw)) {
                score += 100;
                where.append("名称 ");
            }
            if (contains(s.title, kw)) {
                score += 60;
                where.append("标题 ");
            }
            if (contains(s.description, kw)) {
                score += 40;
                where.append("描述 ");
            }
            if (contains(s.tags, kw)) {
                score += 30;
                where.append("标签 ");
            }
            if (contains(s.content, kw)) {
                score += 10;
                where.append("正文 ");
            }
            if (score > 0) {
                hits.add(new SiteDtos.SearchHit(cardFactory.card(s), score, where.toString().trim()));
            }
        }
        hits.sort((a, b) -> Integer.compare(b.score(), a.score()));
        int max = Math.max(1, Math.min(limit, 50));
        return hits.size() > max ? hits.subList(0, max) : hits;
    }

    public List<SkillDtos.Card> related(Skill skill) {
        List<Skill> rows = skillMapper.findRelated(skill.categoryCode, skill.skillId, 6);
        return cardFactory.cards(rows);
    }

    // ------------------------------------------------------------------ 写

    /**
     * 编辑 Skill。
     *
     * <p>任何一次编辑都会：
     * <ol>
     *   <li>更新主表（只改传了值的字段）</li>
     *   <li>若 {@code version} 或 {@code content} 有变化 → 追加一条版本快照</li>
     *   <li>若正文变化 → 重新做一次风险扫描并写评估</li>
     *   <li>写审计</li>
     * </ol>
     */
    @Transactional
    public SkillDtos.Card edit(String skillId, SkillDtos.EditRequest req, Actor actor) {
        Skill existing = require(skillId);
        if (!actor.canEdit()) {
            throw ApiException.of(ErrorCode.ROLE_FORBIDDEN, actor.role());
        }

        Skill patch = new Skill();
        patch.skillId = skillId;
        if (hasText(req.getName())) {
            patch.name = req.getName().trim();
        }
        if (hasText(req.getTitle())) {
            patch.title = req.getTitle().trim();
        }
        if (hasText(req.getDescription())) {
            patch.description = req.getDescription().trim();
        }
        if (hasText(req.getCategoryCode())) {
            patch.categoryCode = Categories.of(req.getCategoryCode()).code();
        }
        if (hasText(req.getSceneCode())) {
            patch.sceneCode = req.getSceneCode().trim();
        }
        if (req.getTags() != null) {
            patch.tags = Texts.joinTags(req.getTags());
        }
        if (hasText(req.getVersion())) {
            patch.version = req.getVersion().trim();
        }
        if (hasText(req.getLicense())) {
            patch.license = req.getLicense().trim();
        }
        if (req.getSourceUrl() != null) {
            patch.sourceUrl = req.getSourceUrl().isBlank() ? null : req.getSourceUrl().trim();
            var parsed = Texts.parseRepoUrl(patch.sourceUrl, existing.name);
            if (!parsed.isEmpty()) {
                patch.source = parsed.get("source");
                patch.sourceRepo = parsed.get("source_repo");
            }
        }

        boolean contentChanged = false;
        if (hasText(req.getContent())) {
            String content = org.skillhub.support.SkillMdParser.normalizeNewlines(req.getContent()).trim();
            if (!content.equals(existing.content)) {
                contentChanged = true;
                patch.content = content;
                patch.contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                patch.contentSha256 = Texts.sha256(content);
                applyRiskScan(skillId, content);
            }
        }

        // —— Skill-MS 门禁（标准版核心）：约束裁决必须在落库之前 ——
        MsEngine.Gate gate = msEngine.beforeWrite("UPDATE", existing, MsEngine.merge(existing, patch), actor);
        skillMapper.updateSelective(patch);

        String targetVersion = Texts.firstNonBlank(patch.version, existing.version);
        if (contentChanged || (patch.version != null && !patch.version.equals(existing.version))) {
            SkillVersion v = new SkillVersion();
            v.skillId = skillId;
            v.version = targetVersion;
            v.content = contentChanged ? patch.content : existing.content;
            v.notes = Texts.firstNonBlank(req.getVersionNote(), "通过「编辑」更新");
            v.sizeBytes = v.content == null ? 0 : v.content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            v.origin = "edit";
            SkillVersion existingSame = versionMapper.findBySkillIdAndVersion(skillId, targetVersion);
            if (existingSame != null) {
                throw ApiException.of(ErrorCode.VERSION_EXISTS, targetVersion);
            }
            versionMapper.insert(v);
        }

        // —— 写完：索引维护 + 事件派发 + 台账 ——
        msEngine.afterWrite(gate, "UPDATE");
        opLogService.record(actor, "skill.edit", "skill", skillId,
                "name=" + patch.name + " version=" + targetVersion + " contentChanged=" + contentChanged);
        return cardFactory.card(require(skillId));
    }

    /**
     * 软删（归档）：默认不物理删除，保证"误删可恢复"。
     *
     * @param hard 是否物理删除（仅管理员可用）
     * @return 归档/恢复后的卡片；物理删除时返回 {@code null}
     */
    @Transactional
    public SkillDtos.Card archive(String skillId, boolean hard, Actor actor) {
        require(skillId);
        if (hard) {
            if (!actor.isAdmin()) {
                throw ApiException.ofMessage(ErrorCode.ROLE_FORBIDDEN,
                        "物理删除需要管理员身份。怎么办：以 X-SKH-Role: admin 访问，或改用归档（软删）。");
            }
            fileMapper.deleteBySkillId(skillId);
            skillMapper.deleteHard(skillId);
            opLogService.record(actor, "skill.delete.hard", "skill", skillId, "物理删除");
            return null;
        }
        Skill before = require(skillId);
        Skill after = MsEngine.merge(before, patchOfStatus("archived"));
        MsEngine.Gate gate = msEngine.beforeWrite("UPDATE", before, after, actor);
        skillMapper.updateStatus(skillId, "archived");
        msEngine.afterWrite(gate, "STATUS_CHANGE");
        opLogService.record(actor, "skill.archive", "skill", skillId, "归档（软删，可恢复）");
        return cardFactory.card(require(skillId));
    }

    @Transactional
    public SkillDtos.Card restore(String skillId, Actor actor) {
        require(skillId);
        Skill before = require(skillId);
        Skill after = MsEngine.merge(before, patchOfStatus("published"));
        MsEngine.Gate gate = msEngine.beforeWrite("UPDATE", before, after, actor);
        skillMapper.updateStatus(skillId, "published");
        msEngine.afterWrite(gate, "STATUS_CHANGE");
        opLogService.record(actor, "skill.restore", "skill", skillId, "恢复为已发布");
        return cardFactory.card(require(skillId));
    }

    // ------------------------------------------------------------------ 内部

    /** 造一个"只改 status"的补丁行，供写门禁计算"写之后的行"。 */
    private static Skill patchOfStatus(String status) {
        Skill s = new Skill();
        s.status = status;
        return s;
    }

    /** 供贡献发布复用：把一次对外部可见的写入也纳入门禁。 */
    public MsEngine.Gate gateInsert(Skill skill, Actor actor) {
        return msEngine.beforeWrite("INSERT", null, skill, actor);
    }

    /** 供贡献发布复用：写完之后维护索引与事件台账。 */
    public void gateAfter(MsEngine.Gate gate, String trigger) {
        msEngine.afterWrite(gate, trigger);
    }
    /** 供贡献发布复用：写一次评估并回填 skill 的风险字段。 */
    public void applyRiskScan(String actualSkillId, String content) {
        org.skillhub.support.RiskScanner.Report report = riskScanner.scan("SKILL.md", content);
        RiskAudit audit = new RiskAudit();
        audit.skillId = actualSkillId;
        audit.riskScore = report.score();
        audit.riskLevel = report.level();
        audit.engine = "rule-1.0";
        audit.signalTotal = report.signalTotal();
        audit.dimensionJson = toJson(report.dimensionScores());
        audit.summary = summarize(report);
        audit.conclusion = report.hits().isEmpty()
                ? "未命中任何风险指标。"
                : "命中 " + report.signalTotal() + " 条信号，涉及 "
                        + report.dimensionScores().size() + " 个维度。";
        riskMapper.insertAudit(audit);
        if (!report.hits().isEmpty()) {
            List<org.skillhub.model.RiskSignal> signals = new ArrayList<>();
            for (org.skillhub.support.RiskScanner.Hit h : report.hits()) {
                org.skillhub.model.RiskSignal s = new org.skillhub.model.RiskSignal();
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
        }
        Skill patch = new Skill();
        patch.skillId = actualSkillId;
        patch.riskScore = report.score();
        patch.riskLevel = report.level();
        skillMapper.updateSelective(patch);
    }

    public Skill require(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, "skill_id");
        }
        Skill skill = skillMapper.findDetailBySkillId(skillId);
        if (skill == null) {
            throw ApiException.of(ErrorCode.SKILL_NOT_FOUND, skillId);
        }
        return skill;
    }

    public Skill find(String skillId) {
        return skillId == null || skillId.isBlank() ? null : skillMapper.findDetailBySkillId(skillId);
    }

    public static String urlEncode(String raw) {
        return java.net.URLEncoder.encode(raw == null ? "" : raw, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String toJson(java.util.Map<String, Integer> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String summarize(org.skillhub.support.RiskScanner.Report report) {
        if (report.hits().isEmpty()) {
            return "无风险信号";
        }
        Set<String> codes = new LinkedHashSet<>();
        report.hits().forEach(h -> codes.add(h.code()));
        return Texts.truncate("命中指标：" + String.join("、", codes), 500);
    }
}
