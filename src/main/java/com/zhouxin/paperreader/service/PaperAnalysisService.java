package com.zhouxin.paperreader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouxin.paperreader.config.RabbitMQConfig;
import com.zhouxin.paperreader.entity.PaperTask;
import com.zhouxin.paperreader.mapper.PaperTaskMapper;
import com.zhouxin.paperreader.utils.MinioUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class PaperAnalysisService {

    private final PaperTaskMapper paperTaskMapper;
    private final MinioUtils minioUtils;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api.key}")
    private String apiKey;
    @Value("${deepseek.api.url}")
    private String apiUrl;

    // 修复了原来代码中杂乱的字符串拼接语法
    private static final String SYSTEM_PROMPT = "你是一名 OMT 光学重建领域的专家，专注于 FMT、BLT、XLCT 等医学成像模型。\n" +
            "你知道重建的核心在于：通过光学模态构建系统矩阵 A 和测量向量 b，然后用数学方法解决线性方程组 Ax=b 的反问题。\n" +
            "由于 A 通常是病态的（ill-posed），导致存在无穷多解，因此必须引入正则化或其他数学约束来求解 x（光源分布或吸收系数）。\n" +
            "请阅读用户提供的论文，以专家的视角，用中文输出一份 Markdown 格式的深度阅读报告。\n\n" +
            "报告结构必须严格遵守以下格式：\n" +
            "# 论文标题\n" +
            "## 1. 核心贡献 (TL;DR - 一句话总结)\n" +
            "## 2. 解决的问题与背景\n" +
            "   (简述物理背景，说明它针对的是哪种成像模态？解决了 Ax=b 求解中的什么具体困难？例如：计算速度慢、L1/L2正则化精度低、非凸优化问题等)\n" +
            "## 3. 核心方法与架构 (重点)\n" +
            "   (必须提取核心数学公式，使用 LaTeX 格式包裹，例如 $Ax=b$ 或 $\\min ||Ax-b||_2^2 + \\lambda ||x||_1$。)\n" +
            "   (请详细解释公式中每个符号的物理意义，特别是正则化项的设计思路。)\n" +
            "## 4. 实验结果与对比 (Evaluation)\n" +
            "   (列出它对比了哪些传统算法？例如 CG, FISTA, ADMM 等？重建质量提升了多少？)\n" +
            "## 5. 这篇文章的创新点\n" +
            "   (总结它在数学求解策略或物理建模上的独特之处。)";

    public PaperAnalysisService(PaperTaskMapper paperTaskMapper, MinioUtils minioUtils) {
        this.paperTaskMapper = paperTaskMapper;
        this.minioUtils = minioUtils;
    }

    /**
     * 处理上传请求 (v2.0 完整版)
     * 包含了 MinIO上传、数据库记录、PDF解析、DeepSeek调用、数据库回填
     */
    @Autowired
    private RabbitTemplate rabbitTemplate; // 注入 RabbitMQ 操作类

    /**
     * v3.0 异步处理版
     * 只做三件事：上传MinIO、插数据库(status=0)、发消息给MQ
     */
    public String processPaper(MultipartFile file, Long userId) {
        // 1. 上传 MinIO
        String minioUrl = minioUtils.upload(file);
        if (minioUrl == null) return "文件上传失败";

        // 2. 插入数据库 (状态: 0 - 排队中)
        PaperTask task = new PaperTask();
        task.setFileName(file.getOriginalFilename());
        task.setMinioUrl(minioUrl);
        task.setStatus(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUserId(userId);

        paperTaskMapper.insert(task);

        // 3. 【v3.0 核心】发送消息到队列
        // 我们只需要把 "任务ID" 发过去，消费者那边拿着ID去数据库查文件
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, task.getId().toString());

        // 4. 立刻返回！告诉前端：我们收到了，你不用等了
        return "任务已提交后台处理！任务ID: " + task.getId() + " (请稍后刷新查看结果)";
    }

    /**
     * 提取 PDF 文本
     */
    private String extractText(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 调用 AI 模型
     */
    private String callDeepSeek(String content) {
        try {
            // 截断文本防止超长
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

            // 解析 JSON
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("DeepSeek API 调用失败: " + e.getMessage());
        }
    }
}