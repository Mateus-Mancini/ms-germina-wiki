package com.wikigerminare.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wikigerminare.dto.ReadinessStatus;
import com.wikigerminare.service.HealthService;

/**
 * Readiness check the web app calls on load; it also wakes the database (contracts/health.openapi.yaml).
 */
@RestController
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping("/health")
	public ResponseEntity<ReadinessStatus> health() {
		ReadinessStatus readiness = healthService.checkReadiness();
		return ResponseEntity.status(ReadinessStatus.ready().equals(readiness) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
			.cacheControl(CacheControl.noStore())
			.body(readiness);
	}

}
