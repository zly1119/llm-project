package com.edu.llm.kg.service;

import com.edu.llm.common.api.dto.KgConceptDto;
import org.neo4j.driver.Driver;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeGraphService {

    private final Neo4jClient neo4jClient;
    private final Driver driver;

    public KnowledgeGraphService(Neo4jClient neo4jClient, Driver driver) {
        this.neo4jClient = neo4jClient;
        this.driver = driver;
    }

    public Optional<String> findDefinitionByKeyword(String keyword) {
        String cypher =
                """
                MATCH (n:知识点)
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
                MATCH (n:知识点)
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

    public String exportConceptOutline() {
        String cypher =
                """
                MATCH (n:知识点)
                RETURN coalesce(n.chapter,'未分类') AS chapter, n.name AS name
                ORDER BY chapter, name
                """;
        StringBuilder sb = new StringBuilder();
        String currentChapter = "";
        try (var session = driver.session()) {
            var result = session.run(cypher);
            while (result.hasNext()) {
                var record = result.next();
                String chapter = record.get("chapter").isNull() ? "未分类" : record.get("chapter").asString();
                String name = record.get("name").asString();
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
        chapters.addAll(findChaptersFromGraph(q));
        chapters.addAll(ChapterKeywordInference.inferChaptersFromQuestion(q));

        StringBuilder md = new StringBuilder();
        md.append("## 知识体系与章节关联\n\n");
        if (chapters.isEmpty()) {
            md.append("未明确匹配到特定章节，建议先从以下基础内容开始：\n");
            md.append("- 第一章 函数、极限与连续\n");
            md.append("- 第二章 一元函数微分学：导数与微分\n");
            return md.toString();
        }

        if (isBroadTopic(q)) {
            md.append("检测到这是一个范围较宽的主题，已结合知识图谱与课程结构自动展开关联章节：\n");
        } else {
            md.append("根据知识图谱与主题词推断，当前问题优先关联以下章节：\n");
        }
        for (String chapter : chapters) {
            md.append("- ").append(chapter).append("\n");
        }
        return md.toString();
    }

    public Map<String, Object> capabilitySummary() {
        return Map.of(
                "scope", "当前 Demo 重点覆盖高等数学知识图谱与学习规划，不是所有数学分支。",
                "chapterCount", countChapters(),
                "conceptCount", countConcepts(),
                "focusAreas",
                        List.of(
                                "函数、极限与连续",
                                "一元函数微分学",
                                "一元函数积分学",
                                "常微分方程",
                                "多元函数微分学",
                                "多元函数积分学",
                                "曲线曲面积分",
                                "无穷级数"),
                "tolerance",
                        List.of(
                                "支持口语化提问、章节级主题、概念别名和部分模糊描述。",
                                "攻略模式遇到“微积分 / 高数”这类宽主题时，会自动扩展关联章节。",
                                "超出高等数学范围，或特别细的竞赛证明、纯理论延伸题，结果可能不稳定。"));
    }

    private List<String> findChaptersFromGraph(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> chapters = new LinkedHashSet<>();
        String cypher =
                """
                MATCH (n:知识点)
                WHERE toLower($q) CONTAINS toLower(n.name)
                   OR toLower(n.name) CONTAINS toLower($q)
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
        return new ArrayList<>(chapters);
    }

    private boolean isBroadTopic(String question) {
        String q = question == null ? "" : question.toLowerCase();
        return q.contains("微积分") || q.contains("高数") || q.contains("高等数学") || q.contains("calculus");
    }

    private long countConcepts() {
        String cypher = "MATCH (n:知识点) RETURN count(n) AS total";
        return neo4jClient
                .query(cypher)
                .fetchAs(Long.class)
                .mappedBy((t, r) -> r.get("total").asLong())
                .one()
                .orElse(0L);
    }

    private long countChapters() {
        String cypher =
                """
                MATCH (n:知识点)
                WHERE trim(coalesce(n.chapter,'')) <> ''
                RETURN count(DISTINCT n.chapter) AS total
                """;
        return neo4jClient
                .query(cypher)
                .fetchAs(Long.class)
                .mappedBy((t, r) -> r.get("total").asLong())
                .one()
                .orElse(0L);
    }
}
