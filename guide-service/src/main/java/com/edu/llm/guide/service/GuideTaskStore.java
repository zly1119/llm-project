package com.edu.llm.guide.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class GuideTaskStore {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public GuideTaskStore(StringRedisTemplate redis, @Value("${app.guide.task-ttl-hours:2}") int hours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(hours);
    }

    public String createTask() {
        String taskId = UUID.randomUUID().toString();
        String key = key(taskId);
        redis.opsForHash().put(key, "status", "PROCESSING");
        redis.expire(key, ttl);
        return taskId;
    }

    public void markDone(String taskId, String markdown) {
        String key = key(taskId);
        redis.opsForHash().put(key, "status", "DONE");
        redis.opsForHash().put(key, "guide", markdown);
        redis.expire(key, ttl);
    }

    public void markFailed(String taskId, String message) {
        String key = key(taskId);
        redis.opsForHash().put(key, "status", "FAILED");
        redis.opsForHash().put(key, "error", message);
        redis.expire(key, ttl);
    }

    /** @return null 表示任务不存在 */
    public Map<Object, Object> inspect(String taskId) {
        String key = key(taskId);
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        return entries.isEmpty() ? null : entries;
    }

    public void delete(String taskId) {
        redis.delete(key(taskId));
    }

    private static String key(String taskId) {
        return "edu:guide:task:" + taskId;
    }
}
