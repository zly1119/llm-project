package com.edu.llm.guide.controller;

import com.edu.llm.guide.service.GuideGenerationWorker;
import com.edu.llm.guide.service.GuideTaskStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class GuideController {

    private final GuideTaskStore taskStore;
    private final GuideGenerationWorker worker;

    public GuideController(GuideTaskStore taskStore, GuideGenerationWorker worker) {
        this.taskStore = taskStore;
        this.worker = worker;
    }

    @PostMapping(value = "/guide", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> guidePost(
            @RequestParam(value = "q", required = false) String question,
            @RequestParam(value = "goal", required = false) String goal,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "days", required = false) String days) {

        boolean needGoal = goal == null || goal.isBlank();
        boolean needLevel = level == null || level.isBlank();
        boolean needDays = days == null || days.isBlank();
        if (needGoal || needLevel || needDays) {
            return Map.of(
                    "needInfo", true,
                    "message", "为了帮你制定最合适的学习攻略，请告诉我你的学习目标和当前基础：",
                    "options",
                            Map.of(
                                    "goal", List.of("初学", "应对考试", "考研/深度学习"),
                                    "level", List.of("基础扎实", "基础一般", "几乎零基础")));
        }
        if (question == null || question.isBlank()) {
            return Map.of("error", "问题描述不能为空");
        }

        String taskId = taskStore.createTask();
        worker.run(taskId, question.trim(), goal.trim(), level.trim(), days.trim());
        return Map.of("taskId", taskId);
    }

    @GetMapping("/guideResult")
    public Map<String, Object> guideResult(@RequestParam(value = "taskId", required = false) String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Map.of("error", "缺少taskId");
        }
        Map<Object, Object> map = taskStore.inspect(taskId);
        if (map == null) {
            return Map.of("status", "pending");
        }
        String status = String.valueOf(map.getOrDefault("status", ""));
        if ("PROCESSING".equals(status)) {
            return Map.of("status", "pending");
        }
        if ("FAILED".equals(status)) {
            String err = map.get("error") != null ? map.get("error").toString() : "生成失败";
            taskStore.delete(taskId);
            // 与单体 Main 一致：异常时也走 status=done，正文在 guide 中展示
            return Map.of("status", "done", "guide", err);
        }
        if ("DONE".equals(status)) {
            Object guide = map.get("guide");
            String text = guide != null ? guide.toString() : "";
            taskStore.delete(taskId);
            return Map.of("status", "done", "guide", text);
        }
        return Map.of("status", "pending");
    }
}
