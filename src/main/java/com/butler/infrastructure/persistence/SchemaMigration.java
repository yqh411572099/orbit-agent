package com.butler.infrastructure.persistence;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量启动期建表/补列迁移。H2 用 ddl-auto=update，但对“已存在表新增非空列”不一定自动补，
 * 这里用 ALTER TABLE ADD COLUMN IF NOT EXISTS 显式补齐，避免老库缺列导致查询失败。
 */
@Component
public class SchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("sub_session", "updated_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("task", "updated_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
        } catch (Exception e) {
            // 列已存在或库方言不支持 IF NOT EXISTS 时忽略
        }
    }
}
