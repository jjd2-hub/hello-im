package com.him.imclient.sender;

import cn.hutool.core.collection.CollUtil;
import com.him.imclient.listener.MessageListenerMulticaster;
import com.him.imcommon.constant.IMRedisKey;
import com.him.imcommon.enums.IMCmdType;
import com.him.imcommon.enums.IMListenerType;
import com.him.imcommon.enums.IMSendCode;
import com.him.imcommon.enums.IMTerminalType;
import com.him.imcommon.model.*;
import com.him.imcommon.model.IMSystemMessage;
import com.him.imcommon.mq.RedisMQTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor//为类中所有 final 字段和标注了 @NonNull 的字段，自动生成一个构造方法
public class IMSender {

    @Value("${spring.application.name}")
    private String appName;
    @Autowired
    private RedisMQTemplate redisMQTemplate;

    private final MessageListenerMulticaster listenerMulticaster;
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

    public <T> void sendSystemMessage(IMSystemMessage<T> message) {
        // 根据群聊每个成员所连的IM-server，进行分组
        Map<String, IMUserInfo> sendMap = new LinkedHashMap<>();
        //遍历 recvTerminals × recvIds 的笛卡尔积，为每个组合生成：Redis key
        for (Integer terminal : message.getRecvTerminals()) {
            message.getRecvIds().forEach(id -> {
                String key = IMRedisKey.userServerIdKey(id, terminal);
                sendMap.put(key, new IMUserInfo(id, terminal));
            });
        }
        // sendMap.keySet()：拿到所有路由 key。
        //multiGet(...)：一次 Redis 调用，查所有 key 的 value。
        //serverIds：返回的 value 列表。
        List<Object> serverIds = redisMQTemplate.opsForValue().multiGet(sendMap.keySet());
        // 格式:map<服务器id,list<接收方>>
        Map<Integer, List<IMUserInfo>> serverMap = new HashMap<>();
        List<IMUserInfo> offLineUsers = new LinkedList<>();
        // 遍历 sendMap，用 idx++ 对应 serverIds 的下标。
        // serverId 非空 → 加入 serverMap（按 serverId 分组）。
        // serverId 为空 → 加入 offLineUsers（离线列表）。
        int idx = 0;
        for (Map.Entry<String, IMUserInfo> entry : sendMap.entrySet()) {
            Integer serverId = (Integer)serverIds.get(idx++);
            if (!Objects.isNull(serverId)) {
                List<IMUserInfo> list = serverMap.computeIfAbsent(serverId, o -> new LinkedList<>());
                list.add(entry.getValue());
            } else {
                // 加入离线列表
                offLineUsers.add(entry.getValue());
            }
        }
        // 逐个server发送。对每个 serverId，构造 IMRecvInfo，投到对应队列
        for (Map.Entry<Integer, List<IMUserInfo>> entry : serverMap.entrySet()) {
            IMRecvInfo recvInfo = new IMRecvInfo();
            recvInfo.setCmd(IMCmdType.SYSTEM_MESSAGE.code());
            recvInfo.setReceivers(new LinkedList<>(entry.getValue()));
            recvInfo.setServiceName(appName);
            recvInfo.setSendResult(message.getSendResult());
            recvInfo.setData(message.getData());
            // 推送至队列
            String key = String.join(":", IMRedisKey.IM_MESSAGE_SYSTEM_QUEUE, entry.getKey().toString());
            redisMQTemplate.opsForList().rightPush(key, recvInfo);
        }
        // 对离线用户回复消息状态
        // 离线用户不需要 server 处理（他们根本没连）。SDK 查 Redis 就知道离线，直接生成回执。省一次 Redis 往返。
        if (message.getSendResult() && !offLineUsers.isEmpty()) {
            IMBatchSendResult<T> result = new IMBatchSendResult<>();
            result.setReceivers(offLineUsers);
            result.setCode(IMSendCode.NOT_ONLINE.code());
            result.setData(message.getData());
            listenerMulticaster.multicast(IMListenerType.SYSTEM_MESSAGE, List.of(result));
        }
    }

    public <T> void sendPrivateMessage(IMPrivateMessage<T> message) {
        IMBatchPrivateMessage<T> batch = new IMBatchPrivateMessage<>();
        batch.setSender(message.getSender());
        if (!Objects.isNull(message.getRecvId())) {
            batch.setRecvIds(List.of(message.getRecvId()));
        }
        batch.setRecvTerminals(message.getRecvTerminals());
        batch.setSendToSelf(message.getSendToSelf());
        batch.setSendResult(message.getSendResult());
        batch.setData(message.getData());
        // 单条消息是批量消息的特例，复用批量逻辑。
        sendBatchPrivateMessage(batch);
    }

