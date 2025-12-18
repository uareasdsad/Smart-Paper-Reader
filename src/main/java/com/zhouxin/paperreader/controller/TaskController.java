package com.zhouxin.paperreader.controller;

import com.zhouxin.paperreader.entity.PaperTask;
import com.zhouxin.paperreader.mapper.PaperTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private PaperTaskMapper taskMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 查询任务结果 (v4.0 高性能版)
     * 策略：先查 Redis -> 没有再查 MySQL -> 查到后回填 Redis
     */
    @GetMapping("/{taskId}")
    public String getTaskResult(@PathVariable Long taskId) {
        String cacheKey = "task:report:" + taskId;

        // 1. 先查 Redis (内存，极快)
        String cachedReport = redisTemplate.opsForValue().get(cacheKey);
        if (cachedReport != null) {
            System.out.println(">>> 命中 Redis 缓存，直接返回！");
            return cachedReport;
        }

        // 2. Redis 没有，去 MySQL 查 (磁盘，较慢)
        System.out.println(">>> 缓存未命中，查询 MySQL...");
        PaperTask task = taskMapper.selectById(taskId);

        if (task == null) return "任务不存在";
        if (task.getStatus() != 1) return "任务处理中或失败，状态码: " + task.getStatus();

        // 3. 查到了，补回 Redis (防止下次再穿透)
        redisTemplate.opsForValue().set(cacheKey, task.getReportContent());

        return task.getReportContent();
    }
}