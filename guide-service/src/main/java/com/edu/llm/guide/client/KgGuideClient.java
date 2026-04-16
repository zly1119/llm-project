package com.edu.llm.guide.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "kg-service", contextId = "kgGuide")
public interface KgGuideClient {

    @GetMapping("/kg/knowledge-outline")
    Map<String, String> knowledgeOutline();

    @GetMapping("/kg/association-markdown")
    Map<String, String> associationMarkdown(@RequestParam("q") String q);
}
