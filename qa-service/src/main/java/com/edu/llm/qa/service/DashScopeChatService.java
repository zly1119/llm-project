package com.edu.llm.qa.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashScopeChatService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.model:qwen-plus}")
    private String model;

    public String call(List<Message> messages) throws Exception {
        Generation gen = new Generation();
        GenerationParam param =
                GenerationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .messages(messages)
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .build();
        GenerationResult result = gen.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    }
}
