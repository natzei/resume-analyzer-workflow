package com.example.resume.repository;

import akka.javasdk.JsonSupport;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.util.ByteString;
import com.example.resume.domain.Answers;
import com.example.resume.domain.Question;
import com.example.resume.domain.ResumeFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final String apiKey;
    private final HttpClient client;

    public GeminiService(String apiKey, HttpClientProvider clientProvider) {
        this.apiKey = apiKey;
        this.client = clientProvider.httpClientFor("https://generativelanguage.googleapis.com");
    }

    // Request
    private record RequestBody(List<Content> contents, Config generationConfig) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
    private record Config(String response_mime_type) {}

    // Response
    private record ResponseBody(List<Candidate> candidates) {}
    private record Candidate(Content content) {}

    // Internal API call
    private CompletionStage<String> call(String query) {

        logger.info("Calling Gemini with [query={}]", query);

        /*
            {
                "contents": [
                    {
                        "parts":[{"text": "${query}"}]
                    }
                ]
            }
         */

        var request = new RequestBody(
                Collections.singletonList(
                        new Content(
                                Collections.singletonList(new Part(query))
                        )
                ),
                new Config("application/json")
        );

        return client.POST("/v1beta/models/gemini-2.0-flash:generateContent")
                .addQueryParameter("key", apiKey)
                .withRequestBody(request)
                .responseBodyAs(ResponseBody.class)
                .invokeAsync()
                .thenApply(res -> res.body().candidates.getFirst().content().parts().getFirst().text());
    }

    public CompletionStage<ResumeFields> convertMarkdownToJson(String markdown) {
        var query = """
                This is a parsed form.
                Convert it into a JSON object containing only the list
                of fields to be filled in, in the form {{ fields: [...] }}.
                <form>%s</form>.
                Return JSON ONLY, no markdown.
                """.formatted(markdown);
        return call(query)
                .thenApply(result -> {
                            logger.info(result);
                            return JsonSupport.decodeJson(ResumeFields.class, ByteString.fromString(result));
                        }
                );
    }

    public CompletionStage<Answers> answerToQuestions(String resumeInfo, List<Question> questions) {

        var questionsXmls = questions.stream()
                .map(q -> "<question>" + q.question() + "</question>")
                .collect(Collectors.joining("\n"));

        var query = """
                These are questions about the specific resume.
                The output must be in JSON format containing a list of pairs question/answer,
                in the form {{ "answers": [ { "question" : "...", "answer" : "..." }, ... ] }}
                <resume>%s</resume>
                <questions>%s</questions>
                Return JSON ONLY, no markdown.
                """.formatted(resumeInfo, questionsXmls);
        return call(query)
                .thenApply(result -> {
                    logger.info(result);
                    return JsonSupport.decodeJson(Answers.class, ByteString.fromString(result));
                });
    }
}
