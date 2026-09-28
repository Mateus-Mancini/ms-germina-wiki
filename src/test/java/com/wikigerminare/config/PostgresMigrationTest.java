package com.wikigerminare.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresMigrationTest {
    @Test
    void appliesCommentsMigrationAgainstConfiguredPostgres() throws Exception {
        String url = System.getenv("POSTGRES_TEST_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set POSTGRES_TEST_URL, POSTGRES_TEST_USER and POSTGRES_TEST_PASSWORD to run this integration test");
        String user = System.getenv().getOrDefault("POSTGRES_TEST_USER", "postgres");
        String password = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "postgres");

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Properties properties = new Properties();
        properties.put("user", user);
        properties.put("password", password);
        try (var connection = DriverManager.getConnection(url, properties);
             var tables = connection.getMetaData().getTables(null, null, "comments", null)) {
            assertTrue(tables.next(), "comments table must exist after migration");
        }
    }
}
