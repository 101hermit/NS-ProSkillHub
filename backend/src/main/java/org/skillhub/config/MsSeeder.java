package org.skillhub.config;

import org.skillhub.business.EvalService;
import org.skillhub.mapper.EvalMapper;
import org.skillhub.mapper.MsMapper;
import org.skillhub.model.MsEntities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标准版的"库语义 + 实测"初始数据：逻辑库、基础索引、基础约束、基础事件、
 * 指标字典、Agent / MCP 注册表。
 *
 * <p>全部**幂等**（先查后插）：重复启动不会产生重复数据，也不会覆盖用户在页面上改过的配置。
 *
 * <p><b>接入的可靠性分层（本轮重写的重点）</b>：登记的接入点按"在任何环境都能连上"排序 ——
 * <ol>
 *   <li>{@code local-gateway}（Agent）与 {@code local-mcp}（MCP）指向<b>本站自己的端点</b>
 *       （{@code /stub/v1} 与 {@code /mcp}）。它们是真实 HTTP、真实 JSON-RPC，断网可用，
 *       是"链路到底通不通"的基准锚点；</li>
 *   <li>{@code local-stub} / {@code builtin-calc} 是**进程内**实现，连网络都不经过；</li>
 *   <li>{@code deepseek-flash} 是真实厂商端点（需要 Key 与出网），用于验证"真的能调模型"。</li>
 * </ol>
 * 这样页面上永远不会出现"配了却连不上"的死链接。
 */
