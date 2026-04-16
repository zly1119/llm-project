package com.edu.llm.user.controller;

import com.edu.llm.common.api.dto.ChatMessageDto;
import com.edu.llm.user.service.SessionMessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
public class SessionController {

    private final SessionMessageService sessionMessageService;

    public SessionController(SessionMessageService sessionMessageService) {
        this.sessionMessageService = sessionMessageService;
    }

    /** 首次访问或刷新 sessionId（由网关/问答层写入 Cookie）。 */
    @PostMapping("/ensure")
    public Map<String, String> ensure(@RequestParam(value = "sid", required = false) String sid) {
        String id = sessionMessageService.resolveSessionId(sid);
        return Map.of("sessionId", id);
    }

    @GetMapping("/messages")
    public List<ChatMessageDto> messages(@RequestParam("sessionId") String sessionId) {
        return sessionMessageService.getMessages(sessionId);
    }

    @PostMapping("/messages/append")
    public void append(@RequestBody ChatMessageDto body, @RequestParam("sessionId") String sessionId) {
        sessionMessageService.append(sessionId, body);
    }
}
