package com.example.demo.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;

/**
 * The body of every error response.
 *
 * @param fieldErrors only present for validation errors: field name -> what is wrong with it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatusCode status, String message, String path, Map<String, String> fieldErrors) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return new ApiError(Instant.now(), status.value(), known != null ? known.getReasonPhrase() : status.toString(),
                message, path, fieldErrors);
    }
}