@Component
@Order(2)
public class MsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MsSeeder.class);

    private final MsMapper msMapper;
    private final EvalMapper evalMapper;
    private final SiteProperties props;
    private final org.springframework.core.env.Environment env;

    public MsSeeder(MsMapper msMapper, EvalMapper evalMapper, SiteProperties props,
                    org.springframework.core.env.Environment env) {
        this.msMapper = msMapper;
        this.evalMapper = evalMapper;
        this.props = props;
        this.env = env;
    }

    /**
     * 本站自己的地址（用于登记"应用内自带"的 MCP 与网关）。
     *
     * <p><b>为什么不能直接用 {@code props.getPort()}</b>：那个值是 application.yml 里写的默认端口。
     * 一旦用 {@code start.cmd 30810} 换端口、或用 {@code SERVER_PORT} 覆盖，
     * 登记进去的自指 MCP/网关就会指向一个没人监听的端口 —— 页面上显示"已接入"，
     * 实测却报"MCP 连接被拒绝"。这正是「配了却连不上」的死链接。
     *
     * <p>所以取"真正生效的端口"：优先 Spring 自己记的 {@code local.server.port}（Web 容器已启动后必然存在），
     * 其次 {@code -Dserver.port}，最后才退回配置值。
     */
    private String selfBase() {
        String port = env.getProperty("local.server.port");
        if (port == null || port.isBlank()) {
            port = System.getProperty("server.port", String.valueOf(props.getPort()));
        }
        return "http://127.0.0.1:" + port;
    }

    /** 这个地址是不是"指向本站自己"的（换端口后需要跟着改）。 */
    private static boolean isSelfPointing(String url) {
        if (url == null) {
            return false;
        }
        return url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost");
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedAll();
        } catch (RuntimeException e) {
            log.error("Skill-MS 初始数据写入失败（站点仍可启动，但「库管理」「接入管理」可能为空）", e);
        }
    }

    /** 幂等地写入全部种子数据。 */
    @Transactional
    public void seedAll() {
        seedSkillsets();
        seedIndexes();
        seedConstraints();
        seedEvents();
        seedMetrics();
        seedAgents();
        seedMcps();
    }

    // ================================================================ 逻辑库

    private void seedSkillsets() {
        createSkillset("default", "默认库", "内置逻辑库：Skill 库导入的内容都放在这里。不可删除。");
        createSkillset("staging", "暂存库", "示例逻辑库：把不成熟或待评估的 Skill 放这里，不和默认库混在一起。");
    }

    private void createSkillset(String code, String name, String desc) {
        if (msMapper.findSkillset(code) != null) {
            return;
        }
        MsEntities.Skillset set = new MsEntities.Skillset();
        set.code = code;
        set.name = name;
        set.description = desc;
        set.owner = "system";
        msMapper.insertSkillset(set);
        log.info("已创建逻辑库 {}", code);
    }

    // ================================================================ 索引

    private void seedIndexes() {
        createIndex("idx_cat", "按分类", "category_code");
        createIndex("idx_scene", "按场景", "scene_code");
        createIndex("idx_risk", "按风险等级", "risk_level");
        createIndex("idx_cat_scene", "按分类+场景", "category_code,scene_code");
    }

    private void createIndex(String code, String name, String columns) {
        if (msMapper.findIndex(code) != null) {
            return;
        }
        MsEntities.IndexDef def = new MsEntities.IndexDef();
        def.code = code;
        def.name = name;
        def.skillsetCode = "default";
        def.columnsCol = columns;
        def.indexType = "BTREE";
        def.uniqueFlag = false;
        msMapper.insertIndex(def);
        log.info("已创建索引 {}（{}）", code, columns);
    }

    // ================================================================ 约束

    private void seedConstraints() {
        createConstraint("ck_name_not_null", "名字不能为空", "NOT_NULL", "name", "", "ENFORCED");
        createConstraint("ck_name_format", "名字只用小写字母/数字/连字符或中文", "REGEX", "name",
                "^[a-z0-9\\u4e00-\\u9fff][a-z0-9\\u4e00-\\u9fff-]*$", "WARN");
        createConstraint("ck_status_enum", "状态只能是已发布或已归档", "ENUM", "status",
                "published,archived", "ENFORCED");
        createConstraint("ck_risk_range", "风险分必须在 0-100 之间", "RANGE", "risk_score",
                "min=0,max=100", "ENFORCED");
        createConstraint("ck_skillset_ref", "所属逻辑库必须存在", "REFERENCE", "skillset_code",
                "skillset_code->skillset.code", "ENFORCED");
    }

    private void createConstraint(String code, String name, String type, String column, String expr,
                                  String severity) {
        if (msMapper.findConstraint(code) != null) {
            return;
        }
        MsEntities.ConstraintDef def = new MsEntities.ConstraintDef();
        def.code = code;
        def.name = name;
        def.skillsetCode = "default";
        def.constraintType = type;
        def.targetColumn = column;
        def.expression = expr;
        def.severity = severity;
        def.status = "ACTIVE";
        msMapper.insertConstraint(def);
    }

    // ================================================================ 事件

    private void seedEvents() {
        createEvent("ev_tag_on_insert", "新增 Skill 自动打来源标签", "INSERT", "", "TAG", "from-contribution");
        createEvent("ev_audit_on_risk_high", "高风险 Skill 自动写审计", "RISK_HIGH", "", "AUDIT",
                "risk_level=high");
        createEvent("ev_audit_on_status", "状态变化写审计", "STATUS_CHANGE", "", "AUDIT", "status-changed");
    }

    private void createEvent(String code, String name, String trigger, String condition, String action,
                             String payload) {
        if (msMapper.findEvent(code) != null) {
            return;
        }
        MsEntities.EventDef def = new MsEntities.EventDef();
        def.code = code;
        def.name = name;
        def.skillsetCode = "default";
        def.triggerEvent = trigger;
        def.conditionExpr = condition;
        def.actionType = action;
        def.actionPayload = payload;
        def.enabled = true;
        msMapper.insertEvent(def);
    }

    // ================================================================ 指标字典

    private void seedMetrics() {
        if (evalMapper.countMetrics() > 0) {
            return;
        }
        int sort = 0;
        for (EvalService.MetricSpec s : metricSpecs()) {
            MsEntities.MetricDef def = new MsEntities.MetricDef();
            def.code = s.code();
            def.label = s.label();
            def.dimension = s.dimension();
            def.unit = s.unit();
            def.higherBetter = s.higherBetter();
            def.weight = java.math.BigDecimal.valueOf(s.weight());
            def.formula = s.formula();
            def.description = s.formula();
            def.sortNo = sort += 10;
            evalMapper.insertMetric(def);
        }
        log.info("已写入 {} 条评估指标字典", metricSpecs().size());
    }

    /** 指标口径与 {@link EvalService} 保持一致（唯一真源在 {@code EvalService.SPECS}）。 */
    private java.util.List<EvalService.MetricSpec> metricSpecs() {
        return java.util.List.of(
                new EvalService.MetricSpec("correctness", "正确性", "EFFICACY", "分", true, 3.0,
                        "F1 = 2·精确率·召回率/(精确率+召回率)"),
                new EvalService.MetricSpec("coverage", "期望覆盖", "EFFICACY", "比例", true, 2.0,
                        "期望要点被覆盖的比例"),
                new EvalService.MetricSpec("completeness", "内容完整", "EFFICACY", "比例", true, 1.0,
                        "输出长度/期望长度，上限 1"),
                new EvalService.MetricSpec("task_success", "任务完成", "EFFICACY", "0/1", true, 2.0,
                        "覆盖≥0.5 且 F1≥0.4"),
                new EvalService.MetricSpec("consistency", "一致性", "EFFICACY", "比例", true, 1.0,
                        "重复运行的相似度"),
                new EvalService.MetricSpec("ttft", "首字效率", "COMMUNICATION", "比例", true, 1.0,
                        "1 − min(1, 首字时延/2000ms)"),
                new EvalService.MetricSpec("latency", "时延效率", "COMMUNICATION", "比例", true, 1.0,
                        "1 − min(1, 总时延/30000ms)"),
                new EvalService.MetricSpec("throughput", "吞吐", "COMMUNICATION", "归一", true, 1.0,
                        "tokens/s 按 50 归一"),
                new EvalService.MetricSpec("success", "成功率", "COMMUNICATION", "0/1", true, 1.0,
                        "调用是否成功"));
    }

    // ================================================================ 接入注册表

    private void seedAgents() {
        createAgent("local-gateway", "站点自带网关（协议锚点）", selfBase() + "/stub/v1",
                "skillhub-stub", "env:NONE", 0.3);
        createAgent("local-stub", "进程内离线桩", "", "offline", "env:NONE", 0.3);
        createAgent("deepseek-flash", "DeepSeek-flash", "https://api.deepseek.com/v1",
                "deepseek-chat", "env:DEEPSEEK_API_KEY", 0.3);
        // 专业版要求"多智能体"：下面两家是真实可用的国内 OpenAI 兼容端点
        createAgent("glm-4-flash", "智谱 GLM-4-Flash", "https://open.bigmodel.cn/api/paas/v4",
                "glm-4-flash", "env:ZHIPU_API_KEY", 0.3);
        createAgent("ark-kimi", "火山方舟 Kimi", "https://ark.cn-beijing.volces.com/api/v3",
                "kimi-k3-1-pro-261024", "env:ARK_API_KEY", 0.3);
        createAgent("dashscope-qwen", "阿里百炼 Qwen-Plus", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "qwen-plus", "env:DASHSCOPE_API_KEY", 0.3);
    }

    private void createAgent(String code, String name, String baseUrl, String model, String key,
                             double temperature) {
        MsEntities.AgentRecord existing = evalMapper.findAgent(code);
        if (existing != null) {
            reconcileAgentBaseUrl(existing, baseUrl);
            return;
        }
        MsEntities.AgentRecord rec = new MsEntities.AgentRecord();
        rec.code = code;
        rec.name = name;
        rec.provider = "openai-compatible";
        rec.baseUrl = baseUrl;
        rec.model = model;
        rec.apiKey = key;
        rec.temperature = java.math.BigDecimal.valueOf(temperature);
        rec.topP = java.math.BigDecimal.valueOf(1.0);
        rec.maxTokens = 2048;
        rec.enabled = true;
        evalMapper.insertAgent(rec);
    }

    /**
     * 已存在的自指 Agent：如果记的端口和当前监听端口对不上，就地改正。
     *
     * <p>为什么需要"纠正已有数据"而不只是"首次写入用对端口"：数据库是会被复制的
     * （换机器、换端口启动、把 data/ 一起拷走），只在首次写入时取对端口，第二次以后仍然是死链接。
     * 这里只动 baseUrl 一个字段，其余原样写回，避免覆盖用户改过的模型、Key、温度。
     */
    private void reconcileAgentBaseUrl(MsEntities.AgentRecord existing, String expected) {
        if (!isSelfPointing(expected) || !isSelfPointing(existing.baseUrl)) {
            return;
        }
        if (!expected.equals(existing.baseUrl)) {
            log.info("自指 Agent {} 的地址已随端口更新：{} → {}", existing.code, existing.baseUrl, expected);
            existing.baseUrl = expected;
            evalMapper.updateAgent(existing);
        }
    }

    /** 已存在的自指 MCP：同上，只纠正 endpoint。 */
    private void reconcileMcpEndpoint(MsEntities.McpRecord existing, String expected) {
        if (!isSelfPointing(expected) || !isSelfPointing(existing.endpoint)) {
            return;
        }
        if (!expected.equals(existing.endpoint)) {
            log.info("自指 MCP {} 的端点已随端口更新：{} → {}", existing.code, existing.endpoint, expected);
            existing.endpoint = expected;
            evalMapper.updateMcp(existing);
        }
    }

    private void seedMcps() {
        createMcp("local-mcp", "站点自带 MCP（本地 Skill 库工具）", selfBase() + "/mcp", "skill_search",
                "应用内 MCP 服务端：initialize → notifications/initialized → tools/list → tools/call 全部真实实现，工具直接读本站 Skill 库。",
                true);
        createMcp("builtin-calc", "进程内工具（skill_lookup）", "", "skill_lookup",
                "不经过网络的进程内实现，断网兜底用。", true);
        createMcp("kb-search", "外部 MCP 示例（默认停用）", "http://127.0.0.1:30900/mcp", "kb_search",
                "示例：把外部 MCP 服务接进来的形状。默认停用，避免出现「配了却连不上」的假象。", false);
    }

    private void createMcp(String code, String name, String endpoint, String tool, String desc,
                           boolean enabled) {
        MsEntities.McpRecord existing = evalMapper.findMcp(code);
        if (existing != null) {
            reconcileMcpEndpoint(existing, endpoint);
            return;
        }
        MsEntities.McpRecord rec = new MsEntities.McpRecord();
        rec.code = code;
        rec.name = name;
        rec.transport = "HTTP";
        rec.endpoint = endpoint;
        rec.toolName = tool;
        rec.description = desc;
        rec.headersJson = "{}";
        rec.enabled = enabled;
        evalMapper.insertMcp(rec);
    }
}
