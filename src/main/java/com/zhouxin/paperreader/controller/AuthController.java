package com.zhouxin.paperreader.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhouxin.paperreader.entity.SysUser;
import com.zhouxin.paperreader.mapper.SysUserMapper;
import com.zhouxin.paperreader.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate; // 【v4.0 新增】注入 Redis

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        Map<String, Object> result = new HashMap<>();

        // 1. 判空
        if (username == null || password == null) {
            result.put("code", 400);
            result.put("msg", "账号或密码不能为空");
            return result;
        }

        // 2. 查数据库
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        SysUser user = userMapper.selectOne(queryWrapper);

        // 3. 校验用户和密码
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            result.put("code", 401);
            result.put("msg", "账号或密码错误");
            return result;
        }

        // 4. 生成 Token
        boolean isVip = (user.getIsVip() != null && user.getIsVip() == 1);
        String token = jwtUtils.generateToken(user.getUsername(), user.getId(), isVip);

        // 5. 【v4.0 核心】登录成功后，将用户积分写入 Redis 缓存
        // Key 格式: "user:points:用户ID"
        // 这样后续扣费时，就不用查数据库了
        String pointsKey = "user:points:" + user.getId();
        redisTemplate.opsForValue().set(pointsKey, String.valueOf(user.getPoints()));

        result.put("code", 200);
        result.put("msg", "登录成功");
        result.put("token", token);
        result.put("username", user.getUsername());
        result.put("points", user.getPoints());

        return result;
    }

    // 注册接口保持不变，为了节省篇幅略去，你保留原有的即可
}