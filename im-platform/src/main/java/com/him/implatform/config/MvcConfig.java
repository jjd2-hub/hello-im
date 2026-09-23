package com.him.implatform.config;

import com.him.implatform.interceptor.AuthInterceptor;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class MvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只放行注册/登录/刷新token这三个无需登录的接口。
        // 注意不要写成 /auth/** 或 /file/** 这种前缀通配:一旦以后往这些前缀下新增接口,
        // 会静默地变成免登录接口。/logout 需要登录态,因此不再放行。
        registry.addInterceptor(authInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/auth/refreshToken", "/favicon.ico",
                        "/swagger/**", "/v3/api-docs/**", "/swagger-resources/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/doc.html");
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        // 使用BCrypt加密密码
        return new BCryptPasswordEncoder();
    }
}
