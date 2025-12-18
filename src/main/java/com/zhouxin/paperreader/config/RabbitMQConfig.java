package com.zhouxin.paperreader.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 定义队列名称
    public static final String QUEUE_NAME = "paper_analysis_queue";

    @Bean
    public Queue paperQueue() {
        // true 表示持久化，重启 RabbitMQ 队列还在
        return new Queue(QUEUE_NAME, true);
    }
}