package com.him.imserver.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
//让任何普通的 Java 类（哪怕不是 Spring 管理的类），都能随时随地从 Spring 容器里拿出需要的 Bean
//Netty 的 ChannelHandler 以及很多底层的业务处理器，它们的实例化过程是由 Netty 框架自己通过反射 new 出来的，并没有交给 Spring 管理。
@Component

//implements ApplicationContextAware：这是 Spring 提供的接口。
// 只要一个 Bean 实现了这个接口，Spring 在启动时，就会自动调用它的 setApplicationContext 方法，把整个 Spring 容器（ApplicationContext）当作参数传进来。
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        assertApplicationContext();
        return applicationContext;
    }

    public static <T> T getBean(String beanName) {
        assertApplicationContext();
        return (T) applicationContext.getBean(beanName);
    }

    public static <T> T getBean(Class<T> requiredType) {
        assertApplicationContext();
        return applicationContext.getBean(requiredType);
    }

    private static void assertApplicationContext() {
        if (SpringContextHolder.applicationContext == null) {
            throw new RuntimeException("applicaitonContext属性为null,请检查是否注入了SpringContextHolder!");
        }
    }

}
