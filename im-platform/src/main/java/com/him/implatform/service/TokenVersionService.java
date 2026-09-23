package com.him.implatform.service;

import com.him.implatform.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户token版本号管理。
 *
 * <p>JWT本身是无状态的,签发后在过期前一直有效,因此"封禁用户""退出登录"这类操作
 * 无法立刻生效。这里为每个用户维护一个版本号:签发token时把当前版本号写进token,
 * 请求校验时与Redis中的版本号比对,不一致即判定token已失效。
 * 只需自增版本号就能让该用户已签发的所有token立即全部失效。
 */
@Service
@RequiredArgsConstructor
public class TokenVersionService {

    /**
     * 从未失效过的用户版本号为0
     */
    private static final int DEFAULT_VERSION = 0;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 读取用户当前token版本号,不存在视为0
     */
    public int getVersion(Long userId) {
        Object value = redisTemplate.opsForValue().get(buildKey(userId));
        if (value == null) {
            return DEFAULT_VERSION;
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 使该用户已签发的所有token立即失效(退出登录、封禁时调用)
     *
     * @return 自增后的版本号
     */
    public int invalidate(Long userId) {
        Long version = redisTemplate.opsForValue().increment(buildKey(userId));
        return version == null ? 0 : version.intValue();
    }

    private String buildKey(Long userId) {
        return RedisKey.IM_USER_TOKEN_VERSION + ":" + userId;
    }
}
