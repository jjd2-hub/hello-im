package com.him.implatform.service;

import com.him.implatform.dto.FriendDndDTO;
import com.him.implatform.entity.Friend;
import com.him.implatform.vo.FriendVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public interface FriendService {
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

    /**
     * 根据朋友id找到朋友
     * @param friendIds ids
     * @return 朋友
     */
    List<Friend> findByFriendIds(@Size(max = 50, message = "一次最多只能邀请50位用户") @NotEmpty(message = "群id不可为空") List<Long> friendIds);

    /**
     * 判断是否都是本人好友
     * @param userId 本人id
     * @param recvId 好友id
     * @return 正确与否
     */
    Boolean isFriend(Long userId, @NotNull(message = "接收用户id不可为空") Long recvId);

    /**
     * 返回本用户所有好友id
     * @return ids
     */
    List<Long> findFriendIds();
}
