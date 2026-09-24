package com.him.implatform.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 分布式自增计数器。
 *
 * <p>解决的问题:像"会话内消息序号""好友/群成员变更版本号"这类计数,
 * 需要 <b>Redis 自增 + 计数器丢失时从数据库回填</b>。回填和自增如果不在同一把锁内,
 * 并发下会分配出重复值 —— 而重复的版本号会让客户端的增量同步**永久丢失一次变更**,
 * 重复的消息序号则会破坏客户端排序。
 *
 * <p>这段逻辑原本在三个地方各抄了一份(私聊序号、好友版本号、群成员版本号),
 * 修 bug 得改三处,漏一处就留坑。现在收敛到这里。
 *
 * <p>用法:
 * <pre>{@code
 * long seq = counter.next(
 *         RedisKeys.privateMessageMaxSeq(convKey),
 *         RedisKeys.lockPrivateMessageMaxSeq(convKey),
 *         () -> 从数据库查当前最大值);   // 只在计数器不存在时才会被调用
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedCounter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 取下一个值。
     *
     * @param counterKey 计数器 key
     * @param lockKey    初始化锁 key。<b>务必按业务维度隔离</b>(比如按会话、按群),
     *                   否则一把全局锁会让所有会话串行
     * @param seedFromDb 计数器不存在时用来回填初值的回调(返回数据库里的当前最大值,
     *                   没有记录返回 0)。只会在锁内、且 key 缺失时调用一次
     * @return 自增后的值
     */
    public long next(String counterKey, String lockKey, Supplier<Long> seedFromDb) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            if (!redisTemplate.hasKey(counterKey)) {
                long seed = Objects.requireNonNullElse(seedFromDb.get(), 0L);
                redisTemplate.opsForValue().setIfAbsent(counterKey, seed);
                log.debug("初始化计数器,key:{},初值:{}", counterKey, seed);
            }
            Long value = redisTemplate.opsForValue().increment(counterKey);
            return Objects.requireNonNullElse(value, 0L);
        } finally {
            lock.unlock();
        }
    }
}
