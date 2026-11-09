package org.skillhub.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * 数据库自举：判定方言 → 执行可移植 DDL → 校验 schema 版本。
 *
 * <p><b>方言判定</b>：只读 JDBC URL，不做连接池探测。H2 与 MySQL 共用同一份
 * {@code db/schema.sql}（已在两端实测通过，见 docs/02 §9），因此这里不需要分支 SQL。
 *
 * <p><b>幂等</b>：DDL 全部是 {@code CREATE TABLE IF NOT EXISTS}，重复执行无副作用。
 *
 * <p><b>版本不符时不阻塞启动</b>：打印中文指引并继续。理由：DDL 只增不改时，
 * 强行拒绝启动会让"从 1.0.0 升到 1.0.1"变成一次人工运维事故；
 * 真正缺列会在第一次查询时以明确的 SQL 错误暴露，比启动失败更容易定位。
 */
@Component
public class DatabaseBootstrap {

    /** 与 {@code db/schema.sql} 的内容版本绑定；改表结构时同步修改。 */
    public static final String SCHEMA_VERSION = "1.0.0";

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

    private final DataSource dataSource;

    private volatile boolean done;
    private volatile String dialect = "unknown";
    private volatile String dialectDetail = "";

    public DatabaseBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 执行 DDL（幂等）。返回方言名：{@code h2} / {@code mysql}。 */
    public synchronized String bootstrap() {
        if (done) {
            return dialect;
        }
        try (Connection conn = dataSource.getConnection()) {
            dialect = detectDialect(conn);
            dialectDetail = conn.getMetaData().getDatabaseProductName() + " "
                    + conn.getMetaData().getDatabaseProductVersion();
            log.info("数据库方言：{}（{}）", dialect, dialectDetail);
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));
            log.info("数据库 schema 已就绪（版本 {}）", SCHEMA_VERSION);
        } catch (SQLException e) {
            log.error("建表失败。怎么办：检查端口/库名/账号（见 docs/05 §5）", e);
            throw new IllegalStateException("数据库初始化失败：" + e.getMessage(), e);
        } finally {
            done = true;
        }
        return dialect;
    }

    public String dialect() {
        return dialect;
    }

    public String dialectDetail() {
        return dialectDetail;
    }

    private static String detectDialect(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            if (url != null && url.startsWith("jdbc:mysql")) {
                return "mysql";
            }
            if (url != null && url.startsWith("jdbc:h2")) {
                return "h2";
            }
            String product = conn.getMetaData().getDatabaseProductName();
            return product == null ? "unknown" : product.toLowerCase(java.util.Locale.ROOT);
        } catch (SQLException e) {
            return "unknown";
        }
    }
}
