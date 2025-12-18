package com.zhouxin.paperreader.config;

import com.zhouxin.paperreader.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 1. 获取请求头 Authorization
        String authHeader = request.getHeader("Authorization");

        String username = null;
        String token = null;

        // 2. 检查格式是否是 "Bearer " 开头
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                username = jwtUtils.extractClaims(token).getSubject();
            } catch (Exception e) {
                // Token 无效或过期，忽略
            }
        }

        // 3. 如果拿到了用户名，且当前环境没登录，就强制帮他登录
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 这里简单处理，默认给个 USER 权限，实际项目要去数据库查
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

            // 把用户信息存进 Security 上下文，后续 Controller 就能用
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // 【关键】把解析出来的 UserID 和 VIP状态 存入 request 属性，方便 Controller 取用
            Claims claims = jwtUtils.extractClaims(token);
            request.setAttribute("currentUserId", claims.get("userId"));
            request.setAttribute("isVip", claims.get("isVip"));
        }

        // 4. 放行
        chain.doFilter(request, response);
    }
}