package com.him.imcommon.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 消息推送信封:im-platform 投递到推送队列、im-server 消费并转发给客户端的统一数据结构。
 *
 * <p>这是三方(im-platform / im-client / im-server)之间唯一的数据契约,
 * 任何字段的增删改都需要三方同步。放在 im-common 就是为了避免各写一份。
 *
 * <h3>字段分两组</h3>
 * <ul>
 *   <li><b>路由信息</b>(cmd / chatType / sendId / recvIds / groupId):
 *       由 im-client 在投递前填好,im-server 只读它来决定"推给谁"。
 *       其中 recvIds 在单聊时是接收方用户id列表(多端在线时该用户的所有端都在里面),
 *       群聊时改用 groupId,由 im-server 结合本地连接自行筛选。</li>
 *   <li><b>消息负载</b>(id / seqNo / type / content ...):
 *       由 im-platform 填好,im-server <b>原样转发、不做解析</b>,由客户端消费。
 *       这样 im-server 就不需要知道任何业务语义,满足"im-server 不依赖业务"的边界。</li>
 * </ul>
 *
 * <h3>各命令的字段约定</h3>
 * <table border="1">
 *   <tr><th>cmd</th><th>必填字段</th></tr>
 *   <tr><td>PRIVATE_MESSAGE</td><td>chatType=1, sendId, recvIds, id, localId, seqNo, recvId,
 *       type, content, sendTime, status</td></tr>
 *   <tr><td>GROUP_MESSAGE</td><td>chatType=2, sendId, groupId, id, localId, seqNo,
 *       sendNickName, type, content, sendTime, status, atUserIds, receipt, receiptOk, readedCount</td></tr>
 *   <tr><td>PRIVATE_READED</td><td>chatType=1, sendId=已读者, recvIds=对方, id=已读到的消息id</td></tr>
 *   <tr><td>GROUP_READED</td><td>chatType=2, sendId=已读者, groupId, id=已读到的消息id, readedCount</td></tr>
 *   <tr><td>FORCE_LOGOUT</td><td>recvIds=被下线的用户, content=原因</td></tr>
 * </table>
 *
 * <h3>几条硬约定</h3>
 * <ol>
 *   <li><b>时间戳一律用毫秒 long</b>,不要塞 Date 对象,避免时区和序列化差异。</li>
 *   <li><b>不要依赖 Jackson 的 default typing</b>(即不要在 JSON 里带 {@code @class})。
 *       im-platform 的 RedisTemplate 开了 default typing,如果直接复用它序列化信封,
 *       会把 platform 的类名写进队列,导致 im-server 反序列化时找不到类、
 *       并被倒逼依赖业务模块,直接破坏"im-server 不依赖业务"的边界。
 *       im-client 投递时应使用不带类型信息的普通 JSON 序列化器。</li>
 *   <li><b>字段只增不删、只改不改义</b>。新增字段时老版本忽略即可
 *       (反序列化应配置为忽略未知字段)。</li>
 *   <li>{@code deleted} 在推送时恒为 false —— 刚发出的消息对谁都没删。
 *       删除同步是独立事件,不要复用它。</li>
 * </ol>
 */
@Data
public class IMPushMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 路由信息(im-client 填,im-server 读) ====================

    /**
     * 推送命令,取值见 {@code IMCmd.code}
     */
    private Integer cmd;

    /**
     * 会话类型,取值见 {@code ChatType.code}:1私聊 2群聊
     */
    private Integer chatType;

    /**
     * 事件发起人(消息发送者 / 已读者)。控制类命令可能为空。
     */
    private Long sendId;

    /**
     * chatType=1 时的接收方用户id列表(多端在线时该用户的所有端都推)
     */
    private List<Long> recvIds;

    /**
     * chatType=2 时的群id
     */
    private Long groupId;

    /**
     * 服务端投递时间戳(毫秒)
     */
    private Long timestamp;

    // ==================== 消息负载(im-platform 填,客户端读) ====================

    /**
     * 服务端消息id
     */
    private Long id;

    /**
     * 客户端生成的本地消息id,用于发送方匹配"本地临时消息"和"服务端确认"
     */
    private String localId;

    /**
     * 会话内序号,用于客户端排序、去重和断点续传
     */
    private Long seqNo;

    /**
     * 单聊接收方用户id
     */
    private Long recvId;

    /**
     * 群内发送者昵称快照(群聊显示用)
     */
    private String sendNickName;

    /**
     * 消息类型,取值见 {@code MessageType}
     */
    private Integer type;

    /**
     * 消息内容。文字消息是纯文本,图片/文件等是约定格式的 JSON 字符串
     */
    private String content;

    /**
     * 消息发送时间(毫秒)
     */
    private Long sendTime;

    /**
     * 消息状态,取值见 {@code MessageStatus}
     */
    private Integer status;

    /**
     * 该消息对当前接收者是否已删除。推送新消息时恒为 false
     */
    private Boolean deleted;

    /**
     * 被@的用户id列表(群聊)
     */
    private List<Long> atUserIds;

    /**
     * 是否回执消息(群聊)
     */
    private Boolean receipt;

    /**
     * 回执是否已完成(群聊)
     */
    private Boolean receiptOk;

    /**
     * 已读人数(群聊)
     */
    private Integer readedCount;
}
