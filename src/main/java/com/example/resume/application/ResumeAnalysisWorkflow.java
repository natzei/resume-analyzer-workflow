package com.example.resume.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.workflow.Workflow;
import com.example.resume.domain.*;
import com.example.resume.repository.GeminiService;
import com.example.resume.repository.LLamaIndexService;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.example.resume.Utils.readByteString;
import static java.time.Duration.ofSeconds;

@ComponentId("resume-analysis-workflow")
public class ResumeAnalysisWorkflow extends Workflow<ResumeAnalysisState> {

    private static final Logger logger = LoggerFactory.getLogger(ResumeAnalysisWorkflow.class);
    private final GeminiService gemini;
    private final LLamaIndexService llama;

    public ResumeAnalysisWorkflow(Config config, HttpClientProvider clientProvider) {
        this.llama =  new LLamaIndexService(config.getString("llamaindex-api-key"), clientProvider);
        this.gemini = new GeminiService(config.getString("gemini-api-key"), clientProvider);
    }

    // Step names
    private final String extractApplicationFormStepName = "extract-application-step";
    private final String generateQuestionsStepName = "generate-questions-step";
    private final String extractResumeInfoStepName = "extract-resume-step";
    private final String answerQuestionsStepName = "answer-questions-step";
    private final String resultStepName = "result-step";
    private final String failoverStepName = "failover-step";

    /**
     * Workflow Diagram: Resume Analysis
     *
     * <pre><code>
     * +----------------------------+
     * | extract-application-step  |
     * | (Extract fields from      |
     * |  application form PDF)    |
     * +------------+--------------+
     *              |
     *              v
     * +----------------------------+
     * | generate-questions-step    |
     * | (Convert markdown to JSON, |
     * |  generate questions)       |
     * +------------+--------------+
     *              |
     *              v
     * +----------------------------+
     * | extract-resume-step        |
     * | (Extract resume info as    |
     * |  markdown)                 |
     * +------------+--------------+
     *              |
     *              v
     * +----------------------------+
     * | answer-questions-step      |
     * | (Answer questions using    |
     * |  extracted resume info)    |
     * +------------+--------------+
     *              |
     *              v
     * +----------------------------+
     * | result-step                |
     * | (Log results, mark as done)|
     * +------------+--------------+
     *              |
     *              v
     * +----------------------------+
     * | END                        |
     * +----------------------------+
     *
     *          |
     *          v (on failure)
     * +----------------------------+
     * | failover-step              |
     * | (Log failure, end flow)    |
     * +----------------------------+
     * </code></pre>
     */
    @Override
    public WorkflowDef<ResumeAnalysisState> definition() {

        // Given a PDF file path containing a job application form,
        // extract the data fields that need to be filled by candidates
        Step extractApplicationForm = step(extractApplicationFormStepName)
                .asyncCall(() -> llama.uploadAndWaitForCompletion(llama::uploadAndParseApplicationForm, currentState().applicationFormBytes()))
                .andThen(String.class, markdown -> {
                    logger.info("Fields as markdown:\n{}", markdown);
                    return effects()
                            .updateState(currentState().withState(ResumeAnalysisState.StateEnum.APPLICATION_FORM_PROCESSED))
                            .transitionTo(generateQuestionsStepName, markdown);
                });

        // Convert from Markdown to Json by calling Gemini.
        // The output is a list of Questions.
        Step generateQuestionsStep = step(generateQuestionsStepName)
                .asyncCall(String.class, gemini::convertMarkdownToJson)
                .andThen(ResumeFields.class, resumeFields -> {
                    logger.info("Markdown has been converted to JSON, generating the questions...");
                    var questions = resumeFields.fields().stream().map(Question::new).toList();
                    var newState = currentState()
                            .withState(ResumeAnalysisState.StateEnum.QUESTIONS_GENERATED)
                            .withQuestions(questions);
                    return effects()
                            .updateState(newState)
                            .transitionTo(extractResumeInfoStepName);
                });

        // Given a PDF file path containing a resume,
        // extract the info as markdown
        Step extractResumeInfoStep = step(extractResumeInfoStepName)
                .asyncCall(() -> llama.uploadAndWaitForCompletion(llama::uploadAndParseResume, currentState().resumeBytes()))
                .andThen(String.class, markdown ->
                        effects()
                                .updateState(currentState().withResumeInfo(markdown))
                                .transitionTo(answerQuestionsStepName)
                );

        // Answer the questions
        Step answerQuestionsStep = step(answerQuestionsStepName)
                .asyncCall(() -> {
                    var resumeInfo = currentState().resumeInfo();
                    var questions = currentState().questions();
                    return gemini.answerToQuestions(resumeInfo, questions);
                })
                .andThen(Answers.class, answers -> {
                    var newState = currentState()
                            .withState(ResumeAnalysisState.StateEnum.ANSWERS_GENERATED)
                            .withAnswers(answers.answers());
                    return effects()
                            .updateState(newState)
                            .transitionTo(resultStepName);
                });

        // Display the results
        Step resultStep = step(resultStepName)
                .asyncCall(() -> {
                    var state = currentState();
                    logger.info("""
                            Resume path: {}
                            Application form path: {}
                            
                            Extracted answers/questions:
                            {}
                            """, state.resumeBytes(), state.applicationFormBytes(), state.answers());

                    return CompletableFuture.completedStage(Done.done());
                })
                .andThen(Done.class, __ ->
                        effects()
                                .updateState(currentState().withState(ResumeAnalysisState.StateEnum.FINISHED))
                                .end()
                );

        Step failoverHandler = step(failoverStepName)
                .asyncCall(() -> {
                    logger.error("Failover procedure. Something went wrong!");
                    return CompletableFuture.completedStage(Done.done());
                })
                .andThen(Done.class, __ -> effects().end());

        return workflow()
                .defaultStepTimeout(ofSeconds(30))
                .defaultStepRecoverStrategy(maxRetries(0).failoverTo(failoverStepName))
                .addStep(extractApplicationForm)
                .addStep(generateQuestionsStep)
                .addStep(extractResumeInfoStep)
                .addStep(answerQuestionsStep)
                .addStep(resultStep)
                .addStep(failoverHandler)
                ;
    }

