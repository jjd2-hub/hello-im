package com.him.imcommon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推送命令:决定 im-server 怎么路由、客户端收到后怎么处理。
 *
 * <p>与 {@code MessageType} 的区别:MessageType 描述"消息内容的类型"(文字/图片/撤回/提示),
 * 而 IMCmd 描述"这是一个什么事件"。撤回不单独占用 cmd,它是一条 type=RECALL 的消息。
 *
 * <p><b>协议约定:信封里传的是 code(数字),不是枚举名。</b>
 * 这样以后重命名枚举不会破坏协议兼容性。
 */
@Getter
@AllArgsConstructor
public enum IMCmd {

    PRIVATE_MESSAGE(1, "私聊消息"),
    GROUP_MESSAGE(2, "群聊消息"),
    PRIVATE_READED(3, "私聊已读回执"),
    GROUP_READED(4, "群聊已读回执"),

    FRIEND_CHANGED(10, "好友关系变更(新增/删除/免打扰)"),
    GROUP_CHANGED(11, "群聊变更(新增/解散/免打扰)"),
    GROUP_MEMBER_CHANGED(12, "群成员变更"),

    USER_STATE_CHANGED(20, "用户在线状态变更"),

    FORCE_LOGOUT(30, "强制下线(封禁/登出)");

    private final Integer code;
    private final String desc;

    public static IMCmd fromCode(Integer code) {
        for (IMCmd cmd : values()) {
            if (cmd.getCode().equals(code)) {
                return cmd;
            }
        }
        return null;
    }
}
