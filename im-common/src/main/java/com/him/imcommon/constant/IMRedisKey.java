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
}
