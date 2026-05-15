package com.edu.llm.kg.controller;

import com.edu.llm.common.api.dto.KgConceptDto;
import com.edu.llm.kg.service.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/kg")
public class KgController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KgController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping("/definition")
    public Map<String, Object> definition(@RequestParam("name") String name) {
        return knowledgeGraphService
                .findDefinitionByKeyword(name)
                .map(def -> Map.<String, Object>of("found", true, "definition", def))
                .orElse(Map.of("found", false));
    }

    @GetMapping("/search")
    public List<KgConceptDto> search(@RequestParam("keyword") String keyword) {
        return knowledgeGraphService.search(keyword);
    }

    @GetMapping("/knowledge-outline")
    public Map<String, String> knowledgeOutline() {
        String outline = knowledgeGraphService.exportConceptOutline();
        return Map.of("outline", outline.isBlank() ? "" : outline);
    }

    @GetMapping("/association-markdown")
    public Map<String, String> associationMarkdown(@RequestParam("q") String q) {
        return Map.of("markdown", knowledgeGraphService.buildAssociationMarkdown(q));
    }

    @GetMapping("/capability-summary")
    public Map<String, Object> capabilitySummary() {
        return knowledgeGraphService.capabilitySummary();
    }
}
