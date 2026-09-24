package com.him.imclient.annotation;

import com.him.imcommon.enums.IMListenerType;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE,ElementType.FIELD})  //注解可以贴在类上，也可以贴在字段上
@Retention(RetentionPolicy.RUNTIME)  //注解在运行时保留，因为 MessageListenerMulticaster 运行时要反射读它
@Component  //注解是组件
public @interface IMListener {

    IMListenerType type();  //注解属性，不是函数。没有方法体（没有 {}），没有参数。
    // 使用时：@IMListener(type = IMListenerType.PRIVATE_MESSAGE) type = ... 是给属性赋值，不是"调方法"。
}
