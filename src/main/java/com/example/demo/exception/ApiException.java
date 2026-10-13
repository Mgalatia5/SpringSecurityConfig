package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

/** An error reported to the client with the given status and message. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** For {@code findById(id).orElseThrow(notFound("Post", id))}: 404 "Post with id 42 not found". */
    public static Supplier<ApiException> notFound(String resource, Object id) {
        return () -> new ApiException(HttpStatus.NOT_FOUND, "%s with id %s not found".formatted(resource, id));
    }
}
