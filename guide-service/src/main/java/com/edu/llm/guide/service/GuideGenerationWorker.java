package com.edu.llm.guide.service;

import com.edu.llm.guide.client.KgGuideClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GuideGenerationWorker {

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
            long t0 = System.currentTimeMillis();
            Map<String, String> outlineMap = kgGuideClient.knowledgeOutline();
            String knowledgeBase = outlineMap.getOrDefault("outline", "");
            System.err.println("[攻略任务 " + taskId + "] 获取知识库耗时: " + (System.currentTimeMillis() - t0) + "ms");
            if (knowledgeBase == null || knowledgeBase.isEmpty()) {
                knowledgeBase = "（知识库暂不可用，以下攻略将依据高等数学通用教学大纲组织。）";
            }

            String assocBlock =
                    kgGuideClient.associationMarkdown(question).getOrDefault("markdown", "");

            String prompt =
                    """
                    你是高等数学学习规划师。用户的学习诉求或具体描述：%s
                    用户的学习目标是：%s，当前基础：%s，计划学习天数：%s天。
                    请根据以下高等数学知识体系，制定一份详细的学习攻略。

                    知识体系：
                    %s

                    要求：
                    1. 输出使用Markdown格式，包含标题、子标题、列表、公式。
                    2. 严格按照用户填写的学习天数（%s天）来规划每个阶段的时间分配，给出具体到每天哪个时间段的具体安排（小于三天），（大于三天则具体到每天）。
                    3. 将学习过程分为3-4个阶段（如基础巩固、核心深化、冲刺提升、考前突破），每阶段注明占用天数。
                    4. 每个阶段列出重点概念、学习方法、典型例题（可引用知识体系中的具体概念）。
                    5. 不要编造不存在的视频链接或评论区内容；若推荐学习资源，只写通用类型（如教材章节、习题类型），勿虚构具体平台与评论。
                    6. 语言亲切，像一位有经验的学长/学姐在分享经验。
                    """
                    .formatted(question, goal, level, days, knowledgeBase, days);

            System.err.println("[攻略任务 " + taskId + "] 提示词长度: " + prompt.length() + " 字符");
            long beforeLLM = System.currentTimeMillis();
            String guide = guideLlmService.generateGuideBody(prompt);
            System.err.println("[攻略任务 " + taskId + "] 大模型生成耗时: " + (System.currentTimeMillis() - beforeLLM) + "ms");
            String full = assocBlock + "\n" + guide;
            guideTaskStore.markDone(taskId, full);
        } catch (Exception e) {
            e.printStackTrace();
            guideTaskStore.markFailed(taskId, "抱歉，生成攻略时遇到问题：" + e.getMessage());
        }
    }
}
