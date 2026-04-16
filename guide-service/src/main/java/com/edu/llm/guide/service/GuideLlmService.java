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
