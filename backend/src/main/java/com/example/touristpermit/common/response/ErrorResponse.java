package com.example.touristpermit.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard error envelope returned by every endpoint")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(example = "2026-10-07T12:00:00Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "NOT_FOUND") String error,
        @Schema(example = "Permit not found") String message,
        @Schema(example = "/api/v1/permits/123") String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
