package com.him.imclient.sender;

import com.him.imcommon.constant.IMRedisKey;
import com.him.imcommon.enums.IMTerminalType;
import com.him.imcommon.model.IMForceLogoutInfo;
import com.him.imcommon.mq.RedisMQTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor//为类中所有 final 字段和标注了 @NonNull 的字段，自动生成一个构造方法
public class IMSender {

    @Autowired
    private RedisMQTemplate redisMQTemplate;

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     */
    public Boolean isOnline(Long userId) {
        //存该用户所有终端的 Redis key
        List<String> keys = new ArrayList<>(IMTerminalType.codes().size());
        for (Integer terminal : IMTerminalType.codes()) {
            //为当前终端生成 Redis key，加入 keys 列表
            keys.add(IMRedisKey.userServerIdKey(userId, terminal));
        }
        //一次 Redis 调用，统计 keys 中有多少个 key 真实存在
        Long count = redisMQTemplate.countExistingKeys(keys);
        //判断是否有设备在线
        return count != null && count > 0;
    }

    /**
     * 多用户 → 哪些用户在线（返回用户 id 列表）
     *
     * @param userIds 用户 id 列表
     * @return 在线的用户列表
     */
    public List<Long> getOnlineUser(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 一次性收集所有用户所有终端的 key，并用平行列表 keyOwnerIds 记录每个 key 属于哪个用户
        List<String> keys = new ArrayList<>(userIds.size() * IMTerminalType.codes().size());
        List<Long> keyOwnerIds = new ArrayList<>(userIds.size() * IMTerminalType.codes().size());
        for (Long userId : userIds) {
            for (Integer terminal : IMTerminalType.codes()) {
                keys.add(IMRedisKey.userServerIdKey(userId, terminal));
                keyOwnerIds.add(userId);
            }
        }
        // 一次 Redis MGET 批量拉取：key 存在返回其值，不存在返回 null，与 keys 一一对应
        List<Object> values = redisMQTemplate.opsForValue().multiGet(keys);
        // 值非 null 的 key 对应用户在线；用 Set 去重（一个用户可能有多个终端同时在线）
        Set<Long> onlineUserIds = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                onlineUserIds.add(keyOwnerIds.get(i));
            }
        }
        return new ArrayList<>(onlineUserIds);
    }

    public void forceLogout(Long userId, Integer type, String reason) {
        // 遍历终端
        for (Integer terminal : IMTerminalType.codes()) {
            String key = IMRedisKey.userServerIdKey(userId, terminal);
            Object serverId = redisMQTemplate.opsForValue().get(key);  // 查该终端连在哪个 server
            if (Objects.isNull(serverId)) {
                continue;   // 不在线，跳过
            }
            // 构造下线命令
            IMForceLogoutInfo logoutInfo = new IMForceLogoutInfo();
            logoutInfo.setUserId(userId);
            logoutInfo.setTerminal(terminal);
            logoutInfo.setType(type);
            logoutInfo.setReason(reason);
            // 投递到该 server 的队列
            String queueKey = String.join(":", IMRedisKey.IM_USER_FORCE_LOGOUT_QUEUE, serverId.toString());
            redisMQTemplate.opsForList().rightPush(queueKey, logoutInfo);
        }
    }
}
