package com.example.demo.exception;

import com.example.demo.dto.res.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.*;

/**
 * Turns every exception into an {@link ApiError} with a message that says what went wrong.
 * Errors from the security filters (missing or invalid token) are routed here by {@code SecurityConfig}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception ex, HttpServletRequest request) {
        return switch (ex) {
            case ApiException e -> error(e.getStatus(), e.getMessage(), request);
            case MethodArgumentNotValidException e -> validationError(e, request);
            case HttpMessageNotReadableException e -> error(BAD_REQUEST,
                    "Request body is missing or is not valid JSON, or a field has the wrong type", request);
            case MethodArgumentTypeMismatchException e -> error(BAD_REQUEST,
                    "Parameter '%s' must be of type %s, but was '%s'".formatted(e.getName(),
                            e.getRequiredType() == null ? "a different type" : e.getRequiredType().getSimpleName(),
                            e.getValue()), request);
            case HttpRequestMethodNotSupportedException e -> error(METHOD_NOT_ALLOWED,
                    "Method %s is not supported for this endpoint. Supported methods: %s"
                            .formatted(e.getMethod(), e.getSupportedHttpMethods()), request);
            case HttpMediaTypeNotSupportedException e -> error(UNSUPPORTED_MEDIA_TYPE,
                    "Content type '%s' is not supported. Send the body as application/json"
                            .formatted(e.getContentType()), request);
            case NoResourceFoundException e -> error(NOT_FOUND,
                    "No endpoint %s %s".formatted(request.getMethod(), request.getRequestURI()), request);
            case AuthenticationException e -> ResponseEntity.status(UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(ApiError.of(UNAUTHORIZED, authenticationMessage(e), request.getRequestURI(), null));
            case AccessDeniedException e -> error(FORBIDDEN, accessDeniedMessage(e), request);
            case DataIntegrityViolationException e -> {
                log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), e);
                yield error(CONFLICT,
                        "The request conflicts with existing data, e.g. a duplicate value or a record still in use", request);
            }
            // other Spring web errors already carry a client status and message
            case ErrorResponse e when !e.getStatusCode().is5xxServerError() ->
                    error(e.getStatusCode(), Objects.requireNonNullElse(e.getBody().getDetail(), ex.getMessage()), request);
            default -> {
                // unexpected: log the details, but don't leak internals to the client
                String reference = UUID.randomUUID().toString();
                log.error("Unexpected error [{}] on {} {}", reference, request.getMethod(), request.getRequestURI(), ex);
                yield error(INTERNAL_SERVER_ERROR,
                        "Unexpected server error. Quote reference " + reference + " when reporting it", request);
            }
        };
    }

    private static String authenticationMessage(AuthenticationException ex) {
        return switch (ex) {
            case InsufficientAuthenticationException e ->
                    "Authentication required: send a token in the 'Authorization: Bearer <token>' header";
            case InvalidBearerTokenException e -> "Invalid token: " + e.getMessage();
            case UsernameNotFoundException e -> "Invalid token: the user no longer exists";
            case BadCredentialsException e -> "Invalid username or password";
            case DisabledException e -> "Account is disabled";
            default -> "Authentication failed: " + ex.getMessage();
        };
    }

    private static String accessDeniedMessage(AccessDeniedException ex) {
        if (ex instanceof AuthorizationDeniedException denied
                && denied.getAuthorizationResult() instanceof ExpressionAuthorizationDecision decision) {
            // denied by @PreAuthorize: name the rule, e.g. hasAuthority('user:read')
            return "Access denied: this action requires " + decision.getExpression().getExpressionString();
        }
        return ex.getMessage() == null || "Access Denied".equals(ex.getMessage())
                ? "Access denied: you don't have permission to perform this action"
                : ex.getMessage();
    }

    private static ResponseEntity<ApiError> validationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.merge(error.getField(), error.getDefaultMessage(), (first, second) -> first + "; " + second));
        String message = fieldErrors.entrySet().stream()
                .map(entry -> "'%s' %s".formatted(entry.getKey(), entry.getValue()))
                .collect(joining(", ", "Validation failed: ", ""));
        return ResponseEntity.badRequest().body(ApiError.of(BAD_REQUEST, message, request.getRequestURI(), fieldErrors));
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI(), null));
    }
}
