package com.spotnana.flightsearch.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One error shape for the whole API, using RFC 7807 {@link ProblemDetail}.
 *
 * <p>Every client mistake is a 400 carrying a stable {@code code}. Only genuinely unexpected
 * failures reach 500, and a bad airport code is never one of them.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_REQUEST_TITLE = "Invalid search request";

    /** A well-formed request the dataset cannot answer, such as an unknown airport. */
    @ExceptionHandler(InvalidSearchException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSearch(
            InvalidSearchException exception, HttpServletRequest request) {

        ProblemDetail problem =
                problemDetail(
                        HttpStatus.BAD_REQUEST,
                        INVALID_REQUEST_TITLE,
                        exception.getMessage(),
                        exception.code(),
                        request.getRequestURI());
        problem.setProperty(
                "errors", List.of(fieldError(exception.field(), exception.getMessage())));

        return ResponseEntity.badRequest().body(problem);
    }

    /** Bean Validation failures raised through the {@code @Validated} proxy. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {

        List<Map<String, String>> errors =
                exception.getConstraintViolations().stream()
                        .map(violation -> fieldError(lastNodeOf(violation), violation.getMessage()))
                        .toList();

        return ResponseEntity.badRequest()
                .body(
                        validationProblem(
                                errors, ErrorCode.INVALID_AIRPORT_CODE, request.getRequestURI()));
    }

    /** A parameter that could not be converted, in practice a malformed date. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {

        boolean isDate = "date".equals(exception.getName());
        String detail =
                isDate
                        ? "date must be an ISO-8601 calendar date such as 2024-03-15, but was '%s'."
                                .formatted(exception.getValue())
                        : "%s has an invalid value '%s'."
                                .formatted(exception.getName(), exception.getValue());

        ProblemDetail problem =
                problemDetail(
                        HttpStatus.BAD_REQUEST,
                        INVALID_REQUEST_TITLE,
                        detail,
                        isDate ? ErrorCode.INVALID_DATE : ErrorCode.INVALID_AIRPORT_CODE,
                        request.getRequestURI());
        problem.setProperty("errors", List.of(fieldError(exception.getName(), detail)));

        return ResponseEntity.badRequest().body(problem);
    }

    /** Anything unforeseen. Logged with its stack trace; the client sees no internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request) {

        log.error("Unhandled failure serving {}", request.getRequestURI(), exception);

        return ResponseEntity.internalServerError()
                .body(
                        problemDetail(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal server error",
                                "The request could not be completed.",
                                ErrorCode.INTERNAL_ERROR,
                                request.getRequestURI()));
    }

    /**
     * Bean Validation failures raised by Spring's built-in method validation.
     *
     * <p>Overridden rather than declared with {@code @ExceptionHandler}: the base class
     * already claims this type, and a second handler of equal specificity makes the mapping
     * ambiguous and fails context startup.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> errors =
                exception.getAllErrors().stream()
                        .map(error -> fieldError(null, error.getDefaultMessage()))
                        .toList();

        return ResponseEntity.badRequest()
                .body(
                        validationProblem(
                                errors, ErrorCode.INVALID_AIRPORT_CODE, pathOf(request)));
    }

    /** A required query parameter was absent. */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String detail = "%s is required.".formatted(exception.getParameterName());

        ProblemDetail problem =
                problemDetail(
                        HttpStatus.BAD_REQUEST,
                        INVALID_REQUEST_TITLE,
                        detail,
                        ErrorCode.MISSING_PARAMETER,
                        pathOf(request));
        problem.setProperty("errors", List.of(fieldError(exception.getParameterName(), detail)));

        return ResponseEntity.badRequest().body(problem);
    }

    // ------------------------------------------------------------------ helpers

    private ProblemDetail validationProblem(
            List<Map<String, String>> errors, ErrorCode code, String path) {

        String detail =
                errors.stream()
                        .map(error -> error.get("message"))
                        .reduce((first, second) -> first + " " + second)
                        .orElse("The request parameters are invalid.");

        ProblemDetail problem =
                problemDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_TITLE, detail, code, path);
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problemDetail(
            HttpStatus status, String title, String detail, ErrorCode code, String path) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return field == null
                ? Map.of("message", message)
                : Map.of("field", field, "message", message);
    }

    /** Violations are reported as {@code method.parameter}; only the parameter is useful. */
    private static String lastNodeOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    private static String pathOf(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : null;
    }
}
