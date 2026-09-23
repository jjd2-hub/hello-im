package com.him.imcommon.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户端类型。用作"用户连接位置注册表"({@code im:user:server:{userId}})的 hash field,
 * 从而支持同一用户多端同时在线:每种端各占一个 field。
 *
 * <p>同类型端重复登录(例如同一个浏览器开两个标签页)视为同一端,
 * 后连接覆盖前连接,由 im-server 负责断开旧连接。
 */
@Getter
@AllArgsConstructor
public enum ClientType {

    WEB(1, "web端"),
    APP(2, "app端"),
    H5(3, "h5端"),
    MINI(4, "小程序端");

    private final Integer code;
    private final String desc;

    public static ClientType fromCode(Integer code) {
        for (ClientType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
