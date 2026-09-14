package com.him.implatform.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MessageStatus {
    PENDING(0, "等待推送"),
    DELIVERED(1, "已送达"),
    RECALL(2, "撤回"),
    READED(3, "已读");

    private final Integer code;
    private final String desc;

    public Integer getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }
}
