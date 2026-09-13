package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.entity.Friend;
import com.him.implatform.vo.FriendVO;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface FriendService extends IService<Friend> {
    /**
     * 获取好友列表
     * @param version 版本
     * @return 好友列表
     */
    List<FriendVO> findFriends(Long version);

}