    public <T> void sendBatchPrivateMessage(IMBatchPrivateMessage<T> message) {
        List<Long> recvIds = message.getRecvIds();  //接收者 id 列表
        List<Integer> recvTerminals = message.getRecvTerminals();  //接收者终端列表
        List<IMUserInfo> offlineUsers = new ArrayList<>();  //离线接收者列表（后面填充
        IMUserInfo sender = message.getSender();  //发送者（用于多端同步）
        // 接收方：redisKey → 接收用户信息（LinkedHashMap 保证遍历顺序与下方 multiGet 结果下标一致）
        Map<String, IMUserInfo> recvKeyToUser = new LinkedHashMap<>();
        if (!CollUtil.isEmpty(recvIds) && !CollUtil.isEmpty(recvTerminals)) {
            for (Integer terminal : recvTerminals) {
                for (Long id : recvIds) {
                    //遍历 recvTerminals × recvIds 的笛卡尔积，为每个组合生成 Redis key + IMUserInfo
                    String key = IMRedisKey.userServerIdKey(id, terminal);
                    recvKeyToUser.put(key, new IMUserInfo(id, terminal));
                }
            }
        }
        //构建"发送者自己的其他终端"的 key 列表（多端同步）
        // 发送方其它终端：与 selfKeys 按下标一一对应，用于根据 multiGet 结果推送同步消息
        List<String> selfKeys = new ArrayList<>();
        List<Integer> selfOtherTerminals = null;
        if (message.getSendToSelf()) {
            Long senderId = sender.getId();
            List<Integer> terminals = IMTerminalType.codes();
            selfOtherTerminals = new ArrayList<>(terminals.size());
            for (Integer terminal : terminals) {
                if (terminal.equals(sender.getTerminal())) {
                    continue;
                }
                selfKeys.add(IMRedisKey.userServerIdKey(senderId, terminal));
                selfOtherTerminals.add(terminal);
            }
        }
        int recvKeyCount = recvKeyToUser.size();
        int selfKeyCount = selfKeys.size();
        int totalKeys = recvKeyCount + selfKeyCount;
        // 一次 MGET：先全部接收方 key，再全部「自己其它终端」key；结果前半段对应接收方，后半段从 recvKeyCount 起对应 sendToSelf
        //[接收方 key 1, 接收方 key 2, ..., 自己终端 key 1, 自己终端 key 2, ...]
        List<Object> serverIds = Collections.emptyList();
        if (totalKeys > 0) {
            List<String> keys = new ArrayList<>(totalKeys);
            keys.addAll(recvKeyToUser.keySet());
            keys.addAll(selfKeys);
            serverIds = redisMQTemplate.opsForValue().multiGet(keys);
        }
        // 按 IM-server 分组后写入私聊队列；无 serverId 的记入离线列表
        if (!recvKeyToUser.isEmpty()) {
            Map<Integer, List<IMUserInfo>> serverMap = new HashMap<>(16);
            int idx = 0;
            for (Map.Entry<String, IMUserInfo> entry : recvKeyToUser.entrySet()) {
                Integer serverId = (Integer)serverIds.get(idx++);
                if (serverId != null) {
                    serverMap.computeIfAbsent(serverId, k -> new ArrayList<>()).add(entry.getValue());
                } else {
                    offlineUsers.add(entry.getValue());
                }
            }
            for (Map.Entry<Integer, List<IMUserInfo>> entry : serverMap.entrySet()) {
                pushPrivateMessage(entry.getKey(), sender, entry.getValue(), message.getSendResult(),
                        message.getData());
            }
        }
        // 自己的其它终端在线则入队；sendResult 固定 false，与单条私聊「同步给自己」行为一致
        if (!selfKeys.isEmpty()) {
            Long senderId = sender.getId();
            for (int i = 0; i < selfKeys.size(); i++) {
                Integer serverId = (Integer)serverIds.get(recvKeyCount + i);
                if (serverId != null) {
                    List<IMUserInfo> receivers = List.of(new IMUserInfo(senderId, selfOtherTerminals.get(i)));
                    pushPrivateMessage(serverId, sender, receivers, false, message.getData());
                }
            }
        }
        // 需要回推发送结果时，将离线接收方一次性回调给业务侧
        if (message.getSendResult() && !offlineUsers.isEmpty()) {
            IMBatchSendResult<T> result = new IMBatchSendResult<>();
            result.setSender(message.getSender());
            result.setReceivers(offlineUsers);
            result.setCode(IMSendCode.NOT_ONLINE.code());
            result.setData(message.getData());
            listenerMulticaster.multicast(IMListenerType.PRIVATE_MESSAGE, List.of(result));
        }
    }

