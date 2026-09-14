package com.him.implatform.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MessageType {
    TEXT(0, "文字消息"),
    IMAGE(1, "图片消息"),
    FILE(2, "文件消息"),
    AUDIO(3, "语音消息"),
    VIDEO(4, "视频消息"),
    RECALL(10, "撤回"),
    READED(11, "已读"),
    RECEIPT(12, "消息已读回执"),
    TIP_TIME(20, "时间提示"),
    TIP_TEXT(21, "文字提示"),
    FRIEND_NEW(80, "新增好友"),
    FRIEND_DEL(81, "删除好友"),
    FRIEND_ONLINE(82, "好友在线状态变化"),
    FRIEND_DND(83, "好友免打扰"),
    GROUP_NEW(90, "新增群聊"),
    GROUP_DEL(91, "删除群聊"),
    GROUP_DND(92, "群聊免打扰");

    private final Integer code;
    private final String desc;

    public Integer getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }
}
