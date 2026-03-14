package com.borko.rajkovic.pizip.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
public final class PiZipDTOs {

    public record EncodeRequest(
        @NotNull(message = "text must not be null")
        @Size(min = 1, max = 10_000, message = "text must be between 1 and 10,000 characters")
        String text,

        SearchMode searchMode
    ) {}

    public record DecodeRequest(
        @NotNull(message = "encoded must not be null")
        @Size(min = 1, message = "encoded must not be blank")
        String encoded
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EncodeResponse(
        String encoded,
        String originalText,
        int originalBytes,
        int encodedLength,
        double compressionRatio,
        int piRefChunks,
        int literalChunks,
        int totalChunks,
        String searchMode
    ) {}

    public record DecodeResponse(
        String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
        int status,
        String error,
        String message,
        String path
    ) {}
}