    public <T> void sendGroupMessage(IMGroupMessage<T> message) {
        // 根据群聊每个成员所连的IM-server，进行分组
        List<Long> recvIds = message.getRecvIds();
        List<Integer> recvTerminals = message.getRecvTerminals();
        List<IMUserInfo> offlineUsers = new ArrayList<>();
        IMUserInfo sender = message.getSender();
        // 接收方：redisKey → 用户信息（LinkedHashMap 保证遍历顺序与 multiGet 结果下标一致）
        Map<String, IMUserInfo> recvKeyToUser = new LinkedHashMap<>();
        if (!CollUtil.isEmpty(recvIds) && !CollUtil.isEmpty(recvTerminals)) {
            for (Integer terminal : recvTerminals) {
                for (Long id : recvIds) {
                    String key = IMRedisKey.userServerIdKey(id, terminal);
                    recvKeyToUser.put(key, new IMUserInfo(id, terminal));
                }
            }
        }
        // 发送方其它终端：与 selfKeys 按下标一一对应
        List<String> selfKeys = new ArrayList<>();
        List<Integer> selfOtherTerminals = null;
        if (message.getSendToSelf()) {
            Long senderId = sender.getId();
            List<Integer> terminals = IMTerminalType.codes();
            selfOtherTerminals = new ArrayList<>(terminals.size());
            for (Integer terminal : terminals) {
                if (terminal.equals(sender.getTerminal())) {
                    continue;
                }
                selfKeys.add(IMRedisKey.userServerIdKey(senderId, terminal));
                selfOtherTerminals.add(terminal);
            }
        }
        int recvKeyCount = recvKeyToUser.size();
        int totalKeys = recvKeyCount + selfKeys.size();
        List<Object> serverIds = Collections.emptyList();
        if (totalKeys > 0) {
            List<String> keys = new ArrayList<>(totalKeys);
            keys.addAll(recvKeyToUser.keySet());
            keys.addAll(selfKeys);
            // 批量拉取
            serverIds = redisMQTemplate.opsForValue().multiGet(keys);
        }
        if (!recvKeyToUser.isEmpty()) {
            // 格式:map<服务器id,list<接收方>>
            Map<Integer, List<IMUserInfo>> serverMap = new HashMap<>(16);
            int idx = 0;
            for (Map.Entry<String, IMUserInfo> entry : recvKeyToUser.entrySet()) {
                Integer serverId = (Integer)serverIds.get(idx++);
                if (serverId != null) {
                    serverMap.computeIfAbsent(serverId, k -> new ArrayList<>()).add(entry.getValue());
                } else {
                    // 加入离线列表
                    offlineUsers.add(entry.getValue());
                }
            }
            // 逐个server发送
            for (Map.Entry<Integer, List<IMUserInfo>> entry : serverMap.entrySet()) {
                pushGroupMessage(entry.getKey(), sender, entry.getValue(), message.getSendResult(), message.getData());
            }
        }
        // 推送给自己的其他终端
        if (!selfKeys.isEmpty()) {
            Long senderId = sender.getId();
            for (int i = 0; i < selfKeys.size(); i++) {
                // 获取终端连接的channelId
                Integer serverId = (Integer)serverIds.get(recvKeyCount + i);
                // 如果终端在线，将数据存储至redis，等待拉取推送
                if (serverId != null) {
                    // 自己的消息不需要回推消息结果
                    List<IMUserInfo> receivers = List.of(new IMUserInfo(senderId, selfOtherTerminals.get(i)));
                    pushGroupMessage(serverId, sender, receivers, false, message.getData());
                }
            }
        }
        // 对离线用户回复消息状态
        if (message.getSendResult() && !offlineUsers.isEmpty()) {
            IMBatchSendResult<T> result = new IMBatchSendResult<>();
            result.setReceivers(offlineUsers);
            result.setCode(IMSendCode.NOT_ONLINE.code());
            result.setData(message.getData());
            listenerMulticaster.multicast(IMListenerType.GROUP_MESSAGE, List.of(result));
        }
    }

    //"投递工具方法"——把消息打包成 IMRecvInfo，投到对应 im-server 的队列
    private <T> void pushPrivateMessage(Integer serverId, IMUserInfo sender, List<IMUserInfo> receivers,
                                        Boolean sendResult, T data) {
        IMRecvInfo recvInfo = new IMRecvInfo();
        recvInfo.setCmd(IMCmdType.PRIVATE_MESSAGE.code());
        recvInfo.setSender(sender);
        recvInfo.setReceivers(receivers);
        recvInfo.setServiceName(appName);
        recvInfo.setSendResult(sendResult);
        recvInfo.setData(data);
        String queueKey = String.join(":", IMRedisKey.IM_MESSAGE_PRIVATE_QUEUE, serverId.toString());
        redisMQTemplate.opsForList().rightPush(queueKey, recvInfo);
    }

    //"投递工具方法"——把消息打包成 IMRecvInfo，投到对应 im-server 的队列
    private <T> void pushGroupMessage(Integer serverId, IMUserInfo sender, List<IMUserInfo> receivers,
                                      Boolean sendResult, T data) {
        IMRecvInfo recvInfo = new IMRecvInfo();
        recvInfo.setCmd(IMCmdType.GROUP_MESSAGE.code());
        recvInfo.setSender(sender);
        recvInfo.setReceivers(receivers);
        recvInfo.setServiceName(appName);
        recvInfo.setSendResult(sendResult);
        recvInfo.setData(data);
        String queueKey = String.join(":", IMRedisKey.IM_MESSAGE_GROUP_QUEUE, serverId.toString());
        // 推送至队列
        redisMQTemplate.opsForList().rightPush(queueKey, recvInfo);
    }


}
