package com.him.imcommon.model;

import lombok.Data;

/**
 * 服务端内部使用的强制下线信息
 */
@Data
public class IMForceLogoutInfo {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户终端类型 IMTerminalType
     */
    private Integer terminal;

    /**
     * 设备id
     */
    private String devId;

    /**
     * 下线类型，见 {@link com.him.imcommon.enums.IMForceLogoutType}
     */
    private Integer type;

    /**
     * 原因说明（封禁时由管理端传入）
     */
    private String reason;

}
