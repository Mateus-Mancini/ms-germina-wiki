package com.wikigerminare;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every database-backed test starts from the Flyway-migrated schema; this pins what V1 must create
 * (specs/002-database-migrations/data-model.md).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void createsAllApplicationTables() {
		List<String> tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("users", "folders", "pages", "page_images", "comments", "tags",
				"page_tags", "page_links");
	}

	@Test
	void createsEnumTypesWithTheirLabelsInOrder() {
		assertThat(enumLabels("user_role")).containsExactly("admin", "member");
		assertThat(enumLabels("comment_status")).containsExactly("OPEN", "RESOLVED");
	}

	@Test
	void createsUpdatedAtTriggersAndFullTextIndex() {
		List<String> triggers = jdbc.queryForList(
				"SELECT DISTINCT trigger_name FROM information_schema.triggers WHERE trigger_schema = 'public'",
				String.class);
		assertThat(triggers).containsExactlyInAnyOrder("users_updated_at", "folders_updated_at", "pages_updated_at",
				"comments_updated_at");

		String indexDefinition = jdbc.queryForObject(
				"SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_pages_full_text_search'",
				String.class);
		assertThat(indexDefinition).contains("USING gin").contains("to_tsvector");
	}

	@Test
	void enablesPgcrypto() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void recordsVersionOneAndNoFailedMigrations() {
		// Pins V1 without pinning the latest version, so adding V2, V3, ... doesn't break this test.
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
			.isZero();
	}

	private List<String> enumLabels(String type) {
		return jdbc.queryForList("""
				SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid
				WHERE t.typname = ? ORDER BY e.enumsortorder
				""", String.class, type);
	}

}
