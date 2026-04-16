package com.edu.llm.common.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KgConceptDto(
        String name,
        String chapter,
        String definition,
        String formulas,
        String questionTypes
) {}