    @Override
    public ResumeAnalysisState emptyState() {
        return ResumeAnalysisState.initial();
    }

    // Entry point that starts the workflow
    public Effect<Done> start() {
        var current = currentState();

        // State validation
        if (current.resumeBytes() == null)
            return effects()
                    .error("Missing mandatory data. Please upload a resume file.");

        if (current.applicationFormBytes() == null)
            return effects()
                    .error("Missing mandatory data. Please upload a application form file.");

        // Start processing
        return effects()
                // set the internal state to STARTED
                .updateState(currentState().withState(ResumeAnalysisState.StateEnum.STARTED))
                // jump to the first step
                .transitionTo(extractApplicationFormStepName)
                // ack, no response
                .thenReply(Done.done());
    }

    public Effect<Done> setup() {
        return effects()
                // set the data
                .updateState(currentState()
                        .withApplicationForm(new ApplicationFormBytes(readByteString(Path.of("/Users/nicola/Downloads/fake_application_form.pdf"))))
                        .withResume(new ResumeBytes(readByteString(Path.of("/Users/nicola/Downloads/fake_resume.pdf")))))
                // wait for manual input to start the processing
                .pause()
                // ack, no response
                .thenReply(Done.done());
    }

    public Effect<Done> acceptApplicationForm(ApplicationFormBytes bytes) {
        return effects()
                .updateState(currentState()
                    .withApplicationForm(bytes)
                )
                .pause()
                .thenReply(Done.done());
    }

    public Effect<Done> acceptResume(ResumeBytes bytes) {
        return effects()
                .updateState(currentState()
                        .withResume(bytes)
                )
                .pause()
                .thenReply(Done.done());
    }

    // Read the current status of the workflow.
    public ReadOnlyEffect<ResumeAnalysisState> getStatus() {
        return effects().reply(currentState());
    }
}
