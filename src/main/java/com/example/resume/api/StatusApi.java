package com.example.resume.api;

import com.example.resume.domain.Answer;
import com.example.resume.domain.ResumeAnalysisState;

import java.util.List;

public record StatusApi(
            Boolean resumeIsAvailable,
            Boolean applicationFormIsAvailable,
            String status,
            // TODO: create AnswerApi record
            List<Answer> answers
    ) {
        public static StatusApi toApi(ResumeAnalysisState domain) {
            return new StatusApi(
                    domain.resumeBytes() != null,
                    domain.applicationFormBytes() != null,
                    domain.state().name(),
                    domain.answers()
            );
        }
    }