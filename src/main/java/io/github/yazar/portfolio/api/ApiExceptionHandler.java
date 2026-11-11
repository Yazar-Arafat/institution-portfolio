package io.github.yazar.portfolio.api;

import io.github.yazar.portfolio.europepmc.EuropePmcException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Returns errors as RFC 9457 problem details. Spring's base class already handles malformed requests
 * (missing parameter, wrong type) with 400; this adds upstream failures and clearer validation messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The grant search failed, so there is nothing useful to return: 504 for a timeout, 502 otherwise. */
    @ExceptionHandler(EuropePmcException.class)
    ProblemDetail handleEuropePmcFailure(EuropePmcException exception) {
        HttpStatus status = exception.failure() == EuropePmcException.Failure.TIMEOUT
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        // An upstream outage is expected from time to time: one line with the cause is enough, not a stack trace.
        log.warn("Grant search failed: {} (cause: {})", exception.getMessage(),
                NestedExceptionUtils.getMostSpecificCause(exception).toString());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("upstream", exception.api());
        return problem;
    }

    /** Lists each invalid parameter, e.g. "limit: must be less than or equal to 10". */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        ProblemDetail problem = createProblemDetail(exception, status, detail, null, null, request);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
