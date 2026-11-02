package com.him.imserver.netty;

import com.him.imcommon.constant.IMRedisKey;
import com.him.imcommon.mq.RedisMQTemplate;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 负责在启动时分配全局唯一的 serverId，并统一启动/停止所有的网络服务器（如 WebSocketServer、TcpSocketServer）。
 *
 * 在分布式架构中，一个 IM 系统通常有多台 im-server 节点。
 * 这个类确保每台机器启动时，都能从 Redis 领到一个自己的编号，并管理本机上所有协议的启动。
 */
@Slf4j
@Component
@AllArgsConstructor
public class IMServerGroup implements CommandLineRunner {
    //implements CommandLineRunner在应用启动完成后立即执行 run 方法。

    // volatile保证多线程可见性（多个 Netty 线程会读它）
    public static volatile Long serverId = 0L;

    private final RedisMQTemplate redisMQTemplate;

    private final List<IMServer> imServers;

    /***
     * 判断服务器是否就绪
     *
     **/
    public boolean isReady() {
        for (IMServer imServer : imServers) {
            if (!imServer.isReady()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void run(String... args) {
        // 初始化SERVER_ID,每启动一台 im-server，自增一次
        String key = IMRedisKey.IM_MAX_SERVER_ID;
        serverId = redisMQTemplate.opsForValue().increment(key, 1);
        // 启动服务
        for (IMServer imServer : imServers) {
            imServer.start();
        }
    }

    @PreDestroy
    public void destroy() {
        // 停止服务
        for (IMServer imServer : imServers) {
            imServer.stop();
        }
    }
}
