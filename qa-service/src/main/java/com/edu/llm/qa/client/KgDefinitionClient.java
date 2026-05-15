package com.edu.llm.qa.client;

import com.edu.llm.common.api.dto.KgConceptDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "kg-service", contextId = "kgDefinition")
public interface KgDefinitionClient {

    @GetMapping("/kg/definition")
    Map<String, Object> definition(@RequestParam("name") String name);

    @GetMapping("/kg/search")
    List<KgConceptDto> search(@RequestParam("keyword") String keyword);
}
