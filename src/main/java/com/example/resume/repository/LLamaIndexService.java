package com.example.resume.repository;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.Multiparts;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.util.ByteString;
import com.example.resume.domain.JobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static com.example.resume.Utils.sleep;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;


public class LLamaIndexService {

    private static final Logger logger = LoggerFactory.getLogger(LLamaIndexService.class);

    private final String apiKey;
    private final HttpClient client;

    public LLamaIndexService(String apiKey, HttpClientProvider clientProvider) {
        this.apiKey = apiKey;
        this.client = clientProvider.httpClientFor("https://api.cloud.eu.llamaindex.ai");
    }

    public CompletionStage<JobResponse> uploadAndParseApplicationForm(ByteString file)  {

        var entity = Multiparts.createStrictFormDataFromParts(
                Multiparts.createFormDataBodyPartStrict(
                        "file",
                        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, file),
                        Collections.singletonMap("filename", "application-form.pdf")
                ),
                Multiparts.createFormDataBodyPartStrict(
                     "content_guideline_instruction",
                     HttpEntities.create("This is a job application form. Create a list of all the fields that need to be filled in.")
                ),
                Multiparts.createFormDataBodyPartStrict(
                     "formatting_instruction",
                     HttpEntities.create("Return a bulleted list of the fields ONLY.")
                )
        ).toEntity();
        var bytes = entity.getData().toArray();

        return client.POST("/api/v1/parsing/upload")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer" + apiKey)
                .withRequestBody(entity.getContentType(), bytes)
                .withTimeout(ofSeconds(5))
                .responseBodyAs(JobResponse.class)
                .invokeAsync()
                .thenApply(response -> {
                    if (response.status().isSuccess()) {
                        logger.info("File {} uploaded successfully", file);
                        return response.body();
                    }
                    else {
                        logger.error("An error occurred calling POST /api/parsing/upload: {}", response);
                        throw new RuntimeException("An error occurred calling POST /api/parsing/upload");
                    }
                });
    }

    public CompletionStage<JobResponse> uploadAndParseResume(ByteString file)  {

        var entity = Multiparts.createStrictFormDataFromParts(
                Multiparts.createFormDataBodyPartStrict(
                        "file",
                        HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, file),
                        Collections.singletonMap("filename", "resume.pdf")
                ),
                Multiparts.createFormDataBodyPartStrict(
                     "content_guideline_instruction",
                     HttpEntities.create("This is a resume, gather related facts together and format it as bullet points with headers")
                )
        ).toEntity();
        var bytes = entity.getData().toArray();

        return client.POST("/api/v1/parsing/upload")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer" + apiKey)
                .withRequestBody(entity.getContentType(), bytes)
                .withTimeout(ofSeconds(5))
                .responseBodyAs(JobResponse.class)
                .invokeAsync()
                .thenApply(response -> {
                    if (response.status().isSuccess()) {
                        logger.info("File {} uploaded successfully", file);
                        return response.body();
                    }
                    else {
                        logger.error("An error occurred calling POST /api/parsing/upload: {}", response);
                        throw new RuntimeException("An error occurred calling POST /api/parsing/upload");
                    }
                });
    }

    public CompletionStage<JobResponse> getJob(UUID id) {

        logger.info("Getting job {}", id);

        return client.GET("/api/v1/parsing/job/" + id)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer" + apiKey)
                .responseBodyAs(JobResponse.class)
                .invokeAsync()
                .thenApply(response -> {
                    if (response.status().isSuccess()) return response.body();
                    else {
                        logger.error("An error occurred calling GET /api/parsing/job/:id: {}", response);
                        throw new RuntimeException("An error occurred calling POST /api/parsing/job/:id");
                    }
                });
    }

    public CompletionStage<String> getResultMarkdown(UUID id) {

        logger.info("Getting result for job {}", id);

        return client.GET("/api/v1/parsing/job/" + id + "/result/raw/markdown")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer" + apiKey)
                .responseBodyAs(String.class)
                .invokeAsync()
                .thenApply(response -> {
                    if (response.status().isSuccess()) {
                        logger.info("Result is ready. Size: {}", response.body().length());
                        return response.body();
                    }
                    else {
                        logger.error("An error occurred calling GET /api/parsing/job/:id/result/raw/markdown: {}", response);
                        throw new RuntimeException("An error occurred calling POST /api/parsing/job/:id/result/raw/markdown");
                    }
                });
    }

    public CompletionStage<String> uploadAndWaitForCompletion(Function<ByteString, CompletionStage<JobResponse>> action, ByteString file) {

        return action.apply(file)
                .thenApply(job -> {

                    logger.info("Submitted file {}. Job id {} is in status {}.", file, job.id(), job.status());

                    var latestJob = job;
                    var timeout = ofSeconds(30).toMillis();
                    var deadline = currentTimeMillis() + timeout;

                    // polling with timeout
                    while (latestJob.status() == JobResponse.JobStatus.PENDING && currentTimeMillis() < deadline) {

                        logger.info("Job {} is still {}. Waiting...", job.id(), job.status());
                        sleep(3000);

                        // wait for completion
                        try {
                            latestJob = getJob(job.id()).toCompletableFuture().join();
                        } catch (Exception e) {
                            logger.warn("Unable to get the job status of {}. Retry...", job.id());
                        }
                    }

                    if (latestJob.status() != JobResponse.JobStatus.SUCCESS)
                        throw new RuntimeException("Invalid job status " + latestJob.status());

                    return latestJob;
                })
                .thenComposeAsync(job -> getResultMarkdown(job.id()));
    }


}
