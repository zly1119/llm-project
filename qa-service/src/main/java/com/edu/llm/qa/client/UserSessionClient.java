package com.edu.llm.qa.client;

import com.edu.llm.common.api.dto.ChatMessageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service", contextId = "userSession")
public interface UserSessionClient {

    @PostMapping("/session/ensure")
    Map<String, String> ensure(@RequestParam(value = "sid", required = false) String sid);

    @GetMapping("/session/messages")
    List<ChatMessageDto> messages(@RequestParam("sessionId") String sessionId);

    @PostMapping("/session/messages/append")
    void append(@RequestBody ChatMessageDto msg, @RequestParam("sessionId") String sessionId);
}
