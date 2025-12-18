package com.zhouxin.paperreader;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 主启动类
 * 这是整个程序的入口。
 */
// 1. 标识这是一个 Spring Boot 应用
@SpringBootApplication
// 2. 【重要】告诉 MyBatis Plus 去哪里扫描 Mapper 接口文件
// 这里的包名必须和你实际存放 PaperTaskMapper.java 的包名一致
@MapperScan("com.zhouxin.paperreader.mapper")
public class PaperReaderApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot
        SpringApplication.run(PaperReaderApplication.class, args);
        System.out.println("\n(♥◠‿◠)ﾉﾞ  论文分析平台(v1.0 Web版) 启动成功   ლ(´ڡ`ლ)ﾞ \n" +
                "核心服务已就绪：\n" +
                "1. Web服务运行在: http://localhost:8080\n" +
                "2. MySQL连接正常\n" +
                "3. MinIO连接正常\n" +
                "---------------------------------------------------------");
    }
}