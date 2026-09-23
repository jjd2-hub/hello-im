package com.him.implatform.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeleteType {
    /**
     * 按消息删除:只隐藏指定的几条消息
     */
    MESSAGE(1, "按消息删除"),
    /**
     * 按会话删除:以删除时该会话的最大消息id为界,该id及之前的消息全部隐藏
     */
    CHAT(2, "按会话删除");

    private final Integer code;
    private final String desc;

    public static DeleteType fromCode(Integer code) {
        for (DeleteType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
