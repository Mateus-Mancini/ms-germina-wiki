package com.wikigerminare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wikigerminare.dto.ReadinessStatus;
import com.wikigerminare.repository.HealthRepository;

@Service
public class HealthService {

	private static final Logger log = LoggerFactory.getLogger(HealthService.class);

	private final HealthRepository healthRepository;

	public HealthService(HealthRepository healthRepository) {
		this.healthRepository = healthRepository;
	}

	/**
	 * Ready only when the database answers. Failures are logged, never exposed to the caller.
	 */
	public ReadinessStatus checkReadiness() {
		try {
			healthRepository.ping();
			return ReadinessStatus.ready();
		}
		catch (RuntimeException ex) {
			log.warn("Readiness check failed: database did not answer", ex);
			return ReadinessStatus.notReady();
		}
	}

}
