package com.him.implatform.redis;

import com.him.imcommon.constant.RedisKey;

/**
 * Redis key 工厂。
 *
 * <p>为什么要有这个类:{@link RedisKey} 只是字符串常量,业务代码里到处写
 * {@code StrUtil.join(":", RedisKey.XXX, id)},拼接口径散落在十几处 ——
 * 漏个冒号、写错前缀都不会报错,只会静默读不到数据(历史上 conv_key 就踩过这类问题)。
 *
 * <p>把"前缀 + 分隔符 + 标识"的拼接收敛到这里之后:
 * <ul>
 *   <li>业务代码只描述语义:{@code RedisKeys.privateReadedPosition(userId)}</li>
 *   <li>将来改 key 结构(比如加环境前缀、换分隔符)只改这一个文件</li>
 *   <li>拼错的概率趋近于零,因为不再有手写的分隔符</li>
 * </ul>
 *
 * <p>跨模块契约 key(推送队列、连接注册表)也在这里给出构造方法,
 * 方便 im-client / im-server 对齐 —— 定义见 {@code docs/消息推送契约.md}。
 */
public final class RedisKeys {

    private static final String SEPARATOR = ":";

    private RedisKeys() {
    }

    private static String join(String prefix, Object... parts) {
        StringBuilder builder = new StringBuilder(prefix);
        for (Object part : parts) {
            builder.append(SEPARATOR).append(part);
        }
        return builder.toString();
    }

    // ==================== 私聊 ====================

    /**
     * 私聊会话已读位置(hash:key=用户, field=好友id, value=已读到的最大消息id)
     */
    public static String privateReadedPosition(Long userId) {
        return join(RedisKey.IM_PRIVATE_READED_POSITION, userId);
    }

    /**
     * 私聊会话最新消息id缓存
     */
    public static String privateMessageMaxId(String convKey) {
        return join(RedisKey.IM_PRIVATE_MESSAGE_MAX_ID, convKey);
    }

    /**
     * 私聊会话序号计数器
     */
    public static String privateMessageMaxSeq(String convKey) {
        return join(RedisKey.IM_PRIVATE_MESSAGE_MAX_SEQ, convKey);
    }

    /**
     * 私聊会话序号计数器初始化锁(按会话隔离)
     */
    public static String lockPrivateMessageMaxSeq(String convKey) {
        return join(RedisKey.IM_LOCK_PRIVATE_MESSAGE_MAX_SEQ, convKey);
    }

    // ==================== 群聊 ====================

    /**
     * 群聊已读位置(hash:key=群, field=用户id, value=已读到的最大消息id)
     */
    public static String groupReadedPosition(Long groupId) {
        return join(RedisKey.IM_GROUP_READED_POSITION, groupId);
    }

    /**
     * 群聊最新消息id缓存
     */
    public static String groupMessageMaxId(Long groupId) {
        return join(RedisKey.IM_GROUP_MESSAGE_MAX_ID, groupId);
    }

    /**
     * 群聊序号计数器
     */
    public static String groupMessageMaxSeq(Long groupId) {
        return join(RedisKey.IM_GROUP_MESSAGE_MAX_SEQ, groupId);
    }

    /**
     * 群聊序号计数器初始化锁(按群隔离)
     */
    public static String lockGroupMessageMaxSeq(Long groupId) {
        return join(RedisKey.IM_LOCK_GROUP_MESSAGE_MAX_SEQ, groupId);
    }

    // ==================== 用户 ====================

    /**
     * 用户token版本号
     */
    public static String userTokenVersion(Long userId) {
        return join(RedisKey.IM_USER_TOKEN_VERSION, userId);
    }

    // ==================== 跨模块契约 key(见 docs/消息推送契约.md) ====================

    /**
     * 【契约】推送队列:im-client 投递、对应 serverId 的 im-server 消费
     */
    public static String pushQueue(Long serverId) {
        return join(RedisKey.IM_PUSH_QUEUE, serverId);
    }

    /**
     * 【契约】用户连接位置注册表(hash:field=客户端类型, value=serverId)
     */
    public static String userServer(Long userId) {
        return join(RedisKey.IM_USER_SERVER, userId);
    }

    /**
     * 【契约】群成员id缓存
     */
    public static String groupMemberIds(Long groupId) {
        return join(RedisKey.IM_CACHE_GROUP_MEMBER_ID, groupId);
    }
}
