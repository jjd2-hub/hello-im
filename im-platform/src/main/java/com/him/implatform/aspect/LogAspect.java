package com.him.implatform.aspect;

import com.alibaba.fastjson.JSON;
import com.him.implatform.annotation.LogSensitive;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@Slf4j
public class LogAspect {

    @Around("execution(* com.him.implatform.controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.info("请求开始: {}, 参数: {}", methodName, maskArgs(args));

        long startTime = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("请求结束: {}, 耗时: {}ms", methodName, elapsedTime);
        }
    }

    private String maskArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        List<Object> masked=new ArrayList<>();
        for(Object arg:args){
            if(arg instanceof MultipartFile){
                masked.add("[MultipartFile]");
            } else {
                masked.add(maskSensitive(arg));
            }
        }
        return JSON.toJSONString(masked);
    }

    private Object maskSensitive(Object obj){
        if(obj==null){
            return null;
        }
        try{
            String json=JSON.toJSONString(obj);
            Object copy=JSON.parseObject(json,obj.getClass());
            for(Field field:copy.getClass().getDeclaredFields()){
                if(field.isAnnotationPresent(LogSensitive.class)){
                    field.setAccessible(true);
                    field.set(copy,field.getAnnotation(LogSensitive.class).mask());
                }
            }
            return copy;
        } catch (Exception e) {
            log.warn("日志脱敏失败:{}",e.getMessage());
            return obj.getClass().getSimpleName();
        }
    }
}
