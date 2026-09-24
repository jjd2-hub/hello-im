package com.him.imcommon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IM 系统内部（client ↔ server）的"用户终端标识"，用于标识用户登录的终端类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IMUserInfo {

    /**
     * 用户id
     */
    private Long id;

    /**
     * 用户终端类型 IMTerminalType
     */
    private Integer terminal;


}
