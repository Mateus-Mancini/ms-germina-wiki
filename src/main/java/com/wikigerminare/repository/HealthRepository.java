package com.wikigerminare.repository;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {

	/**
	 * Bounds the query itself; connection acquisition is bounded by Hikari's connection-timeout (5 s).
	 * Together they keep a "not ready" answer under 10 s (SC-007).
	 */
	private static final int QUERY_TIMEOUT_SECONDS = 3;

	private final JdbcTemplate jdbcTemplate;

	public HealthRepository(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
	}

	/**
	 * Round-trips to the database, waking it if it was suspended.
	 */
	public void ping() {
		jdbcTemplate.queryForObject("SELECT 1", Integer.class);
	}

}
