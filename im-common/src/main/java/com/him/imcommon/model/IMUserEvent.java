package com.him.imcommon.model;

import lombok.Data;

/**
 * 用户事件
 */
@Data
public class IMUserEvent {

    /**
     * 事件类型
     */
    private Integer eventType;

    /**
     * 用户信息
     */
    IMUserInfo userInfo;

}
