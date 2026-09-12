package com.him.implatform.interceptor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.him.imcommon.util.JwtUtil;
import com.him.implatform.config.props.JwtProperties;
import com.him.implatform.context.UserContext;
import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.session.UserSession;
import io.micrometer.common.lang.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        // 如果不是映射到方法直接放行
        if(!(handler instanceof HandlerMethod)){
            return true;
        }
        // 取token
        String token=request.getHeader("accessToken");
        if(StrUtil.isEmpty(token)){
            log.error("未登录，url:{}",request.getRequestURI());
            throw new GlobalException(ResultCode.NO_LOGIN);
        }
        // 检验token
        if(!JwtUtil.checkToken(token,jwtProperties.getAccessTokenSecret())){
            log.error("token无效或过期:{}",token);
            throw new GlobalException(ResultCode.INVALID_TOKEN);
        }
        String strJson= JwtUtil.getInfoByToken(token);
        UserSession userSession= JSON.parseObject(strJson,UserSession.class);
        if(userSession==null){
            log.error("反序列化失败:{}",strJson);
            return false;
        }
        UserContext.set(userSession);
        return true;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, @Nullable Exception ex) throws Exception {
        UserContext.remove();
    }
}
