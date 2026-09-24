package com.him.implatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.him"})
@MapperScan("com.him.implatform.mapper")
public class ImPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImPlatformApplication.class, args);
    }

}
