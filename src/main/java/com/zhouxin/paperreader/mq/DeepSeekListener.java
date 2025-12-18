package com.zhouxin.paperreader.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouxin.paperreader.config.RabbitMQConfig;
import com.zhouxin.paperreader.entity.PaperTask;
import com.zhouxin.paperreader.mapper.PaperTaskMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekListener {

    @Autowired
    private PaperTaskMapper paperTaskMapper;

    @Autowired
    private StringRedisTemplate redisTemplate; // 【v4.0 新增】

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api.key}")
    private String apiKey;
    @Value("${deepseek.api.url}")
    private String apiUrl;

    private static final String SYSTEM_PROMPT = "你是一名 OMT 光学重建领域的专家，专注于 FMT、BLT、XLCT 等医学成像模型。\n" +
            "请阅读用户提供的论文，以专家的视角，用中文输出一份 Markdown 格式的深度阅读报告。\n" +
            "报告结构必须严格遵守以下格式：\n" +
            "# 论文标题\n" +
            "## 1. 核心贡献 (TL;DR)\n" +
            "## 2. 解决的问题与背景\n" +
            "## 3. 核心方法与架构 (重点，提取公式)\n" +
            "## 4. 实验结果与对比\n" +
            "## 5. 创新点总结";

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleAnalysisTask(String taskIdStr) {
        Long taskId = Long.valueOf(taskIdStr);
        System.out.println(">>> [MQ消费者] 收到任务，开始处理 TaskID: " + taskId);

        PaperTask task = paperTaskMapper.selectById(taskId);
        if (task == null) return;

        try {
            // 1. 下载并解析 PDF (使用修复后的方法，支持中文)
            String pdfContent = downloadAndParsePdf(task.getMinioUrl());
            if (pdfContent == null || pdfContent.isEmpty()) {
                throw new RuntimeException("PDF 解析结果为空");
            }
            System.out.println(">>> PDF 解析成功，准备调用 AI...");

            // 2. 调用 DeepSeek
            String report = callDeepSeek(pdfContent);

            // 3. 更新 MySQL 数据库 (持久化存储)
            task.setReportContent(report);
            task.setStatus(1);
            paperTaskMapper.updateById(task);

            // 4. 【v4.0 核心】写入 Redis 缓存
            // 这样用户在前端查询结果时，直接读 Redis，不用查数据库
            // 设置过期时间 1 小时 (1, TimeUnit.HOURS)
            String cacheKey = "task:report:" + taskId;
            redisTemplate.opsForValue().set(cacheKey, report, 1, TimeUnit.HOURS);

            System.out.println(">>> [MQ消费者] 任务完成！结果已存入 DB 和 Redis，Key: " + cacheKey);

        } catch (Exception e) {
            e.printStackTrace();
            task.setStatus(2);
            task.setReportContent("处理失败: " + e.getMessage());
            paperTaskMapper.updateById(task);
            System.err.println(">>> [MQ消费者] 任务失败: " + e.getMessage());
        }
    }

    /**
     * 辅助方法：修复 URL 中文问题并下载解析
     */
    private String downloadAndParsePdf(String fileUrl) throws Exception {
        URL originalUrl = new URL(fileUrl);
        // 使用 URI 自动编码中文和空格
        URI uri = new URI(
                originalUrl.getProtocol(),
                originalUrl.getUserInfo(),
                originalUrl.getHost(),
                originalUrl.getPort(),
                originalUrl.getPath(),
                originalUrl.getQuery(),
                originalUrl.getRef()
        );
        URL encodedUrl = uri.toURL();

        try (InputStream inputStream = encodedUrl.openStream();
             PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String callDeepSeek(String content) {
        try {
            if (content.length() > 40000) content = content.substring(0, 40000);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", "deepseek-chat");
            root.put("stream", false);
            ArrayNode messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            String response = restTemplate.postForObject(apiUrl, new HttpEntity<>(root.toString(), headers), String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("AI调用失败: " + e.getMessage());
        }
    }
}