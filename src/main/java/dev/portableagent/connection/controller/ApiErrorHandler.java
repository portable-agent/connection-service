package dev.portableagent.connection.controller;

import dev.portableagent.connection.provider.ProviderCallFailed;
import dev.portableagent.connection.provider.ProviderNotConfigured;
import dev.portableagent.connection.service.ConnectionNotFound;
import dev.portableagent.connection.service.InvalidOAuthCallback;
import dev.portableagent.connection.service.InvalidOAuthSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ConnectionNotFound.class)
    ProblemDetail connectionNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Connection not found", "connection-not-found");
    }

    @ExceptionHandler({InvalidOAuthCallback.class, InvalidOAuthSession.class, HandlerMethodValidationException.class})
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid OAuth request", "invalid-oauth-request");
    }

    @ExceptionHandler(ProviderNotConfigured.class)
    ProblemDetail providerNotConfigured() {
        return problem(HttpStatus.BAD_REQUEST, "Provider is not configured", "provider-not-configured");
    }

    @ExceptionHandler(ProviderCallFailed.class)
    ProblemDetail providerUnavailable() {
        return problem(HttpStatus.BAD_GATEWAY, "Provider is unavailable", "provider-unavailable");
    }

    private ProblemDetail problem(HttpStatus status, String title, String type) {
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setType(URI.create("https://portable-agent.dev/problems/" + type));
        return problem;
    }
}
