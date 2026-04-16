package com.edu.llm.qa.service;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.edu.llm.common.api.dto.ChatMessageDto;
import com.edu.llm.qa.client.KgDefinitionClient;
import com.edu.llm.qa.client.UserSessionClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QaChatOrchestrator {

    private final UserSessionClient userSessionClient;
    private final KgDefinitionClient kgDefinitionClient;
    private final DashScopeChatService dashScopeChatService;

    public QaChatOrchestrator(
            UserSessionClient userSessionClient,
            KgDefinitionClient kgDefinitionClient,
            DashScopeChatService dashScopeChatService) {
        this.userSessionClient = userSessionClient;
        this.kgDefinitionClient = kgDefinitionClient;
        this.dashScopeChatService = dashScopeChatService;
    }

    /**
     * @return sessionId（用于写 Cookie）与 answer 正文
     */
    public ChatResult handle(String cookieSessionId, String question) throws Exception {
        Map<String, String> ensured = userSessionClient.ensure(cookieSessionId);
        String sessionId = ensured.get("sessionId");

        if (question == null || question.isBlank()) {
            return new ChatResult(sessionId, "");
        }

        // 1. 知识图谱优先（命中则不写入多轮历史，与原 Main 一致）
        Map<String, Object> kg = kgDefinitionClient.definition(question);
        if (Boolean.TRUE.equals(kg.get("found"))) {
            Object def = kg.get("definition");
            String answer = def != null ? def.toString() : "";
            return new ChatResult(sessionId, answer);
        }

        // 2. 大模型：先写入用户消息（可带「给我答案」后缀），再整表调用
        String finalQuestion = question;
        if (question.matches(".*(给我答案|直接答案|答案|结果是多少|告诉我答案).*")) {
            finalQuestion = question + " 请直接给出最终答案，不要任何解释、提示或反问。";
        }

        userSessionClient.append(new ChatMessageDto(Role.USER.getValue(), finalQuestion), sessionId);

        List<ChatMessageDto> history = userSessionClient.messages(sessionId);
        List<Message> messages = new ArrayList<>();
        for (ChatMessageDto dto : history) {
            messages.add(Message.builder().role(dto.role()).content(dto.content()).build());
        }

        String reply = dashScopeChatService.call(messages);

        userSessionClient.append(new ChatMessageDto(Role.ASSISTANT.getValue(), reply), sessionId);
        return new ChatResult(sessionId, reply);
    }

    public record ChatResult(String sessionId, String answer) {}
}
