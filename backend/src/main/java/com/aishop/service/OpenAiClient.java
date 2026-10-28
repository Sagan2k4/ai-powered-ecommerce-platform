package com.aishop.service;

import com.aishop.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal client for the OpenAI Chat Completions API, constrained to JSON-object output. */
@Component
public class OpenAiClient {

    private final AppProperties.OpenAi config;
    private final RestClient http;

    public OpenAiClient(AppProperties props) {
        this.config = props.openai();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(config.timeout()).build());
        factory.setReadTimeout(config.timeout());
        this.http = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** Returns the model's raw JSON string reply. */
    public Optional<String> completeJson(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", config.model(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        ChatResponse response = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.choices().getFirst().message().content());
    }

    record ChatResponse(List<Choice> choices) {
    }

    record Choice(Message message) {
    }

    record Message(String content) {
    }
}
