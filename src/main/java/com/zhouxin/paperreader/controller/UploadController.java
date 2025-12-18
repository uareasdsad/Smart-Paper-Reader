package com.zhouxin.paperreader.controller;

import com.zhouxin.paperreader.entity.SysUser;
import com.zhouxin.paperreader.mapper.SysUserMapper;
import com.zhouxin.paperreader.service.PaperAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
public class UploadController {

    @Autowired
    private PaperAnalysisService analysisService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate; // 【v4.0 新增】

    @PostMapping("/upload")
    public String uploadPaper(@RequestParam("file") MultipartFile file,
                              HttpServletRequest request) {

        // 1. 基础校验
        if (file.isEmpty()) return "上传失败：请选择文件";

        // 2. 获取用户信息
        Object userIdObj = request.getAttribute("currentUserId");
        Object isVipObj = request.getAttribute("isVip");

        if (userIdObj == null) return "认证失败：请重新登录";

        Long userId = Long.valueOf(userIdObj.toString());
        boolean isVip = (isVipObj != null) && (Boolean) isVipObj;

        // 3. 【v4.0 核心】Redis 原子扣费逻辑
        if (isVip) {
            System.out.println(">>> VIP 用户 (ID:" + userId + ") 免积分上传");
        } else {
            String pointsKey = "user:points:" + userId;

            // 安全检查：如果 Redis 里没数据（比如 Redis 重启了），先去数据库查一次填进去
            if (Boolean.FALSE.equals(redisTemplate.hasKey(pointsKey))) {
                SysUser user = userMapper.selectById(userId);
                if (user != null) {
                    redisTemplate.opsForValue().set(pointsKey, String.valueOf(user.getPoints()));
                } else {
                    return "用户异常";
                }
            }

            // 3.1 原子扣减 (decrement)：操作是瞬间完成的，不会有并发问题
            Long newBalance = redisTemplate.opsForValue().decrement(pointsKey);

            // 3.2 检查是否扣成负数了
            if (newBalance != null && newBalance < 0) {
                // 扣错了（原本是0，扣完变-1），赶紧加回去（回滚）
                redisTemplate.opsForValue().increment(pointsKey);
                return "😭 余额不足！请充值。";
            }

            System.out.println(">>> Redis 扣费成功，剩余积分: " + newBalance);

            // 注意：为了极致性能，这里暂时不写回 MySQL。
            // 真实场景下，会有一个定时任务每隔几分钟把 Redis 里的积分同步回 MySQL。
        }

        // 4. 调用 Service (Service 里负责传 MinIO 和 发 RabbitMQ)
        return analysisService.processPaper(file, userId);
    }
}