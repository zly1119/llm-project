package com.edu.llm.qa.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "kg-service", contextId = "kgDefinition")
public interface KgDefinitionClient {

    @GetMapping("/kg/definition")
    Map<String, Object> definition(@RequestParam("name") String name);
}
