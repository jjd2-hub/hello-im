package com.him.imcommon.util;

import org.springframework.util.ReflectionUtils;

public class BeanUtil {
    private BeanUtil() {}

    /**
     * 异常统一处理
     */
    private static void handleReflectionException(Exception e) {
        ReflectionUtils.handleReflectionException(e);
    }

    /**
     * 创建目标对象+拷贝
     */
    public static <T> T copyProperties(Object orig,Class<T> destClass){
        if(orig==null){
            return null;
        }
        try{
            T target = org.springframework.beans.BeanUtils.instantiateClass(destClass);
            copyProperties(orig,target);
            return target;
        }catch (Exception e){
            handleReflectionException(e);
            return null;
        }
    }
    /**
     * 原样委托属性拷贝
     */
    public static void copyProperties(Object orig,Object dest){
        org.springframework.beans.BeanUtils.copyProperties(orig,dest);
    }
}
