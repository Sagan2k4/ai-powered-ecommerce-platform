package com.aishop.controller;

import com.aishop.dto.AssistantDtos.AssistantRequest;
import com.aishop.dto.AssistantDtos.AssistantResponse;
import com.aishop.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping
    public AssistantResponse ask(@Valid @RequestBody AssistantRequest request) {
        return assistantService.ask(request.message());
    }
}
