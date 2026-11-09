package org.skillhub.business;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.skillhub.common.Actor;
import org.skillhub.common.ApiException;
import org.skillhub.common.ErrorCode;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.config.SiteProperties;
import org.skillhub.dto.ContributionDtos;
import org.skillhub.dto.SkillDtos;
import org.skillhub.mapper.ContributionMapper;
import org.skillhub.mapper.CategoryMapper;
import org.skillhub.mapper.RiskMapper;
import org.skillhub.mapper.SkillFileMapper;
import org.skillhub.mapper.SkillMapper;
import org.skillhub.mapper.SkillVersionMapper;
import org.skillhub.model.Contribution;
import org.skillhub.model.RiskAudit;
import org.skillhub.model.Skill;
import org.skillhub.model.SkillFile;
import org.skillhub.model.SkillVersion;
import org.skillhub.port.ClockPort;
import org.skillhub.support.BundleTextReader;
import org.skillhub.support.CardFactory;
import org.skillhub.support.Dates;
import org.skillhub.support.RiskScanner;
import org.skillhub.support.SkillMdParser;
import org.skillhub.support.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在线贡献：四步状态机 {@code draft → submitted → published}（可 {@code rejected}）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>每一步都是幂等的：重复点"提交"不会把状态推两次（第二次直接返回当前状态）</li>
 *   <li>签 CLA 只置标记、不推进状态 —— "签了协议"和"提交审核"是两件事</li>
 *   <li>发布时若版本号已存在，返回 409 {@code VERSION_EXISTS}，绝不覆盖别人的历史版本</li>
 *   <li>每一步都给出 {@code next_step} 与 {@code checklist}，前端不需要自己推断流程</li>
 * </ul>
 */
@Service
public class ContributionService {

    private static final Logger log = LoggerFactory.getLogger(ContributionService.class);

    private final ContributionMapper contributionMapper;
    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillFileMapper fileMapper;
    private final CategoryMapper categoryMapper;
    private final RiskMapper riskMapper;
    private final RiskService riskService;
    private final ContributorService contributorService;
    private final SkillService skillService;
    private final OpLogService opLogService;
    private final RiskScanner riskScanner;
    private final ClockPort clock;
    private final SiteProperties props;

    public ContributionService(ContributionMapper contributionMapper, SkillMapper skillMapper,
                               SkillVersionMapper versionMapper, SkillFileMapper fileMapper,
                               CategoryMapper categoryMapper, RiskMapper riskMapper,
                               RiskService riskService, ContributorService contributorService,
                               SkillService skillService, OpLogService opLogService,
                               RiskScanner riskScanner, ClockPort clock, SiteProperties props) {
        this.contributionMapper = contributionMapper;
        this.skillMapper = skillMapper;
        this.versionMapper = versionMapper;
        this.fileMapper = fileMapper;
        this.categoryMapper = categoryMapper;
        this.riskMapper = riskMapper;
        this.riskService = riskService;
        this.contributorService = contributorService;
        this.skillService = skillService;
        this.opLogService = opLogService;
        this.riskScanner = riskScanner;
        this.clock = clock;
        this.props = props;
    }

    // ------------------------------------------------------------------ 四步

