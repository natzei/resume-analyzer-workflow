package com.example.resume.api;

import akka.http.javadsl.model.*;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import com.example.resume.application.ResumeAnalysisWorkflow;
import com.example.resume.domain.ApplicationFormBytes;
import com.example.resume.domain.ResumeBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/my-workflow")
public class MyEndpoint extends AbstractHttpEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MyEndpoint.class);

    private final ComponentClient componentClient;

    public MyEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/{id}/setup")
    public CompletionStage<HttpResponse> setup(String id) {
        logger.info("Setup of workflow id [{}].", id);
        return componentClient.forWorkflow(id)
                .method(ResumeAnalysisWorkflow::setup).invokeAsync()
                .thenApply(__ -> HttpResponses.created(id).addHeader(HttpHeader.parse("Location", id)));
    }

    private final static ContentType APPLICATION_PDF = ContentTypes.create(MediaTypes.APPLICATION_PDF);

    @Post("/{id}/application-form")
    public CompletionStage<HttpResponse> uploadApplicationForm(String id, HttpEntity.Strict body) {
        if (!body.getContentType().equals(APPLICATION_PDF))
            throw HttpException.badRequest("You must provide a " + APPLICATION_PDF);
        else {
            var bytes = body.getData();
            logger.info("Received application form PDF [size={}]", bytes.length());

            return componentClient.forWorkflow(id)
                    .method(ResumeAnalysisWorkflow::acceptApplicationForm)
                    .invokeAsync(new ApplicationFormBytes(bytes))
                    .thenApply(__ -> HttpResponses.ok("Application form PDF received"));
        }
    }

    @Post("/{id}/resume")
    public CompletionStage<HttpResponse> uploadResume(String id, HttpEntity.Strict body) {
        if (!body.getContentType().equals(APPLICATION_PDF))
            throw HttpException.badRequest("You must provide a " + APPLICATION_PDF);
        else {
            var bytes = body.getData();
            logger.info("Received resume PDF [size={}]", bytes.length());

            return componentClient.forWorkflow(id)
                    .method(ResumeAnalysisWorkflow::acceptResume)
                    .invokeAsync(new ResumeBytes(bytes))
                    .thenApply(__ -> HttpResponses.ok("Resume PDF received"));
        }
    }

    @Post("/{id}/start")
    public CompletionStage<HttpResponse> start(String id) {
        logger.info("Starting workflow id [{}].", id);
        return componentClient.forWorkflow(id)
                .method(ResumeAnalysisWorkflow::start).invokeAsync()
                .thenApply(__ -> HttpResponses.ok(id));
    }

    @Get("/{id}")
    public CompletionStage<HttpResponse> getStatus(String id) {
        logger.info("Getting status for workflow id [{}].", id);
        return componentClient.forWorkflow(id)
                .method(ResumeAnalysisWorkflow::getStatus)
                .invokeAsync()
                .thenApply(StatusApi::toApi)
                .thenApply(HttpResponses::ok);
    }
}
