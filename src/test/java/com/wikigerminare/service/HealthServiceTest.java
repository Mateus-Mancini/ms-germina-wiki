package com.wikigerminare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

import com.wikigerminare.dto.ReadinessStatus;
import com.wikigerminare.repository.HealthRepository;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

	@Mock
	private HealthRepository healthRepository;

	@InjectMocks
	private HealthService healthService;

	@Test
	void reportsReadyWhenDatabaseAnswers() {
		assertThat(healthService.checkReadiness()).isEqualTo(ReadinessStatus.ready());
	}

	@Test
	void reportsNotReadyWhenDatabaseIsUnreachable() {
		doThrow(new DataAccessResourceFailureException("connection refused")).when(healthRepository).ping();

		assertThat(healthService.checkReadiness()).isEqualTo(ReadinessStatus.notReady());
	}

	@Test
	void reportsNotReadyWhenDatabaseQueryTimesOut() {
		doThrow(new QueryTimeoutException("timed out")).when(healthRepository).ping();

		assertThat(healthService.checkReadiness()).isEqualTo(ReadinessStatus.notReady());
	}

}
