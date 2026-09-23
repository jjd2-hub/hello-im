package com.him.imcommon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话类型。
 *
 * <p>放在 im-common 是因为它出现在跨模块的消息信封里(见 {@code IMPushMessage.chatType}),
 * platform / im-client / im-server 三方必须用同一份定义。
 */
@Getter
@AllArgsConstructor
public enum ChatType {

    PRIVATE(1, "私聊"),
    GROUP(2, "群聊");

    private final Integer code;
    private final String desc;

    public static ChatType fromCode(Integer code) {
        for (ChatType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