    /** 第 1 步：接收上传，落盘，做预评估，状态 {@code draft}。 */
    @Transactional
    public ContributionDtos.Detail create(ContributionDtos.CreateRequest req, String skillMd,
                                         byte[] bundle, Actor actor) {
        String content = normalizeIncoming(req, skillMd);
        Contribution c = new Contribution();
        c.kind = normalizeKind(req.getKind());
        c.contributorName = requireText(req.getContributorName(), "contributor_name");
        c.name = Texts.slugify(requireText(req.getName(), "name"));
        c.title = Texts.firstNonBlank(req.getTitle(), c.name);
        c.version = Texts.firstNonBlank(req.getVersion(), "1.0.0").trim();
        c.description = Texts.truncate(Texts.firstNonBlank(req.getDescription(), firstLine(content)), 1000);
        c.sourceType = req.getSourceUrl() != null && !req.getSourceUrl().isBlank() ? "link" : "upload";
        c.sourceUrl = req.getSourceUrl() == null || req.getSourceUrl().isBlank() ? null : req.getSourceUrl().trim();
        c.status = "draft";
        c.claSigned = false;
        c.claSignature = "";
        c.skillId = req.getExistingSkillId();
        // storage_dir 是 NOT NULL 列：先给空串，等 persistUpload 落盘后再回填真实路径
        c.storageDir = "";
        c.notes = "";

        RiskScanner.Report scan = scanUpload(content, bundle);
        c.riskScore = scan.score();
        c.riskLevel = scan.level();
        c.notes = Texts.truncate("预评估：" + scan.signalTotal() + " 条信号", 1000);

        contributionMapper.insert(c);
        persistUpload(c.id, content, bundle);
        opLogService.record(actor, "contribution.create", "contribution", String.valueOf(c.id),
                "name=" + c.name + " kind=" + c.kind + " risk=" + c.riskLevel);
        return detail(c.id);
    }

    /** 第 2 步：签贡献者协议（只置标记）。 */
    @Transactional
    public ContributionDtos.Item signCla(long id, String signature, Actor actor) {
        Contribution c = require(id);
        String sign = Texts.firstNonBlank(signature, c.contributorName, "匿名贡献者");
        c.claSigned = true;
        c.claSignature = Texts.truncate(sign, 190);
        c.claSignedAt = clock.now();
        contributionMapper.updateSelective(c);
        opLogService.record(actor, "contribution.cla", "contribution", String.valueOf(id), "signature=" + sign);
        return toItem(require(id));
    }

