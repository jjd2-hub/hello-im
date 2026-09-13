package com.him.implatform.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 连接测试
 * 测试 RedisTemplate 各种数据结构的操作是否正常
 */
@SpringBootTest
@DisplayName("Redis 连接测试")
class SpringDataRedisTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 需要清理的 key 收集
    private final List<String> keysToClean = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (!keysToClean.isEmpty()) {
            redisTemplate.delete(keysToClean);
            keysToClean.clear();
        }
    }

    private void trackKey(String key) {
        keysToClean.add(key);
    }

    // ==================== String 操作 ====================

    @Test
    @DisplayName("String - 基本存取")
    void value_setAndGet() {
        trackKey("test:hello");

        redisTemplate.opsForValue().set("test:hello", "world");
        Object result = redisTemplate.opsForValue().get("test:hello");

        assertEquals("world", result);
    }

    @Test
    @DisplayName("String - 设置过期时间")
    void value_setWithExpire() throws InterruptedException {
        trackKey("test:code");

        redisTemplate.opsForValue().set("test:code", "1234", 2, TimeUnit.SECONDS);
        Object before = redisTemplate.opsForValue().get("test:code");
        assertEquals("1234", before);

        Thread.sleep(2500);
        Object after = redisTemplate.opsForValue().get("test:code");
        assertNull(after);
    }

    @Test
    @DisplayName("String - setIfAbsent（分布式锁原理）")
    void value_setIfAbsent() {
        trackKey("test:lock");

        Boolean first = redisTemplate.opsForValue().setIfAbsent("test:lock", "1");
        Boolean second = redisTemplate.opsForValue().setIfAbsent("test:lock", "2");

        assertTrue(first);
        assertFalse(second);
        assertEquals("1", redisTemplate.opsForValue().get("test:lock"));
    }

    // ==================== Hash 操作 ====================

    @Test
    @DisplayName("Hash - 基本存取")
    void hash_putAndGet() {
        trackKey("test:user:1");

        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
        ops.put("test:user:1", "name", "jake");
        ops.put("test:user:1", "age", 18);

        assertEquals("jake", ops.get("test:user:1", "name"));
        assertEquals(18, ops.get("test:user:1", "age"));
    }

    @Test
    @DisplayName("Hash - 获取所有 key 和 value")
    void hash_keysAndValues() {
        trackKey("test:user:2");

        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
        ops.put("test:user:2", "name", "lisi");
        ops.put("test:user:2", "age", 25);

        Set<Object> keys = ops.keys("test:user:2");
        Collection<Object> values = ops.values("test:user:2");

        assertEquals(2, keys.size());
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("age"));
        assertEquals(2, values.size());
    }

    // ==================== List 操作 ====================

    @Test
    @DisplayName("List - 左进右出（队列）")
    void list_queue() {
        trackKey("test:queue");

        redisTemplate.opsForList().leftPush("test:queue", "a");
        redisTemplate.opsForList().leftPush("test:queue", "b");
        redisTemplate.opsForList().leftPush("test:queue", "c");

        Long size = redisTemplate.opsForList().size("test:queue");
        assertEquals(3L, size);

        Object first = redisTemplate.opsForList().rightPop("test:queue");
        assertEquals("a", first);

        Object second = redisTemplate.opsForList().rightPop("test:queue");
        assertEquals("b", second);
    }

    @Test
    @DisplayName("List - 获取范围")
    void list_range() {
        trackKey("test:list");

        redisTemplate.opsForList().rightPush("test:list", "x");
        redisTemplate.opsForList().rightPush("test:list", "y");
        redisTemplate.opsForList().rightPush("test:list", "z");

        List<Object> range = redisTemplate.opsForList().range("test:list", 0, -1);
        assertEquals(3, range.size());
        assertEquals("x", range.get(0));
        assertEquals("y", range.get(1));
        assertEquals("z", range.get(2));
    }

    // ==================== Set 操作 ====================

    @Test
    @DisplayName("Set - 添加和去重")
    void set_addAndDedup() {
        trackKey("test:set");

        redisTemplate.opsForSet().add("test:set", "a", "b", "c");
        redisTemplate.opsForSet().add("test:set", "a"); // 重复添加

        Long size = redisTemplate.opsForSet().size("test:set");
        assertEquals(3L, size);
    }

    @Test
    @DisplayName("Set - 判断是否包含")
    void set_isMember() {
        trackKey("test:set2");

        redisTemplate.opsForSet().add("test:set2", "online_user_1", "online_user_2");

        assertTrue(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("test:set2", "online_user_1")));
        assertFalse(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("test:set2", "offline_user")));
    }

    // ==================== ZSet 操作 ====================

    @Test
    @DisplayName("ZSet - 添加和按分数排序")
    void zset_addAndSort() {
        trackKey("test:zset");

        redisTemplate.opsForZSet().add("test:zset", "user_a", 80);
        redisTemplate.opsForZSet().add("test:zset", "user_b", 95);
        redisTemplate.opsForZSet().add("test:zset", "user_c", 70);

        Set<Object> range = redisTemplate.opsForZSet().range("test:zset", 0, -1);
        List<Object> list = new ArrayList<>(range);

        assertEquals("user_c", list.get(0)); // 70 分最低
        assertEquals("user_a", list.get(1)); // 80 分
        assertEquals("user_b", list.get(2)); // 95 分最高
    }

    @Test
    @DisplayName("ZSet - 获取分数")
    void zset_score() {
        trackKey("test:zset2");

        redisTemplate.opsForZSet().add("test:zset2", "player1", 100);
        redisTemplate.opsForZSet().add("test:zset2", "player2", 200);

        Double score1 = redisTemplate.opsForZSet().score("test:zset2", "player1");
        Double score2 = redisTemplate.opsForZSet().score("test:zset2", "player2");

        assertEquals(100.0, score1);
        assertEquals(200.0, score2);
    }

    // ==================== 通用操作 ====================

    @Test
    @DisplayName("通用 - 删除 key")
    void delete_key() {
        redisTemplate.opsForValue().set("test:to_delete", "value");
        assertEquals("value", redisTemplate.opsForValue().get("test:to_delete"));

        Boolean deleted = redisTemplate.delete("test:to_delete");
        assertTrue(deleted);
        assertNull(redisTemplate.opsForValue().get("test:to_delete"));
    }

    @Test
    @DisplayName("通用 - 判断 key 是否存在")
    void hasKey() {
        trackKey("test:exists");

        redisTemplate.opsForValue().set("test:exists", "yes");

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey("test:exists")));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey("test:not_exists")));
    }
}
