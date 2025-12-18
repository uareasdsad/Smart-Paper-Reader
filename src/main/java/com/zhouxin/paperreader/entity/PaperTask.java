package com.zhouxin.paperreader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("paper_task")
public class PaperTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String minioUrl;
    private String reportContent;
    private Integer status; // 0-处理中 1-完成
    private LocalDateTime createTime;
    // 【v2.0 新增】
    private Long userId;
}