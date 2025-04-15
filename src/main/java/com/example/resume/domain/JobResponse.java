package com.example.resume.domain;

import java.util.Optional;
import java.util.UUID;

public record JobResponse(UUID id, JobStatus status, Optional<String> errorCode, Optional<String> errorMessage) {

    public enum JobStatus {
        PENDING, SUCCESS, ERROR, PARTIAL_SUCCESS, CANCELLED
    }
}