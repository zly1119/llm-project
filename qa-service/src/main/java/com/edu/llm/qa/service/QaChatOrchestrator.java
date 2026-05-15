package com.edu.llm.qa.service;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.edu.llm.common.api.dto.ChatMessageDto;
import com.edu.llm.common.api.dto.KgConceptDto;
import com.edu.llm.qa.client.KgDefinitionClient;
import com.edu.llm.qa.client.UserSessionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QaChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QaChatOrchestrator.class);

    private static final String DEFAULT_PROMPT =
            """
            你是高等数学学习助手。请直接回答用户问题，语言自然、准确，不要无故展开成长篇讲义。
            公式请使用 LaTeX。
            """;

    private static final String CONCEPT_PROMPT =
            """
            你是高等数学学习助手。用户现在在问概念题。
            请按“定义 + 核心公式 + 一句直观解释”回答，控制篇幅简洁，不要展开成大段讲义。
            公式请使用 LaTeX。
            """;

    private static final String EXAMPLE_PROMPT =
            """
            你是高等数学学习助手。用户现在是在要“一道例题”。
            请统一按下面风格回答：
            1. 只给 1 道匹配主题的典型例题。
            2. 按“例题、思路引导、解法步骤、答案”输出。
            3. 思路引导要简短，解法步骤保持课堂例题风格，结构清楚，像卡片式讲解。
            4. 如果用户没有明确要求“详细讲解”，就不要加入大段原理铺垫。
            5. 公式使用 LaTeX。
            """;

    private static final String DETAILED_EXAMPLE_PROMPT =
            """
            你是高等数学学习助手。用户现在既要例题，也要讲解。
            请给 1 道典型例题，并按“题目、思路引导、解题步骤、答案”结构回答。
            保持讲解清楚，但不要跑题，不要写成大篇讲义。
            公式使用 LaTeX。
            """;

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

    public ChatResult handle(String cookieSessionId, String question) throws Exception {
        Map<String, String> ensured = userSessionClient.ensure(cookieSessionId);
        String sessionId = ensured.get("sessionId");

        if (question == null || question.isBlank()) {
            return new ChatResult(sessionId, "");
        }

        String trimmedQuestion = question.trim();
        boolean exampleRequest = isExampleRequest(trimmedQuestion);
        boolean conceptRequest = isConceptRequest(trimmedQuestion);
        boolean detailedRequest = wantsDetailedAnswer(trimmedQuestion);

        try {
            if (conceptRequest && !exampleRequest && !detailedRequest) {
                String conceptAnswer = buildConceptAnswer(trimmedQuestion);
                if (!conceptAnswer.isBlank()) {
                    return new ChatResult(sessionId, conceptAnswer);
                }
            }

            Map<String, Object> kg = kgDefinitionClient.definition(trimmedQuestion);
            if (!exampleRequest && !conceptRequest && Boolean.TRUE.equals(kg.get("found"))) {
                Object def = kg.get("definition");
                String answer = def != null ? def.toString() : "";
                return new ChatResult(sessionId, answer);
            }
        } catch (Exception e) {
            log.warn("KG definition lookup failed, falling back to LLM. question={}", trimmedQuestion, e);
        }

        String finalQuestion = buildFinalQuestion(trimmedQuestion, exampleRequest, detailedRequest);
        userSessionClient.append(new ChatMessageDto(Role.USER.getValue(), finalQuestion), sessionId);

        List<ChatMessageDto> history = userSessionClient.messages(sessionId);
        List<Message> messages = buildMessages(history, runtimePrompt(exampleRequest, conceptRequest && !detailedRequest, detailedRequest));
        String reply = dashScopeChatService.call(messages);

        userSessionClient.append(new ChatMessageDto(Role.ASSISTANT.getValue(), reply), sessionId);
        return new ChatResult(sessionId, reply);
    }

    private List<Message> buildMessages(List<ChatMessageDto> history, String runtimePrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder().role(Role.SYSTEM.getValue()).content(runtimePrompt).build());
        for (ChatMessageDto dto : history) {
            if (dto == null || dto.content() == null || dto.content().isBlank()) {
                continue;
            }
            if (Role.SYSTEM.getValue().equalsIgnoreCase(dto.role())) {
                continue;
            }
            messages.add(Message.builder().role(dto.role()).content(dto.content()).build());
        }
        return messages;
    }

    private String runtimePrompt(boolean exampleRequest, boolean conceptRequest, boolean detailedRequest) {
        if (exampleRequest && detailedRequest) {
            return DETAILED_EXAMPLE_PROMPT;
        }
        if (exampleRequest) {
            return EXAMPLE_PROMPT;
        }
        if (conceptRequest) {
            return CONCEPT_PROMPT;
        }
        return DEFAULT_PROMPT;
    }

    private String buildFinalQuestion(String question, boolean exampleRequest, boolean detailedRequest) {
        if (wantsAnswerOnly(question)) {
            return question + " 请直接给出最终答案，不要任何解释、提示或反问。";
        }
        if (exampleRequest && detailedRequest) {
            return question + " 请给我 1 道匹配主题的典型例题，并按题目、思路引导、解题步骤、答案来讲解。";
        }
        if (exampleRequest) {
            return question + " 请只给我 1 道匹配主题的典型例题，并按例题、思路引导、解法步骤、答案来回答，不要展开成长篇讲义。";
        }
        if (detailedRequest) {
            return question + " 请分步骤讲清楚，但不要加入无关扩展。";
        }
        return question;
    }

    private String buildConceptAnswer(String question) {
        List<KgConceptDto> concepts = kgDefinitionClient.search(question);
        if (concepts == null || concepts.isEmpty()) {
            return "";
        }

        KgConceptDto concept = concepts.get(0);
        String definition = clean(concept.definition());
        String formulas = clean(concept.formulas());
        if (definition.isBlank() && formulas.isBlank()) {
            return "";
        }

        StringBuilder answer = new StringBuilder();
        if (!definition.isBlank()) {
            answer.append(ensureSentence(definition));
        }

        List<String> formulaLines = formatFormulaLines(formulas);
        if (!formulaLines.isEmpty()) {
            if (!answer.isEmpty()) {
                answer.append("\n\n");
            }
            for (int i = 0; i < formulaLines.size(); i++) {
                answer.append(formulaLines.get(i));
                if (i < formulaLines.size() - 1) {
                    answer.append("\n");
                }
            }
        }

        String intuition = buildConceptIntuition(clean(concept.name()), question, definition);
        if (!intuition.isBlank()) {
            if (!answer.isEmpty()) {
                answer.append("\n\n");
            }
            answer.append(intuition);
        }
        return answer.toString().trim();
    }

    private boolean isExampleRequest(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "例题",
                "来一道",
                "来一题",
                "出一道",
                "出一题",
                "给我一题",
                "给我一道",
                "给我一个例题",
                "请给我一个例题",
                "请给我一道",
                "请给我一题");
    }

    private boolean isConceptRequest(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "什么是",
                "是什么意思",
                "定义",
                "概念",
                "公式是什么",
                "性质是什么",
                "几何意义",
                "曲率",
                "导数",
                "极限")
                || normalized.endsWith("是什么")
                || normalized.endsWith("的定义");
    }

    private boolean wantsDetailedAnswer(String question) {
        String normalized = normalize(question);
        return containsAny(
                normalized,
                "详细",
                "具体",
                "展开",
                "推导",
                "证明",
                "步骤",
                "一步一步",
                "怎么做",
                "如何做",
                "为什么",
                "分析",
                "讲解",
                "说明");
    }

    private boolean wantsAnswerOnly(String question) {
        String normalized = normalize(question);
        return containsAny(normalized, "给我答案", "直接答案", "答案", "结果是多少", "告诉我答案");
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }

    private String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private String ensureSentence(String text) {
        if (text.isBlank()) {
            return "";
        }
        if (text.endsWith("。") || text.endsWith("！") || text.endsWith("？")) {
            return text;
        }
        return text + "。";
    }

    private List<String> formatFormulaLines(String formulas) {
        List<String> lines = new ArrayList<>();
        if (formulas == null || formulas.isBlank()) {
            return lines;
        }
        String[] parts = formulas.split("\\r?\\n|；|;");
        for (String part : parts) {
            String line = part == null ? "" : part.trim();
            if (line.isBlank()) {
                continue;
            }
            lines.add(wrapFormula(line));
            if (lines.size() >= 3) {
                break;
            }
        }
        return lines;
    }

    private String wrapFormula(String line) {
        if (line.startsWith("\\[") || line.startsWith("\\(") || line.startsWith("$$") || line.startsWith("$")) {
            return line;
        }
        return "\\[" + line + "\\]";
    }

    private String buildConceptIntuition(String name, String question, String definition) {
        String combined = normalize(name + " " + question + " " + definition);
        if (combined.contains("曲率")) {
            return "曲率越大，曲线在该点越弯；直线的曲率为 0。";
        }
        if (combined.contains("导数")) {
            return "直观上，导数刻画函数在该点的瞬时变化率。";
        }
        if (combined.contains("极限")) {
            return "直观上，极限描述变量不断逼近某个值时的变化趋势。";
        }
        return "";
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public record ChatResult(String sessionId, String answer) {}
}
