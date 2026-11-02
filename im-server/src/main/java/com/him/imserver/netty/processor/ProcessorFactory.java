package com.him.imserver.netty.processor;

import com.him.imcommon.enums.IMCmdType;
import com.him.imserver.util.SpringContextHolder;

//根据不同cmd值的指令，返回对应的处理器
public class ProcessorFactory {

    public static AbstractMessageProcessor createProcessor(IMCmdType cmd) {
        return switch (cmd) {
            case FORCE_LOGOUT -> SpringContextHolder.getApplicationContext().getBean(ForceLogoutProcessor.class);
            case LOGIN->SpringContextHolder.getApplicationContext().getBean(LoginProcessor.class);
            case HEART_BEAT -> SpringContextHolder.getApplicationContext().getBean(HeartbeatProcessor.class);
            case PRIVATE_MESSAGE->SpringContextHolder.getApplicationContext().getBean(PrivateMessageProcessor.class);
            case GROUP_MESSAGE->SpringContextHolder.getApplicationContext().getBean(GroupMessageProcessor.class);
            case SYSTEM_MESSAGE->SpringContextHolder.getApplicationContext().getBean(SystemMessageProcessor.class);
            default -> null;
        };
    }

}
