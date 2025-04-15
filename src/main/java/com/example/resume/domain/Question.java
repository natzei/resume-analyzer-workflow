package com.example.resume.domain;

public record Question(String field) {
    public String question() {
        return "How would you answer this question about the candidate? %s".formatted(field);
    }
}