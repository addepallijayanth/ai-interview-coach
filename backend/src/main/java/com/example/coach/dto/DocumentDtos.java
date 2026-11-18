package com.example.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public final class DocumentDtos {

    private DocumentDtos() {}

    /** {@code redactedText} is exactly what the AI will see, so the UI can show that PII was masked. */
    public record ResumeResponse(Long id, String fileName, int characters, Map<String, Integer> redactions,
            String redactedText, Instant createdAt) {}

    public record ResumeSummary(Long id, String fileName, Instant createdAt) {}

    public record JobDescriptionRequest(
            @Size(max = 200) String title,
            @Size(max = 200) String company,
            @NotBlank @Size(min = 50, max = 20000, message = "must be between 50 and 20,000 characters") String text) {}

    public record JobDescriptionResponse(Long id, String title, String company, int characters, Instant createdAt) {}
}
