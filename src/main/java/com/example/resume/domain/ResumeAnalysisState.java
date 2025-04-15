package com.example.resume.domain;

import akka.util.ByteString;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Collections;
import java.util.List;

public record ResumeAnalysisState(
        @JsonSerialize(using = ByteStringSerializer.class)
        @JsonDeserialize(using = ByteStringDeserializer.class)
        ByteString resumeBytes,

        @JsonSerialize(using = ByteStringSerializer.class)
        @JsonDeserialize(using = ByteStringDeserializer.class)
        ByteString applicationFormBytes,

        StateEnum state,
        String resumeInfo,
        List<Question> questions,
        List<Answer> answers) {

    public enum StateEnum {
        READY,
        STARTED,
        APPLICATION_FORM_PROCESSED,
        QUESTIONS_GENERATED,
        ANSWERS_GENERATED,
        FINISHED
    }

    public static ResumeAnalysisState initial() {
        return new ResumeAnalysisState(null, null, StateEnum.READY, null, Collections.emptyList(), Collections.emptyList());
    }

    public ResumeAnalysisState withApplicationForm(ApplicationFormBytes applicationFormPath) {
        return new ResumeAnalysisState(resumeBytes, applicationFormPath.bytes(), state, resumeInfo, questions, answers);
    }

    public ResumeAnalysisState withResume(ResumeBytes resumePath) {
        return new ResumeAnalysisState(resumePath.bytes(), applicationFormBytes, state, resumeInfo, questions, answers);
    }

    public ResumeAnalysisState withState(StateEnum state) {
        return new ResumeAnalysisState(resumeBytes, applicationFormBytes, state, resumeInfo, questions, answers);
    }

    public ResumeAnalysisState withResumeInfo(String resumeInfo) {
        return new ResumeAnalysisState(resumeBytes, applicationFormBytes, state, resumeInfo, questions, answers);
    }

    public ResumeAnalysisState withQuestions(List<Question> questions) {
        return new ResumeAnalysisState(resumeBytes, applicationFormBytes, state, resumeInfo, questions, answers);
    }

    public ResumeAnalysisState withAnswers(List<Answer> answers) {
        return new ResumeAnalysisState(resumeBytes, applicationFormBytes, state, resumeInfo, questions, answers);
    }

}