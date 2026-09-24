package com.him.imcommon.mq;


import org.apache.logging.log4j.util.Strings;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Properties;

public class RedisMQTemplate extends RedisTemplate<String, Object> {

    private String version = Strings.EMPTY;//开始变量值为空。懒加载：只有第一次调用时才去查 Redis 版本

    //获取Redis版本  6.2以上支持批量拉取
    public String getVersion() {
        if (version.isEmpty()) {
            RedisConnection connection = RedisConnectionUtils.getConnection(getConnectionFactory());
            Properties properties = connection.info();
            for (String key : properties.stringPropertyNames()) {
                if (key.contains("redis_version")) {
                    version = properties.getProperty(key);
                    break;
                }
            }
            RedisConnectionUtils.releaseConnection(connection,getConnectionFactory());
        }
        return version;
    }

    /**
     * 是否支持批量拉取，redis版本大于6.2支持批量拉取  LPOP queue 100 # 一次弹出 100 条
     * @return
     */
    Boolean isSupportBatchPull() {
        String version = getVersion();
        //split 的参数是正则表达式，. 在正则里是"任意字符"，需要用 \\. 表示字面量的 .  。比如 "7.0.5" → ["7", "0", "5"]
        String[] arr = version.split("\\.");
        //分割后至少要有 2 段（主版本 + 次版本），否则版本格式不对，保守返回 false
        if (arr.length < 2) {
            return false;
        }
        Integer firVersion = Integer.valueOf(arr[0]);//主版本号
        Integer secVersion = Integer.valueOf(arr[1]);//次版本号
        return firVersion > 6 || (firVersion == 6 && secVersion >= 2);
    }

}
