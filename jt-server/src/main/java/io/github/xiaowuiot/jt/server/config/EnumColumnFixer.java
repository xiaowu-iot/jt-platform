package io.github.xiaowuiot.jt.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 枚举字段的建表问题：Hibernate 6 给 {@code @Enumerated(STRING)} 的列加了限定取值的约束
 * （H2 是 CHECK (x IN ('A','B')) 或者 ENUM 列类型，MySQL 是 ENUM('A','B') 列类型），而 ddl-auto=update 不会更新它们。
 * 以后给枚举加新值（新的车辆类型、权限点），旧库一插入就报错。
 * <p>
 * 启动时最先执行：删掉这些 CHECK 约束，ENUM 列改成 VARCHAR。枚举值由程序校验，库里不再限制。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EnumColumnFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumColumnFixer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public EnumColumnFixer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String product;
        try (Connection c = dataSource.getConnection()) {
            product = c.getMetaData().getDatabaseProductName().toLowerCase();
        }
        try {
            if (product.contains("h2")) {
                h2();
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                mysql();
            }
        } catch (Exception e) {
            log.warn("检查枚举字段约束失败：{}", e.toString());
        }
    }

    private void h2() {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE"
                + " FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc"
                + " ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " WHERE tc.CONSTRAINT_TYPE = 'CHECK' AND tc.TABLE_SCHEMA = SCHEMA()");
        int n = 0;
        for (Map<String, Object> m : list) {
            String clause = String.valueOf(m.get("CHECK_CLAUSE"));
            // 只删枚举取值的约束：... IN('A', 'B', ...)
            if (!clause.contains(" IN(") && !clause.contains(" IN (")) {
                continue;
            }
            jdbc.execute("ALTER TABLE \"" + m.get("TABLE_NAME") + "\" DROP CONSTRAINT \"" + m.get("CONSTRAINT_NAME") + "\"");
            n++;
        }
        // 有的列 Hibernate 直接建成了 H2 的 ENUM 类型，改成 VARCHAR（原有的值不变）
        for (Map<String, Object> m : jdbc.queryForList("SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_SCHEMA = SCHEMA() AND DATA_TYPE = 'ENUM'")) {
            jdbc.execute("ALTER TABLE \"" + m.get("TABLE_NAME") + "\" ALTER COLUMN \"" + m.get("COLUMN_NAME") + "\" SET DATA TYPE VARCHAR(32)");
            n++;
        }
        if (n > 0) {
            log.info("已去掉 {} 个枚举取值限制", n);
        }
    }

    private void mysql() {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'enum'");
        for (Map<String, Object> m : list) {
            jdbc.execute("ALTER TABLE `" + m.get("TABLE_NAME") + "` MODIFY COLUMN `" + m.get("COLUMN_NAME") + "` VARCHAR(32)"
                    + ("NO".equals(m.get("IS_NULLABLE")) ? " NOT NULL" : " NULL"));
        }
        if (!list.isEmpty()) {
            log.info("已把 {} 个 ENUM 列改成 VARCHAR", list.size());
        }
    }
}
