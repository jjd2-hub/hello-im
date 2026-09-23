package com.him.implatform.context;

import com.him.implatform.enums.ResultCode;
import com.him.implatform.exception.GlobalException;
import com.him.implatform.session.UserSession;
import org.jetbrains.annotations.NotNull;

public class UserContext {
    private static final ThreadLocal<UserSession> HOLDER=new ThreadLocal<>();

    public static void set(UserSession userSession){
        HOLDER.set(userSession);
    }
    public static UserSession get(){
        return HOLDER.get();
    }
    @NotNull
    public static Long getUserId(){
        UserSession userSession=HOLDER.get();
        if(userSession==null){
            throw new GlobalException(ResultCode.FORMAT_FAILED.getCode(),"校验id出问题");
        }
        return userSession.getUserId();
    }
    public static void remove(){
        HOLDER.remove();
    }
}
