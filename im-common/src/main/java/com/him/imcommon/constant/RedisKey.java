package com.him.imcommon.constant;

/**
 * 后端所有服务共用的 Redis key 定义。
 *
 * <p>放在 im-common 而不是 im-platform,是因为其中一部分 key 是 <b>跨模块契约</b>:
 * im-platform 写、im-server 读(或反过来),三方必须用同一份字符串定义,
 * 否则联调时会出现"键对不上"的静默错误(历史上 conv_key 就踩过一次)。
 *
 * <p>命名规范:{@code im:{域}:{资源}[:{标识}]},冒号分隔,全小写下划线。
 *
 * <p><b>注意</b>:平台私有的 key 也放在这里,目的是"后端只有一个地方查 Redis key",
 * 避免出现两个 RedisKey 类让人不知道该往哪加。跨模块的那部分已在下面单独标注。
 */
public final class RedisKey {

    private RedisKey() {
    }

    // ==================================================================
    // 一、跨模块契约 key —— im-platform / im-client / im-server 都必须遵守
    // ==================================================================

    /**
     * 【契约】消息推送队列:list 结构,完整 key 为 {@code im:unread:{serverId}}。
     * <p>im-client 用 LPUSH 投递,对应的 im-server 只消费自己 serverId 的那一条队列。
     * <p>队列里放的是 {@code IMPushMessage} 的 JSON(不带 Java 类型信息)。
     */
    public static final String IM_PUSH_QUEUE = "im:unread";

    /**
     * 【契约】用户连接位置注册表:hash 结构,完整 key 为 {@code im:user:server:{userId}},
     * field 为客户端类型({@code ClientType} 的 code),value 为该连接所在的 serverId。
     *
     * <p>用途有两个,是"多端在线"方案的基础:
     * <ol>
     *   <li>路由:推送前先查目标用户都连在哪几个 server 上,再往对应队列投递;</li>
     *   <li>在线状态:hash 非空即在线的。</li>
     * </ol>
     * <p>im-server 写、im-client(以及 platform 查在线状态时)读。
     */
    public static final String IM_USER_SERVER = "im:user:server";

    /**
     * 【契约】用户忙闲状态:无值=空闲,1=正在忙。
     * <p>注意区分:这是"用户自己设置的忙闲状态",不是在线状态;
     * 在线与否请用 {@link #IM_USER_SERVER} 判断。im-server 写、platform 读。
     */
    public static final String IM_USER_STATE = "im:user:state";

    /**
     * 【契约】用户被封禁事件队列:im-platform 写、im-server 消费后强制该用户下线。
     */
    public static final String IM_QUEUE_USER_BANNED = "im:queue:user:banned";

    /**
     * 【契约】群聊被封禁事件队列。
     */
    public static final String IM_QUEUE_GROUP_BANNED = "im:queue:group:banned";

    /**
     * 【契约】群聊解封事件队列。
     */
    public static final String IM_QUEUE_GROUP_UNBAN = "im:queue:group:unban";

    /**
     * 【契约】群聊成员id缓存:im-platform 在成员变更时维护,im-server 群发时读取。
     * <p><b>这是 platform 的义务</b>:不维护它,im-server 群发时查不到成员,群消息发不出去。
     */
    public static final String IM_CACHE_GROUP_MEMBER_ID = "im:cache:group_member_ids";

    /**
     * 【契约】webrtc 单人通话信令。
     */
    public static final String IM_WEBRTC_PRIVATE_SESSION = "im:webrtc:private:session";

    // ==================================================================
    // 二、im-platform 私有 key
    // ==================================================================

    /**
     * 私聊会话已读位置(已读最大id)
     */
    public static final String IM_PRIVATE_READED_POSITION = "im:readed:private:position";

    /**
     * 已读群聊消息位置(已读最大id)
     */
    public static final String IM_GROUP_READED_POSITION = "im:readed:group:position";

    /**
     * 私聊会话消息最大id
     */
    public static final String IM_PRIVATE_MESSAGE_MAX_ID = "im:message:private:max_id";

    /**
     * 群聊会话消息最大id
     */
    public static final String IM_GROUP_MESSAGE_MAX_ID = "im:message:group:max_id";

    /**
     * 私聊会话消息最大序列号
     */
    public static final String IM_PRIVATE_MESSAGE_MAX_SEQ = "im:message:private:max_seq";

    /**
     * 群聊会话消息最大序列号
     */
    public static final String IM_GROUP_MESSAGE_MAX_SEQ = "im:message:group:max_seq";

    /**
     * 分布式锁-保存私聊会话消息
     */
    public static final String IM_LOCK_PRIVATE_MESSAGE_SAVE = "im:lock:message:private:save";

    /**
     * 分布式锁-私聊会话消息最大序列号
     */
    public static final String IM_LOCK_PRIVATE_MESSAGE_MAX_SEQ = "im:lock:message:private:max_seq";

    /**
     * 分布式锁-群聊会话消息最大序列号
     */
    public static final String IM_LOCK_GROUP_MESSAGE_MAX_SEQ = "im:lock:message:group:max_seq";

    /**
     * 分布式锁-保存群聊会话消息
     */
    public static final String IM_LOCK_GROUP_MESSAGE_SAVE = "im:lock:message:group:save";

    /**
     * 缓存是否好友:bool
     */
    public static final String IM_CACHE_FRIEND = "im:cache:friend";

    /**
     * 缓存群聊信息
     */
    public static final String IM_CACHE_GROUP = "im:cache:group";

    /**
     * 缓存消息删除记录
     */
    public static final String IM_CACHE_MESSAGE_DELETION = "im:cache:message:deletion";

    /**
     * 重复提交
     */
    public static final String IM_REPEAT_SUBMIT = "im:repeat:submit";

    /**
     * 分布式锁-添加好友
     */
    public static final String IM_LOCK_FRIEND_ADD = "im:lock:friend:add";

    /**
     * 分布式锁-进入群聊
     */
    public static final String IM_LOCK_GROUP_ENTER = "im:lock:group:enter";

    /**
     * 分布式锁-清理过期文件
     */
    public static final String IM_LOCK_FILE_TASK = "im:lock:task:file";

    /**
     * 群成员最大版本号
     */
    public static final String IM_GROUP_MEMBER_MAX_VERSION = "im:group:member:max_version";

    /**
     * 好友信息最大版本号
     */
    public static final String IM_FRIEND_MAX_VERSION = "im:friend:max_version";

    /**
     * 分布式锁-群成员最大版本号(初始化计数器时使用)
     */
    public static final String IM_LOCK_GROUP_MEMBER_MAX_VERSION = "im:lock:group:member:max_version";

    /**
     * 分布式锁-好友信息最大版本号(初始化计数器时使用)
     */
    public static final String IM_LOCK_FRIEND_MAX_VERSION = "im:lock:friend:max_version";

    /**
     * 用户token版本号:自增即可让该用户已签发的所有token失效(登出/封禁)
     */
    public static final String IM_USER_TOKEN_VERSION = "im:user:token:version";
}
