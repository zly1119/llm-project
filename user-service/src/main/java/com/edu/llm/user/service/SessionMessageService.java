package com.edu.llm.user.service;

import com.edu.llm.common.api.dto.ChatMessageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SessionMessageService {

    private static final String SYSTEM_PROMPT =
            "你是一名温和耐心的老师。你的教学风格是：先用启发式提问引导学生思考，如果学生实在想不出来，再逐步给出提示，最后才给出完整解答。请用中文回答。";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public SessionMessageService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.session.redis-ttl-hours:12}") long ttlHours) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    public List<ChatMessageDto> getMessages(String sessionId) {
        String key = key(sessionId);
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            List<ChatMessageDto> init = new ArrayList<>();
            init.add(new ChatMessageDto("system", SYSTEM_PROMPT));
            save(sessionId, init);
            return init;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            List<ChatMessageDto> init = new ArrayList<>();
            init.add(new ChatMessageDto("system", SYSTEM_PROMPT));
            save(sessionId, init);
            return init;
        }
    }

    public void append(String sessionId, ChatMessageDto msg) {
        List<ChatMessageDto> list = new ArrayList<>(getMessages(sessionId));
        list.add(msg);
        save(sessionId, list);
    }

    public void save(String sessionId, List<ChatMessageDto> messages) {
        try {
            String json = objectMapper.writeValueAsString(messages);
            redis.opsForValue().set(key(sessionId), json, ttl);
        } catch (Exception ignored) {
        }
    }

    private static String key(String sessionId) {
        return "edu:session:messages:" + sessionId;
    }
}
