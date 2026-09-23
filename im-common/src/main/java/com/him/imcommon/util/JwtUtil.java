package com.him.imcommon.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;

import java.util.Date;

public final class JwtUtil {
    /**
     * token版本号声明,用于让封禁/登出后已签发的token立即失效
     */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    private JwtUtil() {
    }

    /**
     * 生成jwt字符串
     */
    public static String sign(Long userId, String info, long expireIn, String secret) {
        return sign(userId, info, expireIn, secret, 0);
    }

    /**
     * 生成jwt字符串(带token版本号)
     */
    public static String sign(Long userId, String info, long expireIn, String secret, Integer tokenVersion) {
        try {
            Date date = new Date(System.currentTimeMillis() + expireIn * 1000);
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withAudience(userId.toString())
                    .withClaim("info", info)
                    .withClaim(TOKEN_VERSION_CLAIM, tokenVersion == null ? 0 : tokenVersion)
                    .withExpiresAt(date)
                    .sign(algorithm);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取token中的版本号,缺失或解析失败返回null
     */
    public static Integer getTokenVersion(String token) {
        try {
            Claim claim = JWT.decode(token).getClaim(TOKEN_VERSION_CLAIM);
            return claim == null ? null : claim.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据token获取userId
     */
    public static Long getUserIdByToken(String token) {
        try {
            String userId = JWT.decode(token).getAudience().get(0);
            return Long.parseLong(userId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据token获取用户数据
     */
    public static String getInfoByToken(String token) {
        try {
            return JWT.decode(token).getClaim("info").asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验token
     */
    public static Boolean checkToken(String token, String secret) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
