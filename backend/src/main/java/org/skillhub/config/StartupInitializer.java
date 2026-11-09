package org.skillhub.config;

import java.time.LocalDateTime;
import org.skillhub.business.DocSeedService;
import org.skillhub.business.LibraryImportService;
import org.skillhub.port.ClockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动初始化：建表 → 导入 Skill 库（仅当库为空）→ 同步在线文档。
 *
 * <p>顺序不能换：DDL 必须先于任何 Mapper 调用；库导入必须先于首页统计。
 *
 * <p>任何一步失败都**不阻止启动**（除建表）：前端仍能打开，并在健康检查里
 * 明确告诉用户"库没导进来，为什么"。这是"能启动"优先于"完美启动"的取舍。
 */
@Component
@Order(1)
public class StartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);

    private final DatabaseBootstrap bootstrap;
    private final LibraryImportService libraryImport;
    private final DocSeedService docSeed;
    private final MsSeeder msSeeder;
    private final ClockPort clock;
    private final SiteProperties props;

    private volatile LocalDateTime startedAt;

    public StartupInitializer(DatabaseBootstrap bootstrap, LibraryImportService libraryImport,
                              DocSeedService docSeed, MsSeeder msSeeder, ClockPort clock,
                              SiteProperties props) {
        this.bootstrap = bootstrap;
        this.libraryImport = libraryImport;
        this.docSeed = docSeed;
        this.msSeeder = msSeeder;
        this.clock = clock;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        startedAt = clock.now();
        bootstrap.bootstrap();
        try {
            libraryImport.importIfEmpty(false);
        } catch (RuntimeException e) {
            libraryImport.markFailed(e.getMessage());
            log.error("Skill 库导入失败（站点仍会启动，但列表可能为空）。怎么办：检查 skills/ 目录与 logs", e);
        }
        try {
            docSeed.seed();
        } catch (RuntimeException e) {
            log.error("在线文档同步失败（页面仍可打开，「文档」页可能为空）", e);
        }
        try {
            msSeeder.seedAll();
        } catch (RuntimeException e) {
            log.error("Skill-MS 初始数据写入失败（「库管理」「接入管理」可能为空）", e);
        }
        // 端口以实际生效的 JVM 参数为准（start.cmd 30810 这类临时改端口也能打印对）
        log.info("启动完成，可以访问 http://127.0.0.1:{}", System.getProperty("server.port", String.valueOf(props.getPort())));
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public String schemaVersion() {
        return DatabaseBootstrap.SCHEMA_VERSION;
    }
}
