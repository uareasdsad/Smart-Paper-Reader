package com.zhouxin.paperreader.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {
    // 密钥 (随便写，但在企业里要很复杂)
    private static final String SECRET_KEY = "ZhouXin_Paper_Reader_Secret_Key";
    // 过期时间 24小时
    private static final long EXPIRATION_TIME = 86400000;

    // 生成 Token
    public String generateToken(String username, Long userId, boolean isVip) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("isVip", isVip);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    // 解析 Token 获取 Claims (包含用户信息)
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractClaims(token).getSubject();
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }
}