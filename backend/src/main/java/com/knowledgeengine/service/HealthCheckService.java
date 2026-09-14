package com.knowledgeengine.service;

import org.springframework.stereotype.Service;

@Service
public class HealthCheckService {

    public String getHealthStatus() {
        return "UP";
    }
}
