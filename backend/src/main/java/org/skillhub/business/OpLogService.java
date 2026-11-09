package org.skillhub.business;

import org.skillhub.common.Actor;
import org.skillhub.common.PageQuery;
import org.skillhub.common.PageResult;
import org.skillhub.common.Trace;
import org.skillhub.dto.SiteDtos;
import org.skillhub.mapper.OpLogMapper;
import org.skillhub.model.OpLog;
import org.skillhub.support.Dates;
import org.skillhub.support.Texts;
import org.springframework.stereotype.Service;

/**
 * 操作审计：所有写操作都留一条流水，用于排障、责任追溯和"刚才谁改的"。
 *
 * <p>审计只追加、不修改，也不参与业务判断 —— 审计失败绝不阻断业务
 * （见 {@link #record} 里的 try/catch 说明）。
 */
@Service
public class OpLogService {

    private final OpLogMapper mapper;

    public OpLogService(OpLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记一条审计。
     *
     * <p>刻意吞掉异常：审计是旁路能力，写审计失败不应该让用户的操作失败。
     * 只在日志里留痕，便于运维发现（日志里会带 traceId）。
     */
    public void record(Actor actor, String action, String targetType, String targetId, String detail) {
        try {
            OpLog log = new OpLog();
            log.actor = actor == null ? "guest" : actor.name();
            log.role = actor == null ? "viewer" : actor.role();
            log.action = Texts.truncate(action, 64);
            log.targetType = Texts.truncate(targetType, 48);
            log.targetId = Texts.truncate(targetId, 191);
            log.detail = Texts.truncate(detail, 2000);
            log.traceId = Trace.current();
            mapper.insert(log);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(OpLogService.class)
                    .warn("写审计失败（已忽略，不影响业务）action={} target={}", action, targetId, e);
        }
    }

    public PageResult<SiteDtos.OpLogItem> page(PageQuery query, String action) {
        long total = mapper.count(blankToNull(action));
        if (total == 0) {
            return PageResult.empty(query);
        }
        var items = mapper.findPage(blankToNull(action), query.getLimit(), query.getOffset()).stream()
                .map(OpLogService::toItem)
                .toList();
        return PageResult.of(query, total, items);
    }

    private static SiteDtos.OpLogItem toItem(OpLog l) {
        return new SiteDtos.OpLogItem(l.id, l.actor, l.role, l.action, l.targetType, l.targetId,
                Texts.truncate(l.detail, 400), l.traceId, Dates.format(l.createdAt));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
