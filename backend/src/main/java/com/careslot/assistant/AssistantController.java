package com.careslot.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final String apiKey;
    private final String model;
    public AssistantController(@Value("${careslot.anthropic-api-key:}") String apiKey,
                               @Value("${careslot.anthropic-model}") String model) {
        this.apiKey = apiKey; this.model = model;
    }
    public record DraftRequest(@NotBlank @Size(max=6000) String sourceNotes) {}
    public record DraftResponse(String draft, boolean requiresReview) {}

    @PostMapping("/summarize")
    @PreAuthorize("hasAuthority('SCOPE_practitioner')")
    public DraftResponse summarize(@Valid @RequestBody DraftRequest request) {
        if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI note drafting is not configured.");
        Map<String,Object> body = Map.of(
            "model", model,
            "max_tokens", 500,
            "system", "Draft a concise clinical visit-note summary using only facts explicitly present in the practitioner notes. Do not add diagnoses, recommendations, or inferred facts. Preserve uncertainty. Return plain text only.",
            "messages", List.of(Map.of("role", "user", "content", request.sourceNotes()))
        );
        try {
            Map<?,?> response = RestClient.create("https://api.anthropic.com")
                .post().uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(body).retrieve().body(Map.class);
            Object contentValue = response == null ? null : response.get("content");
            if (!(contentValue instanceof List<?> content) || content.isEmpty() || !(content.getFirst() instanceof Map<?,?> first) || !(first.get("text") instanceof String draft))
                throw new IllegalStateException("Unexpected AI response.");
            return new DraftResponse(draft, true);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The note draft could not be generated.");
        }
    }
}
