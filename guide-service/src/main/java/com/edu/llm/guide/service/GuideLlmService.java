package com.edu.llm.guide.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GuideLlmService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.model:qwen-plus}")
    private String model;

    public String generateGuideBody(String prompt) throws Exception {
        return call(prompt);
    }

    public String generateAssociationMarkdown(String question) throws Exception {
        String prompt =
                """
                你是高等数学课程规划助手。用户提问主题是：%s

                请只输出一个 Markdown 小节，标题必须是“## 知识体系与章节关联”。
                目标是为“攻略模式”补全关联章节，要求：
                1. 优先按高等数学课程结构罗列与该主题直接相关的章节。
                2. 如果主题较宽，如“微积分”“高数”“高等数学”，要展开成较完整的章节清单，而不是只给一个章节。
                3. 输出先写一句简短说明，再用无序列表列出章节。
                4. 不要输出多余解释，不要写代码块。
                """
                        .formatted(question);
        return call(prompt);
    }

    private String call(String prompt) throws Exception {
        Generation gen = new Generation();
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(prompt).build();
        GenerationParam param =
                GenerationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .messages(List.of(userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .build();
        GenerationResult result = gen.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    }
}
