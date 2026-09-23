package com.him.imcommon.util;

public class ConvUtil {

    public static String buildConvKey(Long userId1, Long userId2) {
        return Math.min(userId1, userId2) + "_" + Math.max(userId1, userId2);
    }
}