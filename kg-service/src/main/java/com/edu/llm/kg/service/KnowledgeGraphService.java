package com.edu.llm.kg.service;

import com.edu.llm.common.api.dto.KgConceptDto;
import org.neo4j.driver.Driver;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeGraphService {

    private final Neo4jClient neo4jClient;
    private final Driver driver;

    public KnowledgeGraphService(Neo4jClient neo4jClient, Driver driver) {
        this.neo4jClient = neo4jClient;
        this.driver = driver;
    }

    /** 与单体 queryKnowledgeGraph 一致：按问题串模糊匹配概念名，取第一条 definition。 */
    public Optional<String> findDefinitionByKeyword(String keyword) {
        String cypher =
                """
                MATCH (n:Concept)
                WHERE toLower(n.name) CONTAINS toLower($name)
                RETURN n.definition AS definition
                LIMIT 1
                """;
        return neo4jClient
                .query(cypher)
                .bind(keyword).to("name")
                .fetchAs(String.class)
                .mappedBy((t, r) -> r.get("definition").isNull() ? null : r.get("definition").asString())
                .one();
    }

    public List<KgConceptDto> search(String keyword) {
        String cypher =
                """
                MATCH (n:Concept)
                WHERE toLower(n.name) CONTAINS toLower($kw)
                   OR toLower(coalesce(n.definition,'')) CONTAINS toLower($kw)
                RETURN n.name AS name,
                       coalesce(n.chapter,'') AS chapter,
                       coalesce(n.definition,'') AS definition,
                       coalesce(n.formulas,'') AS formulas,
                       coalesce(n.questionTypes,'') AS questionTypes
                LIMIT 20
                """;
        return new ArrayList<>(
                neo4jClient
                        .query(cypher)
                        .bind(keyword).to("kw")
                        .fetchAs(KgConceptDto.class)
                        .mappedBy(
                                (t, r) ->
                                        new KgConceptDto(
                                                r.get("name").asString(""),
                                                r.get("chapter").asString(""),
                                                r.get("definition").asString(""),
                                                r.get("formulas").asString(""),
                                                r.get("questionTypes").asString("")))
                        .all());
    }

    /** 攻略提示词用：全书概念按章节列出（与 Main#getAllKnowledge 一致）。 */
    public String exportConceptOutline() {
        String cypher =
                """
                MATCH (n:Concept)
                RETURN coalesce(n.chapter,'未分类') AS chapter, n.name AS name
                ORDER BY chapter, name
                """;
        StringBuilder sb = new StringBuilder();
        String currentChapter = "";
        try (var session = driver.session()) {
            var result = session.run(cypher);
            while (result.hasNext()) {
                var r = result.next();
                String chapter = r.get("chapter").isNull() ? "未分类" : r.get("chapter").asString();
                String name = r.get("name").asString();
                if (!chapter.equals(currentChapter)) {
                    currentChapter = chapter;
                    sb.append("\n### ").append(currentChapter).append("\n");
                }
                sb.append("- ").append(name).append("\n");
            }
        }
        return sb.toString();
    }

    public String buildAssociationMarkdown(String userQuestion) {
        String q = userQuestion == null ? "" : userQuestion.trim();
        LinkedHashSet<String> chapters = new LinkedHashSet<>();

        String cypher =
                """
                MATCH (n:Concept)
                WHERE toLower($q) CONTAINS toLower(n.name) OR toLower(n.name) CONTAINS toLower($q)
                RETURN DISTINCT coalesce(n.chapter,'') AS ch
                LIMIT 32
                """;
        try {
            neo4jClient
                    .query(cypher)
                    .bind(q).to("q")
                    .fetchAs(String.class)
                    .mappedBy((t, r) -> r.get("ch").asString(""))
                    .all()
                    .forEach(ch -> {
                        if (ch != null && !ch.isBlank()) {
                            chapters.add(ch.trim());
                        }
                    });
        } catch (Exception ignored) {
        }

        if (chapters.isEmpty()) {
            chapters.addAll(ChapterKeywordInference.inferChaptersFromQuestion(q));
        }

        StringBuilder md = new StringBuilder();
        md.append("## 知识体系与章节关联\n\n");
        md.append("根据知识图谱检测到关联章节有：");
        if (chapters.isEmpty()) {
            md.append("未明确匹配到特定章节，建议从基础章节（函数与极限、导数与微分）开始学习。\n\n");
        } else {
            md.append(String.join("、", chapters)).append("。\n\n");
        }
        return md.toString();
    }
}
