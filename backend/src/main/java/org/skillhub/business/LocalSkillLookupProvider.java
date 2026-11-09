package org.skillhub.business;

import java.util.List;
import org.skillhub.adapter.mcp.BuiltinMcpAdapter;
import org.springframework.stereotype.Component;

/**
 * 给内置 MCP 工具提供"本地 Skill 检索"能力。
 *
 * <p>用一个窄接口（{@link BuiltinMcpAdapter.LocalSkillLookup}）而不是直接依赖
 * {@link SkillService}：适配器只该看到"我要查什么"，不该看到整个业务服务。
 */
@Component
public class LocalSkillLookupProvider implements BuiltinMcpAdapter.LocalSkillLookup {

    private final SkillService skillService;

    public LocalSkillLookupProvider(SkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public List<String> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return skillService.search(keyword, Math.max(1, Math.min(limit, 5))).stream()
                .map(hit -> "《" + hit.card().name() + "》 分类："
                        + hit.card().categoryLabel() + "，风险：" + hit.card().riskLabel()
                        + "，得分 " + hit.score() + "（命中 " + hit.matchedIn() + "）")
                .toList();
    }
}
