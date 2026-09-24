package com.him.implatform.dto;

/**
 * "带内容的待发消息"的抽象。
 *
 * <p>存在的唯一目的:让 {@code MessageValidator} 能对私聊和群聊消息用同一套规则校验,
 * 而不必把两个 DTO 抽成继承体系(它们的字段本来就不一样,强行继承会更糟)。
 *
 * <p>由 {@link PrivateMessageDTO} 和 {@link GroupMessageDTO} 实现 ——
 * 两者的 getter 名字一致,所以 Lombok 生成的 getter 天然满足这个接口,不需要额外代码。
 */
public interface MessageContent {

    /**
     * 消息内容。文字消息是纯文本,其它类型是约定格式的 JSON 字符串
     */
    String getContent();

    /**
     * 消息类型,取值见 {@code MessageType}
     */
    Integer getType();
}
