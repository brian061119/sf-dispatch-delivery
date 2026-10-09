package com.wedelivery.controller;

import com.wedelivery.entity.User;
import com.wedelivery.service.AiDeliveryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiDeliveryService service;
    @PostMapping("/parse")
    public Map<String,Object> parse(@Valid @RequestBody ParseRequest request) { return service.parse(request.getText()); }
    @PostMapping("/chat")
    public Map<String,Object> chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal User user) {
        return service.chat(request.getMessage(), request.getHistory(), user);
    }
    @Data public static class ParseRequest { @NotBlank @Size(max=4000) private String text; }
    @Data public static class ChatRequest {
        @NotBlank @Size(max=4000) private String message;
        @Size(max=40) private List<Map<String,String>> history;
    }
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String,String> unavailable(IllegalStateException e) { return Map.of("message", e.getMessage()); }
}
