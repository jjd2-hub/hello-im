package com.him.implatform.context;

import com.him.implatform.session.UserSession;

public class UserContext {
    private static final ThreadLocal<UserSession> HOLDER=new ThreadLocal<>();

    public static void set(UserSession userSession){
        HOLDER.set(userSession);
    }
    public static UserSession get(){
        return HOLDER.get();
    }
    public static Long getUserId(){
        UserSession userSession=HOLDER.get();
        return userSession==null?null:userSession.getUserId();
    }
    public static void remove(){
        HOLDER.remove();
    }
}
