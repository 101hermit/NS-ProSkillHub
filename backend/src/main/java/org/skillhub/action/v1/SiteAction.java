package org.skillhub.action.v1;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skillhub.business.CatalogService;
import org.skillhub.business.LibraryImportService;
import org.skillhub.business.StatService;
import org.skillhub.common.ApiResult;
import org.skillhub.config.DatabaseBootstrap;
import org.skillhub.config.SiteProperties;
import org.skillhub.config.StartupInitializer;
import org.skillhub.dto.SiteDtos;
import org.skillhub.support.Dates;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点级端点：健康检查、产品元信息、能力开关。
 *
 * <p>这三个端点是**三个系统共有的契约**：字段集合一致、值不同
 * （见 docs/03 §2、docs/07 §5）。前端启动时先拿它们，再决定渲染哪些菜单。
 */
@RestController
@RequestMapping("/api/v1")
public class SiteAction {

    private final SiteProperties props;
    private final CatalogService catalogService;
    private final StatService statService;
    private final org.skillhub.business.SkillService skillService;
    private final DatabaseBootstrap bootstrap;
    private final StartupInitializer initializer;
    private final LibraryImportService libraryImport;
    private final org.skillhub.business.AuthService authService;

    public SiteAction(SiteProperties props, CatalogService catalogService, StatService statService,
                      org.skillhub.business.SkillService skillService,
                      DatabaseBootstrap bootstrap, StartupInitializer initializer,
                      LibraryImportService libraryImport,
                      org.skillhub.business.AuthService authService) {
        this.props = props;
        this.catalogService = catalogService;
        this.statService = statService;
        this.skillService = skillService;
        this.bootstrap = bootstrap;
        this.initializer = initializer;
        this.libraryImport = libraryImport;
        this.authService = authService;
    }

    @GetMapping("/health")
    public ApiResult<SiteDtos.Health> health() {
        long skillCount;
        try {
            skillCount = statService.stats().skillCount();
        } catch (RuntimeException e) {
            skillCount = 0;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        try {
            extra.put("library", libraryImport.describe());
        } catch (RuntimeException ignore) {
            extra.put("library", Map.of("available", false));
        }
        extra.put("account_count", authService.accountCount());
        extra.put("offline_capable", true);
        extra.put("external_dependencies", List.of());

        return ApiResult.ok(new SiteDtos.Health(
                bootstrap.dialect() != null && !"unknown".equals(bootstrap.dialect()),
                props.getProduct(),
                props.getAbbr(),
                props.getVersion(),
                bootstrap.dialect(),
                bootstrap.dialectDetail(),
                skillCount,
                libraryImport.describe().getOrDefault("version", "unknown").toString(),
                Dates.format(initializer.startedAt()),
                initializer.schemaVersion(),
                props.capabilities(),
                extra));
    }

    @GetMapping("/meta")
    public ApiResult<SiteDtos.Meta> meta() {
        return ApiResult.ok(catalogService.meta());
    }

    @GetMapping("/meta/capabilities")
    public ApiResult<List<SiteDtos.Capability>> capabilities() {
        return ApiResult.ok(catalogService.features());
    }

    @GetMapping("/categories")
    public ApiResult<List<SiteDtos.CategoryItem>> categories() {
        return ApiResult.ok(catalogService.categories());
    }

    @GetMapping("/index/stats")
    public ApiResult<SiteDtos.Stats> indexStats() {
        return ApiResult.ok(statService.stats());
    }

    /** 首页检索（与 {@code /skills/search} 等价，保留两个路径以兼容前端两种写法）。 */
    @GetMapping("/index/search")
    public ApiResult<List<SiteDtos.SearchHit>> indexSearch(
            @org.springframework.web.bind.annotation.RequestParam("kw") String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "12") int limit) {
        return ApiResult.ok(skillService.search(keyword, limit));
    }
}
