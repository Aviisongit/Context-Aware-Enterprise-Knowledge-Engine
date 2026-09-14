package com.knowledgeengine.controller;

import com.knowledgeengine.service.HealthCheckService;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    public HealthCheckController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", healthCheckService.getHealthStatus(),
                "service", "context-aware-knowledge-engine",
                "timestamp", Instant.now().toString()
        );
    }
}
