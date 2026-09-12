package com.him.implatform.util;

import com.him.imcommon.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * 纯逻辑测试，不需要启动 Spring，速度极快
 */
@DisplayName("JwtUtil 工具类测试")
class JwtUtilTest {

    private static final String SECRET = "testSecretKey123";
    private static final Long USER_ID = 1001L;
    private static final String INFO = "{\"userId\":1001,\"username\":\"testuser\"}";

    @Test
    @DisplayName("sign - 正常生成token")
    void sign_shouldReturnNonNullToken() {
        String token = JwtUtil.sign(USER_ID, INFO, 1800, SECRET);
        assertNotNull(token, "生成的token不应为null");
        assertEquals(3, token.split("\\.").length, "JWT应由3部分组成");
    }

    @Test
    @DisplayName("getUserIdByToken - 正常解析userId")
    void getUserIdByToken_shouldReturnCorrectUserId() {
        String token = JwtUtil.sign(USER_ID, INFO, 1800, SECRET);
        Long result = JwtUtil.getUserIdByToken(token);
        assertEquals(USER_ID, result);
    }

    @Test
    @DisplayName("getInfoByToken - 正常解析info")
    void getInfoByToken_shouldReturnCorrectInfo() {
        String token = JwtUtil.sign(USER_ID, INFO, 1800, SECRET);
        String result = JwtUtil.getInfoByToken(token);
        assertEquals(INFO, result);
    }

    @Test
    @DisplayName("checkToken - 有效token返回true")
    void checkToken_withValidToken_shouldReturnTrue() {
        String token = JwtUtil.sign(USER_ID, INFO, 1800, SECRET);
        Boolean result = JwtUtil.checkToken(token, SECRET);
        assertTrue(result);
    }

    @Test
    @DisplayName("checkToken - 错误密钥返回false")
    void checkToken_withWrongSecret_shouldReturnFalse() {
        String token = JwtUtil.sign(USER_ID, INFO, 1800, SECRET);
        Boolean result = JwtUtil.checkToken(token, "wrongSecretKey");
        assertFalse(result);
    }

    @Test
    @DisplayName("checkToken - 过期token返回false")
    void checkToken_withExpiredToken_shouldReturnFalse() {
        // expireIn = -1 表示已过期
        String token = JwtUtil.sign(USER_ID, INFO, -1, SECRET);
        Boolean result = JwtUtil.checkToken(token, SECRET);
        assertFalse(result);
    }

    @Test
    @DisplayName("getUserIdByToken - 无效token返回null")
    void getUserIdByToken_withInvalidToken_shouldReturnNull() {
        Long result = JwtUtil.getUserIdByToken("invalid.token.here");
        assertNull(result);
    }

    @Test
    @DisplayName("getInfoByToken - 无效token返回null")
    void getInfoByToken_withInvalidToken_shouldReturnNull() {
        String result = JwtUtil.getInfoByToken("invalid.token.here");
        assertNull(result);
    }

    @Test
    @DisplayName("sign - 传入null参数返回null")
    void sign_withNullParams_shouldReturnNull() {
        // secret为null时，Algorithm.HMAC256会抛异常，sign应返回null
        String result = JwtUtil.sign(USER_ID, INFO, 1800, null);
        assertNull(result);
    }
}
