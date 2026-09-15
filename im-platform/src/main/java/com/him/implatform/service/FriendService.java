package com.him.implatform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.vo.FriendVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface FriendService extends IService<Friend> {
    /**
     * 获取好友列表
     * @param version 版本
     * @return 好友列表
     */
    List<FriendVO> findFriends(Long version);

    /**
     * 添加好友，暂时不上分布式锁
     * @param friendId 好友id
     */
    void addFriend(@NotNull(message = "好友id不能为空") Long friendId);

    /**
     * 绑定两人为好友（单向）
     * @param userId 自己
     * @param friendId 对方
     */
    void bindFriend(Long userId, Long friendId);

    /**
     * 获取下一个版本
     * @return 版本号
     */
    Long getNextVersion();

    /**
     * 查找某好友信息
     * @param friendId 好友id
     * @return 好友信息
     */
    FriendVO findFriend(@NotNull(message = "好友id不能为空") Long friendId);

    /**
     * 删除好友
     * @param friendId 好友id
     */
    void delFriend(@NotNull(message = "好友id不能为空") Long friendId);

    /**
     * 解除好友绑定（单向）
     * @param userId 本人
     * @param friendId 好友
     */
    void unbindFriend(Long userId, Long friendId);

    /**
     * 设置免打扰模式
     * @param dto 免打扰DTO
     */
    void setDnd(@Valid FriendDndDTO dto);
}
