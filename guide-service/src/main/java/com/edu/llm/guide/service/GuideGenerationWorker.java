package com.edu.llm.guide.service;

import com.edu.llm.guide.client.KgGuideClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GuideGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(GuideGenerationWorker.class);
    private static final String KNOWLEDGE_BASE_FALLBACK = "（知识库暂时不可用，以下攻略将依据高等数学通用教学大纲组织。）";

    private final KgGuideClient kgGuideClient;
    private final GuideLlmService guideLlmService;
    private final GuideTaskStore guideTaskStore;

    public GuideGenerationWorker(
            KgGuideClient kgGuideClient, GuideLlmService guideLlmService, GuideTaskStore guideTaskStore) {
        this.kgGuideClient = kgGuideClient;
        this.guideLlmService = guideLlmService;
        this.guideTaskStore = guideTaskStore;
    }

    @Async("guideExecutor")
    public void run(String taskId, String question, String goal, String level, String days) {
        try {
            String knowledgeBase = loadKnowledgeBase(taskId);
            String assocBlock = loadAssociationBlock(taskId, question);

            String prompt =
                    """
                    你是高等数学学习规划师。用户的学习诉求或具体描述：%s
                    用户的学习目标是：%s，当前基础：%s，计划学习天数：%s天。
                    请根据以下高等数学知识体系，制定一份详细的学习攻略。
                    知识体系：
                    %s

                    要求：
                    1. 输出使用 Markdown 格式，包含标题、子标题、列表、公式。
                    2. 严格按照用户填写的学习天数（%s天）来规划每个阶段的时间分配，给出具体到每天的学习安排。
                    3. 将学习过程分为 2-4 个阶段（如基础巩固、核心深化、冲刺提升、考前突破），每个阶段注明占用天数。
                    4. 每个阶段列出重点概念、学习方法、典型例题。
                    5. 若推荐学习资源，只写通用类型（如教材章节、习题类型），不要虚构平台、链接或评论。
                    6. 语言亲切，像一位有经验的学长学姐在分享经验。
                    """
                            .formatted(question, goal, level, days, knowledgeBase, days);

            log.info("[攻略任务 {}] 提示词长度: {} 字符", taskId, prompt.length());
            long beforeLLM = System.currentTimeMillis();
            String guide = guideLlmService.generateGuideBody(prompt);
            log.info("[攻略任务 {}] 大模型生成耗时: {}ms", taskId, System.currentTimeMillis() - beforeLLM);

            String full = assocBlock.isBlank() ? guide : assocBlock + "\n" + guide;
            guideTaskStore.markDone(taskId, full);
        } catch (Exception e) {
            log.error("Guide generation failed. taskId={}", taskId, e);
            guideTaskStore.markFailed(taskId, "抱歉，生成攻略时遇到问题：" + e.getMessage());
        }
    }

    private String loadKnowledgeBase(String taskId) {
        long start = System.currentTimeMillis();
        try {
            Map<String, String> outlineMap = kgGuideClient.knowledgeOutline();
            String outline = outlineMap.getOrDefault("outline", "");
            log.info("[攻略任务 {}] 获取知识库耗时: {}ms", taskId, System.currentTimeMillis() - start);
            return (outline == null || outline.isBlank()) ? KNOWLEDGE_BASE_FALLBACK : outline;
        } catch (Exception e) {
            log.warn("Load knowledge outline failed, fallback to generic plan. taskId={}", taskId, e);
            return KNOWLEDGE_BASE_FALLBACK;
        }
    }

    private String loadAssociationBlock(String taskId, String question) {
        String associationMarkdown = "";
        try {
            Map<String, String> associationMap = kgGuideClient.associationMarkdown(question);
            associationMarkdown = associationMap.getOrDefault("markdown", "");
        } catch (Exception e) {
            log.warn("Load association markdown failed, continue with fallback. taskId={}", taskId, e);
        }

        if (shouldUseLlmAssociation(question, associationMarkdown)) {
            try {
                String llmAssociation = guideLlmService.generateAssociationMarkdown(question);
                if (llmAssociation != null && !llmAssociation.isBlank()) {
                    return llmAssociation;
                }
            } catch (Exception e) {
                log.warn("LLM association fallback failed. taskId={}", taskId, e);
            }
        }
        return associationMarkdown;
    }

    private boolean shouldUseLlmAssociation(String question, String associationMarkdown) {
        String q = question == null ? "" : question.toLowerCase();
        boolean broadTopic =
                q.contains("微积分") || q.contains("高数") || q.contains("高等数学") || q.contains("calculus");
        if (!broadTopic) {
            return associationMarkdown == null || associationMarkdown.isBlank();
        }
        return countBulletItems(associationMarkdown) < 4;
    }

    private int countBulletItems(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                count++;
            }
        }
        return count;
    }
}
