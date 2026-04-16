package com.edu.llm.qa.controller;

import com.edu.llm.qa.service.QaChatOrchestrator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
public class ChatController {

    private final QaChatOrchestrator orchestrator;

    public ChatController(QaChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> chat(
            @CookieValue(value = "session", required = false) String sessionCookie,
            @RequestParam(value = "q", required = false) String q,
            HttpServletResponse response)
            throws Exception {
        if (q == null || q.isBlank()) {
            return Map.of("error", "问题不能为空");
        }
        QaChatOrchestrator.ChatResult result = orchestrator.handle(sessionCookie, q);

        ResponseCookie cookie =
                ResponseCookie.from("session", result.sessionId())
                        .httpOnly(true)
                        .path("/")
                        .maxAge(Duration.ofHours(12))
                        .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return Map.of("answer", result.answer());
    }
}
