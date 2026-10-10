package com.him.imcommon.constant;

public final class IMRedisKey {
    /**
     * 用户ID所连接的IM-server的ID
     */
    public static final String  IM_USER_SERVER_ID = "im:user:server_id";

    /**
     * 生成用户ID所连接的IM-server的ID的key
     * 格式：im:user:server_id:{userId}:{terminal}，现在terminal全是0，web
     */
    public static String userServerIdKey(Long userId, Integer terminal) {
        return String.join(":", IM_USER_SERVER_ID, "{" + userId + "}", terminal.toString());
    }

    /**
     * 强制用户退出队列
     */
    public static final String IM_USER_FORCE_LOGOUT_QUEUE = "im:user:force_logout";

    /**
     * 系统消息队列
     */
    public static final String IM_MESSAGE_SYSTEM_QUEUE = "im:message:system";
    /**
     * 私聊消息队列
     */
    public static final String IM_MESSAGE_PRIVATE_QUEUE = "im:message:private";
    /**
     * 群聊消息队列
     */
    public static final String IM_MESSAGE_GROUP_QUEUE = "im:message:group";

    /**
     * 系统消息发送结果队列
     */
    public static final String IM_RESULT_SYSTEM_QUEUE = "im:result:system";
    /**
     * 私聊消息发送结果队列
     */
    public static final String IM_RESULT_PRIVATE_QUEUE = "im:result:private";
    /**
     * 群聊消息发送结果队列
     */
    public static final String IM_RESULT_GROUP_QUEUE = "im:result:group";

}
