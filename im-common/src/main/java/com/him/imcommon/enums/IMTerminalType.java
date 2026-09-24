package com.him.imcommon.enums;

import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public enum IMTerminalType {

    /**
     * web
     */
    WEB(0, "web");

    private final Integer code;

    private final String desc;


    //根据code反查枚举类型
    public static IMTerminalType fromCode(Integer code) {
        for (IMTerminalType typeEnum : values()) {
            if (typeEnum.code.equals(code)) {
                return typeEnum;
            }
        }
        return null;
    }

    //获取所有枚举类型的code
    public static List<Integer> codes() {
        return Arrays.stream(values()).map(IMTerminalType::code).collect(Collectors.toList());
    }

    public Integer code() {
        return this.code;
    }

}
