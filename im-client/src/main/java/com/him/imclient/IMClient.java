package com.him.imclient;

import com.him.imclient.sender.IMSender;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@AllArgsConstructor
public class IMClient {

    private final IMSender imSender;

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     */
    public Boolean isOnline(Long userId) {
        return imSender.isOnline(userId);
    }

    /**
     * 判断多个用户是否在线
     *
     * @param userIds 用户id列表
     * @return 在线的用户列表
     */
    public List<Long> getOnlineUser(List<Long> userIds){
        return imSender.getOnlineUser(userIds);
    }

    /**
     * 强制用户所有在线终端下线
     *
     * @param userId 用户id
     * @param type   下线类型，见 IMForceLogoutType
     * @param reason 原因说明
     */
    public void forceLogout(Long userId, Integer type, String reason){
        imSender.forceLogout(userId, type, reason);
    }


}
