package com.zhouxin.paperreader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password; // 存的是加密后的密文

    private Integer isVip;   // 0-普通, 1-VIP

    private Integer points;  // 积分

    private LocalDateTime createTime;
}