    /** 第 3 步：提交审核（必须已签协议）。 */
    @Transactional
    public ContributionDtos.Item submit(long id, Actor actor) {
        Contribution c = require(id);
        if ("submitted".equals(c.status) || "published".equals(c.status)) {
            return toItem(c);
        }
        if (!Boolean.TRUE.equals(c.claSigned)) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "还没签贡献者协议，不能提交。怎么办：先在第 2 步签署协议（填你的名字即可）。");
        }
        c.status = "submitted";
        contributionMapper.updateSelective(c);
        opLogService.record(actor, "contribution.submit", "contribution", String.valueOf(id), "已提交审核");
        return toItem(require(id));
    }

    /**
     * 第 4 步：评估并发布。
     *
     * <p>发布 = 写 Skill（新建或追加版本） + 写评估 + 登记贡献者 + 回填贡献记录，全在一个事务里。
     */
    @Transactional
    public ContributionDtos.Detail publish(long id, Actor actor) {
        Contribution c = require(id);
        if ("published".equals(c.status)) {
            return detail(id);
        }
        if (!Boolean.TRUE.equals(c.claSigned)) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "还没签贡献者协议，不能发布。怎么办：先签协议再发布。");
        }
        if ("rejected".equals(c.status)) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "这条贡献已被驳回，不能直接发布。怎么办：重新提交一条新的贡献。");
        }

        String content = readUploadedSkillMd(c.id);
        String skillId = resolveSkillId(c);
        Skill existing = skillService.find(skillId);

        if (existing != null) {
            SkillVersion dup = versionMapper.findBySkillIdAndVersion(skillId, c.version);
            if (dup != null) {
                throw ApiException.of(ErrorCode.VERSION_EXISTS, c.version);
            }
        }

        RiskScanner.Report scan = riskScanner.scan("SKILL.md", content);
        String riskLevel = scan.level();
        MsEngine.Gate gate;

        if (existing == null) {
            Skill skill = new Skill();
            skill.skillId = skillId;
            skill.slug = Texts.slugify(c.name);
            skill.name = skill.slug;
            skill.title = Texts.truncate(Texts.firstNonBlank(c.title, c.name), 190);
            skill.description = Texts.truncate(c.description, 4000);
            skill.categoryCode = "general";
            skill.sceneCode = "general";
            skill.tags = "";
            skill.version = c.version;
            skill.source = sourceOf(skillId);
            skill.sourceUrl = c.sourceUrl;
            skill.sourceRepo = repoOf(skillId);
            skill.license = "UNKNOWN";
            skill.author = c.contributorName;
            skill.authorKind = c.kind;
            skill.riskScore = scan.score();
            skill.riskLevel = riskLevel;
            skill.status = "published";
            skill.content = content;
            skill.contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            skill.fileCount = 1;
            skill.packageBytes = (long) skill.contentBytes;
            skill.contentSha256 = Texts.sha256(content);
            skill.evalCount = 0;
            skill.avgScore = java.math.BigDecimal.ZERO;
            skill.skillsetCode = "default";
            skill.createdAt = clock.now();
            skill.updatedAt = clock.now();
            // —— Skill-MS 门禁：新增也要过约束 ——
            gate = skillService.gateInsert(skill, actor);
            skillMapper.insert(skill);

            SkillFile file = new SkillFile();
            file.skillId = skillId;
            file.path = "SKILL.md";
            file.bytes = (long) skill.contentBytes;
            file.sha256 = skill.contentSha256;
            file.isPrimary = true;
            file.createdAt = clock.now();
            fileMapper.insertBatch(List.of(file));
        } else {
            Skill patch = new Skill();
            patch.skillId = skillId;
            patch.version = c.version;
            patch.content = content;
            patch.contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            patch.contentSha256 = Texts.sha256(content);
            patch.riskScore = scan.score();
            patch.riskLevel = riskLevel;
            patch.status = "published";
            patch.description = Texts.truncate(Texts.firstNonBlank(c.description, existing.description), 4000);
            gate = skillService.gateInsert(MsEngine.merge(existing, patch), actor);
            skillMapper.updateSelective(patch);
        }

        SkillVersion version = new SkillVersion();
        version.skillId = skillId;
        version.version = c.version;
        version.content = content;
        version.notes = Texts.truncate("来自在线贡献 #" + c.id, 500);
        version.sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        version.origin = "contribution";
        versionMapper.insert(version);

        writeAudit(skillId, content, scan);
        contributorService.upsert(sourceOf(skillId), c.contributorName, c.contributorName, c.kind,
                "通过 " + props.getProductName() + " 提交贡献");

        c.status = "published";
        c.skillId = skillId;
        c.riskScore = scan.score();
        c.riskLevel = riskLevel;
        contributionMapper.updateSelective(c);

        // —— 写完：索引维护 + 事件派发（高风险额外触发 RISK_HIGH 事件）——
        skillService.gateAfter(gate, "INSERT");
        if ("high".equals(riskLevel)) {
            skillService.gateAfter(gate, "RISK_HIGH");
        }
        refreshCategoryCount();
        opLogService.record(actor, "contribution.publish", "contribution", String.valueOf(id),
                "skillId=" + skillId + " version=" + c.version + " risk=" + riskLevel);
        return detail(id);
    }

    /** 驳回。 */
    @Transactional
    public ContributionDtos.Item reject(long id, String reason, Actor actor) {
        Contribution c = require(id);
        if ("published".equals(c.status)) {
            throw ApiException.ofMessage(ErrorCode.STATE_ILLEGAL,
                    "已发布的贡献不能驳回。怎么办：直接对 Skill 做归档。");
        }
        c.status = "rejected";
        c.notes = Texts.truncate(Texts.firstNonBlank(reason, "未填写原因"), 1000);
        contributionMapper.updateSelective(c);
        opLogService.record(actor, "contribution.reject", "contribution", String.valueOf(id), c.notes);
        return toItem(require(id));
    }

    // ------------------------------------------------------------------ 读

    public PageResult<ContributionDtos.Item> page(PageQuery query, String status, String kind) {
        long total = contributionMapper.count(blankToNull(status), blankToNull(kind));
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<ContributionDtos.Item> items = contributionMapper
                .findPage(blankToNull(status), blankToNull(kind), query.getLimit(), query.getOffset())
                .stream().map(ContributionService::toItem).toList();
        return PageResult.of(query, total, items);
    }

    public ContributionDtos.Detail detail(long id) {
        Contribution c = require(id);
        ContributionDtos.Item item = toItem(c);
        SkillDtos.Audit audit = auditFor(c);
        return new ContributionDtos.Detail(item, audit, nextStep(c.status), checklist(c.status));
    }

    // ------------------------------------------------------------------ 内部

    private SkillDtos.Audit auditFor(Contribution c) {
        if (c.skillId == null || c.skillId.isBlank()) {
            return null;
        }
        RiskAudit audit = riskMapper.findLatestMeta(c.skillId);
        if (audit == null) {
            return null;
        }
        return riskService.toAudit(audit, riskMapper.findSignals(audit.id));
    }

    private void writeAudit(String skillId, String content, RiskScanner.Report scan) {
        RiskAudit audit = new RiskAudit();
        audit.skillId = skillId;
        audit.riskScore = scan.score();
        audit.riskLevel = scan.level();
        audit.engine = "rule-1.0";
        audit.signalTotal = scan.signalTotal();
        audit.dimensionJson = toJson(scan.dimensionScores());
        audit.summary = scan.hits().isEmpty() ? "无风险信号"
                : "命中指标：" + String.join("、", scan.hits().stream().map(RiskScanner.Hit::code).distinct().toList());
        audit.conclusion = "由在线贡献发布时评估（engine rule-1.0）。";
        riskMapper.insertAudit(audit);
        if (!scan.hits().isEmpty()) {
            List<org.skillhub.model.RiskSignal> signals = new ArrayList<>();
            for (RiskScanner.Hit h : scan.hits()) {
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
    }

    private void refreshCategoryCount() {
        // 分类计数是派生数据：每次发布后重算一次全量（15 个分类，代价可忽略）
        for (org.skillhub.support.Categories.Def def : org.skillhub.support.Categories.ALL) {
            categoryMapper.updateCount(def.code(), (int) skillMapper.countByCategoryCode(def.code()));
        }
    }

    private Contribution require(long id) {
        Contribution c = contributionMapper.findById(id);
        if (c == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND, "贡献记录 #" + id);
        }
        return c;
    }

    private RiskScanner.Report scanUpload(String content, byte[] bundle) {
        List<RiskScanner.Doc> docs = new ArrayList<>();
        docs.add(new RiskScanner.Doc("SKILL.md", content, true));
        BundleTextReader.Result bundleText = BundleTextReader.extract(bundle);
        if (!bundleText.text().isBlank()) {
            docs.add(new RiskScanner.Doc("bundle", bundleText.text(), false));
        }
        return riskScanner.scan(docs);
    }

    private String normalizeIncoming(ContributionDtos.CreateRequest req, String skillMd) {
        String content = skillMd;
        if ((content == null || content.isBlank()) && req.getCodeDraft() != null) {
            content = req.getCodeDraft();
        }
        if (content == null || content.isBlank()) {
            throw ApiException.ofMessage(ErrorCode.MISSING_PARAM,
                    "缺少 SKILL.md 内容。怎么办：上传一个 SKILL.md 文件，或在「在线编辑」里直接粘贴。");
        }
        return SkillMdParser.normalizeNewlines(content).trim();
    }

    private void persistUpload(long contributionId, String content, byte[] bundle) {
        Path dir = Paths.get(props.getUpload().getDir()).toAbsolutePath().resolve(String.valueOf(contributionId));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
            if (bundle != null && bundle.length > 0) {
                if (bundle.length > props.getUpload().getMaxBundleBytes()) {
                    throw ApiException.ofMessage(ErrorCode.INVALID_PARAM,
                            "附件包超过上限（" + Texts.humanBytes(props.getUpload().getMaxBundleBytes())
                                    + "）。怎么办：只保留运行 Skill 必需的文件。");
                }
                Files.write(dir.resolve("bundle.zip"), bundle);
            }
            Contribution c = new Contribution();
            c.id = contributionId;
            c.storageDir = dir.toString();
            contributionMapper.updateSelective(c);
        } catch (IOException e) {
            log.error("保存上传文件失败 dir={}", dir, e);
            throw ApiException.of(ErrorCode.INTERNAL_ERROR, e, "-");
        }
    }

    private String readUploadedSkillMd(long contributionId) {
        Contribution c = require(contributionId);
        Path dir = c.storageDir == null || c.storageDir.isBlank()
                ? Paths.get(props.getUpload().getDir()).toAbsolutePath().resolve(String.valueOf(contributionId))
                : Paths.get(c.storageDir);
        Path md = dir.resolve("SKILL.md");
        try {
            if (Files.isRegularFile(md)) {
                return Files.readString(md, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("读取上传的 SKILL.md 失败：{}", md, e);
        }
        throw ApiException.ofMessage(ErrorCode.NOT_FOUND,
                "找不到这条贡献的 SKILL.md。怎么办：重新上传一次（第 1 步）。");
    }

    private String resolveSkillId(Contribution c) {
        if (c.skillId != null && !c.skillId.isBlank()) {
            return c.skillId;
        }
        var parsed = Texts.parseRepoUrl(c.sourceUrl, c.name);
        if (!parsed.isEmpty()) {
            return parsed.get("skill_id");
        }
        return "local:" + Texts.slugify(c.contributorName) + "/" + Texts.slugify(c.name);
    }

    private static String sourceOf(String skillId) {
        int colon = skillId.indexOf(':');
        return colon > 0 ? skillId.substring(0, colon) : "upload";
    }

    private static String repoOf(String skillId) {
        int colon = skillId.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String tail = skillId.substring(colon + 1);
        int lastSlash = tail.lastIndexOf('/');
        return lastSlash > 0 ? tail.substring(0, lastSlash) : tail;
    }

    private static String normalizeKind(String kind) {
        return "personal".equalsIgnoreCase(kind) ? "personal" : "department";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.of(ErrorCode.MISSING_PARAM, field);
        }
        return value.trim();
    }

    private static String firstLine(String content) {
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#") && !t.equals("---")) {
                return Texts.truncate(t, 200);
            }
        }
        return "";
    }

    private static String nextStep(String status) {
        return switch (status == null ? "" : status) {
            case "draft" -> "下一步：签署贡献者协议（第 2 步）。";
            case "submitted" -> "下一步：点击「评估并发布」，系统会做风险扫描并入库。";
            case "published" -> "已完成：Skill 已经可以在列表里被检索到。";
            case "rejected" -> "这条贡献被驳回。请按驳回原因修改后重新提交。";
            default -> "";
        };
    }

    private static List<String> checklist(String status) {
        List<String> out = new ArrayList<>();
        out.add("SKILL.md 的 frontmatter 里有 name 与 description（AI 才知道什么时候该用它）");
        out.add("说明使用边界：允许做什么、不允许做什么");
        out.add("不要包含密钥、内网地址、真实个人信息");
        if ("draft".equals(status)) {
            out.add("签协议后才能提交（第 2 步）");
        }
        return out;
    }

    private static ContributionDtos.Item toItem(Contribution c) {
        return new ContributionDtos.Item(
                c.id == null ? 0 : c.id,
                c.kind,
                CardFactory.authorKindLabel(c.kind),
                c.contributorName,
                c.name,
                c.title,
                c.version,
                c.status,
                CardFactory.statusLabel(c.status),
                Boolean.TRUE.equals(c.claSigned),
                c.claSignature == null ? "" : c.claSignature,
                Dates.format(c.claSignedAt),
                c.skillId == null ? "" : c.skillId,
                c.riskScore == null ? 0 : c.riskScore,
                c.riskLevel == null ? "unrated" : c.riskLevel,
                RiskScanner.levelLabel(c.riskLevel),
                c.sourceType,
                c.sourceUrl == null ? "" : c.sourceUrl,
                c.notes == null ? "" : c.notes,
                Dates.format(c.createdAt),
                Dates.format(c.updatedAt),
                0);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String toJson(java.util.Map<String, Integer> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
