package com.him.implatform.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
